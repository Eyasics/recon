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

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.dom4j.*;

/**
 * Format validation and String manipulation
 * <p>
 * e.g. email, IMEI etc. e.g. extractOrg
 * </p>
 * */
public class RString {
	static boolean debug = false;

	/**
	 * Select good fields and filter unorganized characters(usually unicode).
	 * <p>
	 * TODO: UNICODE
	 * </p>
	 */
	static String host = "";
	static String user_agent = "";


	public Map<String, Integer> Words = null;
	public String NiceLine = null;
	public Map<String, String> keyValuePairs;
	public final static String DELIMITERS = ",|\t|/|\\||\\*|!|#|&|\\?|\n|;|\\{|\\}|\\(|\\)| ";

	/**
	 * Seprate a line of string into separated words. This function could not
	 * run as a parallel function.
	 *
	 * @param filename
	 *            name of original flow, for later recovery
	 * @param line
	 * @return A list of words appearing in the line and their frequency.
	 *
	 */
	public ArrayList<String> breakLineIntoWords(String line) {
		String delimiters = DELIMITERS;
		String niceLine = "";
		// /delivery/lg.php?b=39833&c=4221&zoneid=5437&cb=067855bb&ml=html&mn=Funny+Mouth&vr=a0.1.16&aid=9419f52ee69ffcba
		// Test for xml
		ArrayList<String> termXML = new ArrayList<String>();
		String rest_line = "";
		for (String fd : line.split("\t")) {
			ArrayList<String> tmptermXML = tryXml(fd);
			if (tmptermXML == null) {
				rest_line += fd + "\t";
			} else {
				termXML = tmptermXML;
				Util.debug("" + termXML.size());
			}
		}
		line = rest_line;
		Util.debug(line);
		String[] terms = line.split(delimiters);
		ArrayList<String> termList = new ArrayList<String>();
		Map<String, Integer> words = new HashMap<String, Integer>();
		for (int i = 0; i < terms.length; i++) {
			String t = terms[i];
			if (t.length() < 1)
				continue;
			t = t.replaceAll("\"", "").replaceAll("'", "")
					.replaceAll("\\[", "").replaceAll("\\]", "")
					.replaceAll("http://", "").replaceAll("https://", "")
					.replaceAll("ftp://", "");
			boolean isValueOnly = true;
			if (t.contains(":") && !t.contains("=")) {
				t = t.replaceFirst(":", "=");
				if (t.contains("mac_address")) {
					Util.debug(t);
				}
			}
			if (t.contains("=")) {
				String[] tsplit = t.split("=");
				if (tsplit.length > 1) {
					String reconk = tsplit[0];
					String reconv = tsplit[1].trim();
					if (reconv.endsWith(":") || reconv.endsWith("-")
							|| reconv.endsWith("="))
						reconv = reconv.substring(0, reconv.length() - 1);
					if (reconv.startsWith(">"))
						reconv = reconv.substring(1);
					if (reconk.length() >= 1) {
						termList.add(reconk);
						Util.debug("reconk:" + reconk);
					}
					if (reconv.length() >= 1) {
						termList.add(reconv);
						Util.debug("reconv:" + reconv);
					}
					isValueOnly = false;
					if (words.containsKey(reconk)) {
						words.put(reconk, words.get(reconk) + 1);
					} else {
						words.put(reconk, 1);
					}
					// DONE:skip values, as we don't anticipate it at feature.
					// if (words.containsKey(reconv)) {
					// words.put(reconv, words.get(reconv) + 1);
					// } else {
					// words.put(reconv, 1);
					// }
				} else if (tsplit.length == 1) {
					t = tsplit[0];
				}
			} else {
				if (words.containsKey(t)) {
					words.put(t, words.get(t) + 1);
				} else {
					words.put(t, 1);
				}

			}
			if (isValueOnly) {
				t = t.trim();
				if (t.length() == 0)
					continue;
				if (t.length() == 1) {
					char tmpt = t.charAt(0);
					if (!Character.isAlphabetic(tmpt)
							&& !Character.isDigit(tmpt))
						continue;
				}
				termList.add(t);
			}
			terms[i] = t;
			niceLine += terms[i] + '\t';

		}
		niceLine = niceLine.trim();
		// Word and NiceLine

		for (String t : termXML) {
			termList.add(t);
			niceLine+=t+"\t";
		}
		NiceLine = niceLine;
//		System.out.println(NiceLine);
		Words = words;
		Util.debug(termList.size()+"");
		return termList;
	}

