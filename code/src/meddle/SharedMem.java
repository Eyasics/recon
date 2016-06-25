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
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.*;

import org.json.simple.*;
import org.json.simple.parser.*;

/**
 * Shared Information across classes, especially those configurations and data
 * that are the same when training and predicting.
 * */
public class SharedMem {

	public static Logger logger;

	public static String wekaRoot;
	/** the path of configuration for this project */
	public static String CONFIG;
	/** make sure only initialized once */
	public static boolean Initialized = false;

	static Map<String, Integer> stopWords = new HashMap<String, Integer>();
	public static Map<String, Map<String, JSONObject>> domainOSKeys;
	public static Map<String, Map<String, JSONObject>> wildKeys;

	static double lastTimeUpdate = 0; // PENDING

	public static void main(String[] args) {
		Util.getClassifierClassName("SGD");
	}

	public static void init() {
		Initialized = true;
		// Initializing logger
		CONFIG = wekaRoot + "config/meddle-config.txt";
		logger = Logger.getLogger(SharedMem.class.toString());

		if (!RConfig.isConfigLoaded)
			RConfig.loadConfig(CONFIG);
		if (RConfig.debug)
			logger.setLevel(Level.ALL);
		else
			logger.setLevel(Level.FINE);

		// logger.severe("Logging Level:" + logger.getLevel());
		FileHandler fh;
		try {
			String t = new SimpleDateFormat("yyyyMMdd").format(new Date());
			fh = new FileHandler(wekaRoot + "data/logs/overall-" + t + ".txt",
					true);
			SimpleFormatter formater = new SimpleFormatter();
			fh.setFormatter(formater);
		} catch (SecurityException | IOException e) {
			logger.severe(e.getStackTrace().toString());
			System.exit(-1);
		}
		loadStopWords();
		loadDomainOSKeys();
		loadWildKeys();
	}

	public static void loadStopWords() {
		try {
			BufferedReader br = new BufferedReader(new FileReader(
					RConfig.stopwordConfig));
			String line = br.readLine();
			while (line != null && line.length() > 0) {
				if (line.startsWith("#")) {
					line = br.readLine();
					continue;
				}
				String[] ll = line.split("\t");
				if (ll.length > 1)
					stopWords.put(ll[0], Integer.parseInt(ll[1]));
				else
					stopWords.put(line.trim(), 0);
				line = br.readLine();
			}
			br.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@SuppressWarnings("unchecked")
	public static void loadDomainOSKeys() {
		JSONParser parser = new JSONParser();
		String badKeysFileName = RConfig.domainOSKeysFile;
		try {
			BufferedReader br = new BufferedReader(new FileReader(
					badKeysFileName));
			Object obj = parser.parse(br);
			JSONObject obj2 = (JSONObject) obj;
			Iterator<String> iterator = obj2.keySet().iterator();
			domainOSKeys = new HashMap<String, Map<String, JSONObject>>();

			while (iterator.hasNext()) {
				String domainOS = iterator.next();
				JSONObject innerObj = (JSONObject) obj2.get(domainOS);
				Iterator<String> it2 = innerObj.keySet().iterator();
				Map<String, JSONObject> bkItem = new HashMap<String, JSONObject>();
				while (it2.hasNext()) {
					String keyType = it2.next();
					double confidence = (double) innerObj.get(keyType);
					String piiKey = keyType.split(",")[0];
					String reconType = keyType.split(",")[1];
					JSONObject jObj = new JSONObject();
					jObj.put(reconType, confidence);
					bkItem.put(piiKey, jObj);
				}
				domainOSKeys.put(domainOS, bkItem);
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ParseException e) {
			e.printStackTrace();
		}

	}

	@SuppressWarnings("unchecked")
	public static void loadWildKeys() {
		JSONParser parser = new JSONParser();
		String badKeysFileName = RConfig.wildKeysFile;
		try {
			BufferedReader br = new BufferedReader(new FileReader(
					badKeysFileName));
			Object obj = parser.parse(br);
			JSONObject obj2 = (JSONObject) obj;
			Iterator<String> iterator = obj2.keySet().iterator();
			wildKeys = new HashMap<String, Map<String, JSONObject>>();

			while (iterator.hasNext()) {
				String osKeyType = iterator.next();
				String os = osKeyType.split(",")[0];
				String piiKey = osKeyType.split(",")[1];
				String reconType = osKeyType.split(",")[2];
				double confidence = (double) obj2.get(osKeyType);
				Map<String, JSONObject> bkItem = wildKeys.getOrDefault(os,
						new HashMap<String, JSONObject>());
				JSONObject jObj = new JSONObject();
				jObj.put(reconType, confidence);
				bkItem.put(piiKey, jObj);
				wildKeys.put(os, bkItem);
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ParseException e) {
			e.printStackTrace();
		}

	}

}
