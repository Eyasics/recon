/**
 * This file is part of ReCon.
     Copyright (C) 2016  Jingjing Ren, Northeastern University.

     This program is free software; you can redistribute it and/or
     modify it under the terms of the GNU General Public License
     as published by the Free Software Foundation; either version 2
     of the License, or (at your option) any later version.

     This program is distributed in the hope that it will be useful,
     but WITHOUT ANY WARRANTY; without even the implied warranty of
     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
     GNU General Public License for more details.

     You should have received a copy of the GNU General Public License
     along with this program; if not, write to the Free Software
     Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.

 */
package meddle;

import java.io.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

import org.json.simple.JSONObject;

import weka.associations.Apriori;
import weka.classifiers.bayes.NaiveBayes;
import weka.classifiers.bayes.NaiveBayesUpdateable;
import weka.classifiers.functions.SGD;
import weka.classifiers.functions.SGDText;
import weka.classifiers.meta.AdaBoostM1;
import weka.classifiers.meta.AdditiveRegression;
import weka.classifiers.meta.Bagging;
import weka.classifiers.meta.LogitBoost;
import weka.classifiers.meta.Stacking;
import weka.classifiers.rules.PART;
import weka.classifiers.trees.J48;

/** IO Tools and other auxiliary functions */
public class Util {

	static final String AB = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
	static final String AB1= "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
	static final String LETTERS= "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
	static boolean debug = true;

	public static String filterASCII(String line) {
		line = line.replaceAll("[\\\\][x][0-9a-e][0-9a-f]", "");
		System.out.println(line);
		return line;
	}

