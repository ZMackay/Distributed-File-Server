import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Queue;
import java.util.HashSet;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;

public class MasterServer {

	private static final SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

	// Queue to list node servers based on value representing start order
	static Queue<NodeConnection> Q = new ConcurrentLinkedQueue<NodeConnection>();

	// set up MasterAuth
	static MasterAuth auth = null;

	// get a MasterRecords instance
	static MasterRecords records = new MasterRecords("masterfiles.txt"); // will load saved data

	public static void main(String[] args) {
		// required system files
		checkAndMakeFiles();

		// read user file to auth class
		auth = new MasterAuth("users");

		// client thread pool executor can support up to 500 client connections
		ExecutorService clientThreadHandler = Executors.newFixedThreadPool(500);

		// node thread pool executor
		ExecutorService nodeThreadHandler = Executors.newFixedThreadPool(10);

		// input port form client
		int acceptingPort = 32005;

		// node host and port
		// port increments but we need to make that dynamic
		String nodeAddress = "kylertracy.com";
		int[] nodePorts = { 32000, 32001, 32002, 32003 };

		// init server socket to listen for client/////////
		ServerSocket ss = null;
		try {
			ss = new ServerSocket(acceptingPort);
		} catch (IOException e) {
			System.err.println("Error starting server");
			e.printStackTrace();
		}

		// state to log file that the server started
		Timestamp ts = new Timestamp(System.currentTimeMillis());
		String stamp = String.format("[serverstart] %s\n", timeFormat.format(ts));
		writeToLogFile(stamp);

		//////////////////////////////////////////////////
		///// attempt connection to every node server //
		// can possible make this dynamic by check all /
		// in a range /
		// Each node server will hold a log and based on /
		// the first time of each we decide which will be/
		// the head server. Then every node must keep a /
		// record of all files in it and then share it to/
		// all other nodes to compare and request any /
		// missing files /
		// For now loop through node ports and set for /
		// Node Connection object /
		//////////////////////////////////////////////////

		
		attemptNodeConnection(nodeThreadHandler, nodeAddress, 40050, "node");
		

		//////////////////////////////////////////////////
		///// listen for client connection in new thread//
		// So we can have multiple users running commands/
		// at the same time /
		//////////////////////////////////////////////////
		listenForClient(clientThreadHandler, ss);

	}

	// public method for connections so that it can be called from the
	// ClientConnection class
	// this allows for a new listening to happen after one connection was already
	// established.
	public static void listenForClient(ExecutorService clientThreadHandler, ServerSocket ss) {
		clientThreadHandler.execute(new ClientConnection("localhost", 32005, "", "", ss, clientThreadHandler));
	}

	public static void attemptNodeConnection(ExecutorService nodeThreadHandler, String host, int port, String name) {
		nodeThreadHandler.execute(new NodeConnection(port, host, nodeThreadHandler, name));
	}

