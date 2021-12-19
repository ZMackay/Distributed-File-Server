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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
/**
 * Handles initial authentication and user adding for client connections.
 * Starts by listening for the next connection. On connection, instantiates another
 * ClientListener and adds it to the thread pool. If the client connection fails
 * to authenticate, the connection is closed. If authentication is successful,
 * a ClientHandler instance is created for this connection and added to the thread pool.
 * @author Zachary Mackay
 * @author Kyler Tracy
 */
public class ClientListener implements Runnable {
	private ServerSocket ss;
	private Socket client;
	private DataInputStream dis;
	private DataOutputStream dos;
	private ExecutorService threads;
	private MasterAuth clientAuth;
	private ConcurrentHashMap<String,NodeHandler> activeNodes;
	private MasterRecords records;
	/**
	 * Class constructor. Gets all the necessary information to initialize a client connection.
	 * @param ss			the ServerSocket instance listening for client connections
	 * @param clientAuth	the MasterAuth instance used to authenticate users
	 * @param activeNodes	the set of activeNodes. Used by the ClientHandler instances.
	 * @param threads		the thread pool for this server
	 * @param records		the MasterRecords instance used by this server
	 */
	public ClientListener(ServerSocket ss, MasterAuth clientAuth, ConcurrentHashMap<String,NodeHandler> activeNodes,
		ExecutorService threads, MasterRecords records) {
		this.ss = ss;
		this.clientAuth = clientAuth;
		this.activeNodes = activeNodes;
		this.threads = threads;
		this.records = records;
	}
	/**
	 * Initializes another ClientListener on connection and handles client authentication.
	 */
	public void run() {
		try {
			client = ss.accept(); // accept new connection, listen for another one
			threads.submit(new ClientListener(ss, clientAuth, activeNodes, threads, records));

			dis = new DataInputStream(client.getInputStream());
			dos = new DataOutputStream(client.getOutputStream());
			String message = dis.readUTF();
			String[] part = message.split(",");
			if (part.length <3) return; // not enough info to connect user
			System.out.println(message);

			// log event - user trying to authenticate or add a new account
			MasterServer.logEvent("userauth", System.currentTimeMillis(),
			new String[]{part[1], client.getInetAddress().toString()});

			// authentication
			if (part[0].equals("AUTHENTICATE")) {
				if (clientAuth.authenticate(part[1],part[2])) {
					dos.writeUTF("SUCCESS");
					dos.flush();
					System.out.println("Connecting client: " + part[1]);
				} else {
					client.close();
					System.out.println("Authentication failed. Client disconnected.");
					return;
				}
			// new user
			} else if (part[0].equals("ADD-USER")) {
				if (clientAuth.addUser(part[1],part[2])) {
					dos.writeUTF("SUCCESS");
					dos.flush();
					records.addUser(part[1]);
					System.out.println("Connecting new client: " + part[1]);
				} else {
					client.close();
					System.out.println("Failed to add user. Client disconnected.");
					return;
				}
			}
			threads.submit(new ClientHandler(client, part[1], activeNodes, records));

			// log event - user is connected
			MasterServer.logEvent("userconnect", System.currentTimeMillis(),
			new String[]{part[1], client.getInetAddress().toString()});

		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
