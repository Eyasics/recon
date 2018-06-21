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

import org.json.simple.JSONObject;

/**
 * INPUT: ordered suspecious keys for each type of leak. e.g. {IMEI: "imei":0.8;
 * "o":0.2} OUTPUT: suspecious <key, value> pairs
 *
 * sample command:
 * */

public class ExtractPIIFromFlow {
	static boolean debug = false;

	/**
	 * Given a dictionary of keys and values, traverse the dictory, and do
	 * followings: 1. compare with wildBadKeys/badKeys to get a best estimate of
	 * the data type by matching key with maximum confidence; 2. if matched,
	 * validate the data format based on its type.
	 *
	 * TODO: History feature, historically incorrect TODO: Match by confirmed
	 * value
	 *
	 * @param isWildGuess
	 *            set to 1 if the host isn't trained; otherwise set to 0
	 * @param keyValuePairs
	 *            the dictory stored keys and values
	 * @return A list of JSONObject that is infered from the key to be the PII.
	 * */
	@SuppressWarnings("unchecked")
	public static List<JSONObject> run(Map<String, String> keyValuePairs,
			boolean isWildGuess, String domain, String OS) {
		if (keyValuePairs == null)
			return null;
		List<JSONObject> foundList = new ArrayList<JSONObject>();
		// For each JSONObject, the definition of each key:
		// t - type, e.g. recon_email
		// k - key, e.g. email
		// v - value, e.g. recon@gmail.com
		// c - confidence, e.g. 0.99
		// l - lowConfFlag, 1 or 0

		String domainOS = domain + "_" + OS;
		for (Map.Entry<String, String> pair : keyValuePairs.entrySet()) {
			String maxReconType = "";
			double maxConfidence = 0;
			String reconKey = pair.getKey();
			String reconValue = pair.getValue();
//			if (reconValue.length() == 15 || reconValue.length() == 17)
//				System.out.println(reconKey + "=" + reconValue);
			// WildGuess first
			if (SharedMem.wildKeys.get(OS).containsKey(reconKey)) {
				JSONObject listOfTypes = SharedMem.wildKeys.get(OS).get(
						reconKey);
				for (Object k : listOfTypes.keySet()) {
					// System.out.println(listOfTypes.toJSONString());
					double confidence = Util.getDoubleFromJSONObject(
							listOfTypes, k.toString());
					String reconType = k.toString();
					if (confidence > maxConfidence) {
						maxConfidence = confidence;
						maxReconType = reconType;
					}
				}
				// System.exit(0);
			}

			// domain
			if (SharedMem.domainOSKeys.containsKey(domainOS)
					&& SharedMem.domainOSKeys.get(domainOS).containsKey(
							reconKey)) {
				JSONObject listOfTypes = SharedMem.domainOSKeys.get(domainOS)
						.get(reconKey);
				for (Object k : listOfTypes.keySet()) {
					String reconType = k.toString();
					double confidence = Util.getDoubleFromJSONObject(
							listOfTypes, reconType);

					if (confidence > maxConfidence) {
						maxConfidence = confidence;
						maxReconType = reconType;
					}
				}

			}

			// System.out.println(reconKey + "=" + reconValue + "\t"
			// + maxReconType + ":" + maxConfidence + isWildGuess);

			/** ==========start format validation===================== */
			int lowConfFlag = 1;
			if (maxConfidence > 0) {
				// format validation for location
				boolean isFormatGood = false;
				boolean definitelyWrong = false;
				switch (maxReconType) {
				case "recon_location_gps":
					isFormatGood = RString.isLocationGPS(reconValue, reconKey);
					break;
				case "recon_location":
					isFormatGood = RString.isLocationGPS(reconValue, reconKey);
					break;
				case "recon_androidid":
					isFormatGood = RString.isAndroidId(reconValue);
					Util.debug("here:" + reconValue);
					if (reconValue.length() < 16 || reconValue.contains(":"))
						definitelyWrong = true;
					break;
				case "recon_advertiserid":
					isFormatGood = RString.isAndroidId(reconValue);
					Util.debug("here:" + reconValue);
					if (reconValue.length() < 16 || reconValue.contains(":"))
						definitelyWrong = true;
					break;
				case "recon_idfa":
					isFormatGood = RString.isIDFA(reconValue);
					if (reconValue.length() < 36)
						definitelyWrong = true;
					break;
				case "recon_macaddr":
					isFormatGood = RString.isMacAddress(reconValue);
					break;
				case "recon_imei":
					isFormatGood = RString.isIMEI(reconValue);
					if (reconValue.length() < 15)
						definitelyWrong = true;
					break;
				case "recon_meid":
					isFormatGood = RString.isIMEI(reconValue);
					if (reconValue.length() < 15)
						definitelyWrong = true;
					break;
				case "recon_imsi":
					isFormatGood = RString.isIMSI(reconValue);
					if (reconValue.length() < 15)
						definitelyWrong = true;
					break;
				case "recon_iccid":
					isFormatGood = RString.isICCID(reconValue);
					if (reconValue.length() < 15)
						definitelyWrong = true;
					break;

				case "recon_serialnumber":
					isFormatGood = RString.isICCID(reconValue);
					if (reconValue.length() < 12)
						definitelyWrong = true;
					break;
				case "recon_email":
					isFormatGood = RString.isEmail(reconValue);
					break;
				case "recon_phonenumber":
					isFormatGood = RString.isPhoneNumber(reconValue);
					break;
				case "recon_gender":
					isFormatGood = RString.isGender(reconValue);
					break;
				case "recon_address":
					if (reconKey.equals("zipcode")) {
						isFormatGood = RString.isZipCode(reconValue);
					} else {
						// TODO: Stree name, city name, country name etc.
						isFormatGood = true;
					}
					break;
				case "recon_zipcode":
					isFormatGood = RString.isZipCode(reconValue);
					break;
				default:
					isFormatGood = true;
				}

				/** =============end of format validation============== */
				if (definitelyWrong) {
					continue;
				}
				if (isFormatGood) {
					// System.out.println(reconKey + "=" + reconValue + "\t1" +
					// maxReconType + ":" + maxConfidence + isFormatGood +
					// isWildGuess + (maxConfidence >= 0.1));
					// for wild guessing
					if ((isWildGuess && maxConfidence >= 0.01)
							|| (!isWildGuess && maxConfidence > 0.01)) {
						// for classifier guessing
						// System.out.println(reconKey + "=" + reconValue +
						// "\t2" + maxReconType + ":" + maxConfidence +
						// isFormatGood + isWildGuess);
						lowConfFlag = 0;
						JSONObject foundKeyValue = new JSONObject();
						foundKeyValue.put("t", maxReconType);
						foundKeyValue.put("k", reconKey);
						foundKeyValue.put("v", reconValue);
						foundKeyValue.put("c", maxConfidence);
						foundKeyValue.put("l", lowConfFlag);
						// System.out.println(reconKey +
						// foundKeyValue.toJSONString());
						foundList.add(foundKeyValue);
					}
					// System.out.println(reconKey + "=" + reconValue + "\t3" );
				}
			} else {
				// System.out.println(reconKey + "=" + reconValue + "\t"
				// + maxReconType + ":" + maxConfidence + isWildGuess);
				JSONObject foundKeyValue = getPIIResult(reconValue);
				if (foundKeyValue != null) {
					foundKeyValue.put("k", reconKey);
					foundList.add(foundKeyValue);
				}
			}
		}
		// System.out.println(foundList);
		return foundList;
	}

