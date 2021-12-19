/********************************
Name: Zachary Mackay, Kyler Tracy
Username: ?????, ua839
Problem Set: LionDB
Due Date: December 8, 2021
********************************/

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.net.ServerSocket;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * Starting point for all processes that take place on the master server. Initializes resources
 * and creates ClientListener and NodeListener instances to handle connections.
 * This class and the classes it uses will create auth.txt, nodes.txt, masterfiles.txt, and log.txt
 * in this directory and relies on auth.txt, nodes.txt, and masterfiles.txt to function properly.
 * @author Zachary Mackay
 * @author Kyler Tracy
 */
public class MasterServer {

	private static final int clientPort = 32005;
	private static final int nodePort = 40050;
	private static final String authFile = "auth.txt";
	private static final String nodeFile = "nodes.txt";
	private static final String recordsFile = "masterfiles.txt";
	private static final String logFile = "log.txt";
	private final MasterRecords records = new MasterRecords(recordsFile);
	private final MasterAuth clientAuth = new MasterAuth(authFile);
	private ServerSocket clientSS;
	private ServerSocket nodeSS;
	private final ExecutorService threads = Executors.newCachedThreadPool();
	private ConcurrentHashMap<String,NodeHandler> activeNodes;
	private ConcurrentHashMap<String,NodeHandler> inactiveNodes;
    private static final SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static final ReentrantLock logLock = new ReentrantLock(true);
	private static final ReentrantLock nodeLock = new ReentrantLock(true);

	/**
	 * Class constructor. Creates necessary files if needed and reads in a list of nodes from
	 * nodes.txt if it already exists in this directory.
	 */
	public MasterServer() {
		try {
			new File(nodeFile).createNewFile();
			new File(logFile).createNewFile();
			createNodeMaps();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	/**
	 * Main method. Initializes an instance of this class and calls its startServers() method.
	 * @param args	no command line arguments are used
	 */
	public static void main(String[] args) {
		MasterServer dms = new MasterServer();
		dms.startServers();
	}
	/**
	 * Starts listening for TCP socket connections.
	 * Initializes both a ClientListener and a NodeListener to start listening for connections.
	 */
	public void startServers() {
		try {
			clientSS = new ServerSocket(clientPort);
			nodeSS = new ServerSocket(nodePort);
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(100);
		}
		try {
			threads.submit(new ClientListener(clientSS, clientAuth, activeNodes, threads, records));
			threads.submit(new NodeListener(nodeSS, activeNodes, inactiveNodes, threads, records));
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(100);
		}

	}

	private void createNodeMaps() {
		activeNodes = new ConcurrentHashMap<String,NodeHandler>();
		inactiveNodes = new ConcurrentHashMap<String,NodeHandler>();
		try {
			BufferedReader br = new BufferedReader(new FileReader(nodeFile));
			String nodeID;
			while ((nodeID = br.readLine()) != null) {
				inactiveNodes.put(nodeID, NodeHandler.NULL_HANDLER);
			}
			br.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	/**
	 * Adds a data node name to the list in nodes.txt file. These names are used
	 * like logins when a node connects to the server, allowing data nodes to check for
	 * any missing files and retrieve their previous state on reconnection to the master server.
	 * @param nodeID	the name of the node to be added to the file
	 */
	public static void addNode(String nodeID) {
		nodeLock.lock();
		try {
			BufferedWriter bw = new BufferedWriter(new FileWriter(nodeFile, true));
			bw.write(nodeID);
			bw.newLine();
			bw.close();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			nodeLock.unlock();
		}
	}
	/**
	 * Formats a time using the standard MasterServer time format.
	 * @param timeMillis	time in milliseconds. Returned by System.currentTimeMillis();
	 * @return				formatted String of the time represented by the timeMillis argument
	 */
	public static String formatTime(long timeMillis) {
		return timeFormat.format(new Timestamp(timeMillis));
	}
	/**
	 * Logs an event to the log file, log.txt, after formatting the provided information.
	 * @param event			name of event to log
	 * @param timeMillis	time of event in milliseconds
	 * @param info			any additional information for this log entry
	 */
	public static void logEvent(String event, long timeMillis, String[] info) {
        String line = "";
        try {
            String time = timeFormat.format(new Timestamp(timeMillis));
            line = String.format("%-14s %s,", "["+event+"]", time);
            for (int i = 0; i < info.length; i++) {
                line += info[i];
                if (i < info.length-1) line += ",";
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        logLock.lock();
        try {
			BufferedWriter bw = new BufferedWriter(new FileWriter(logFile, true));
            bw.write(line);
            bw.newLine();
            bw.close();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            logLock.unlock();
        }
    }

}
