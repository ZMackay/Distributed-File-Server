package ClientGUI;



/********************************
Name: Kyler Tracy
Username: ua839
Problem Set: LionDB
Due Date: December 8, 2021
********************************/

import java.net.Socket;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.LinkedList;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.concurrent.locks.ReentrantLock;
import java.util.Collections;
import java.io.File;
import java.util.Comparator;
import javax.swing.JProgressBar;

/**
 * Provides the functions necessary for the client application to connect to and interact with the MasterServer.
 * All methods will only be useful once a connection has been established, meaning connectToServer returned true.
 * Methods for sending and receiving information will only be useful once addUser or authenticate have succeeded.
 * Passwords passed to either of these methods are hashed before they are sent to the server.
 * @author Kyler Tracy
 */
// Helper class designed to communicate with MasterServer and maintain a list of files
// uses a lock to keep internal LinkedList and socket communication thread-safe
public class ClientAPI {

	private static final int PORT = 32003; // port that the server is listening on
	private static final String HOST = "localhost"; // hostname of server
	private static final String ALG = "SHA-256";
	private static final long MAX_UPLOAD = 524288000L; // 500 MiB max upload size
	private static final boolean DEBUG = true; // turn on to print error stack traces
	private static String downloadFolder;
	private Socket clientSocket;
	private BufferedInputStream in;
	private BufferedOutputStream out;
	private DataInputStream din;
	private DataOutputStream dout;
	private LinkedList<RemoteFile> fileList;
	private static final SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	private final ReentrantLock lock = new ReentrantLock();

	/**
	 * Class constructor.
	 */
	public ClientAPI() {
		// set downloadFolder to user's home Downloads folder
		downloadFolder = System.getProperty("user.home") + 
			File.separator + "Downloads";
	}

	/**
	 * Attempts to connect to the server.
	 * @return		true if connection is successful, false otherwise
	 */
	public boolean connectToServer() {
		boolean ret = false;
		lock.lock();
		try {
            clientSocket = new Socket(HOST, PORT);
            // set input and output streams
            in = new BufferedInputStream(clientSocket.getInputStream());
            out = new BufferedOutputStream(clientSocket.getOutputStream());
            din = new DataInputStream(in);
            dout = new DataOutputStream(out);
            ret = true;
        } catch (Exception e) {
            if (DEBUG) e.printStackTrace();
            close();
        } finally {
        	lock.unlock();
        }
        return ret;
	}
	/**
	 * Sends a request to the server to authenticate this session with the provided credentials.
	 * @param uname		the username associated with the account
	 * @param passwd	the password to the account
	 * @return			true if authentication was successful, false otherwise
	 */
	// sends "AUTHENTICATE,<uname>,<passwdHash>", expects "SUCCESS" response on success
	public boolean authenticate(String uname, String passwd) {
		boolean ret = false;
		if (uname.length() < 1 || passwd.length() < 1) {
			return ret;
		}
		lock.lock();
		try {
			String passwdHash = hashPassword(passwd);
			if (passwdHash != null) {
				String response = issueCommand("AUTHENTICATE," + uname + "," + passwdHash);
				if (response.compareTo("SUCCESS") == 0) {
					ret = true;
				}
			}
		} catch (Exception e) {
			if (DEBUG) e.printStackTrace();
			close();
		} finally {
			lock.unlock();
		}
		return ret;
	}
	/**
	 * Sends a request to the server to add a user with the provided credentials.
	 * If successful, this user is also authenticated.
	 * @param uname		the username for the user to add
	 * @param passwd	the password for the user to add
	 * @return			true if a user was successfully added, false otherwise
	 */
	// sends "ADD-USER,<uname>,<passwdHash>", expects "SUCCESS" response on success
	public boolean addUser(String uname, String passwd) {
		boolean ret = false;
		if (uname.length() < 1 || passwd.length() < 1) {
			return ret;
		}
		lock.lock();
		try {
			String passwdHash = hashPassword(passwd);
			if (passwdHash != null) {
				String response = issueCommand("ADD-USER," + uname + "," + passwdHash);
				if (response.compareTo("SUCCESS") == 0) {
					ret = true;
				}
			}
		} catch (Exception e) {
			if (DEBUG) e.printStackTrace();
			close();
		} finally {
			lock.unlock();
		}
		return ret;
	}

