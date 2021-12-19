/********************************
Name: Zachary Mackay, Kyler Tracy, David Nguyen
Username: ?????, ua839, ?????
Problem Set: LionDB
Due Date: December 8, 2021
********************************/

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
/**
 * Manages communication and file transfer with connected data nodes.
 * If any of these methods result in an error, the connection to this node is restarted.
 * Implements a fair ReentrantLock for thread safety.
 * @author Zachary Mackay
 * @author Kyler Tracy
 * @author David Nguyen
 */
public class NodeHandler {
    /**
     * Comparator for sorting nodes based on least number of total actions.
     */
    public static final Comparator<NodeHandler> LEAST_ACCESSED_ORDER = new NodeActionCountComparator();
    private Socket node;
    private String nodeID;
    private int totalActions;
    private BufferedInputStream bis;
    private BufferedOutputStream bos;
    private DataInputStream dis;
    private DataOutputStream dos;
    /**
     * NodeHandler instances with all null fields, available as a NIL value in data structures
     * that do not support null values.
     */
    public static final NodeHandler NULL_HANDLER = new NodeHandler(); // used as NIL for ConcurrentHashMap
    private ConcurrentHashMap<String,NodeHandler> activeNodes;
    private ConcurrentHashMap<String,NodeHandler> inactiveNodes;
    private final ReentrantLock lock = new ReentrantLock(true);
    /**
     * Class constructor. Gets information required to communicate with this data node.
     * @param node              Socket instance for this node connection
     * @param nodeID            the ID or name associated with the connected node
     * @param activeNodes       the set of active nodes
     * @param inactiveNodes     the set of inactive nodes
     */
    public NodeHandler(Socket node, String nodeID, ConcurrentHashMap<String,NodeHandler> activeNodes,
        ConcurrentHashMap<String,NodeHandler> inactiveNodes) {
        this.node = node;
        this.nodeID = nodeID;
        this.activeNodes = activeNodes;
        this.inactiveNodes = inactiveNodes;
        makeStreams();
    }
    /**
     * Class constructor. Also initializes the totalActions count for this instance.
     * Useful for reconnecting a previously connected node.
     * @param node              Socket instance for this node connection
     * @param nodeID            the ID or name associated with the connected node
     * @param activeNodes       the set of active nodes
     * @param inactiveNodes     the set of inactive nodes
     * @param totalActions      initial value for the totalActions count
     */
    public NodeHandler(Socket node, String nodeID, ConcurrentHashMap<String,NodeHandler> activeNodes,
        ConcurrentHashMap<String,NodeHandler> inactiveNodes, int totalActions) {
        this.node = node;
        this.nodeID = nodeID;
        this.activeNodes = activeNodes;
        this.inactiveNodes = inactiveNodes;
        this.totalActions = totalActions; // for reinitializing a previously connect node
        makeStreams();
    }

    private NodeHandler() { } // for NULL_HANDLER
    /**
     * Returns the total sum of inserts, deletes, and accesses for this node
     * during this server session.
     * @return  the action count for this node
     */
    public int getTotalActions() {
        return this.totalActions;
    }
    /**
     * Returns the node ID or name associated with this connected node
     * @return  the name of this node
     */
    public String getNodeID() {
        return this.nodeID;
    }

