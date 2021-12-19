/********************************
Name: Kyler Tracy
Username: ua839
Problem Set: LionDB
Due Date: December 8, 2021
********************************/

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.BufferedReader;
import java.io.FileReader;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Simple authentication. Implements a fair ReentrantLock for thread safety.
 * @author	Kyler Tracy
 */
public class MasterAuth {

	private static final boolean DEBUG = false;
	private HashMap<String, byte[]> logins;
	private String path;
	private final ReentrantLock lock = new ReentrantLock(true); // will give the lock to threads in order

	/**
	 * Class constructor. Accepts a path to the file to read existing credentials
	 * and also to save new credientials as accounts are created.
	 * @param path	the path for loading and saving account credentials
	 */
	// accepts a path to the authentication file (username::passwordHash)
	public MasterAuth(String path) {
		logins = new HashMap<String, byte[]>();
		this.path = path;
		loadFromFile();
	}

	// loads data into the HashMap from the save file
	private void loadFromFile() {
		lock.lock();
		try {
			File f = new File(path);
			f.createNewFile();
			BufferedReader br = new BufferedReader(new FileReader(f));
			String line;
			String[] tokens;
			while ((line = br.readLine()) != null) {
				tokens = line.split("::");
				logins.put(tokens[0], Base64.getDecoder().decode(tokens[1].getBytes()));
			}
			br.close();
			System.out.println(logins.keySet().toString()); // DEBUGGING, REMOVE
		} catch (Exception e) {
			if (DEBUG) e.printStackTrace();
		} finally {
			lock.unlock();
		}
	}

	/**
	 * Checks to see if the provided credentials match a known account.
	 * @param uname			the username to authenticate
	 * @param passwdHash	the hash of the password to check
	 * @return				true on successful authentication, false otherwise
	 */
	public boolean authenticate(String uname, String passwdHash) {
		boolean ret = false;
		lock.lock();
		try {
			if (logins.containsKey(uname)) {
				if (MessageDigest.isEqual(logins.get(uname),
					Base64.getDecoder().decode(passwdHash))) {
					ret = true;
				}
			}
		} catch (Exception e) {
			if (DEBUG) e.printStackTrace();
		} finally {
			lock.unlock();
		}
		return ret;
	}
	/**
	 * Requests to add an account with the given credentials.
	 * @param uname			the username of the account to add
	 * @param passwdHash	the hashed password of the account to add
	 * @return				true if this account was successfully added, false otherwise
	 */
	// adds a user
	// returns true if successful, false otherwise
	public boolean addUser(String uname, String passwdHash) {
		boolean ret = false;
		lock.lock();
		try {
			if (!logins.containsKey(uname)) {
				logins.put(uname, Base64.getDecoder().decode(passwdHash));
				ret = true;
				// add new credentials to the file
				BufferedWriter bw = new BufferedWriter(new FileWriter(path, true));
				bw.write(uname + "::" + Base64.getEncoder().encodeToString(logins.get(uname)));
				bw.newLine();
				bw.close();
			}
		} catch (Exception e) {
			if (DEBUG) e.printStackTrace();
		} finally {
			lock.unlock();
		}
		return ret;
	}
	/**
	 * Returns the set of usernames of all accounts on record.
	 * @return	set of all current usernames
	 */
	public Set<String> getKeySet() {
		lock.lock();
		Set<String> ret = logins.keySet();
		lock.unlock();
		return ret;
	}

}