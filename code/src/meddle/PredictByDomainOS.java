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
import java.net.URLDecoder;
import java.util.*;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import weka.classifiers.Classifier;
import weka.core.*;
import weka.core.converters.ArffLoader;

public class PredictByDomainOS {

	public static boolean isModelLoaded = false;
	public static final String GENERAL_CLASSIFIER = "general_all";
	public static Map<String, Classifier> domainOSModel;
	public static Map<String, Map<String, Integer>> domainOSFeature;
	public static Map<String, Instances> domainOSStruct;

	//
	// public static void main(String[] args) {
	// init("J48");
	// predictJSONFile(
	// "data/domain_os/aax-us-east.amazon-adsystem.com_android.json",
	// "J48");
	// }

	public static void init(String classifierName) {
		// SharedInfo
		loadAllModels(Util.getClassifierClassName(classifierName));

	}

	public static boolean loadAllModels(String className) {
		domainOSModel = new HashMap<String, Classifier>();
		domainOSFeature = new HashMap<String, Map<String, Integer>>();
		domainOSStruct = new HashMap<String, Instances>();
		try {
			File modelFolder = new File(RConfig.modelFolder);
			File[] models = modelFolder.listFiles();
			if (models != null)
				for (int i = 0; i < models.length; i++) {
					String fn = models[i].getName();
					if (!fn.endsWith(className + ".model"))
						continue;
					String domainOS = fn.substring(
							0,
							fn.length() - className.length()
									- ".model".length() - 1);
					Classifier classifier;
					classifier = (Classifier) (Class.forName(className)
							.newInstance());
					classifier = (Classifier) SerializationHelper
							.read(RConfig.modelFolder + fn);
					// System.out.println(domainOS);
					domainOSModel.put(domainOS, classifier);

					ArffLoader loader = new ArffLoader();
					String arffStructureFile = RConfig.arffFolder + domainOS
							+ ".arff";
					File af = new File(arffStructureFile);
					if (!af.exists())
						continue;
					loader.setFile(new File(arffStructureFile));
					Instances structure;
					try {
						structure = loader.getStructure();
					} catch (Exception e) {
						continue;
					}
					structure.setClassIndex(structure.numAttributes() - 1);
					domainOSStruct.put(domainOS, structure);
					Map<String, Integer> fi = new HashMap<String, Integer>();
					for (int j = 0; j < structure.numAttributes(); j++) {
						fi.put(structure.attribute(j).name(), j);
					}
					domainOSFeature.put(domainOS, fi);
				}
		} catch (InstantiationException | IllegalAccessException
				| ClassNotFoundException e) {
			e.printStackTrace();
			return false;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
		isModelLoaded = true;
		return true;
	}

	/**
	 * Read from a JSON file that has a list of flows and predict on each of
	 * them.
	 * 
	 * @param flowJSONFilePath
	 *            - Absolute path of the JSON file for new flows; the flows can
	 *            be sent to any host; prerequesites: assigned with proper
	 *            package name.
	 * 
	 * */
	@SuppressWarnings("unchecked")
	public static int predictJSONFile(String flowJSONFilePath,
			String classifierSelected) {
		JSONParser parser = new JSONParser();
		JSONObject domainOSFlows;
		try {
			domainOSFlows = (JSONObject) parser.parse(new FileReader(
					flowJSONFilePath));
			for (Object k : domainOSFlows.keySet()) {
				JSONObject flow = (JSONObject) domainOSFlows.get(k);

				String domain = Util.getStringFromJSONObject(flow,
						JsonKeyDef.F_KEY_DOMAIN);
				String OS = Util.getStringFromJSONObject(flow,
						JsonKeyDef.F_KEY_PLATFORM);
				int trueLabel = Util.getIntFromJSONObject(flow,
						JsonKeyDef.F_KEY_LABEL);
				String domainOS = domain + "_" + OS;
				String line = "";
				// fields: uri, post_body, refererrer, headers+values,
				line += flow.get(JsonKeyDef.F_KEY_URI) + "\t";
				line += flow.get(JsonKeyDef.F_KEY_POST_BODY) + "\t";
				line += flow.get(JsonKeyDef.F_KEY_REFERRER) + "\t";
				JSONObject headers = (JSONObject) flow
						.get(JsonKeyDef.F_KEY_HEADERS);
				for (Object h : headers.keySet()) {
					line += h + "=" + headers.get(h) + "\t";
				}
				line += flow.get(JsonKeyDef.F_KEY_DOMAIN);

				try {
					line = URLDecoder.decode(line, "utf-8");
					line = URLDecoder.decode(line, "utf-8");
				} catch (UnsupportedEncodingException
						| java.lang.IllegalArgumentException e) {
					// System.err.println("decoding failed for this line!!!");
					// e.printStackTrace();
				}
				boolean isPositive = false;
				// whether predicted by a classifier
				boolean isPredicted = false;

				int predictLabel = 0;
				JSONObject predictResult = new JSONObject();
				predictResult.put("trueLabel", trueLabel);
				if (domainOSModel.containsKey(domainOS)) {
					isPredicted = true;
					isPositive = predictOneFlow(line, domainOS);
				} else if (domainOSModel.containsKey(GENERAL_CLASSIFIER)) {
					isPredicted = true;
					isPositive = predictOneFlow(line, GENERAL_CLASSIFIER);
				}

				RString sf = new RString();
				sf.breakLineIntoWords(line);
				sf.findKeyValuePairs();
				List<JSONObject> foundList = null;
				if (isPositive) {
					predictLabel = 1;
					if (sf.keyValuePairs.size() > 0) {
						foundList = ExtractPIIFromFlow.run(sf.keyValuePairs,
								false, domain, OS);
						List<JSONObject> foundList2 = ExtractPIIFromFlow.run(sf.Words);
						foundList.addAll(foundList2);
					}
				}

				if (!isPredicted) {
					foundList = ExtractPIIFromFlow.run(sf.keyValuePairs, true,
							domain, OS);
					List<JSONObject> foundList2 = ExtractPIIFromFlow.run(sf.Words);
					foundList.addAll(foundList2);
				}
				Map<String, Integer> piiTypes = null;
				Map<String, String> keyValues = null;
				if (foundList != null && !foundList.isEmpty()) {
					piiTypes = new HashMap<String, Integer>();
					keyValues = new HashMap<String, String>();
					for (JSONObject obj : foundList) {
						String type = Util.getStringFromJSONObject(obj, "t")
								.trim();
						String key = Util.getStringFromJSONObject(obj, "k")
								.trim();
						String value = Util.getStringFromJSONObject(obj, "v")
								.trim();
						piiTypes.put(type, piiTypes.getOrDefault(type, 0) + 1);
						predictLabel = 1;
						String typeKey = key + "|" + type;
						keyValues.put(typeKey, value);
					}

				}

				predictResult.put("predictLabel", predictLabel);
				predictResult.put("flowID", k);
				predictResult.put("os", OS);
				predictResult.put("domain", domain);
				predictResult.put("predict_piiTypes", piiTypes);
				predictResult.put("key_values", keyValues);
				imcResult(predictResult);

				
				// TODO: Save predictResult to a file
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ParseException e) {
			e.printStackTrace();
		}
		return -1;
	}

	private static boolean predictOneFlow(String line, String domainOS) {
		if (!domainOSModel.containsKey(domainOS))
			return false;
		else {
			try {
				Classifier classifier = domainOSModel.get(domainOS);
				Map<String, Integer> fi = domainOSFeature.get(domainOS);
				Instances structure = domainOSStruct.get(domainOS);
				Instance current = getInstance(line, fi, fi.size());

				Instances is = new Instances(structure);
				is.setClassIndex(is.numAttributes() - 1);
				is.add(current);
				current = is.get(is.size() - 1);
				current.setClassMissing();
				double predicted = classifier.classifyInstance(current);
				if (predicted > 0) {
					return true;
				} else
					return false;
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return false;
	}

	private static Instance getInstance(String line, Map<String, Integer> fi,
			int numAttributes) {
		double instanceValues[] = new double[numAttributes];
		for (int i = 0; i < numAttributes; i++) {
			instanceValues[i] = 0;
		}
		RString sf = new RString();
		sf.breakLineIntoWords(line);
		Map<String, Integer> words = sf.Words;

		for (Map.Entry<String, Integer> entry : words.entrySet()) {
			String key = entry.getKey();
			int val = entry.getValue();
			if (fi.containsKey(key)) {
				instanceValues[fi.get(key)] = val;
			}
		}
		instanceValues[numAttributes - 1] = 0;
		Instance instance = new DenseInstance(1.0, instanceValues);
		return instance;
	}
	
	private static void imcResult(JSONObject predictResult){
		System.out.println(predictResult); 
	}

}
