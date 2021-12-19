/********************************
Name: Zachary Mackay, Kyler Tracy
Username: ?????, ua839
Problem Set: LionDB
Due Date: December 8, 2021
********************************/

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.LinkedList;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
/**
 * Listens for data nodes and manages initial connection.
 * The run() method will be called by the ExecutorService when a NodeListener instance
 * is submitted. It starts by listening for the next connection on this ServerSocket.
 * Once accepted, it submits another NodeListener instance to the ExecutorService and
 * handles the data node setup. Data node setup starts by checking if the node has been
 * previously connected. If it has not and is a new data node, it is assigned and sent a name.
 * If the node was previously connected, it is sent a list of files it should have
 * and if any are missing, those files will be transferred from another node if possible.
 * The node connection is added to the activeNodes set and available to the ClientHandler instances.
 * @author Zachary Mackay
 * @author Kyler Tracy
 */
public class NodeListener implements Runnable {
    private ServerSocket ss;
	private Socket node;
	private DataInputStream dis;
	private DataOutputStream dos;
	private ConcurrentHashMap<String,NodeHandler> activeNodes;
	private ConcurrentHashMap<String,NodeHandler> inactiveNodes;
	private ExecutorService threads;
	private MasterRecords records;
	/**
	 * Class constructor. Gets all information necessary to listen for and handle another data node connection.
	 * @param ss				the ServerSocket instance that is listening for data node connections
	 * @param activeNodes		the set of currently active node connections
	 * @param inactiveNodes		the set of currently inactive node connections
	 * @param threads			the thread pool for this server
	 * @param records			the MasterRecords instance for this server
	 */
	public NodeListener(ServerSocket ss, ConcurrentHashMap<String,NodeHandler> activeNodes,
		ConcurrentHashMap<String,NodeHandler> inactiveNodes, ExecutorService threads, MasterRecords records) {
		this.ss = ss;
		this.activeNodes = activeNodes;
		this.inactiveNodes = inactiveNodes;
		this.threads = threads;
		this.records = records;
	}
	/**
	 * Initializes new data node connections and handles data node file checking.
	 */
	public void run() {
		try {
			node = ss.accept();
			threads.submit(new NodeListener(ss, activeNodes, inactiveNodes, threads, records));

			dis = new DataInputStream(node.getInputStream());
			dos = new DataOutputStream(node.getOutputStream());
			String message = dis.readUTF();
			String nodeID;

			// new node
			if (message.equals("NEW-NODE")) {
				nodeID = "node" + inactiveNodes.size(); // starts at 0 so size will increment
				inactiveNodes.put(nodeID, NodeHandler.NULL_HANDLER); // create entry in inactiveNodes
				activeNodes.put(nodeID, new NodeHandler(node, nodeID, activeNodes, inactiveNodes));
				dos.writeUTF(nodeID); // send assigned name to node
				dos.flush();
				System.out.println("New node connected: "+nodeID);
				MasterServer.addNode(nodeID); // add nodeID to node file
				// log event - node connected
				MasterServer.logEvent("dnconnect", System.currentTimeMillis(),
						new String[]{nodeID, node.getInetAddress().toString()});


			} else {
				nodeID = message;
				if (!inactiveNodes.containsKey(nodeID)) { // if this nodeID doesn't exist
					System.out.println("Node connected with invalid string "+nodeID+", denied.");
					// log event
					MasterServer.logEvent("dnconnect", System.currentTimeMillis(),
						new String[]{"INVALID", node.getInetAddress().toString()});
					return;
				}
				// previously connected node
				if (!inactiveNodes.get(nodeID).equals(NodeHandler.NULL_HANDLER)) {
					 // give node a new NodeHandler with same totalActions count as previous one
					activeNodes.put(nodeID, new NodeHandler(node, nodeID, activeNodes,
						inactiveNodes, inactiveNodes.get(nodeID).getTotalActions()));
					inactiveNodes.put(nodeID, NodeHandler.NULL_HANDLER);
				} else {
					// first time this node has connected during this session; set active with new NodeHandler
					activeNodes.put(nodeID, new NodeHandler(node, nodeID, activeNodes, inactiveNodes));
					inactiveNodes.put(nodeID, NodeHandler.NULL_HANDLER);
				}
				System.out.println("Existing node connected: "+nodeID);
				// log event - node connected
				MasterServer.logEvent("dnconnect", System.currentTimeMillis(),
				new String[]{nodeID, node.getInetAddress().toString()});

				LinkedList<String> filesToSend = new LinkedList<String>(); // list of any possible missing files
				String nodesFiles = records.getNodeFileList(nodeID);
				String[] files;
				if (!nodesFiles.isBlank()) files = nodesFiles.split("\n");
				else files = new String[0];

				for (String file : files) {
					dos.writeUTF(file);
					dos.flush();
					if (dis.readUTF().equals("false")) {
						filesToSend.push(file);
					}
				}
				dos.writeUTF("done");
				dos.flush();
				System.out.println("Sent file list to "+nodeID+". "+filesToSend.size()+" missing files.");

				// "<username>,<filename>,<filesize>"
				String[] part;
				NodeHandler thisNode = activeNodes.get(nodeID);
				NodeHandler nodeWithFile = null;
				while (!filesToSend.isEmpty()) { // find least accessed active node with this file
					try {
						part = filesToSend.pop().split(",");
						Set<String> nodes = records.getFileNodes(part[0], part[1]);
						for (String n : nodes) {
							if(activeNodes.containsKey(n) && !n.equals(thisNode.getNodeID())) {
								if (nodeWithFile == null || activeNodes.get(n).getTotalActions() < nodeWithFile.getTotalActions()) {
									nodeWithFile = activeNodes.get(n);
								}
							}
						}
						// transfer missing files to node from another node that has them, if possible
						if (nodeWithFile != null) {
							nodeWithFile.transferFileToNode(part[0], part[1], Long.parseLong(part[2]), thisNode);
						} else {
							System.out.println("Could not transfer missing files to "+ thisNode.getNodeID());
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
			System.out.println("Currently active nodes: ");
			for (String nh : activeNodes.keySet()) {
				System.out.print(nh + ", ");
			}
			System.out.println();

		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
