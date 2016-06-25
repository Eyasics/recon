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

import java.text.ParseException;

public class Main {

	public static void main(String[] args) {
		String classifierSelected = "J48";
		String mode = "-t";
		String jsonFileName = "";
		String wekaRoot = "";
		if (args.length > 2) {
			wekaRoot = args[0];
			mode = args[1];
			classifierSelected = args[2];
			if (mode.equals("-p")) {
				if (args.length > 3) {
					jsonFileName = args[3];
					System.out.println("#"+jsonFileName);
				} else {
					System.err
							.println("No enough input arguments. \nUsage:predict.sh <http.log>");
					System.exit(-1);
				}
			}

		} else {
			System.out.println(args.length);
			System.out.println("No enough input arguments.Please use as:");
			System.out.println("train J48 ");
			System.out.println("predict <http.log>");
			System.out.println("clear uid");
			System.exit(-1);
		}
		// It is important to configure the root for weka
		SharedMem.wekaRoot = wekaRoot;
//		System.out.println("wekaRoot:" + SharedMem.wekaRoot);
		if (!SharedMem.Initialized)
			SharedMem.init();

		switch (mode) {
		case "-t": // for train
			/**
			 * SGD, J48, PART, NaiveBayes, AdaBoostM1
			 * */
			TrainModelByDomainOS.trainAllDomains(classifierSelected, null);
			break;

		case "-p":
			if (jsonFileName == "") {
				System.err.println("Please input the path to flow json file");
				System.exit(-1);
			} else {
				if (!PredictByDomainOS.isModelLoaded) {
					if (!PredictByDomainOS.loadAllModels(Util
							.getClassifierClassName(classifierSelected))) {
						System.err.println("loadAllModels failed");
						System.exit(-1);
					}
				}
				double endTime = System.currentTimeMillis() / 1000;
				double startTime = endTime - 1440;
				if (args.length > 4) {
					try {
						endTime = new java.text.SimpleDateFormat("yyyy-MM-dd")
								.parse(args[4]).getTime() / 1000;
						startTime = endTime;
						endTime += 86400;
					} catch (ParseException e) {
						e.printStackTrace();
					}
				}
				Util.debug(startTime + " -> " + endTime);
				PredictByDomainOS.predictJSONFile(jsonFileName,
						classifierSelected);
			}
			break;
		}
	}
}
