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

import java.util.Map;
public class RConfig {
	// =========start of new definition as of May 5, 2016=====================
	// TEMPORARY: hard coded FOR NOW
	public static String trainingDataFolder="data/domain_os/";
	public static String arffFolder = "data/arff/";
	public static String modelFolder = "data/model/";
	public static String dtGraphFolder = "data/dt/";
	public static String logFolder = "data/logs/";
	public static String indexDefFile = "data/index_dat.csv";
	public static String indexDefJSON = "data/index_dat.json";
	public static String domainOSKeysFile = "config/domainOSKeys.json";
	public static String wildKeysFile = "config/wildKeys.json";
	
	public static boolean enableCrossValidation = true;
	public static boolean enableGraphicOutput = false;
	public static boolean enableDB = false;
	
	public static String DOT_PATH = "/usr/local/bin/dot";
	// =========end of new definition as of May 5, 2016=====================
	public static boolean isConfigLoaded = false;

	public static Map<String, String> configs;
	public static int numOfDataPoints = 1000;
	public static int numOfFeatures = 0;
	public static int numOfTestPoints = 500;
	public static double ratio = 0.7;
	public static String trainSetDir;
	public static String testSetDir;
	public static String goodFieldConfig;
	public static String userConfig;
	public static String ruleOutConfig;
	public static String featureNameFile;
	public static String trainSetArff;
	public static String trainSetFilteredArff;
	public static String structureArff;
	public static String randstringTxt;
	public static boolean isLoad = false;
	public static boolean more = false;
	public static boolean debug = false;
	public static String selectedFeatureNameFile;
	public static String testSetArff;
	public static String testSetFilteredArff;
	public static String dataNegative;
	public static String dataPositive;
	public static double pTon;
	public static String trackerFile;
	public static boolean trackerFromDB;
	public static String dataDomain0;
	public static String dataDomain1;
	public static int thresholdOfFrequency;
	public static String realUserPcapDataPath;
	public static String dataModel;
	public static String dataArff;
	public static String generalType;
	public static String badKeysConfig;
	public static String wildBadKeysConfig;
	public static String broLogDecryptedPath;
	public static String positivelyPredictedFolder;
	public static String negativelyPredictedFolder;
	public static String wekaRoot;
	public static String trackersFile;
	public static String dataLogs;
	public static String IOS_PREFIX = "10.11.4."; // 
	public static String ANDROID_PREFIX = "10.11.3.";
	public static String stopwordConfig;
	public static String fullirbusersConfig;
	public static String logsDecrypted;
	public static String orgUpdateConfig;
	static final String delimiters = "\\r\\n\\t\\\\/ ?!,=&()[]{}\"\';*+|";
	static String dbPswd = "M3ddling";
	
	public static void loadConfig(String CONFIG) {
		configs = Util.readConfig(CONFIG);
		// INT
		numOfDataPoints = getIntFromConfig("numOfDataPoints");
		numOfTestPoints = getIntFromConfig("numOfDataPoints");
		thresholdOfFrequency = getIntFromConfig("thresholdOfFrequency");

		// DOUBLE
		ratio = getDoubleFromConfig("ratio");
		pTon = getDoubleFromConfig("pTon");
		ratio = getDoubleFromConfig("ratio");
		ratio = getDoubleFromConfig("ratio");

		// STRING
		// wekaRoot = getStringFromConfig("wekaRoot");
		if (!SharedMem.Initialized) {
			SharedMem.init();
		}
		wekaRoot = SharedMem.wekaRoot;
		// trackersFile
		trackersFile = wekaRoot + getStringFromConfig("trackersFile");
		dataLogs = wekaRoot + getStringFromConfig("dataLogs");
		trainSetDir = wekaRoot + getStringFromConfig("trainSetDir");
		testSetDir = wekaRoot + getStringFromConfig("testSetDir");
		goodFieldConfig = wekaRoot + getStringFromConfig("goodFieldConfig");
		userConfig = wekaRoot + getStringFromConfig("userConfig");
		ruleOutConfig = wekaRoot + getStringFromConfig("ruleOutConfig");
		featureNameFile = wekaRoot + getStringFromConfig("featureNameFile");
		trainSetArff = wekaRoot + getStringFromConfig("trainSetArff");
		structureArff = wekaRoot + getStringFromConfig("structureArff");
		trainSetFilteredArff = wekaRoot
				+ getStringFromConfig("trainSetFilteredArff");
		randstringTxt = wekaRoot + getStringFromConfig("randstringTxt");
		selectedFeatureNameFile = wekaRoot
				+ getStringFromConfig("selectedFeatureNameFile");
		testSetArff = wekaRoot + getStringFromConfig("testSetArff");
		testSetFilteredArff = wekaRoot
				+ getStringFromConfig("testSetFilteredArff");
		dataPositive = wekaRoot + getStringFromConfig("dataPositive");
		dataNegative = wekaRoot + getStringFromConfig("dataNegative");
		trackerFile = wekaRoot + getStringFromConfig("trackerFile");
		dataDomain0 = wekaRoot + getStringFromConfig("dataDomain0");
		dataDomain1 = wekaRoot + getStringFromConfig("dataDomain1");
		dataModel = wekaRoot + getStringFromConfig("dataModel");
		realUserPcapDataPath = wekaRoot
				+ getStringFromConfig("realUserPcapDataPath");
		dataArff = wekaRoot + getStringFromConfig("dataArff");
		generalType = wekaRoot + getStringFromConfig("generalType");
		badKeysConfig = wekaRoot + getStringFromConfig("badKeysConfig");
		wildBadKeysConfig = wekaRoot + getStringFromConfig("wildBadKeysConfig");
		broLogDecryptedPath = wekaRoot
				+ getStringFromConfig("broLogDecryptedPath");
		positivelyPredictedFolder = wekaRoot
				+ getStringFromConfig("positivelyPredictedFolder");
		negativelyPredictedFolder = wekaRoot
				+ getStringFromConfig("negativelyPredictedFolder");
		stopwordConfig = wekaRoot + getStringFromConfig("stopwordConfig");
		fullirbusersConfig = wekaRoot + getStringFromConfig("fullirbusersConfig");
		logsDecrypted = wekaRoot + getStringFromConfig("logsDecrypted");
		orgUpdateConfig = wekaRoot + getStringFromConfig("orgUpdateConfig");
		
		
		IOS_PREFIX = getStringFromConfig("IOS_PREFIX");
		ANDROID_PREFIX = getStringFromConfig("ANDROID_PREFIX");
		dbPswd = getStringFromConfig("dbPassword");

		// BOOLEAN
		isLoad = getBooleanFromConfig("isLoad");
		trackerFromDB = getBooleanFromConfig("trackerFromDB");
		debug = getBooleanFromConfig("debug");
		System.out.println(debug);
		isConfigLoaded = true;
	}

	private static String getStringFromConfig(String name) {
		return configs.get(name);
	}

	private static int getIntFromConfig(String name) {
		if (configs.get(name) != null)
			return Integer.parseInt(configs.get(name));
		else
			return -1;
	}

	private static double getDoubleFromConfig(String name) {
		if (configs.get(name) != null)
			return Double.parseDouble(configs.get(name));
		else
			return -1.0;
	}

	private static boolean getBooleanFromConfig(String name) {
		if (configs.get(name)!=null && configs.get(name).equals("true"))
			return true;
		else
			return false;
	}

}