    // initialize IO streams
    private void makeStreams() {
        try {
            this.bis = new BufferedInputStream(node.getInputStream());
            this.bos = new BufferedOutputStream(node.getOutputStream());
            this.dis = new DataInputStream(bis);
            this.dos = new DataOutputStream(bos);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    /**
     * Requests a list of this node's user files and returns it in a String.
     * @param username  the username for the files to retrieve
     * @return          a String of files, possibly empty, or null on error
     */
    // get filelist from node. returns String of files or empty string if no files, returns null on error
    public String fileList(String username) {
        String fileList = "";
        lock.lock();
        try {
            sendCommand("filelist,"+username+",dummyFilename");
            String message;
            while (!(message = getMessage()).equals("done")) {
                fileList += message;
            }
            totalActions++;
        } catch (Exception e) {
            e.printStackTrace();
            fileList = null;
        } finally {
            totalActions++;
            lock.unlock();
        }
        return fileList;
    }
    /**
     * Sends a request to the server to check if a file exists.
     * If this method returns false, meaning the data node did not have
     * the specified file, the node assumes it should, restarts and
     * fixes any problems in the NodeHandler when it reconnects.
     * @param username  username associated with the file to check
     * @param filename  name of the file to check
     * @param size      size of the file to check
     * @return          true if the server contains the file, false otherwise
     */
    public boolean fileExists(String username, String filename, long size) {
        boolean ret = false;
        lock.lock();
        try {
            sendCommand("fileexists,"+username+","+filename+","+size);
            String resp = getMessage();
            getMessage(); // get "done" message
            if (resp.equals("true")) ret = true;
        } catch (Exception e) {
            e.printStackTrace();
            logFailure();
        } finally {
            lock.unlock();
        }
        return ret;
    }
    /**
     * Sends a request to the node to delete a file.
     * @param username  the username associated with the file to delete
     * @param filename  the name of the file to delete
     */
    public void deleteFile(String username, String filename) {
        lock.lock();
        sendCommand("filedelete," + username +","+ filename + ",1234");
        getMessage();
        totalActions++;
        lock.unlock();
    }
    /**
     * Transfer a file from this connected node to another. Uses the startFileTransferToNode
     * method of the other NodeHandler.
     * @param username      the username associated with the file to transfer
     * @param filename      the name of the file to transfer
     * @param fileSize      the size in bytes of the file to transfer. This must be accurate.
     * @param recipient     the NodeHandler instance for the node receiving the file
     */
    public void transferFileToNode(String username, String filename, long fileSize, NodeHandler recipient) {
        lock.lock();
        try {
            totalActions++;
            sendCommand("fileaccess,"+ username +","+ filename +",1234");
            recipient.startFileTransferToNode(username, filename, fileSize);
            int bufSize = 1024*8;
            byte[] bytes = new byte[bufSize];
		    int count = 0;
		    long bytesLeft = fileSize;
		    while (bytesLeft > 0) {
                if (bytesLeft > bufSize) {
                    count = bis.read(bytes);
                    recipient.sendFileBytes(bytes, count);
                } else {
                    count = bis.read(bytes, 0, (int)bytesLeft);
                    recipient.sendFileBytes(bytes, (int)bytesLeft);
                }
		    	bytesLeft -= count;
		    }
            recipient.endFileTransferToNode();
        } catch (Exception e) {
            e.printStackTrace();
            logFailure();
        } finally {
            lock.unlock();
        }
    }
    /**
     * Transfer a file from this connected node to a client.
     * @param username      the username associated with the file to transfer
     * @param filename      the name of the file to transfer
     * @param fileSize      the size in bytes of the file to transfer. This must be accurate.
     * @param clientBos     the BufferedOutputStream instance for the client receiving the file
     */
    public void transferFileToClient(String username, String filename, long fileSize, BufferedOutputStream clientBos) {
        lock.lock();
        try {
            totalActions++;
            sendCommand("fileaccess,"+ username +","+ filename +",1234");
            int bufSize = 1024*8;
            byte[] bytes = new byte[bufSize];
		    int count = 0;
		    long bytesLeft = fileSize;
		    while (bytesLeft > 0) {
                if (bytesLeft > bufSize) {
                    count = bis.read(bytes);
                    clientBos.write(bytes, 0, count);
                } else {
                    count = bis.read(bytes, 0, (int)bytesLeft);
                    clientBos.write(bytes, 0, (int)bytesLeft);
                }
		    	clientBos.flush();
		    	bytesLeft -= count;
		    }
            getMessage();
        } catch (Exception e) {
            e.printStackTrace();
            logFailure();
        } finally {
            lock.unlock();
        }
    }
    /**
     * Sends a test instruction to this connected node to determin if it is responding.
     * If the node is not responding, it is removed from the activeNodes set and the connection
     * is reset.
     * @return  true if the node responds normally, false otherwise
     */
    public boolean isResponding() {
        sendCommand("test,test,test,test");
        getMessage();
        // if read/write caused an error, this node will be inactive
        if (inactiveNodes.get(nodeID).equals(this)) {
            return false;
        }
        return true;
    }
    //--------------------these methods must be used together on file transfer-------------------------------
    /**
     * Sends a request to transfer a file to this connected node. This method grants the
     * calling thread a lock. It must be followed by sendFileBytes() and endFileTransferToNode().
     * This method will retrieve this NodeHandler's lock and it will not be returned until
     * endFileTransferToNode() has been called.
     * @param username      the username associated with the file to transfer
     * @param filename      the name of the file to transfer
     * @param size          the size in bytes of the file to transfer. This must be accurate.
     */
    // allows uploading to multiple nodes at the same time
    // notify the node to start a file upload and get the node handler's lock
    public void startFileTransferToNode(String username, String filename, long size) {
        lock.lock();
        totalActions++;
        sendCommand("fileinsert,"+username+","+filename+","+size);
    }
    /**
     * Sends the specified number of bytes from the buffer to this connected node.
     * To be called after startFileTransferToNode() and before endFileTransferToNode().
     * @param bytes         buffer containing bytes to transfer
     * @param numBytes      the number of bytes to read from the buffer. This must be accurate.
     */
    public void sendFileBytes(byte[] bytes, int numBytes) {
        try {
            bos.write(bytes, 0, numBytes);
            bos.flush();
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Error writing to node socket, setting node to inactive.");
            logFailure(); // log event - node failure
        }
    }
    /**
     * Ends the file tranfer to this node. To be called after all bytes are transferred
     * using the sendFileBytes() method. This will return the lock and allow other threads
     * to use methods in this object.
     */
    // NOTE: it will hang if it just isn't getting anything from the node
    public void endFileTransferToNode() {
        getMessage();
        lock.unlock();
    }
    //-------------------------------------------------------------------------------------------------------

    private void sendCommand(String command) {
        lock.lock();
        try {
            dos.writeUTF(command);
            dos.flush();
            System.out.println(command);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Error writing to node socket, setting node to inactive.");
			logFailure(); // log event - node failure
        } finally {
            lock.unlock();
        }
    }

    private String getMessage() {
        String ret = null;
        lock.lock();
        try {
            ret = dis.readUTF();
            System.out.println(ret);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Error reading from node socket, setting node to inactive.");
			logFailure(); // log event - node failure
        } finally {
            lock.unlock();
        }
        return ret;
    }

    private void logFailure() {
        // set node inactive
        try {
            inactiveNodes.put(nodeID, this);
            activeNodes.remove(nodeID);
            node.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        // log event - node failure
        MasterServer.logEvent("dnfailure", System.currentTimeMillis(),
            new String[]{nodeID, node.getInetAddress().toString()});
    }

    static class NodeActionCountComparator implements Comparator<NodeHandler> {
		// negative if the first value is less than the second value in the sort order
		public int compare(NodeHandler n1, NodeHandler n2) {
			return n1.getTotalActions() - n2.getTotalActions();
		}
	}

}
