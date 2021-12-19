/********************************
Name: Kyler Tracy
Username: ua839
Problem Set: LionDB
Due Date: December 8, 2021
********************************/

import java.util.concurrent.ConcurrentHashMap;
import java.util.HashSet;
import java.util.Set;
import java.io.File;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.BufferedWriter;

/**
 * Stores user file records into a HashMap of users mapped to a HashMap of the user's files.
 * Files are stored using FileInfo instances.
 * @author Kyler Tracy
 */
public class MasterRecords {

	private String logFilePath;
	private ConcurrentHashMap<String,ConcurrentHashMap<String,FileInfo>> userMap;
	/**
	 * Class constructor. Loads data from file with specified name if it exists.
	 * If the file does not exist, it is created.
	 * @param logFilePath	the path of the file to use for storing and loading user file records
	 */
	public MasterRecords(String logFilePath) {
		this.logFilePath = logFilePath;
		userMap = new ConcurrentHashMap<String,ConcurrentHashMap<String,FileInfo>>();
		loadData();
	}
	// userMap = new ConcurrentHashMap<user,ConcurrentHashMap<fileInfoleName,FileInfo>>()
	private synchronized void loadData() {
		try {
			File logFile = new File(logFilePath);
			logFile.createNewFile();
			if (logFile.exists() && logFile.canRead()) {
				BufferedReader br = new BufferedReader(new FileReader(logFile));
				String line;
				String[] part;
				FileInfo fileInfo;
				ConcurrentHashMap<String,FileInfo> fileInfoMap;
				while ((line = br.readLine()) != null) {
					//alice::file1.txt::2021-11-17 12:37::/server/alice/fileInfoles/::32143::data1,data2
					part = line.split("::");
					HashSet<String> s = new HashSet<String>();
					for (String node : part[5].split(",")) { // add nodes to FileInfo object
						s.add(node);
					}
					fileInfo = new FileInfo(part[2], part[3], Long.parseLong(part[4]), s);
					if (userMap.containsKey(part[0])) { // if user is in map
						fileInfoMap = userMap.get(part[0]);
					} else {
						fileInfoMap = new ConcurrentHashMap<String,FileInfo>();
						userMap.put(part[0], fileInfoMap);
					}
					fileInfoMap.put(part[1], fileInfo);
				}
				br.close();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	/**
	 * Returns the underlying ConcurrentHashMap instance used to store user records.
	 * This is the most cumbersome way to manage records user records.
	 * @return	the HashMap containing all user info for this server
	 */
	public ConcurrentHashMap<String,ConcurrentHashMap<String,FileInfo>> getUserMap() {
		return this.userMap;
	}
	/**
	 * Adds a user to the record HashMap.
	 * @param username	the username of the user to add
	 * @return			the HashMap of file info for that user
	 */
	// example: records.addUser(). returns the map for that user
	public ConcurrentHashMap<String,FileInfo> addUser(String username) {
		userMap.putIfAbsent(username, new ConcurrentHashMap<String, FileInfo>());
		saveRecords();
		return userMap.get(username);
	}
	/**
	 * Adds entries into the HashMap for the information provided. If a user with the
	 * specified username does not exist, it is added. If a file with said filename
	 * in this user's record does not exist, it is created. If the file does exist,
	 * it is replaced with the provided information. Also saves this current set
	 * of records to the records file.
	 * @param username		username of the user whose file info is being added
	 * @param filename		name of the file being added
	 * @param filesize		size of the file being added
	 * @param date			date that the file was created. Should be formatted correctly.
	 * @param path			the path representing where a data node will store this file
	 * @param nodeSet		the set of nodes that contain this file. The set is converted
	 * 						to a synchronized set and must not contain null values.
	 * @return				returns the FileInfo object created to store the provided information.
	 */
	public synchronized FileInfo putUserFileInfo(String username,
		String filename, long filesize, String date, String path, Set<String> nodeSet) {
		FileInfo fileInfo = new FileInfo(date, path, filesize, nodeSet);
		if (userMap.containsKey(username)) {
				userMap.get(username).put(filename, fileInfo);
		} else {
			userMap.put(username, new ConcurrentHashMap<String,FileInfo>());
			userMap.get(username).put(filename, fileInfo);
		}
		saveRecords();
		return userMap.get(username).get(filename);
	}
	/**
	 * Gets the nodes that a specified file is stored on.
	 * @param username	username associated with the file to check
	 * @param filename	name of the file to check
	 * @return			the set of nodes that contain this file
	 */
	public Set<String> getFileNodes(String username, String filename) {
		return userMap.get(username).get(filename).getNodes();
	}
	/**
	 * Checks to see if a file matching the provided information exists.
	 * @param username	username associated with file to check
	 * @param filename	name of the file to check
	 * @return			returns true if the file exists, false otherwise
	 */
	public boolean fileExists(String username, String filename) {
		if (userMap.containsKey(username) && userMap.get(username).containsKey(filename)) {
			return true;
		}
		return false;
	}
	/**
	 * Gets the FileInfo object for a specified file, if it exists.
	 * @param username	username associated with the file
	 * @param filename	name of the file
	 * @return			the FileInfo object for the file, or null if it is not found
	 */
	// returns the FileInfo object for a file. might be helpful with getFileInfo().addNode(nodeName)
	public FileInfo getFileInfo(String username, String filename) {
		if (userMap.containsKey(username) && userMap.get(username).containsKey(filename)) {
			return userMap.get(username).get(filename);
		}
		return null;
	}
	/**
	 * Remove the entry for the specified file if it exists. On successful
	 * deletion, saves this current set of records to the specified records file.
	 * @param username	username associated with the file to delete
	 * @param filename	name of file to delete
	 */
	public synchronized void deleteFile(String username, String filename) {
		if (userMap.get(username) != null) {
			userMap.get(username).remove(filename);
			saveRecords();
		}
	}
	/**
	 * Gets a list of files that should be stored on the specified node.
	 * @param nodeName	node to retrieve file list for
	 * @return			line delimited list of files the specified node should have, or an empty string.
	 */
	// returns a String list of files for a given node. format: <username>,<filename>,<filesize>\n
	public String getNodeFileList(String nodeName) {
		String nodeFileList = "";
		Set<String> nodeSet;
		for (String username : userMap.keySet()) { // every user
			for (String filename : userMap.get(username).keySet()) { // each of user's files
				FileInfo fi = userMap.get(username).get(filename);
				nodeSet = fi.getNodes();
				if (nodeSet.contains(nodeName)) {
					nodeFileList += username +","+ filename +","+fi.getSize() +"\n";
				}
			}
		}
		return nodeFileList;
	}
	/**
	 * Returns a list of files belonging to a user.
	 * File information is comma-delimited and files are double-colon-delimited.
	 * For example, "filename,size,formattedDate::filename,1234,formattedDate::"...
	 * @param username	username of user whose files to list
	 * @return			list of files belonging to the specified user
	 */
	// returns a "::" delimited list of a user's files
	public String getUserFileList(String username) {
		String fileList = "";
		try {
			if (userMap.containsKey(username)) { // user exists
				for (String fileName : userMap.get(username).keySet()) {
					FileInfo fi = getFileInfo(username, fileName);
					fileList += fileName +","+ fi.getSize() +","+ fi.getDate() +"::";
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return fileList;
	}
	// saves current set of records to file
	private void saveRecords() {
		try {
			BufferedWriter bw = new BufferedWriter(new FileWriter(logFilePath));
			FileInfo fileInfo;
			String nodes;
			for (String username : userMap.keySet()) { // every username
				for (String filename : userMap.get(username).keySet()) { // each of user's files
					fileInfo = userMap.get(username).get(filename);
					nodes = "";
					for (String node : fileInfo.getNodes()) {
						nodes += node + ",";
					}
					if (!nodes.isBlank()) nodes = nodes.substring(0, nodes.length()-1); // remove extra comma
					bw.write(username +"::"+ filename +"::"+ fileInfo.getDate() +"::"
						+ fileInfo.getPath() +"::"+ fileInfo.getSize() +"::"+ nodes);
					bw.newLine();
				}
			}
			bw.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}