import java.io.*;
import javax.swing.JProgressBar;
//import javax.swing.JFrame;

public class ClientAPITest {

	public static void main(String[] args) {
		String[] unames = new String[]{"shrek","doug","kim possible","letsgobrandon"};
		String[] passwds = new String[]{"donkey","kong","uhhh","joemama"};
		String[] files = null;
		File test1 = new File("uploadTest");
		File test2 = new File("upload1");
		File test3 = new File("upload2");
		File test4 = new File("upload3");
		File[] testFiles = new File[]{test1, test2, test3, test4};
		//JFrame frame = new JFrame("ClientAPI Test");
		JProgressBar pb = new JProgressBar();
		//frame.add(pb);
		//frame.setSize(300,200);
		//frame.setLocationRelativeTo(null);

		// create test file to upload
		/*
		try {
			BufferedWriter bw = new BufferedWriter(new FileWriter(testFile));
			for (int i = 0; i < 100000; i++) { // BIG FILE
				bw.write("This is a test file. It will hopefully make it to the server, and then to a node.\n");
				bw.flush();
			}
			bw.close();
		} catch (Exception e) {
			System.out.println("Failed to create and write to file. Exiting.");
			e.printStackTrace();
			System.exit(100);
		}
		System.exit(0);
		*/

		// frame.setVisible(true);
		
		ClientAPI clapi = new ClientAPI();

		for (int i = 0; i < unames.length; i++) {
			// try to connect to the server
			if (clapi.connectToServer()) {
				System.out.println("Connected to Server.");
				// try to add the user
				if (clapi.addUser(unames[i], passwds[i])) {
					System.out.println("Added user: " + unames[i]);

				} else {
					System.out.println("Failed to add user.");
				}
			} else {
				System.out.println("Failed to connect.");
			}
			// close the connection
			clapi.close();

			// try to connect to the server again
			if (clapi.connectToServer()) {
				System.out.println("Connected to Server.");

				// try to authenticate with the user just created
				if (clapi.authenticate(unames[i], passwds[i])) {
					System.out.println("Authenticated user: " + unames[i]);

					// get list of files. should contain our test file
					System.out.println("Retrieving list of files:");
					if(clapi.getFileListFromServer()) {
						files = clapi.getFilesArray();
					}
					if (files != null) {
						System.out.println("Listing files:");
						for (String file : files) {
							System.out.println(file);
						}
					}
					for (File f : testFiles) {
						// UPLOAD
						if (clapi.uploadFile(f, pb)) {
							System.out.println("Upload successful.");
						} else {
							System.out.println("Upload failed.");
						}
					}
					for (File f : testFiles) {
						// UPLOAD
						if (clapi.uploadFile(f, pb)) {
							System.out.println("Upload successful.");
						} else {
							System.out.println("Upload failed.");
						}
					}
					/// GET-FILES
					if(clapi.getFileListFromServer()) {
						files = clapi.getFilesArray();
					}
					for (File f : testFiles) {
						// DOWNLOAD
						if (clapi.downloadFile(f.getName(), pb)) {
							System.out.println("Download successful.");
						} else {
							System.out.println("Download failed.");
						}
					}
					/*
					for (File f : testFiles) {
						// DELETE
						if (clapi.deleteFile(f.getName())){
							System.out.println("Delete successful.");
						} else {
							System.out.println("Delete failed.");
						}
					}
					*/
					/// GET-FILES
					if(clapi.getFileListFromServer()) {
						files = clapi.getFilesArray();
					}
					/*
					for (File f : testFiles) {
						// DOWNLOAD
						if (clapi.downloadFile(f.getName(), pb)) {
							System.out.println("Download successful.");
						} else {
							System.out.println("Download failed.");
						}
					}
					*/
				} else {
					System.out.println("Failed to authenticate.");
				}
			} else {
				System.out.println("Failed to connect.");
			}
			// close the connection
			clapi.close();
		}
		//frame.setVisible(false);
		//frame.dispose();

	}

}