	public void treeWalk(Document document) {
		treeWalk(document.getRootElement());
	}

	public void treeWalk(Element element) {
		for (int i = 0, size = element.nodeCount(); i < size; i++) {
			Node node = element.node(i);
			if(node==null) continue;
			if (node instanceof Element) {
				treeWalk((Element) node);
			} else {
				if(node.getParent()==null)
					continue;
				Util.debug(node.getParent().getName() + "\t" + node.getText());
				String term = node.getParent().getName() + "=" + node.getText();
				xmlTerms.add(term);
			}
		}
	}

	public ArrayList<String> xmlTerms;

	public ArrayList<String> tryXml(String text) {
		if (text.startsWith("<?xml")) {
			xmlTerms = new ArrayList<String>();
			try {
				Document d = DocumentHelper.parseText(text);
				treeWalk(d);
			} catch (DocumentException e) {
//				e.printStackTrace();
				System.err.println("XML decode failed");
				System.err.println(text);
			}
			return xmlTerms;
		} else {
			return null;
		}
	}

	/**
	 * For each token, split by tab and find those contain = to further split
	 * into key value pairs. <br>
	 * INPUT: NiceLine static
	 * */
	public void findKeyValuePairs() {
		keyValuePairs = new HashMap<String, String>();
		if (NiceLine == null || NiceLine.equals(""))
			return;
		Util.debug(NiceLine);
		String[] terms = NiceLine.split("\t");
		for (int i = 0; i < terms.length; i++) {
			String term = terms[i];
			if (term.contains("=")) {
				String kv[] = term.split("=");
				if (kv.length > 1) {
					keyValuePairs.put(kv[0], kv[1]);
				} else if (kv.length == 1) {
					keyValuePairs.put(kv[0], "");
				}
			}

		}
	}

	/** format validation for email address */
	public static boolean isEmail(String reconValue) {
		String EMAIL_PATTERN = "^[_A-Za-z0-9-\\+]+((\\.|_)[_A-Za-z0-9-]+)*@"
				+ "[A-Za-z0-9-]+(\\.[A-Za-z0-9]+)*(\\.[A-Za-z]{2,})$";
		Pattern pattern = Pattern.compile(EMAIL_PATTERN);
		Matcher matcher = pattern.matcher(reconValue);
		boolean isFormatGood = matcher.matches();
		return isFormatGood;
	}

	/** format validation for mac address */
	public static boolean isMacAddress(String reconValue) {
		int len = reconValue.trim().length();
		boolean isFormatGood = false;
		if(reconValue.contains("02:00:00:00:00:00")){
			return true;
		}
		if (len == 17) {
			String mac_pattern = "(?:[A-Fa-f0-9]{2}[:-]){5}(?:[A-Fa-f0-9]{2})";
			Pattern pattern = Pattern.compile(mac_pattern);
			Matcher matcher = pattern.matcher(reconValue);
			isFormatGood = matcher.find();
		} else if (len == 12) {
			for (char c : reconValue.toCharArray()) {
				if (!(Character.isAlphabetic(c) || Character.isDigit(c))) {
					isFormatGood = false;
					break;
				}
			}
			isFormatGood = true;
		}
		if (debug)
			System.out.println("debug:>>>>>>" + reconValue + isFormatGood);
		return isFormatGood;
	}

	/** format validation for imei */
	public static boolean isIMEI(String reconValue) {
		int len = reconValue.length();
		boolean isFormatGood = false;
		if (len == 17 || len == 15) {
			// 15 or 17 digit sequence of numbers
			isFormatGood = true;
			for (char c : reconValue.toCharArray()) {
				if (c < 0 || c > 9) {
					isFormatGood = false;
					break;
				}
			}

		}
		// else if (len == 36) {
		// // e.g.: AA3804D9-ABD6-46D5-862F-64B19150E007
		// if (reconValue.contains("-"))
		// isFormatGood = true;
		// }
		Util.debug("debug:>>>>>>" + reconValue + isFormatGood);
		return isFormatGood;
	}

	/** format validation for iccid */
	public static boolean isICCID(String reconValue) {
		int len = reconValue.length();
		boolean isFormatGood = false;
		if (len == 20 || len == 19) {
			isFormatGood = true;
		}
		return isFormatGood;
	}

	/** format validation for idfa */
	public static boolean isIDFA(String reconValue) {
		int len = reconValue.length();
		boolean isFormatGood = false;
		if (len == 36 || len == 40 || len == 41) {
			// ifa:
			// idfa:
			isFormatGood = true;
		}
		return isFormatGood;
	}