	public static List<JSONObject> run(Map<String, Integer> values) {
		List<JSONObject> foundList = new ArrayList<JSONObject>();
		if (values == null)
			return foundList;

		for (String value : values.keySet()) {
			JSONObject foundKeyValue = getPIIResult(value);
			if (foundKeyValue != null)
				foundList.add(foundKeyValue);
		}

		return foundList;
	}

	@SuppressWarnings("unchecked")
	public static JSONObject getPIIResult(String value) {
		JSONObject foundKeyValue = null;
		int length = value.length();
		String reconType = "";
		double confidence = 0;
		boolean isPII = false;
		// only look at value (without key)
		if (!value.contains("=")) {
//			if (value.length() == 15 || value.length() == 17)
//				System.out.println(value);
			if (length >= 17) {
				// TODO: MAC Address contains no ":"
				if (RString.isMacAddress(value)) {
					reconType = "recon_macaddr";
					confidence = 1;
					isPII = true;
				}
			}
			if (value.contains("@")) {
				String lower = value.toLowerCase();
				if (lower.endsWith(".png") || lower.endsWith(".jpeg")
						|| lower.endsWith(".jpg") || lower.endsWith(".gif")
						|| lower.endsWith(".tiff") || lower.endsWith(".svg"))
					isPII = false;
				else if (RString.isEmail(value)) {

					reconType = "recon_email";
					confidence = 1;
					isPII = true;

				}
			}
			if (length == 5 || length == 10) {
				if (RString.isZipCode(value)) {
					reconType = "recon_zipcode";
					isPII = true;
					confidence = 0.5;
				}
			}
			if (value.equals("02115") || value.equals("02115")
					|| value.equals("02115") || value.equals("02115")) {
				reconType = "recon_zipcode";
				isPII = true;
				confidence = 0.9;
			}

		}
		if (isPII) {
			foundKeyValue = new JSONObject();
			foundKeyValue.put("v", value);
			foundKeyValue.put("t", reconType);
			foundKeyValue.put("k", "NotApplicable");
			foundKeyValue.put("c", confidence);
			foundKeyValue.put("l", 0);
		}
		return foundKeyValue;
	}

}