	public static void writeToLogFile(String message) {
		try {
			BufferedWriter bw = new BufferedWriter(new FileWriter("LOG.txt", true));
			bw.write(message);

			bw.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/*
	public static void writeToMasterFile(String message) {
		try {
			BufferedWriter bw = new BufferedWriter(new FileWriter("masterfiles.txt", true));
			bw.write(message+"\n");

			bw.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	*/
/*
	public static ArrayList<String> searchMasterFileForFile(String file, String user, String path) {
		ArrayList<String> arr = new ArrayList<String>();
		try {
			BufferedReader br = new BufferedReader(new FileReader("masterfiles.txt"));
			String line = "";

			while ((line = br.readLine()) != null) {

				if (line.split("::")[0].equals(user)) {
					//user matches
					

					if (line.split("::")[1].equals(file)) {
						//file name matches

						if(line.split("::")[3].equals(path)) {
							//file path matches
							arr.add(line);
						}
						
					}
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		return arr;
	}
*/
	/*
	public static ArrayList<String> getMasterFilesForUser(String user) {
		ArrayList<String> arr = new ArrayList<String>();
		try {
			BufferedReader br = new BufferedReader(new FileReader("masterfiles.txt"));
			String line = "";

			while ((line = br.readLine()) != null) {
				if (line.split("::")[0].equals(user)) {
					arr.add(line);
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		return arr;
	}
*/
	/*
	public static ArrayList<String> getMasterFiles() {
		ArrayList<String> arr = new ArrayList<String>();
		try {
			BufferedReader br = new BufferedReader(new FileReader("masterfiles.txt"));
			String line = "";

			while ((line = br.readLine()) != null) {
				arr.add(line);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		return arr;
	}
	*/

	public static void checkAndMakeFiles() {

		String[] filesToCheck = { "users", "masterfiles.txt" }; // NOTE will not need to do this for masterfiles.txt. done in MasterRecords

		for (String file : filesToCheck) {
			File f = new File(file);
			if (!f.exists()) {
				try {
					f.createNewFile();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

	}

}

class NodeConnection extends Thread {
	// class to handle the connection with node servers

	// input streams for instruction strings
	InputStream inputStream;
	DataInputStream dataInputStream;

	// output streams for instruction strings
	OutputStream outputStream;
	DataOutputStream dataOutputStream;

	// file out stream
	BufferedOutputStream bos;

	// file in stream
	BufferedInputStream bis;

	// time stamp format
	private static final SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

	int port = 0;
	String host = null;
	ExecutorService nodeThreadHandler = null;
	String name = null;
	
	String command = "";

	public NodeConnection(int port, String host, ExecutorService nodeThreadHandler, String name) {
		this.port = port;
		this.host = host;
		this.nodeThreadHandler = nodeThreadHandler;
		this.name = name;
	}

	@Override
	public void run() {
		// socket to connect to node server

		try {

			ServerSocket ss = new ServerSocket(40050);
			
			// Socket connection with a time out of 5 seconds and then it retry
			Socket socket = ss.accept();
			
			MasterServer.attemptNodeConnection(nodeThreadHandler, host, 40050, name);
			

			// add to Q
			MasterServer.Q.add(this);

			// init input streams for instruction strings
			inputStream = socket.getInputStream();
			dataInputStream = new DataInputStream(inputStream);

			// init output streams for instruction strings
			outputStream = socket.getOutputStream();
			dataOutputStream = new DataOutputStream(outputStream);

			// init file in stream
			bis = new BufferedInputStream(inputStream);

			// init file out stream
			bos = new BufferedOutputStream(outputStream);

			// writing to the log for a connection to a node
			Timestamp ts = new Timestamp(System.currentTimeMillis());
			String stamp = String.format("[dnconnect] %s,%s,%s\n", timeFormat.format(ts), this.name,
					socket.getInetAddress());
			MasterServer.writeToLogFile(stamp);

			System.out.println(this.name + " connected");
			while (!(socket.isClosed())) {

				if (socketIsStillConnected(socket)) {
					// main arithmetic for node connection
					// <----------------------------------------------------------------|||||||||||||||||||||||||||||||||||||||||||||||||||||||
					
					if(command.split(",")[0].equals("")) {
						
					}
				} else {
					System.out.println(this.name + " no longer connected");
					
					socket.connect(new InetSocketAddress(host,40050),50000);
					

						MasterServer.Q.remove(this);
						
						socket.close();
						break;	
				}

			}

			// if we reach here then the socket was disconnected
			socket.close();
			ts = new Timestamp(System.currentTimeMillis());
			stamp = String.format("[dnfailure] %s,%s,%s\n", timeFormat.format(ts), this.name, socket.getInetAddress());
			MasterServer.writeToLogFile(stamp);

			// when connection is no longer true
			// execute the thread again
			MasterServer.attemptNodeConnection(nodeThreadHandler, host, port, name);

		} catch (IOException ex) {
			// reaching here means that some error happened connecting to the socket
			// most likely there was a timeout while trying to connect
			// start the next attempt for a connection to this node
			MasterServer.attemptNodeConnection(nodeThreadHandler, host, port, name);
		}
	}

	public boolean socketIsStillConnected(Socket socket) {
		try {
			if(inputStream.read() == -1){
                // Notify via terminal, close connection
                System.out.println("client disconnected. Socket closing...");
                socket.close();
                return false;
            }else{
            	return true;
            }
		} catch (IOException e) {

			e.printStackTrace();
		}
		return false;
	}

	// method used to download file, currently only for client side not node side
	public void downLoadFiletoMaster(BufferedOutputStream bos, BufferedInputStream bis, String message) {
		// recieves a string from node structured like
		try {
			bos = new BufferedOutputStream(new FileOutputStream("," + (message.split(",")[2])), (1024 * 8));

			// download file
			byte[] b = new byte[1024 * 8];
			int len;

			while ((len = bis.read(b)) != -1) {
				bos.write(b, 0, len);
				bos.flush();
			}
		} catch (Exception e) {

		}
	}
	
	public void sendFileBuffered(BufferedInputStream bis, BufferedOutputStream bos, DataOutputStream dataOutputStream, String filePath, String user, String fileLength) {
        try {
            File f = new File(filePath);
            FileInputStream fis = new FileInputStream(f);
            System.out.println("f = " + f.getName() + "it's size " + f.length());
            String instruction = "fileinsert,"+ user +"," + f.getName().split(",")[1] + "," + f.length();
            dataOutputStream.writeUTF(instruction);
            dataOutputStream.flush();

            // Write file
            byte[] b = new byte[1024 * 8];
            int len;

            while ((len = fis.read(b)) != -1) {
                bos.write(b, 0, len);
                bos.flush();
            }
            System.out.println("uh oh2");
            fis.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

	// method used to send file to node
	public void uploadFiletoNode(BufferedOutputStream bos, BufferedInputStream bis, String file, String user) {
		// sends instruction string and then file to node
		System.out.println("attempting upload to null");
		try {
			System.out.println("uh oh");
			File f = new File(file);
			

			dataOutputStream
					.writeUTF("fileinsert," + user + "," + file.split(",")[1] + "," + f.length());
			dataOutputStream.flush();
			System.out.println("uh oh1");
			
			
			// write file
			byte[] b = new byte[1024 * 8];
			int len;

			while ((len = bis.read(b)) != -1) {
				bos.write(b, 0, len);
				bos.flush();
			}
			System.out.println("uh oh2");
		} catch (Exception e) {

		}
		System.out.println("uploaded to node");

		Timestamp ts = new Timestamp(System.currentTimeMillis());

		// add to MasterServer.records
		String filename = file.split(",")[1];
		if (MasterServer.records.fileExists(user, filename)) {
			MasterServer.records.getFileInfo(user, filename).addNode(this.name);
		}
	/* replaced by ^^^^^^^
		// we need to check if its there
		String searchResult = "";
		
		System.out.println("Searching master file for " + user + " : " + file.split(",")[1] + ":" + "/server/" + user + "/files" );
		System.out.println(MasterServer.searchMasterFileForFile(file.split(",")[1], user, ".\\server\\" + user+"\\"));
		if(MasterServer.searchMasterFileForFile(file.split(",")[1], user, ".\\server\\" + user+"\\").size() > 0) {
			//we need to check if this a new node for that entry if so then we append this node to the old string 
			System.out.println("Found one");
			//we need to get that row and rewrite the file
			
			searchResult = MasterServer.searchMasterFileForFile(file.split(",")[1], user, ".\\server\\" + user+"\\").get(0);
			
			if(!fileAlreadyAssignedToNode(searchResult)) {
				String newStr = searchResult+ "," + this.name;	
				
				rewriteMaster(searchResult, newStr);
				
			}
		}else {
			MasterServer.writeToMasterFile(String.format("%s::%s::%s::%s::%s",
													user,
													file.split(",")[1],
													timeFormat.format(ts), ".\\server\\" + user + "\\", this.name));
		}
		
		
*/
	}

	
	
	/*
	public void rewriteMaster(String strToReplace, String replacingStr) {
		String cont = "";
		try {
			String line = "";
			BufferedReader br = new BufferedReader(new FileReader("masterfiles.txt"));
			
			while((line = br.readLine()) != null) {
				if(!line.equals(strToReplace)) {
					cont += line;
				}
			}
			
			cont += replacingStr;
			
			br.close();
			
			BufferedWriter bw = new BufferedWriter(new FileWriter("masterfiles.txt"));
			
			bw.write(cont);
			bw.close();
			
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public boolean fileAlreadyAssignedToNode(String masterFileLine) {
		boolean alreadyInthisNode = false;
		for(String name : masterFileLine.split("::")[4].split(",")) {
			
			System.out.println("checking if " + name + " = " + this.name);
			
			if(!name.equals(this.name)) {
				
			}else {
				alreadyInthisNode = true;
				return alreadyInthisNode;
			}
			
		}
		return alreadyInthisNode;
	}
	*/
}


class ClientConnection extends Thread {

	// time stamp format
	private final SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

	// class to handle the connection from the client via an object

	String host, username, password;
	int port;
	ServerSocket ss;
	MasterServer ms;
	ExecutorService clientHandler;
	Socket client = null;

	// input streams for instruction strings
	InputStream inputStream;
	DataInputStream dataInputStream;

	// output streams for instruction strings
	OutputStream outputStream = null;
	DataOutputStream dataOutputStream = null;

	public ClientConnection(String host, int port, String username, String password, ServerSocket ss,
			ExecutorService clientHandler) {

		this.ss = ss;
		this.host = host;
		this.username = username;
		this.password = password;
		this.port = port;
		this.clientHandler = clientHandler;
	}

	@Override
	public void run() {

		// listen for new connection
		try {
			client = ss.accept();

			System.out.println("connected");

			// after accepting read the initial string to be sent by the client
			// the client will send a string structured as
			// COMMAND,USER,ENCRYPTED-PASSWORD
			inputStream = client.getInputStream();
			dataInputStream = new DataInputStream(inputStream);

			// init output streams
			outputStream = client.getOutputStream();
			dataOutputStream = new DataOutputStream(outputStream);

			// Read incoming data
			String message = dataInputStream.readUTF();

			// an attempt has been made to login
			Timestamp ts = new Timestamp(System.currentTimeMillis());
			String stamp = String.format("[userauth] %s,%s,%s\n", timeFormat.format(ts), message.split(",")[1],
					client.getInetAddress());
			MasterServer.writeToLogFile(stamp);

			if (message.split(",")[0].equals("AUTHENTICATE")) {
				// re-read user file in case of change
				// MasterServer.auth = new MasterAuth("users"); ** the hashmap in MasterAuth will be updated

				// check if login is valid
				if (MasterServer.auth.authenticate(message.split(",")[1], message.split(",")[2])) {

					this.username = message.split(",")[1];
					this.password = message.split(",")[2];

					// log connection
					ts = new Timestamp(System.currentTimeMillis());
					stamp = String.format("[userconnect] %s,%s,%s\n", timeFormat.format(ts), this.username,
							client.getInetAddress());
					MasterServer.writeToLogFile(stamp);
					
					// send confirm to client
					dataOutputStream.writeUTF("SUCCESS");
					dataOutputStream.flush();

				} else {
					// send confirm to client
					dataOutputStream.writeUTF("FAILURE");
					dataOutputStream.flush();

					client.close();

					// start listening for next connection
					MasterServer.listenForClient(clientHandler, ss);

					return;

				}
			} else if (message.split(",")[0].equals("ADD-USER")) {
				System.out.println("adding");
				// add user to dir
				if (MasterServer.auth.addUser(message.split(",")[1], message.split(",")[2])) { // MasterAuth checks if it exists in addUser() and updates itself
					MasterServer.auth.saveToFile(); // update file with latest changes without closing auth 
					
					MasterServer.records.addUser(message.split(",")[1]); // add user to records
					// send confirm to client\
					this.sendString("SUCCESS");

				} else {
					this.sendString("FAILURE");
					
				}
				this.username = message.split(",")[1];
			}

			// start listening for next connection
			MasterServer.listenForClient(clientHandler, ss);

			BufferedInputStream bis = new BufferedInputStream(inputStream);

			BufferedOutputStream bos = null;

			// main listening
			// section---------------------------------------------------------
			try {
				while (true) {
					try {
						// catch if message is empty if not continue
						
						message = dataInputStream.readUTF();
					

						// string should be structured as "UPLOAD,FILENAME,FILESIZE"
						System.out.println(message);

						if (message.equals("GET-FILES")) {
							//<filename>,<size>,<date>::
							String filesList = "";
							if (MasterServer.records.getUserMap().containsKey(username)) { // user exists
								for (String fileName : MasterServer.records.getUserMap().get(username).keySet()) {
									FileInfo fi = MasterServer.records.getFileInfo(username, fileName);
									filesList += fileName +","+ fi.getSize() +","+ fi.getDate() +"::";
								}
							}
							if (filesList == "") {
								filesList = "NONE";
							}
							dataOutputStream.writeUTF(filesList);
							dataOutputStream.flush();
							/*
							Object[] serverElements = MasterServer.getMasterFiles().toArray();
							String[] formatedElements = new String[serverElements.length];
							
							for(Object o : serverElements) {
								System.out.println(o);
							}
							
							*/
							//sendString(Arrays.toString());


						} else if (message.split(",")[0].equals("UPLOAD")) {
							System.out.println("here in upload");
							// set output stream to reflect the file name for incoming file
							// for the master server a received file should be represented as
							// "UPLOAD,FILENAME,FILESIZE"

							// FUTURE:: check if file already exist and notify user to decide to overwrite
							// or change name

							// check if exist in nodes if so send OKAY if not send FALSE
							this.sendString("OKAY");

							System.out.println("sent OKAY");

							// commence download
							this.downLoadFile(bos, bis, message);
							System.out.println("downloaded file");
							dataInputStream.readUTF();
							dataOutputStream.writeUTF("p[pk a");

							HashSet<String> nodeSet = new HashSet<String>();
						
							if (MasterServer.Q.size() > 0) {
								System.out.println("nodes do exist");
								// for every node currently active we need to send a copy of the file to it
								for (NodeConnection node : MasterServer.Q) {
									// file in question
									String file = (this.username + "," + (message.split(",")[1]));

									// send instruction string structured as
									// event,userID,fileName,filesize
									//node.dataOutputStream.writeUTF((String.format("fileinsert,%s,%s", this.username, file, new File(file).length())));
									//node.dataOutputStream.flush();
									
									System.out.println("sending to " + node.name);

									// begin uploading file
									//node.uploadFiletoNode(bos, bis, file, this.username);
									
									node.sendFileBuffered(node.bis, node.bos, node.dataOutputStream, file, this.username, message.split(",")[1]);
									System.out.println("poop 1");
									String response  = node.dataInputStream.readUTF();
									System.out.println("poop 2 + " + response);
									if(response.equals("done")) {
										//write to client that it was completed
									}else {
										//write to client that it failed
									}

									nodeSet.add(node.name);
									
									
									System.out.println("uploaded to node");
								}
								System.out.println("stamping time for upload");
								// we also need to call the insert function for this file to every node server
								// and keep to master record
								//System.out.println(message);
								ts = new Timestamp(System.currentTimeMillis());
								stamp = String.format("[fileinsert] %s,%s,%s,%s,%s\n", timeFormat.format(ts),
										message.split(",")[2], this.username, message.split(",")[1],
										message.split(",")[2], "/server/" + username + "/" + message.split(",")[2]);

								// add file to records
								if (MasterServer.records.getUserMap().containsKey(message.split(",")[0])) { // if user exists
									MasterServer.records.putUserFileInfo(username, message.split(",")[2], timeFormat.format(ts), 
										"/home/uadist/userfiles/"+username+"/"+message.split(",")[2], nodeSet);
								}
								MasterServer.writeToLogFile(stamp);
								System.out.println("stamped time for upload");
							}
						

						} else if (message.split(",")[0].equals("DELETE-FILE")) {
							// file in question <DELETE>,<FILE>
							String file = (this.username + "," + (message.split(",")[1]));
							System.out.println("in delete file");
							
							//make a function to get a list of nodes that have this file
							for (NodeConnection node : MasterServer.Q) {
								
								node.dataOutputStream.writeUTF(String.format("filedelete,%s,%s", this.username,file.split(",")[1]));
								node.dataOutputStream.flush();
								
								System.out.println("waiting on respose for delete file");
								
								String response = node.dataInputStream.readUTF();
								
								if(response.equals("done")) {
									
									this.sendString("SUCCESS");
									MasterServer.records.getUserMap().get(username).remove(message.split(",")[1]); // remove from user's records
									
								}else {
									
									this.sendString("FAIL");

								}
								System.out.println("response for delete file");
							}

						} else if (message.split(",")[0].equals("DOWNLOAD-FILE")) { // "DOWNLOAD,<filename>""
							// check master file for entry of that file
							if (MasterServer.records.fileExists(username, message.split(",")[1])) {
								// request a node that has it for that file
								long size = MasterServer.records.getFileInfo(username, message.split(",")[1]).getSize();
								// send back "OKAY,size"
								// get file from node
								// send to client
								this.sendString("FAILURE"); // for now
							} else {
								this.sendString("FAILURE");
							}
							
						}
					} catch (EOFException EOFex) {
						// no string to read do nothing
						
					}

				}
			} catch (SocketException ex) {
				// Socket is already closed
				System.err.println("An action was just attempted to an already closed socket, ignoring");
				ex.printStackTrace();
			}
			// -------------------------------------------------------------------------------
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	// method used to download file, currently only for client side not node side
	public void downLoadFile(BufferedOutputStream bos, BufferedInputStream bis, String message) {
		FileOutputStream fos;
		try {
			System.out.println("this is the file size" + message.split(",")[2]);
			long fileSize = Long.parseLong(message.split(",")[2]);
			fos = new FileOutputStream(this.username + "," + message.split(",")[1]);
		
		int count = 0;
		long bytesLeft = fileSize; // track number of file bytes not read yet
		int bufSize = 1024*8;
		byte[] bytes = new byte[bufSize];
		while(bytesLeft > 0) {
		    if (bytesLeft < bufSize) {
		        count = bis.read(bytes, 0, (int)bytesLeft);
		        // keeps it from reading too many bytes into the buffer. before, it was reading the next UTF as well
		    } else {
		        count = bis.read(bytes);
		    }
		    fos.write(bytes, 0, count);
		    bytesLeft -= count;
		}
		fos.close();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.out.println("Downloaded a file from client");
	}
	
	public void uploadFileToClient(BufferedOutputStream bos, BufferedInputStream bis, String fileName) {
		try {
			bos = new BufferedOutputStream(new FileOutputStream(fileName),
					(1024 * 8));

			// download file
			byte[] b = new byte[1024 * 8];
			int len;

			while ((len = bis.read(b)) > 0) {
				bos.write(b, 0, len);
				bos.flush();
			}
		} catch (Exception e) {

		}
		System.out.println("Downloaded a file from client");
	}

	public boolean socketIsStillConnected(Socket socket) {
		try {
			if (socket.getInetAddress().isReachable(1000)) {
				return true;
			} else {
				return false;
			}
		} catch (IOException e) {

			e.printStackTrace();
		}
		return false;
	}

	public void sendString(String message) {
		try {
			dataOutputStream.writeUTF(message);
			dataOutputStream.flush();
			System.out.println(message + " -> client");
		} catch (IOException ex) {

		}

	}

	public void uploadFiletoNode(BufferedOutputStream bos, BufferedInputStream bis, String file, String user) {
		// sends instruction string and then file to node
		System.out.println("attempting upload to null");
		try {
			File f = new File(file);

			dataOutputStream
					.writeUTF("UPLOAD," + file.split(",")[1] + "," + f.length() + ",/server/" + user + "/files");
			dataOutputStream.flush();

			// write file
			byte[] b = new byte[1024 * 8];
			int len;

			while ((len = bis.read(b)) != -1) {
				bos.write(b, 0, len);
				bos.flush();
			}

		} catch (Exception e) {

		}

		System.out.println("up loaded to client");
	}

}