	/** format validation for imsi */
	public static boolean isIMSI(String reconValue) {
		boolean isFormatGood = false;
		int len = reconValue.length();
		if (len == 15) {
			isFormatGood = true;
			for (char c : reconValue.toCharArray()) {
				if (c < 0 || c > 9) {
					isFormatGood = false;
					break;
				}
			}
		}
		return isFormatGood;
	}

	/** format validation for file name from request */
	public static boolean isImageOrFile(String input) {
		return Pattern
				.matches(
						"([^\\s]+(\\.(?i)(jpg|png|ico|gif|bmp|php|jsp|htm|html|asp|css|js|json|xml)(-http:)?)$)",
						input);
	}

	/** format validation for zipcode */
	public static boolean isZipCode(String reconValue) {
		String ZIPCODE_PATTERN = "\\d{5}(-\\d{4})?";
		Pattern pattern = Pattern.compile(ZIPCODE_PATTERN);
		Matcher matcher = pattern.matcher(reconValue);
		return matcher.matches();
	}

	/** format validation for credit card */
	public static boolean isCreditCard(String reconValue) {
		// TODO: test different credit card number: visa, master, discover etc.
		String credit_regex = "(?:(?<visa>\b4[0-9]{12}(?:[0-9]{3})?\b)|"
				+ "(?<mastercard>\b5[1-5][0-9]{14}\b)|"
				+ "(?<discover>\b6(?:011|5[0-9]{2})[0-9]{12}\b)|"
				+ "(?<amex>\b3[47][0-9]{13}\b)|"
				+ "(?<diners>\b3(?:0[0-5]|[68][0-9])?[0-9]{11}\b)|"
				+ "(?<jcb>\b(?:2131|1800|35[0-9]{3})[0-9]{11}\b))";
		Pattern pattern = Pattern.compile(credit_regex);
		Matcher matcher = pattern.matcher(reconValue);
		return matcher.matches();
	}

	/** format validation for location in gps manner */
	public static boolean isLocationGPS(String reconValue, String reconKey) {
		boolean isFormatGood = false;
		try {
			double rvalue = Double.parseDouble(reconValue);
			// within range for latitude and longitude
			if ((reconKey.equals("lat") || reconKey.equals("latitude"))
					&& rvalue <= 90 && rvalue >= -90) {
				isFormatGood = true;
			}
			if ((reconKey.equals("lon") || reconKey.equals("longitude")
					|| reconKey.equals("lng") || reconKey.equals("long"))
					&& rvalue <= 180 && rvalue >= -180)
				isFormatGood = true;
		} catch (NumberFormatException nfe) {
			isFormatGood = false;
			System.out.println("not numeric value: " + reconValue);
		}
		return isFormatGood;
	}

	/** format validation for android id or apple id */
	public static boolean isAndroidId(String reconValue) {
		// ref:
		// http://support.mobileapptracking.com/entries/22541461-Unique-Identifiers-for-Attribution
		// format validation for device id like android id, ios_ifa etc.
		int len = reconValue.length();
		boolean isFormatGood = false;
		if ( len == 16 || len == 40) {
			//len == 64 || len == 32 ||
			// Google Advertising Identifier (AID)
			// android id
			// 40 - ios_ifa
			// TODO: separate ios and android
			isFormatGood = true;
		}
		if (reconValue.contains(":")) {
			isFormatGood = false;
		}
		return isFormatGood;
	}

	/** format validation for phone number */
	public static boolean isPhoneNumber(String reconValue) {
		// TODO: only support US numbers for now
		int len = reconValue.length();
		boolean isFormatGood = false;
		if (len == 10 || len == 13) {
			// 5556667777
			// (555)666-7777
			String rvalue = reconValue.replace("-", "");
			isFormatGood = true;
			for (char c : rvalue.toCharArray()) {
				if (c < 0 || c > 9) {
					isFormatGood = false;
					break;
				}
			}
		}
		return isFormatGood;
	}

