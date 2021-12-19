/********************************
Name: David Nguyen
Username: N/A
Problem Set: LionDB
Due Date: December 8, 2021
********************************/

import java.io.*;
import java.net.*;

/**
 * DataServerThread Class
 * 
 * @author David Nguyen
 * @author Kyler Tracy
 */
public class DataServerThread {

	private Socket masterSocket;
	private BufferedInputStream bufferedInputStream;
	private BufferedOutputStream bufferedOutputStream;
	private DataInputStream dataInputStream;
	private DataOutputStream dataOutputStream;
	private String userFilesDir = PrimaryDataServerClass.userFilesDir;

	/**
	 * Set masterSocket
	 * 
	 * @param masterSocket Socket parameter
	 */
	public void setMasterSocket(Socket masterSocket) {
		this.masterSocket = masterSocket;
	}

	/**
	 * Instruction string from master server action
	 */
	public void action() {
		try {
			bufferedInputStream = new BufferedInputStream(masterSocket.getInputStream());
			bufferedOutputStream = new BufferedOutputStream(masterSocket.getOutputStream());
			dataInputStream = new DataInputStream(bufferedInputStream);
			dataOutputStream = new DataOutputStream(bufferedOutputStream);
			while (true) {
				// Splitting up instruction string
				String instruction = dataInputStream.readUTF();
				String[] splitStringInstruction = instruction.split(",");
				String event = splitStringInstruction[0];
				String userID = splitStringInstruction[1];
				String fileName = splitStringInstruction[2];
				String fileSize = "-1";
				if (splitStringInstruction.length > 3) {
					fileSize = splitStringInstruction[3];
				}
				if (!splitStringInstruction[0].equals("test")) // skip printing "test" strings
					System.out.println("Instruction String: " + instruction);

				// Check/Make for userID directory
				String directory = userFilesDir + userID + "/";
				File fileObject = new File(directory);
				if (!fileObject.exists()) {
					fileObject.mkdirs();
				}

				// Set up datafiles.txt information
				DataServerDatafilesTxt dataFiles = new DataServerDatafilesTxt();
				dataFiles.setEvent(event);
				dataFiles.setUserID(userID);
				dataFiles.setFileName(fileName);

				// Set up datalog.txt
				DataServerDatalogTxt log = new DataServerDatalogTxt();
				log.setEvent(event);
				log.setUserID(userID);
				log.setFileName(fileName);

				// Set up event file action
				DataServerFileAction fileAction = new DataServerFileAction();
				fileAction.setMasterSocket(masterSocket);
				fileAction.setBufferedInputStream(bufferedInputStream);
				fileAction.setBufferedOutputStream(bufferedOutputStream);
				fileAction.setDataInputStream(dataInputStream);
				fileAction.setDataOutputStream(dataOutputStream);
				fileAction.setUserID(userID);
				fileAction.setFileName(fileName);
				fileAction.setFileSize(fileSize);

				// Events
				if (event.equals("test"))
					; // just send final "done"
				else if (event.equals("filelist")) {
					fileAction.listFile();
				} else if (event.equals("fileexists")) {
					if (fileAction.searchFile()) {
						dataOutputStream.writeUTF("true");
						dataOutputStream.flush();
					} else { // should have the file
						dataOutputStream.writeUTF("false");
						dataOutputStream.flush();
						dataOutputStream.writeUTF("done");
						dataOutputStream.flush();
						break; // restart node if not
					}
					// File events
				} else {
					if (event.equals("fileinsert")) {
						fileAction.addFile();
					}
					if (event.equals("filedelete")) {
						fileAction.deleteFile();
					}
					if (event.equals("fileaccess")) {
						fileAction.fileSendToClient();
					}
					// Write to data files.
					dataFiles.writeDatafiles();
					log.writeDatalog();
				}
				dataOutputStream.writeUTF("done");
				dataOutputStream.flush();
				System.out.println("Sending done to master server");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		System.out.println("Something went wrong. Restarting node...");
		PrimaryDataServerClass dataServer = new PrimaryDataServerClass();
		dataServer.masterConnect();
	}

}
