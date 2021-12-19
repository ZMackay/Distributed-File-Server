/********************************
Name: Zachary Mackay, Kyler Tracy
Username: ?????, ua839
Problem Set: LionDB
Due Date: December 8, 2021
 ********************************/

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Collections;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles requests from a client connection using the set of activeNodes.
 * Chooses the least accessed data node(s) to fulfill a request if possible.
 * @author Zachary Mackay
 * @author Kyler Tracy
 */
public class ClientHandler implements Runnable {
	private static final int MAX_NODES = 2; // upload files to 2 different nodes
	private BufferedInputStream bin;
	private BufferedOutputStream bout;
	private DataInputStream din;
	private DataOutputStream dout;
	private String username;
	private Socket client;
	private MasterRecords records;
	private ConcurrentHashMap<String,NodeHandler> activeNodes;
	/**
	 * Gets all necessary information to handle client requests.
	 * @param client		the Socket instance for this client connection
	 * @param username		the username associated with this client connection
	 * @param activeNodes	the set of active NodeHandler instances
	 * @param records		the MasterRecords instance for this server
	 */
	public ClientHandler(Socket client, String username, ConcurrentHashMap<String,NodeHandler> activeNodes,
			MasterRecords records) {
		this.client = client;
		this.username = username;
		this.activeNodes = activeNodes;
		this.records = records;
	}
	/**
	 * Uses the set of available NodeHandlers to fulfill client requests. This includes
	 * listing a user's files, uploading files to the server, downloading files to the client,
	 * and deleting files on the server.
	 */
	public void run() {
		try {
			bin = new BufferedInputStream(client.getInputStream());
			bout = new BufferedOutputStream(client.getOutputStream());
			din = new DataInputStream(bin);
			dout = new DataOutputStream(bout);
		} catch (IOException e) {
			System.out.println("Error starting client handler. Exiting.");
			e.printStackTrace();
			return;
		}

		String message;
		String[] part;

		while (true) {
			try {
				message = din.readUTF();
			} catch (Exception e) { // either stream is closed or it's sending weird data, close the handler
				System.out.println("Client handler for user \"" + username + "\" closed.");
				return;
			}

			System.out.println(message);
			part = message.split(",");
			// get files command. "GET-FILES"
			if (part[0].equals("GET-FILES")) {
				String filesList = records.getUserFileList(username);
				if (filesList == "") {
					filesList = "NONE";
				}
				respond(filesList);
				// delete file command. "DELETE-FILE,<path>"
			} else if (part[0].equals("DELETE-FILE")) {
				if (part.length < 2) {
					respond("FAILED");
				} else {
					deleteFile(part);
				}
				// download file command. "DOWNLOAD,<filename>". returns "OKAY,<size." on success
			} else if (part[0].equals("DOWNLOAD")) {
				if (part.length < 2) {
					respond("FAILED");
				} else {
					downloadFile(part);
				}
				// upload file command. "UPLOAD,<filename>,<filesize>"
			} else if (part[0].equals("UPLOAD")) {
				if (part.length <3) {
					respond("FAILED");
				} else {
					uploadFile(part);
				}
			} else {
				respond("INVALID COMMAND STRING '" + message + "'");
			}
		}
	}
	// "DELETE-FILE,<path>"
	private void deleteFile(String[] part) {
		String ret = "FAILURE";
		String filename = part[1];
		try {
			if (records.fileExists(username, filename)) {
				// get set of nodes that contain this file
				Set<String> nodesWithFile = records.getFileNodes(username, filename);
				String nodeString = ""; // for log entry
				for (String nodeID : nodesWithFile) {
					// delete file from active nodes
					if (activeNodes.containsKey(nodeID) && activeNodes.get(nodeID).isResponding()) {
						activeNodes.get(nodeID).deleteFile(username, filename);
					}
					nodeString += nodeID +",";
				}
				nodeString = nodeString.substring(0, nodeString.length()-1); // remove a comma
				records.deleteFile(username, filename); // delete file from records
				// log event - file deleted
				MasterServer.logEvent("filedelete", System.currentTimeMillis(),
						new String[]{nodeString, username, filename});
				ret = "SUCCESS";
			} else {
				// log event - file failure
				MasterServer.logEvent("filefailure", System.currentTimeMillis(),
						new String[]{username, filename});
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		respond(ret);
	}
	// "DOWNLOAD,<filename>". returns "OKAY,<size." on success
	private void downloadFile(String[] part) {
		String filename = part[1];
		boolean responded = false;
		try {
			if (!records.fileExists(username, filename)) { // file does not exist
				respond("FAILURE");
				responded = true;
				// log event - file failure
				MasterServer.logEvent("filefailure", System.currentTimeMillis(),
						new String[]{username, filename});
			} else {
				Set<String> fileNodes = records.getFileNodes(username, filename);
				NodeHandler downloadNode = null;
				for (String nodeID : fileNodes) {
					// find least accessed active node containing this file
					if (activeNodes.containsKey(nodeID) && activeNodes.get(nodeID).isResponding()) {
						if (downloadNode == null || activeNodes.get(nodeID).getTotalActions() < downloadNode.getTotalActions()) {
							downloadNode = activeNodes.get(nodeID);
						}
					}
				}
				long size = records.getFileInfo(username, filename).getSize();
				if (downloadNode != null && downloadNode.fileExists(username, filename, size)) {
					respond("OKAY,"+size);
					responded = true;
					// get file from node to client
					downloadNode.transferFileToClient(username, filename, size, this.bout);
					System.out.println("User "+username+" downloaded file "+filename+" from "+downloadNode.getNodeID()+".");
					// log event - file access
					MasterServer.logEvent("fileaccess", System.currentTimeMillis(),
							new String[]{downloadNode.getNodeID(), username, filename});

				} else {
					System.out.println("User "+username+" requested file "+filename+" and download failed.");
					// log event - file failure
					MasterServer.logEvent("filefailure", System.currentTimeMillis(),
							new String[]{username, filename});
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		if (!responded) respond("FAILURE"); // make sure client gets a response and doesn't freeze
	}
	// "UPLOAD,<filename>,<filesize>"
	private void uploadFile(String[] part) {
		String filename = part[1];
		long size = Long.parseLong(part[2]);
		boolean responded = false;
		try {
			// if file exists, override in those servers, otherwise upload to least accessed server
			ArrayList<NodeHandler> nodeList;
			ArrayList<NodeHandler> uploadNodes;
			String nodeString = "";
			// overwriting existing file
			if (records.fileExists(username, filename)) {
				Set<String> nodeSet = records.getFileNodes(username, filename);
				nodeList = new ArrayList<NodeHandler>(nodeSet.size());
				for (String nodeID : nodeSet) {
					// if node is in active node map and responding
					if (activeNodes.containsKey(nodeID) && activeNodes.get(nodeID).isResponding()) {
						nodeList.add(activeNodes.get(nodeID));
						nodeString += nodeID + ",";
					}
					if (!nodeString.isEmpty()) nodeString.substring(0, nodeString.length()-1); // remove comma
				}
				uploadNodes = nodeList;
				if(nodeList.isEmpty()){
					
					records.deleteFile(username, filename); // delete file from records
					nodeList = new ArrayList<NodeHandler>(activeNodes.values());
					Collections.sort(nodeList, NodeHandler.LEAST_ACCESSED_ORDER); // sort by least no. actions
					for (NodeHandler nh : nodeList) {
						System.out.println("This node handler, "+nh.getNodeID()+", is in nodeList to be considered for upload.");
					}
					uploadNodes = new ArrayList<NodeHandler>(MAX_NODES);
					for (int i = 0; i < MAX_NODES; i++) {
						if (i < nodeList.size() && nodeList.get(i).isResponding()) {
							uploadNodes.add(nodeList.get(i)); // get MAX_NODES number of least accessed nodes
							nodeString += uploadNodes.get(i).getNodeID(); // get list of nodes for the log entry
							if (i < uploadNodes.size()-1) nodeString += ",";
						} else {
							break;
						}
					}
					for (NodeHandler nh : uploadNodes) {
						System.out.println("Node in uploadNodes: "+nh.getNodeID());
					}
				}
				// new file that does not exist yet
			} else { // find least accessed data nodes to upload to

				nodeList = new ArrayList<NodeHandler>(activeNodes.values());
				Collections.sort(nodeList, NodeHandler.LEAST_ACCESSED_ORDER); // sort by least no. actions
				for (NodeHandler nh : nodeList) {
					System.out.println("This node handler, "+nh.getNodeID()+", is in nodeList to be considered for upload.");
				}
				uploadNodes = new ArrayList<NodeHandler>(MAX_NODES);
				for (int i = 0; i < MAX_NODES; i++) {
					if (i < nodeList.size() && nodeList.get(i).isResponding()) {
						uploadNodes.add(nodeList.get(i)); // get MAX_NODES number of least accessed nodes
						nodeString += uploadNodes.get(i).getNodeID(); // get list of nodes for the log entry
						if (i < uploadNodes.size()-1) nodeString += ",";
					} else {
						break;
					}
				}
				for (NodeHandler nh : uploadNodes) {
					System.out.println("Node in uploadNodes: "+nh.getNodeID());
				}

				if (uploadNodes.isEmpty()) { // no nodes available, stop
					respond("FAILURE");
					return;
				}
				// continue with upload
				respond("OKAY"); // tell client that server is ready
				responded = true;
				// prepare for upload
				int bufSize = 1024*8;
				byte[] bytes = new byte[bufSize];
				int count = 0;
				long bytesLeft = size;
				for (NodeHandler node : uploadNodes) {
					node.startFileTransferToNode(username, filename, size); // request nodes to transfer
				}
				while (bytesLeft > 0) {
					if (bytesLeft < bufSize) {
						count = bin.read(bytes, 0, (int)bytesLeft);
					} else {
						count = bin.read(bytes);
					}
					for (NodeHandler node : uploadNodes) { // send buffer to all nodes
						node.sendFileBytes(bytes, count);
					}
					bytesLeft -= count;
				}
				for (NodeHandler node : uploadNodes) { // end file transer; return the NodeHandler locks
					node.endFileTransferToNode();
				}
				// collect file info and add to MasterRecords
				String date = MasterServer.formatTime(System.currentTimeMillis());
				HashSet<String> uploadedNodeIDs = new HashSet<String>(uploadNodes.size());
				for (NodeHandler nh : uploadNodes) {
					uploadedNodeIDs.add(nh.getNodeID());
				}
				records.putUserFileInfo(username, filename, size, date, "/"+username+"/"+filename, uploadedNodeIDs);
				// log event - file insert
				if (!nodeString.isBlank())
					MasterServer.logEvent("fileinsert", System.currentTimeMillis(),
					new String[]{nodeString, username, part[1], String.valueOf(size), "/"+username+"/"+filename});
			}			} catch (Exception e) {
				e.printStackTrace();
			}
			if (!responded) respond("FAILURE"); // make sure client gets a response and doesn't freeze
		}

		private void respond(String response) {
			try {
				dout.writeUTF(response);
				dout.flush();
				System.out.println(response);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

	}