	/** format validation for ip address */
	public static boolean isIP(String reconValue) {
		String ipv4Pattern = "(([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.){3}([01]?\\d\\d?|2[0-4]\\d|25[0-5])";
		// TODO: invalid regex for ipv6
		String ipv6Pattern = "([0-9a-f]{1,4}:){7}([0-9a-f]){1,4}";
		Pattern VALID_IPV4_PATTERN = Pattern.compile(ipv4Pattern,
				Pattern.CASE_INSENSITIVE);
		Pattern VALID_IPV6_PATTERN = Pattern.compile(ipv6Pattern,
				Pattern.CASE_INSENSITIVE);
		if (VALID_IPV4_PATTERN.matcher(reconValue).matches())
			return true;
		if (VALID_IPV6_PATTERN.matcher(reconValue).matches())
			return true;
		return false;
	}

	/** format validation for gender */
	public static boolean isGender(String reconValue) {
		// TODO: decline to answer, not known
		// http://www.sarahdopp.com/blog/2010/designing-a-better-drop-down-menu-for-gender/
		reconValue = reconValue.toLowerCase();
		String[] genderDescriptions = { "f", "m", "female", "male", "girl",
				"boy", "other", "not known", "not applicable" };
		for (String g : genderDescriptions)
			if (reconValue.equals(g))
				return true;
		return false;
	}

	/** format validation for city name */
	public static boolean isCityName(String reconValue) {
		// TODO
		return false;
	}

	/**
	 * check is the value(reconValue) already exists in the database for the
	 * user who also label it as positive
	 */
//	public static String isValueInDataBase(String reconValue, int userID,
//			String reconType) {
//		String keyItem = userID + "," + reconValue;
//		String isEntryExist = "false";
//		Util.debug("Using cache to determine the valueInDB");
//		if (SharedInfo.cacheValuesFromDB.containsKey(keyItem)) {
//			isEntryExist = SharedInfo.cacheValuesFromDB.get(keyItem);
//		}
//		return isEntryExist;
//	}


	/**
	 *
	 * userLabel 1 is correct(positive), 0 is incorrect(negative)
	 * */
//	public static boolean isKeyAlwaysIncorrect(String reconKey, int userID) {
//
//		// select count(*) from PIINetworkLeaks where userID=?;
//		// select count(*) from PIINetworkLeaks where userID=? and userLabel=0;
//		double falsePositiveRateTheta = 0.5;
//		int totalNumReconKey = DataBaseCommunicator.getCountReconKey(reconKey,
//				userID);
//		if (totalNumReconKey < 5)
//			/** No enough data */
//			return false;
//		else {
//			double incorrectNumReconKey = DataBaseCommunicator
//					.getCountIncorrectReconKey(reconKey, userID);
//			double fpr = incorrectNumReconKey / totalNumReconKey;
//			if (fpr > falsePositiveRateTheta)
//				return true;
//			else
//				return false;
//		}
//	}


	public static boolean isStopWord(String key) {
		// called by

		if (SharedMem.stopWords.containsKey(key))
			return true;
		return false;
	}

	public static boolean isLongString(String reconKey, String reconValue) {
		if (reconValue.length() > 80 || reconKey.length() > 80)
			return true;
		return false;
	}

	public static boolean isLongString(String key) {
		if (key.length() > 80)
			return true;
		return false;
	}

	public static boolean isAllNumeric(String reconValue) {
		boolean isFormatGood = true;
		int count = 0;
		for (char c : reconValue.toCharArray()) {
			if (count == 0 && c == '-') {
				// negative value
				count = 1;
				continue;
			}

			if (c >= '0' && c <= '9' || c == '.') {
				// accept decimal
				// TODO: also accept multiple dots,
				// might be a version number, an IP etc.
			} else {
				isFormatGood = false;
				break;
			}
		}
		return isFormatGood;
	}

	public static boolean isSomeId(String key) {
		int len = key.length();
		if (len == 64 || len == 32 || len == 40)
			return true;
		return false;
	}

	public static boolean isTime(String input) {
		// 15:58:41
		// 15-Sep-2013
		// Sep
		// Mon
		// "dd.MM.yyyy", "M/dd/yyyy"
		String[] ps = {
				"([01]?[0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9]", // 24-hour format
				"(0?[1-9]|[12][0-9]|3[01])/(0?[1-9]|1[012])/((19|20)\\d\\d)",
				"\\d{4}-\\d{2}-\\d{2}" };
		for (int i = 0; i < ps.length; i++) {
			if (Pattern.matches(ps[i], input))
				return true;
		}

		String[] month = { "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul",
				"Aug", "Sep", "Oct", "Nov", "Dec" };
		for (int i = 0; i < month.length; i++) {
			String pt = "\\d{2}-" + month[i] + "-\\d{4}";
			if (Pattern.matches(pt, input))
				return true;
		}

		return false;
	}

}