	public static Map<String, String> readConfig(String filename) {
		Map<String, String> map = new HashMap<String, String>();
		try {
			BufferedReader br = new BufferedReader(new FileReader(filename));
			String line = br.readLine();
			while (line != null && !line.trim().equals("")) {
				if (line.startsWith("#")) {
					line = br.readLine();
					continue;
				}
				String[] values = line.split("=");
				if (values.length >= 2)
					map.put(values[0].trim(), values[1].trim());
				line = br.readLine();
			}
			br.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return map;
	}

	public static ArrayList<String> readValues(String filename) {
		ArrayList<String> list = new ArrayList<String>();
		try {
			BufferedReader br = new BufferedReader(new FileReader(filename));
			String line = br.readLine();
			while (line != null) {
				line = line.trim().toLowerCase();
				if (line.length() > 0 && !line.startsWith("#"))
					list.add(line);
				line = br.readLine();
			}
			br.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return list;
	}

	public static ArrayList<String> readValues(String filename, boolean keepCase) {
		ArrayList<String> list = new ArrayList<String>();
		try {
			BufferedReader br = new BufferedReader(new FileReader(filename));
			String line = br.readLine();
			while (line != null) {
				if (!keepCase)
					line = line.trim().toLowerCase();
				if (line.length() > 0 && !line.startsWith("#"))
					list.add(line);
				line = br.readLine();
			}
			br.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return list;
	}

	public static void printMap(Map<String, String> map) {
		System.out.println("ALL key value pairs:");
		for (Map.Entry<String, String> entry : map.entrySet()) {
			String key = entry.getKey();
			String value = entry.getValue();
			System.out.println(key + "\t" + value);
		}
	}

	public static void printDict(Map<String, String> map) {
		for (Map.Entry<String, String> entry : map.entrySet()) {
			System.out.println(entry.getKey().toString() + ":"
					+ entry.getValue());
		}
	}

	public static void printDictArray(Map<String, ArrayList<String>> map) {
		for (Map.Entry<String, ArrayList<String>> entry : map.entrySet()) {
			System.out.println(entry.getKey().toString() + ":");
			Iterator<String> tmp = entry.getValue().iterator();
			while (tmp.hasNext()) {
				System.out.println("\t" + tmp.next());
			}
		}
	}

	public static void printDictInt(Map<String, Integer> map) {
		for (Map.Entry<String, Integer> entry : map.entrySet()) {
			System.out.println(entry.getKey().toString() + ":"
					+ entry.getValue());
		}
	}

	public static Map<String, Integer> sortMap(Map<String, Integer> map) {
		List<Map.Entry<String, Integer>> entries = new ArrayList<Map.Entry<String, Integer>>(
				map.entrySet());
		Collections.sort(entries, new Comparator<Map.Entry<String, Integer>>() {
			public int compare(Map.Entry<String, Integer> a,
					Map.Entry<String, Integer> b) {
				return b.getValue().compareTo(a.getValue());
			}
		});
		Map<String, Integer> sortedMap = new LinkedHashMap<String, Integer>();
		for (Map.Entry<String, Integer> entry : entries) {
			sortedMap.put(entry.getKey(), entry.getValue());
		}
		return sortedMap;
	}

	public static void printDictIntTop(Map<String, Integer> map, int top) {
		int i = 0;
		for (Map.Entry<String, Integer> entry : map.entrySet()) {
			if (i < top) {
				System.out.println(entry.getKey().toString());
				i++;
			} else {
				System.out.println(entry.getKey().toString() + ":"
						+ entry.getValue());
				break;
			}
		}
	}

	public static String randomString(int len) {
		Random rnd = new Random();
		StringBuilder sb = new StringBuilder(len);
		for (int i = 0; i < len; i++)
			sb.append(AB.charAt(rnd.nextInt(AB.length())));
		return sb.toString();
	}

	/**
	 * Get classifier's class name by a short name
	 * */
	public static String getClassifierClassName(String classifierName) {
		String className = "";
		switch (classifierName) {
		case "SGD":
			className = SGD.class.toString();
			break;
		case "SGDText":
			className = SGDText.class.toString();
			break;
		case "J48":
			className = J48.class.toString();
			break;
		case "PART":
			className = PART.class.toString();
			break;
		case "NaiveBayes":
			className = NaiveBayes.class.toString();
			break;
		case "NBUpdateable":
			className = NaiveBayesUpdateable.class.toString();
			break;
		case "AdaBoostM1":
			className = AdaBoostM1.class.toString();
			break;
		case "LogitBoost":
			className = LogitBoost.class.toString();
			break;
		case "Bagging":
			className = Bagging.class.toString();
			break;
		case "Stacking":
			className = Stacking.class.toString();
			break;
		case "AdditiveRegression":
			className = AdditiveRegression.class.toString();
			break;
		case "Apriori":
			className = Apriori.class.toString();
			break;
		default:
			className = SGD.class.toString();
		}
		className = className.substring(6);
		return className;
	}

	public static void debug(String msg) {
		if (RConfig.debug)
			System.out.println("debugging:>>>>>" + msg);
	}

	public static void time(String msg) {
		if (RConfig.debug) {
			DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
			Date date = new Date();
			System.out.println("debug:" + dateFormat.format(date) + ">>>>>"
					+ msg);
		}
	}

	public static String getDeviceTypeFromIp(String ip) {
		// now is /24, so prefix is ok here
		if (ip.startsWith(RConfig.IOS_PREFIX) || ip.startsWith("10.11.4"))
			return IOS;
		if (ip.startsWith(RConfig.ANDROID_PREFIX)
				|| ip.startsWith("10.11.3"))
			return ANDROID;
		return OTHER_DEVICE;
	}

	public static ArrayList<String> readLines(String fullPath, String commentSymbol) {
		ArrayList<String> lines = new ArrayList<String>();
		if(!(new File(fullPath)).exists()) return lines;
		try {
			BufferedReader bf = new BufferedReader(new FileReader(fullPath));
			String line = "";
			while (true) {
				line = bf.readLine();
				if (line == null || line.length() == 0)
					break;
				if (line.startsWith(commentSymbol))
					continue;
				lines.add(line.trim());
			}
			bf.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return lines;
	}

	public static void writeTextToFile(String fullPath, String text){
		try {
			BufferedWriter bw = new BufferedWriter(new FileWriter(fullPath));
			bw.write(text+"\n");
			bw.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void appendLineToFile(String fullPath, String line){
		try {
			BufferedWriter bw = new BufferedWriter(new FileWriter(fullPath, true));
			bw.append(line+"\n");
			bw.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	static final String IOS = "ios";
	static final String ANDROID = "android";
	static final String OTHER_DEVICE = "other-device";

	public static void loadJSONObject(String filePath){

	}

	// All below are help functions for JSONObject access
	public static String getStringFromJSONObject(JSONObject obj, String keyName) {
		return (String) obj.get(keyName);
	}

	public static int getIntFromJSONObject(JSONObject obj, String keyName) {
		return (int) (long) obj.get(keyName);
	}

	public static double getDoubleFromJSONObject(JSONObject obj, String keyName) {
		return (double) obj.get(keyName);
	}

	public static JSONObject getJSONObjectFromJSONObject(JSONObject obj,
			String keyName) {
		return (JSONObject) obj.get(keyName);
	}
}