	// hashes a password and returns a string representation, or null on failure
	private String hashPassword(String passwd) {
		try {
			MessageDigest md = MessageDigest.getInstance(ALG);
			byte[] byteHash = md.digest(passwd.getBytes()); // hash password
			String stringHash = Base64.getEncoder().encodeToString(byteHash); // convert to string
			return stringHash;
		} catch (Exception e) {
			if (DEBUG) e.printStackTrace();
			return null;
		}
	}
	/**
	 * Sends a request to the server to update the internal list of this authenticated user's files.
	 * @return		true if list was successfully updated, false otherwise
	 */
	// requests list of files from server, constructs local LinkedList
	// sends "GET-FILES", expects "NONE" for no files or delimited list of files
	// like <filename>,<size>,<date>::<filename>,<size>,<date>:: ...
	public boolean getFileListFromServer() {
		boolean ret = false;
		lock.lock();
		try {
			// EXAMPLE, not sure what command will be
			String response = issueCommand("GET-FILES");
			String[] part = response.split(",", 1);
			fileList = new LinkedList<RemoteFile>();
			if (part[0].equals("NONE")) {
				ret = true;
			} else if (!part[0].isEmpty()) {
				String[] fileArray = response.split("::"); // files are "::" delimited
				for (String file : fileArray) {
					String[] tok = file.split(",");
					fileList.add(new RemoteFile(tok[0], Long.parseLong(tok[1]), tok[2])); // O(1) insertions
					if (DEBUG) System.out.println("Added " + tok[0] + " to list.");
				}
				Collections.sort(fileList, new RemoteFileSort()); // O(nlogn) sort
				ret = true;
			}
		} catch (Exception e) {
			if (DEBUG) e.printStackTrace();
		} finally {
			lock.unlock();
		}
		return ret;
	}

	// add file to LinkedList in sorted order
	// O(n) insertions to avoid having to sort in O(nlogn) everytime the GUI pulls the array
	private void sortedAddToList(RemoteFile rf) {
		if (fileList.isEmpty()) {
			fileList.addFirst(rf); // add to empty list
		} else {
			for (int i = 0; i < fileList.size(); i++) {
				if (fileList.get(i).compareNameTo(rf) > 0) {
					fileList.add(i, rf); // add to middle of list
					if (DEBUG) System.out.println("Added " + rf.getFileName() + " to list.");
					return;
				}
			}
			fileList.addLast(rf); // add to end of list
		}
		if (DEBUG) System.out.println("Added " + rf.getFileName() + " to list.");
	}
	/**
	 * Creates an array of Strings representing each of this user's files.
	 * Each string contains the file name, file size, and date created of the file, delimited with double colons, "::".
	 * File size is rounded to B, MiB, or GiB depending on file size.
	 * @return
	 */
	// added mainly for use with JList or JTable
	public String[] getFilesArray() {
		String[] ret = null;
		lock.lock();
		ret = new String[fileList.size()];
		int i = 0;
		for (RemoteFile file : fileList) {
			ret[i] = file.toString();
			i++;
		}
		lock.unlock();
		return ret;
	}
	/**
	 * Sends a request to the server to delete the specified file belonging to this user.
	 * @param fname		the name of the file to delete
	 * @return			true if deletion was successful, false otherwise
	 */
	// sends "DELETE-FILE,<path>", expects "SUCCESS" response on success
	public boolean deleteFile(String fname) {
		boolean ret = false;
		lock.lock();
		try {
			String response = issueCommand("DELETE-FILE," + fname);
			if (response.equals("SUCCESS")) {
				fileList.remove(new RemoteFile(fname)); // remove from list
				ret = true;
			}
		} catch (Exception e) {
			if (DEBUG) e.printStackTrace();
		} finally {
			lock.unlock();
		}
		return ret;
	}
	/**
	 * Sends a request to the server to download the specified file to this device.
	 * The file will go to the user's Downloads directory.
	 * @param fileToDownload	the name of the file to download
	 * @param jProgressBar		a progress bar to update as the download progresses.
	 * 							This argument can be null.
	 * @return					true if the download was successful, false otherwise
	 */
	// sends "DOWNLOAD,<filename>", expects "OKAY,<size>" if the download is starting
	public boolean downloadFile(String fileToDownload, JProgressBar jProgressBar) {
		boolean ret = false;
		lock.lock();
		try {
			String response = issueCommand("DOWNLOAD," + fileToDownload);
			String[] part = response.split(",");
			boolean pbExists = false;
			long size = 0;
			int bufSize = 1024*8;
			if (part[0].equals("OKAY") && part.length >= 2) {
				size = Long.parseLong(part[1]);
				if (jProgressBar != null) {
					pbExists = true;
					jProgressBar.setMinimum(0);
					jProgressBar.setMaximum(100);
					jProgressBar.setValue(0);
				}
				// download file
				FileOutputStream fileStream = new FileOutputStream(
					downloadFolder + File.separator + fileToDownload);
				byte[] bytes = new byte[bufSize];
				int count;
				long bytesLeft = size;
				while (bytesLeft > 0) {
					if (bytesLeft < bufSize) {
						count = in.read(bytes, 0, (int)bytesLeft);
					} else {
						count = in.read(bytes);
					}
					fileStream.write(bytes, 0, count);
					if (pbExists) {
						int p = (int)((1-(double)bytesLeft/size)*100);
						jProgressBar.setValue(p);
					}
					bytesLeft -= count;
				}
				fileStream.close(); // flushes and then closes
				ret = true;
			}
		} catch (Exception e) {
			if (DEBUG) e.printStackTrace();
		} finally {
			lock.unlock();
		}
		return ret;
	}
	/**
	 * Sends a request to the server to upload the specified file.
	 * @param fileToUpload	file object representing the file to be uploaded.
	 * @param jProgressBar	a progress bar to update as the upload progresses.
	 * 						This argument can be null.
	 * @return				true if the upload was successful, false otherwise
	 */
	// sends "UPLOAD,<filename>,<filesize>", expects "OKAY" if the server is ready for the file upload
	public boolean uploadFile(File fileToUpload, JProgressBar jProgressBar) {
		boolean ret = false;
		lock.lock();
		try {
			long size = fileToUpload.length();
			boolean pbExists = false;
			if (jProgressBar != null) {
				pbExists = true;
				jProgressBar.setMinimum(0);
				jProgressBar.setMaximum(100);
				jProgressBar.setValue(0);
			}
			String fname = fileToUpload.getName();
			String response;
			if (size >= MAX_UPLOAD) response = "WHOA THERE";
			else response = issueCommand("UPLOAD," + fname + "," + fileToUpload.length());
			if (response.equals("OKAY")) {
				FileInputStream fileStream = new FileInputStream(fileToUpload);
				byte[] bytes = new byte[1024 * 8];
				int count = 0;
				long numBytes = 0;
				while ((count = fileStream.read(bytes)) > 0) {
					out.write(bytes, 0, count);
					out.flush();
					if (pbExists) {
						numBytes += count;
						int p = (int)(((double)numBytes/size)*100);
						jProgressBar.setValue(p);
                                                //jProgressBar.update(java.awt.Graphics);
					}
				}
				fileStream.close();
				// add file to list.
				String time = timeFormat.format(new Timestamp(System.currentTimeMillis()));
				sortedAddToList(new RemoteFile(fname, fileToUpload.length(), time));
				ret = true;
			}
		} catch (Exception e) {
			if (DEBUG) e.printStackTrace();
		} finally {
			lock.unlock();
		}
		return ret;
	}

