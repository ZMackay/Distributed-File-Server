/********************************
Name: David Nguyen
Username: N/A
Problem Set: LionDB
Due Date: December 8, 2021
********************************/

import java.io.*;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;

/**
 * DataServerDatafilesTxt Class
 * 
 * @author David Nguyen
 *
 */
public class DataServerDatafilesTxt {

	private String userFilesDir = PrimaryDataServerClass.userFilesDir;
	private String dataServerInfoDir = PrimaryDataServerClass.dataServerInfoDir;
	private String textFileDestination = dataServerInfoDir + "datafiles.txt";
	private String tempFile = dataServerInfoDir + "datafilesTEMP.txt";
	private String event;
	private String userID;
	private String fileName;

	/**
	 * Set event
	 * 
	 * @param event String parameter
	 */
	public void setEvent(String event) {
		this.event = event;
	}

	/**
	 * Set userID
	 * 
	 * @param userID String parameter
	 */
	public void setUserID(String userID) {
		this.userID = userID;
	}

	/**
	 * Set fileName
	 * 
	 * @param fileName String parameter
	 */
	public void setFileName(String fileName) {
		this.fileName = fileName;
	}

	/**
	 * This method decide the action of the datafiles.txt file. fileinsert for
	 * inserting a line into the file. filedelete to delete a line in the file.
	 * fileaccess for changing the file access for a file.
	 */
	public synchronized void writeDatafiles() {
		if (event.equals("fileinsert")) {
			try {
				SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
				Timestamp timeStamp = new Timestamp(System.currentTimeMillis());
				String time = timeFormat.format(timeStamp);
				File textFile = new File(textFileDestination);
				BufferedWriter bufferedWriter;
				File fileOfInterest = new File(userFilesDir + userID + "/" + fileName);
				String filePath = fileOfInterest.getPath();
				String fileSize = Long.toString(fileOfInterest.length());
				String addLine = String.format("%s::%s::%s::%s::%s::%s", userID, fileName, time, filePath, fileSize,
						"0");
				if (!(textFile.exists())) {
					bufferedWriter = new BufferedWriter(new FileWriter(textFileDestination));
					bufferedWriter.write(addLine + "\n");
				} else {
					bufferedWriter = new BufferedWriter(new FileWriter(textFileDestination, true));
					bufferedWriter.write(addLine + "\n");
				}
				bufferedWriter.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		if (event.equals("filedelete")) {
			try {
				BufferedReader bufferedReader = new BufferedReader(new FileReader(textFileDestination));
				BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(tempFile));
				String line;
				while ((line = bufferedReader.readLine()) != null) {
					String[] lineSplit = line.split("::");
					String lineUserID = lineSplit[0];
					String lineFileName = lineSplit[1];
					if (!(lineUserID.equals(userID) && lineFileName.equals(fileName))) {
						bufferedWriter.write(line + "\n");
					}
				}
				bufferedWriter.close();
				bufferedReader.close();
				File removeFile = new File(textFileDestination);
				removeFile.delete();
				File changeFileName = new File(tempFile);
				changeFileName.renameTo(removeFile);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		if (event.equals("fileaccess")) {
			try {
				BufferedReader bufferedReader = new BufferedReader(new FileReader(textFileDestination));
				BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(tempFile));
				String line;
				while ((line = bufferedReader.readLine()) != null) {
					String[] lineSplit = line.split("::");
					String lineUserID = lineSplit[0];
					String lineFileName = lineSplit[1];
					if (lineUserID.equals(userID) && lineFileName.equals(fileName)) {
						lineSplit[5] = Integer.toString(Integer.parseInt(lineSplit[5]) + 1);
						String lineChange = lineSplit[0];
						for (int i = 1; i < lineSplit.length; i++) {
							lineChange = lineChange + "::" + lineSplit[i];
						}
						bufferedWriter.write(lineChange + "\n");
					} else {
						bufferedWriter.write(line + "\n");
					}
				}
				bufferedWriter.close();
				bufferedReader.close();
				File removeFile = new File(textFileDestination);
				removeFile.delete();
				File changeFileName = new File(tempFile);
				changeFileName.renameTo(removeFile);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

}
