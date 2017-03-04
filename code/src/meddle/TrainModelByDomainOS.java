/**
 * This file is part of ReCon.

    ReCon is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Foobar is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with Foobar.  If not, see <http://www.gnu.org/licenses/>."
 */
package meddle;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.*;

import weka.classifiers.*;
import weka.classifiers.functions.*;
import weka.classifiers.lazy.IBk;
import weka.classifiers.meta.*;
import weka.classifiers.trees.*;
import weka.core.*;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class TrainModelByDomainOS {
	// keys for JSON Object of the info
	public final static String TK_FLAG = "tk_flag";
	public final static String NUM_SAMPLES = "num_samples";
	public final static String NUM_POSITIVE = "num_positive";
	public final static String DOMAIN = "domain";
	public final static String DOMAIN_OS = "domain_os";
	public final static String PLATFORM = "platform";
	public final static String iOS = "ios";
	public final static String Android = "android";
	public final static String Windows = "windows";
	public final static String RECON_METHOD_POST = "RECON_METHOD_POST";
	public final static String RECON_METHOD_GET = "RECON_METHOD_GET";
	public final static String RECON_DEVICE_IOS = "RECON_DEVICE_IOS";
	public final static String RECON_DEVICE_ANDROID = "RECON_DEVICE_ANDROID";

	// Class label
	public final static int LABEL_POSITIVE = 1;
	public final static int LABEL_NEGATIVE = 0;
	public final static String CLASS_FEATURE_NAME = "PIILabel";
	public static int MANY = 5000;


	/**
	 * Load all labelled network flows from JSON files and train classifiers for
	 * each domain_os.
	 * 
	 * @param classifierName
	 *            - support for J48, SGD, ...TODO: LIST ALL THAT SUPPORTED
	 */
	public static void trainAllDomains(String classifierName,
			String infoFilePath) {
		JSONParser parser = new JSONParser();
		try {
			if (infoFilePath == null) {
				infoFilePath = RConfig.indexDefJSON;
			}
			Object obj = parser.parse(new FileReader(infoFilePath));
			JSONObject domain_os_reports = (JSONObject) obj;
			int numTrained = 0;
			for (Object k : domain_os_reports.keySet()) {
				String fileName = (String) k;
				if (fileName.equals("general.json")){
					System.out.println("Training general, usually takes 30 mins...");
				}
				
				JSONObject info = (JSONObject) domain_os_reports.get(k);
				int tk_flag = Util.getIntFromJSONObject(info, TK_FLAG);
				int num_pos = Util.getIntFromJSONObject(info, NUM_POSITIVE);
				Info inf = new Info();
				inf.domain = Util.getStringFromJSONObject(info, DOMAIN);
				
				inf.OS = Util.getStringFromJSONObject(info, PLATFORM);
				inf.domainOS = inf.domain + "_" + inf.OS;
				inf.initNumPos = Util.getIntFromJSONObject(info, NUM_POSITIVE);
				inf.initNumTotal = Util.getIntFromJSONObject(info, NUM_SAMPLES);
				inf.initNumNeg = inf.initNumTotal - inf.initNumPos;
				inf.trackerFlag = tk_flag;
				inf.fileNameRelative = fileName;

				// Only train domains that are A&A and have positive flows
				if (tk_flag == 1 && num_pos > 0 && inf.initNumNeg > 0) {
					System.out.println("Training "+ inf.domainOS + " ...");
					trainOneDomain(fileName, classifierName, 0, inf);
					numTrained ++;
				}
			}
			
			System.out.println(numTrained + " <domain,os> trained.");
		} catch (IOException | ParseException e) {
			e.printStackTrace();
		}

	}
	
	/**
	 * Train a classifier given the name of a tracker domain
	 * 
	 * @param jsonDataPathRelative
	 *            - e.g. aax-us-east.amazon-adsystem.com_android.json
	 * @param classifierName
	 *            - e.g. J48
	 * @param thresholdFrequency
	 *            - for filtering infrequency words; e.g. 20
	 * @param info
	 *            - description of the training dataset for a domain_os
	 */
	public static void trainOneDomain(String jsonDataPathRelative,
			String classifierName, int thresholdFrequency, Info info) {
		MetaEvaluationMeasures mem = new MetaEvaluationMeasures();
		mem.info = info;
		// DONE: the general classifier is a specific domain
		long t1 = System.nanoTime();
		TrainingData trainingData = populateTrainingSet(jsonDataPathRelative,
				thresholdFrequency, mem);
		long t2 = System.nanoTime();
		mem.populatingTime = (t2 - t1) / 10e8;

		Classifier classifier;// = new SGD();
		try {
			classifier = getClassifier(classifierName);
			mem = trainWithClassifier(classifier,
					trainingData.trainingInstances, mem.info.domainOS, mem);
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(-1);
		}
	}

	@SuppressWarnings("unchecked")
	public static TrainingData populateTrainingSet(String jsonFilePathRelative,
			int thresholdFrequency, MetaEvaluationMeasures mem) {
		Instances trainingSet = null;
		TrainingData trainingData = new TrainingData();
		trainingData.mem = mem;
		trainingData.mem.numPositive = mem.info.initNumPos;
		trainingData.mem.numNegative = mem.info.initNumNeg;
		trainingData.mem.numTotal = mem.info.initNumTotal;
		trainingData.mem.numInstance = mem.info.initNumTotal;
		JSONParser parser = new JSONParser();
		JSONObject domainOSFlows;
		try {
			domainOSFlows = (JSONObject) parser.parse(new FileReader(
					RConfig.trainingDataFolder + jsonFilePathRelative));
			System.out.println(RConfig.trainingDataFolder + jsonFilePathRelative+domainOSFlows.size());
			int[] changes = balanceClassSamples(
					trainingData.mem.info.initNumPos,
					trainingData.mem.info.initNumNeg, 10);
			if (changes[0] != 0 || changes[1] != 0) {
				// IF the sample needs balancing
				System.out.println("Try to balance the samples...");
				JSONObject posDomainOSFlows = new JSONObject();
				JSONObject negDomainOSFlows = new JSONObject();

				for (Object k : domainOSFlows.keySet()) {
					JSONObject entry = (JSONObject) domainOSFlows.get(k);
					int label = (int) (long) entry.get(JsonKeyDef.F_KEY_LABEL);
					if (label == 1)
						posDomainOSFlows.put(k, entry);
					else
						negDomainOSFlows.put(k, entry);

				}
				// ! Balancing Positive:Negative ratio
				boolean changed = false;
				if (changes[0] > 0) {
					// ! increase positive samples
					// ! by randomly duplicating.
					changed = true;
					// && lenp < 10 && (nn + np) < 100
					//
					Set<String> keySet = posDomainOSFlows.keySet();
					String[] keys = keySet.toArray(new String[0]);
					int repeat = changes[0];
					Random rd = new Random();
					for (int r = 0; r < repeat; r++) {
						int ri = rd.nextInt(keys.length);
						String selectedKey = keys[ri];
						String newK = selectedKey + r;
						Object selectedObj = posDomainOSFlows.get(selectedKey);
						posDomainOSFlows.put(newK, selectedObj);
					}
				}
				if (changes[1] > 0) {
					// ! increase negative samples
					// ! by randomly duplicating.
					changed = true;
					int repeat = changes[1];
					Set<String> keySet = negDomainOSFlows.keySet();
					String[] keys = keySet.toArray(new String[0]);
					Random rd = new Random();
					for (int r = 0; r < repeat; r++) {
						int ri = rd.nextInt(keys.length);
						String selectedKey = keys[ri];
						Object selectedObj = negDomainOSFlows.get(selectedKey);
						String newK = selectedKey + r;
						negDomainOSFlows.put(newK, selectedObj);

					}
				} else if (changes[1] < 0) {
					// ! decrease negative samples
					// ! by randomly removing samples;
					changed = true;
					int repeat = changes[1] * -1;
					Random rd = new Random();
					for (int r = 0; r < repeat; r++) {
						Set<String> keySet = negDomainOSFlows.keySet();
						String[] keys = keySet.toArray(new String[0]);
						int ri = rd.nextInt(keys.length);
						String selectedKey = keys[ri];
						negDomainOSFlows.remove(selectedKey);
					}
				}
				// ! changes[1] == 0 indicts changed=false;
				if (changed) {
					trainingData.mem.numPositive = posDomainOSFlows.size();
					trainingData.mem.numNegative = negDomainOSFlows.size();
					domainOSFlows = new JSONObject();
					for (Object k : posDomainOSFlows.keySet()) {
						domainOSFlows.put(k, posDomainOSFlows.get(k));
					}
					for (Object k : negDomainOSFlows.keySet()) {
						domainOSFlows.put(k, negDomainOSFlows.get(k));
					}
					trainingData.mem.numTotal = domainOSFlows.size();
				}
			}

			if (trainingData.mem.numTotal >= 2 && trainingData.mem.numPositive >= 1) {
				trainingData = populateTrainingMatrix(domainOSFlows,
						trainingData);
				if (trainingData.mem.numOfPossibleFeatures > 5)
					trainingSet = populateArff(mem.info,
							trainingData.wordCount, trainingData.trainMatrix,
							trainingData.piiLabels, trainingData.mem.numTotal,
							thresholdFrequency);
			}

			trainingData.trainingInstances = trainingSet;

		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ParseException e) {
			e.printStackTrace();
		}
		return trainingData;
	}
	
	/**
	 * Give the number of positive samples and the number of negative samples,
	 * find out the balancing values for both parties.
	 * 
	 * @param numFold
	 *            number of folder in evaluation, by default, is 10
	 */
	public static int[] balanceClassSamples(int numPos, int numNeg, int numFold) {
		int numPosChange = 0;
		// numPosChange>= 0, as we don't want to lose positive examples
		// by nature, numPosChange won't be too large? not sure actually.
		int numNegChange = 0;
		if (numPos < numFold && numNeg < numFold) {
			// both classes are too small
			numPosChange = numFold - numPos;
			numNegChange = numFold - numNeg;
		} else if (numPos > numNeg) {
			// more positive numbers
//			if(numPos > MANY )
//			{
//				numPosChange = MANY - numPos;
//			}else{
				numNegChange = numPos - numNeg;
//			}
		} else { // numPos <= numNeg
			if (numNeg > MANY){
				numNegChange = MANY - numNeg;
			}else if (numNeg > 100) {
				if (numPos > 100)
					numNegChange = 100 - numNeg;
				else {
					numNegChange = 100 - numNeg;
					numPosChange = 100 - numPos;
				}
			} else {
				numPosChange = numNeg - numPos;
			}
		}

		int[] changes = new int[2];
		changes[0] = numPosChange; // Change to positive samples
		changes[1] = numNegChange; // Change to negative samples
//		System.out.println(changes[0]);
//		System.out.println(changes[1]);
		return changes;

	}

	/**
	 * Given positive lines and negative lines, generate the overall word_count
	 * and trainMatrix.
	 * 
	 * @param plines
	 *            - ArrayList of lines in positive samples
	 * @param nlines
	 *            - ArrayList of lines in negative samples
	 * @param trainingData
	 *            - the original training data object, could be empty or
	 *            prefilled with some customized entries
	 * @author renjj
	 * */
	public static TrainingData populateTrainingMatrix(JSONObject domainOSFlows,
			TrainingData trainingData) {
		ArrayList<Map<String, Integer>> trainMatrix = trainingData.trainMatrix;
		ArrayList<Integer> piiLabels = trainingData.piiLabels;
		Map<String, Integer> word_count = trainingData.wordCount;
		int numOfPossibleFeatures = word_count.size();
		for (Object k : domainOSFlows.keySet()) {
			JSONObject flow = (JSONObject) domainOSFlows.get(k);
			int label = (int) (long) flow.get(JsonKeyDef.F_KEY_LABEL);
			String line = "";
			// fields: uri, post_body, refererrer, headers+values,
			line += flow.get(JsonKeyDef.F_KEY_URI) + "\t";
			line += flow.get(JsonKeyDef.F_KEY_POST_BODY) + "\t";
			line += flow.get(JsonKeyDef.F_KEY_REFERRER) + "\t";
			JSONObject headers = (JSONObject) flow.get(JsonKeyDef.F_KEY_HEADERS);
			for (Object h : headers.keySet()) {
				line += h + "=" + headers.get(h) + "\t";
			}
			line += flow.get(JsonKeyDef.F_KEY_DOMAIN) + "\t";
			RString sf = new RString();
			sf.breakLineIntoWords(line);
			Map<String, Integer> words = sf.Words;
			for (Map.Entry<String, Integer> entry : words.entrySet()) {
				String word_key = entry.getKey().trim();
				if (word_key.length() == 1) {
					char c = word_key.toCharArray()[0];
					if (!Character.isAlphabetic(c) && !Character.isDigit(c))
						continue;
				}
				if (RString.isStopWord(word_key)
						|| RString.isAllNumeric(word_key))
					continue;
				if (word_key.length() == 0)
					continue;

				int frequency = entry.getValue();
				if (word_count.containsKey(word_key))
					word_count.put(word_key,
							frequency + word_count.get(word_key));
				else {
					numOfPossibleFeatures++;
					word_count.put(word_key, frequency);
				}
			}
			trainMatrix.add(words);
			piiLabels.add(label);

		}
		trainingData.wordCount = word_count;
		trainingData.trainMatrix = trainMatrix;
		trainingData.piiLabels = piiLabels;
		trainingData.mem.numOfPossibleFeatures = numOfPossibleFeatures;
		return trainingData;
	}

	public static Instances populateArff(Info info,
			Map<String, Integer> wordCount,
			ArrayList<Map<String, Integer>> trainMatrix,
			ArrayList<Integer> PIILabels, int numSamples, int theta) {
//		System.out.println(info);
		// Mapping feature_name_index
		Map<String, Integer> fi = new HashMap<String, Integer>();
		int index = 0;
		// Populate Features
		ArrayList<Attribute> attributes = new ArrayList<Attribute>();
		int high_freq = trainMatrix.size();

		if (high_freq - theta < 30)
			theta = 0;
		for (Map.Entry<String, Integer> entry : wordCount.entrySet()) {
			// filter low frequency word
			String currentWord = entry.getKey();
			int currentWordFreq = entry.getValue();
			if (currentWordFreq < theta) {
				if (!SharedMem.wildKeys.get("android").containsKey(currentWord)
						&& !SharedMem.wildKeys.get("ios").containsKey(currentWord)
						&& !SharedMem.wildKeys.get("windows").containsKey(currentWord))
					continue;
			}
			Attribute attribute = new Attribute(currentWord);
			attributes.add(attribute);
			fi.put(currentWord, index);
			index++;
		}

		ArrayList<String> classVals = new ArrayList<String>();
		classVals.add("" + LABEL_NEGATIVE);
		classVals.add("" + LABEL_POSITIVE);
		attributes.add(new Attribute("PIILabel", classVals));

		// Populate Data Points
		Iterator<Map<String, Integer>> all = trainMatrix.iterator();
		int count = 0;
		Instances trainingInstances = new Instances("Rel", attributes, 0);
		trainingInstances.setClassIndex(trainingInstances.numAttributes() - 1);
		while (all.hasNext()) {
			Map<String, Integer> dataMap = all.next();
			double[] instanceValue = new double[attributes.size()];
			for (int i = 0; i < attributes.size() - 1; i++) {
				instanceValue[i] = 0;
			}
			int label = PIILabels.get(count);
			instanceValue[attributes.size() - 1] = label;
			for (Map.Entry<String, Integer> entry : dataMap.entrySet()) {
				if (fi.containsKey(entry.getKey())) {
					int i = fi.get(entry.getKey());
					int val = entry.getValue();
					instanceValue[i] = val;
				}
			}
			Instance data = new SparseInstance(1.0, instanceValue);
			trainingInstances.add(data);
			count++;
		}
		// Write into .arff file for persistence
		try {
			BufferedWriter bw = new BufferedWriter(new FileWriter(
					RConfig.arffFolder + info.domainOS + ".arff"));
			bw.write(trainingInstances.toString());
			bw.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return trainingInstances;
	}

	public static MetaEvaluationMeasures trainWithClassifier(
			Classifier classifier, Instances trainingSet, String domainOS,
			MetaEvaluationMeasures mem) {
		try {
			long t1 = System.nanoTime();
			String classifierName = classifier.getClass().toString();
			classifierName = classifierName.substring(classifierName
					.lastIndexOf(".") + 1);
			if(trainingSet.numInstances() > MANY){
				System.out.println("This might take over 30 minutes for "+ trainingSet.numInstances() + " samples ..." );
			}
			classifier.buildClassifier(trainingSet);
			long t2 = System.nanoTime();
			

			double trainingTime = (t2 - t1) / 10e8;

			if (RConfig.enableCrossValidation) {
				mem.trainingTime = trainingTime;
				mem = doEvaluation(classifier, domainOS, trainingSet, mem);
				Util.appendLineToFile(RConfig.logFolder + "eval.txt",
						mem.recordJSONFormat());
			}
			if (RConfig.enableGraphicOutput && classifierName.equals("J48")) {

				doGraphicOutput((J48) classifier, domainOS, mem);
			}
			SerializationHelper.write(RConfig.modelFolder + domainOS + "-"
					+ classifier.getClass().toString().substring(6) + ".model",
					classifier);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return mem;
	}

	/**
	 * Given the classifierName, return a classifier
	 * 
	 * @param classifierName
	 *            e.g. J48, Bagging etc.
	 */
	public static Classifier getClassifier(String classifierName) {
		Classifier classifier = null;
		if (classifierName.equals("J48")) {
			J48 j48 = new J48();
			j48.setUnpruned(true);
			classifier = j48;
		} else if (classifierName.equals("AdaBoostM1")) {
			AdaBoostM1 adm = new AdaBoostM1();
			adm.setNumIterations(10);
			J48 j48 = new J48();
			adm.setClassifier(j48);
			classifier = adm;
		} else if (classifierName.equals("Bagging")) {
			Bagging bagging = new Bagging();
			bagging.setNumIterations(10);
			J48 j48 = new J48();
			bagging.setClassifier(j48);
			classifier = bagging;
		} else if (classifierName.equals("Stacking")) {
			Stacking stacking = new Stacking();
			stacking.setMetaClassifier(new Logistic());
			Classifier cc[] = new Classifier[2];
			cc[0] = new J48();
			cc[1] = new IBk();
			stacking.setClassifiers(cc);
			classifier = stacking;
		} else if (classifierName.equals("AdditiveRegression")) {
			AdditiveRegression ar = new AdditiveRegression();
			ar.setClassifier(new J48());
			classifier = ar;
		} else if (classifierName.equals("LogitBoost")) {
			LogitBoost lb = new LogitBoost();
			lb.setClassifier(new J48());
			classifier = lb;
		}
		return classifier;
	}

	/**
	 * Do evalution on trained classifier/model, including the summary, false
	 * positive/negative rate, AUC, running time
	 * 
	 * @param j48
	 *            - the trained classifier
	 * @param domain
	 *            - the domain name
	 */
	public static MetaEvaluationMeasures doEvaluation(Classifier classifier,
			String domainOS, Instances tras, MetaEvaluationMeasures mem) {
		try {
			Evaluation evaluation = new Evaluation(tras);
			evaluation.crossValidateModel(classifier, tras, 10, new Random(1));
			mem.numInstance = evaluation.numInstances();
			double M = evaluation.numTruePositives(1)
					+ evaluation.numFalseNegatives(1);
			mem.numPositive = (int) M;
			mem.AUC = evaluation.areaUnderROC(1);
			mem.numCorrectlyClassified = (int) evaluation.correct();
			mem.accuracy = 1.0 * mem.numCorrectlyClassified / mem.numInstance;
			mem.falseNegativeRate = evaluation.falseNegativeRate(1);
			mem.falsePositiveRate = evaluation.falsePositiveRate(1);
			mem.fMeasure = evaluation.fMeasure(1);
			double[][] cmMatrix = evaluation.confusionMatrix();
			mem.confusionMatrix = cmMatrix;
			mem.TP = evaluation.numTruePositives(1);
			mem.TN = evaluation.numTrueNegatives(1);
			mem.FP = evaluation.numFalsePositives(1);
			mem.FN = evaluation.numFalseNegatives(1);
		} catch (Exception e) {
			e.printStackTrace();
		}

		return mem;
	}

	/**
	 * Given a classifier model, print out the tree graph and its html report.
	 * 
	 * @param j48
	 *            - the classifier model
	 * @param domainOS
	 *            - domain,os name
	 * @param mem
	 * 			  - the measurement results           
	 * */
	public static void doGraphicOutput(J48 j48, String domainOS, MetaEvaluationMeasures mem) {
		try {

			String on = RConfig.dtGraphFolder + domainOS + ".dot";
			String png = RConfig.dtGraphFolder + domainOS + ".png";
			BufferedWriter bw = new BufferedWriter(new FileWriter(on));
			bw.write(j48.graph());
			bw.close();

			String sum = "<h3>" + mem.info.domain + "(" + mem.info.OS + ")</h3>\n";
			sum += "Accuracy: " + mem.accuracy + "<br/> \n";
			sum += "AUC: " + mem.AUC + "<br/> \n";
			sum += "False Positive Rate: " + mem.falsePositiveRate + "<br/> \n";
			sum += "False Negative Rate: " + mem.falseNegativeRate + "<br/> <br/> \n";
			
			
			sum += "Initial Number of Samples: " + mem.info.initNumTotal + "<br/>\n";
			sum += "Initial Number of Positive: " + mem.info.initNumPos + "<br/>\n";
			sum += "Initial Number of Negative: " + mem.info.initNumNeg + "<br/><br/>\n";
			sum += ">>> After balancing ...<br/> \n";
			sum += "Trained Number of Samples: " + mem.numTotal + "<br/>\n";
			sum += "Trained Number of Positive: " + mem.numPositive + "<br/>\n";
			sum += "Trained Number of Negative: " + mem.numNegative + "<br/></br/>\n";
			

			
			sum += "<img src='" + domainOS + ".png'/><br/> <br/> \n";
			sum += j48.toString().replace("\n", "<br/>");
			sum += "Number of Rules: " + j48.measureNumRules() + "<br/>\n";
			sum += "<a href='../domain_os/"+ mem.info.fileNameRelative + "'>training data</a>";
			
			sum = "<html><body>" + sum;
			sum += "</body></html>";
			String cmd = RConfig.DOT_PATH + " -o " + png + " " + on + " -Tpng";
			// http://www.graphviz.org/content/output-formats#ddot
			// need to install graphviz tool
			// Mac OX: brew install graphviz
			// Ubuntu: sudo apt-get install graphviz
			String html = RConfig.dtGraphFolder + domainOS + ".html";
			bw = new BufferedWriter(new FileWriter(html));
			bw.write(sum);
			bw.close();
			Runtime.getRuntime().exec(cmd);
		} catch (IOException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	/**
	 * Copy arff and model file to archive folder with date information
	 * */
	public static boolean archiveOldClassifier(String oldArffPath,
			String oldModelPath, String targetArffPath, String targetModelPath) {
		Date dt = new Date();
		SimpleDateFormat ft = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
		String dateString = ft.format(dt);
		System.out.println(dateString);
		try {
			Files.copy(new File(oldArffPath).toPath(), new File(targetArffPath
					+ "." + dateString).toPath(),
					StandardCopyOption.REPLACE_EXISTING);
			Files.copy(new File(oldModelPath).toPath(), new File(
					targetModelPath + "." + dateString).toPath(),
					StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}

	/**
	 * @param modelPath
	 * @param arffPath
	 * @param org
	 */
	public static MetaEvaluationMeasures loadAndEvaluateClassifier(
			String modelPath, String arffPath, String org) {
		MetaEvaluationMeasures mem = null;
		try {
			if ((new File(modelPath).exists())) {
				J48 j48 = (J48) SerializationHelper.read(modelPath);
				// Read data from
				BufferedReader reader = new BufferedReader(new FileReader(
						arffPath));
				Instances data = new Instances(reader);
				reader.close();
				// setting class attribute
				data.setClassIndex(data.numAttributes() - 1);
				mem = doEvaluation(j48, org, data, new MetaEvaluationMeasures());

			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return mem;
	}

}

class TrainingData {
	// feature_name:count, for freqency of word in a specific domain
	public Map<String, Integer> wordCount;
	public ArrayList<Map<String, Integer>> trainMatrix;
	public ArrayList<Integer> piiLabels;
	public Instances trainingInstances;
	
	public MetaEvaluationMeasures mem;
	
	public TrainingData(){
		wordCount = new HashMap<String, Integer>();
		trainMatrix = new ArrayList<Map<String, Integer>>();
		piiLabels = new ArrayList<Integer>();
		mem = new MetaEvaluationMeasures();
	}

}

/**
 * Intermediate and final results during training a classifier for the domain,os. 
 * */
class MetaEvaluationMeasures {
	public double falsePositiveRate;
	public double falseNegativeRate;
	public double trainingTime;
	public double populatingTime;
	
	public int numTotal;
	public int numPositive;
	public int numNegative;
	public int numOfPossibleFeatures;

	public double AUC;
	public double fMeasure;
	public double numInstance;
	public int numCorrectlyClassified;
	public double accuracy; // = NumCorrectlyClassified / NumTotal
	public double[][] confusionMatrix;
	public double TP;
	public double TN;
	public double FP;
	public double FN;
	public Info info;

	public String recordForInitialTrain() {
		String str = "";
		str += this.info.domain + "\t";
		str += this.info.OS + "\t";
		str += String.format("%.4f", this.accuracy) + "\t";
		str += String.format("%.4f", this.falsePositiveRate) + "\t";
		str += String.format("%.4f", this.falseNegativeRate) + "\t";
		str += String.format("%.4f", this.AUC) + "\t";
		str += String.format("%.4f", this.trainingTime) + "\t";

		str += this.numPositive + "\t";
		str += this.numNegative + "\t";
		str += this.numTotal + "\t";

		str += this.info.initNumPos + "\t"; // # positive samples initially
		str += this.info.initNumNeg + "\t";
		str += this.info.initNumTotal + "\t";

		return str;
	}

	@SuppressWarnings("unchecked")
	public String recordJSONFormat() {
		String str = "";
		JSONObject obj = new JSONObject();
		obj.put("domain", info.domain);
		obj.put("os", info.OS);
		obj.put("domain_os", info.domainOS);
		obj.put("json_file", info.fileNameRelative);
		obj.put("accuracy", this.accuracy);
		obj.put("fpr", falsePositiveRate);
		obj.put("fnr", falseNegativeRate);
		obj.put("auc", AUC);
		obj.put("traing_time", trainingTime);
		obj.put("populating_time", populatingTime);

		obj.put("init_num_pos", info.initNumPos);
		obj.put("init_num_neg", info.initNumNeg);
		obj.put("init_num_total", info.initNumTotal);

		obj.put("num_pos", this.numPositive);
		obj.put("num_neg", this.numNegative);
		obj.put("num_total", this.numTotal);

		str = obj.toJSONString() + "";
		System.out.println(str);
		return str;
	}

}

/**
 * Information of the training data set, or description of the network flows.
 * */
class Info {

	public String domain;
	public String OS;
	public String domainOS;
	public String fileNameRelative;
	public int initNumPos;
	public int initNumNeg;
	public int initNumTotal;
	public int trackerFlag;

}