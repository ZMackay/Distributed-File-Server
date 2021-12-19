/********************************
Name: David Nguyen
Username: N/A
Problem Set: LionDB
Due Date: December 8, 2021
********************************/

import java.io.*;
import java.net.*;

/**
 * PrimaryDataServerClass class
 * 
 * @author David Nguyen
 * @author Kyler Tracy
 */
public class PrimaryDataServerClass {

	/**
	 * String for the location for the userFiles directory.
	 */
	public static final String userFilesDir = "/home/uadist/userFiles/";

	/**
	 * String for the location for the dataServerInfo directory.
	 */
	public static final String dataServerInfoDir = "/home/uadist/dataServerInfo/";

	/**
	 * Main method
	 * 
	 * @param args String argument array for input(s)
	 */
	public static void main(String[] args) {
		PrimaryDataServerClass methodInClass = new PrimaryDataServerClass();
		methodInClass.masterConnect();
	}

	/**
	 * Method that calls it self if something happens to master server.
	 */
	public void masterConnect() {
		try {
			//String masterIP = "kylertracy.com";
			String masterIP = "10.181.244.158";
			//int port = 40053;
			int port = 40050;
			Socket toMasterSocket = new Socket(masterIP, port);
			System.out.println("Master is connected");
			initialCheck(toMasterSocket);
			DataServerThread method = new DataServerThread();
			method.setMasterSocket(toMasterSocket);
			method.action();
		} catch (Exception e) {
			System.out.println("Fail to connect to master server. Retrying in 3 seconds...");
			e.printStackTrace();
			for (int i = 3; i > 0; i--) {
				try {
					System.out.println("Seconds left: " + i);
					Thread.sleep(1000);
				} catch (InterruptedException e1) {
					e1.printStackTrace();
				}
			}
			masterConnect();
		}
	}

	/**
	 * When the data server starts up, it will go through a check for the master and
	 * server connection.
	 * 
	 * @param masterSocket Socket parameter
	 */
	public void initialCheck(Socket masterSocket) {
		try {
			// Check/Make DataServerInfo directory
			String directory = dataServerInfoDir;
			File fileObject = new File(directory);
			if (!fileObject.exists()) {
				fileObject.mkdirs();
			}
			File nodeIDFile = new File(dataServerInfoDir + "nodeID"); // This is where this node's nodeID is stored
			DataOutputStream dos = new DataOutputStream(masterSocket.getOutputStream());
			DataInputStream dis = new DataInputStream(masterSocket.getInputStream());
			String nodeID;
			if (nodeIDFile.exists() && nodeIDFile.canRead()) { // Read nodeID from file if it exists
				BufferedReader br = new BufferedReader(new FileReader(nodeIDFile));
				nodeID = br.readLine();
				br.close();
				dos.writeUTF(nodeID);
				dos.flush();
				// Get list of files and check, send response.
				// "<username>,<filename>,<filesize>"
				DataServerFileAction dsfa = new DataServerFileAction();
				String fileToCheck;
				while (!(fileToCheck = dis.readUTF()).equals("done")) {
					String[] part = fileToCheck.split(",");
					dsfa.setUserID(part[0]);
					dsfa.setFileName(part[1]);
					dsfa.setFileSize(part[2]);
					if (!dsfa.searchFile()) {
						dos.writeUTF("false");
					} else {
						dos.writeUTF("true");
					}
					dos.flush();
				}
			} else {
				dos.writeUTF("NEW-NODE");
				dos.flush();
				nodeID = dis.readUTF();
				BufferedWriter bw = new BufferedWriter(new FileWriter(nodeIDFile));
				bw.write(nodeID); // save nodeID to file for next connection
				bw.newLine();
				bw.close();
			}
			System.out.println("Initial check done. NodeID is " + nodeID + ". Continuing.");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