	// issues command to server and returns result string
	private String issueCommand(String command) {
		String ret = null;
		if (DEBUG) System.out.println(command); // DEBUGGING, REMOVE
		lock.lock();
		try {
			dout.writeUTF(command);
			dout.flush();
			ret = din.readUTF();
		} catch (Exception e) {
			if (DEBUG) e.printStackTrace();
		} finally {
			lock.unlock();
		}
		if (DEBUG) System.out.println(ret); // DEBUGGING, REMOVE
		return ret;
	}

	/**
	 * Closes the internal stream objects and TCP socket used to communicate with the server.
	 */
	public void close() {
		lock.lock();
		try {
			din.close();
			dout.close();
			clientSocket.close();
		} catch (Exception e) {
		} finally {
			lock.unlock();
		}
	}

	// simple object to easier represent remote files and their attributes in the GUI
	class RemoteFile {

		private String fileName;
		private long size;
		private String dateCreated;

		public RemoteFile(String fileName, long size, String dateCreated) {
			this.fileName = fileName;
			this.size = size;
			this.dateCreated = dateCreated;
		}

		public RemoteFile(String fileName) { // for creating a comparable instance
			this.fileName = fileName;
		}

		public String getFileName() {
			return this.fileName;
		}

		public long getSize() {
			return this.size;
		}

		public String getDateCreated() {
			return this.dateCreated;
		}

		// formats size to something like "5.03 MiB" or "104.45 KiB"
		public String formatSize(long size) {
			if (size < 1024L)
				return size + " B";
			else if (size < 1048576L)
				return String.format("%.2f KiB",((double)size/1024L));
			else if (size < 1073741824L)
				return String.format("%.2f MiB",((double)size/1048576L));
			else if (size < 1099511627776L)
				return String.format("%.2f GiB",((double)size/1073741824L));
			else
				return null;
		}

		// compare the names of two RemoteFiles using compareToIgnoreCase
		public int compareNameTo(RemoteFile f) {
			return this.fileName.compareToIgnoreCase(f.getFileName());
		}

		// two RemoteFile instances are equal if they have the same name
		public boolean equals(Object o) {
			if(o != null && o instanceof RemoteFile) {
				RemoteFile n = (RemoteFile)o;
				if (this.fileName.equals(n.getFileName())) {
					return true;
				}
			}
			return false;
		}

		public String toString() {
			return String.format("%s::%s::%s", fileName, formatSize(size), dateCreated);
		}
	}

	class RemoteFileSort implements Comparator<RemoteFile> {
		// case insensitive alphabetical sort for RemoteFile objects 
		public int compare(RemoteFile f1, RemoteFile f2) {
			return f1.compareNameTo(f2);
		}
	}


}