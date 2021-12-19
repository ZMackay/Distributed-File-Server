/********************************
Name: David Nguyen
Username: N/A
Problem Set: LionDB
Due Date: December 8, 2021
********************************/

import java.io.*;
import java.net.*;

/**
 * DataServerFileAction Class
 * 
 * @author David Nguyen
 * @author Kyler Tracy
 */
public class DataServerFileAction {

	private Socket masterSocket;
	private BufferedInputStream bufferedInputStream;
	private BufferedOutputStream bufferedOutputStream;
	private DataInputStream dataInputStream;
	private DataOutputStream dataOutputStream;
	private String userID;
	private String fileName;
	private String fileSize;
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
	 * Set bufferedInputStream
	 * 
	 * @param bufferedInputStream BufferedInputStream parameter
	 */
	public void setBufferedInputStream(BufferedInputStream bufferedInputStream) {
		this.bufferedInputStream = bufferedInputStream;
	}

	/**
	 * Set bufferedOutputStream
	 * 
	 * @param bufferedOutputStream BufferedOutputStream parameter
	 */
	public void setBufferedOutputStream(BufferedOutputStream bufferedOutputStream) {
		this.bufferedOutputStream = bufferedOutputStream;
	}

	/**
	 * Set dataInputStream
	 * 
	 * @param dataInputStream DataInputStream parameter
	 */
	public void setDataInputStream(DataInputStream dataInputStream) {
		this.dataInputStream = dataInputStream;
	}

	/**
	 * Set dataOutputStream
	 * 
	 * @param dataOutputStream DataOutputStream parameter
	 */
	public void setDataOutputStream(DataOutputStream dataOutputStream) {
		this.dataOutputStream = dataOutputStream;
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
	 * Set fileSize
	 * 
	 * @param fileSize String parameter
	 */
	public void setFileSize(String fileSize) {
		this.fileSize = fileSize;
	}

	/**
	 * This method add a file into a user directory. fileinsert event method.
	 */
	public void addFile() {
		try {
			String filePath = userFilesDir + userID + "/" + fileName;
			long sizeOfFile = Long.parseLong(fileSize);
			FileOutputStream fileOutputStream = new FileOutputStream(filePath);
			int count = 0;
			long bytesLeft = sizeOfFile;
			int bufferSize = 1024 * 8;
			byte[] bytes = new byte[bufferSize];
			while (bytesLeft > 0) {
				if (bytesLeft < bufferSize) {
					count = bufferedInputStream.read(bytes, 0, (int) bytesLeft);
				} else {
					count = bufferedInputStream.read(bytes);
				}
				fileOutputStream.write(bytes, 0, count);
				bytesLeft -= count;
			}
			fileOutputStream.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * This method delete a file in a user directory. filedelete event method.
	 */
	public void deleteFile() {
		try {
			String filePath = userFilesDir + userID + "/" + fileName;
			File fileObject = new File(filePath);
			if (!fileObject.exists()) {
				System.out.println(fileObject + " does not exist.");
			} else {
				fileObject.delete();
				System.out.println(fileObject + " have been deleted.");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * This method upload the file from the data server to the client. fileaccess
	 * event method
	 */
	public void fileSendToClient() {
		try {
			String filePath = userFilesDir + userID + "/" + fileName;
			File fileObject = new File(filePath);
			if (!fileObject.exists() || !fileObject.canRead()) {
				masterSocket.close();
				System.out.println("File failure, closing connection to restart node.");
				return;
			}
			System.out.println("Size of file being sent: " + fileObject.length());
			FileInputStream fileInputStream = new FileInputStream(fileObject);
			byte[] buffer = new byte[1024 * 8];
			int len;
			while ((len = fileInputStream.read(buffer)) != -1) {
				bufferedOutputStream.write(buffer, 0, len);
				bufferedOutputStream.flush();
			}
			fileInputStream.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * This method return a list of files for a specific user. filelist event method
	 */
	public void listFile() {
		try {
			String filePath = userFilesDir + userID + "/" + fileName;
			File fileObject = new File(filePath);
			File[] fileArray = fileObject.listFiles();
			for (int i = 0; i < fileArray.length; i++) {
				String toMaster = "filelist" + "," + userID + "," + fileArray[i].getName() + ","
						+ fileArray[i].length();
				dataOutputStream.writeUTF(toMaster);
				dataOutputStream.flush();
			}
			System.out.println("Sent file list for: " + filePath);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * This method to check if a file exist or not for a user.
	 * 
	 * @return true if file exist, false if file does not exist.
	 */
	public boolean searchFile() {
		boolean ret = false;
		try {
			String filePath = userFilesDir + userID + "/" + fileName;
			File fileObject = new File(filePath);
			long sizeOfThisFile = Long.parseLong(fileSize);
			if (fileObject.exists() && fileObject.length() == sizeOfThisFile) {
				ret = true; // if file exists and has the same size
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return ret;
	}

}
