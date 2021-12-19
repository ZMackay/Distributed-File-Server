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
 * DataServerDatalogTxt Class
 * 
 * @author David Nguyen
 *
 */
public class DataServerDatalogTxt {

	private String userFilesDir = PrimaryDataServerClass.userFilesDir;
	private String dataServerInfoDir = PrimaryDataServerClass.dataServerInfoDir;
	private String textFileDestination = dataServerInfoDir + "datalog.txt";
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
	 * Write to datalog.txt file
	 */
	public synchronized void writeDatalog() {
		SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		Timestamp timeStamp = new Timestamp(System.currentTimeMillis());
		String time = timeFormat.format(timeStamp);
		try {
			File textFile = new File(textFileDestination);
			BufferedWriter bufferedWriter;
			if (!textFile.exists()) {
				bufferedWriter = new BufferedWriter(new FileWriter(textFileDestination));
			} else {
				bufferedWriter = new BufferedWriter(new FileWriter(textFileDestination, true));
			}
			String fileSize = null;
			String filePath = null;
			if (event.equals("fileinsert")) {
				File fileOfInterest = new File(userFilesDir + userID + "/" + fileName);
				fileSize = Long.toString(fileOfInterest.length());
				filePath = fileOfInterest.getPath();
			}
			String line;
			if ((fileSize != null) && (filePath != null)) {
				line = String.format("[%s]  %s,%s,%s,%s,%s", event, time, userID, fileName, fileSize, filePath);
			} else {
				line = String.format("[%s]  %s,%s,%s", event, time, userID, fileName);
			}
			bufferedWriter.write(line + "\n");
			bufferedWriter.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
