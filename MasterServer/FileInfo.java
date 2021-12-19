/********************************
Name: Kyler Tracy
Username: ua839
Problem Set: LionDB
Due Date: December 8, 2021
********************************/
import java.util.Collections;
import java.util.Set;
/**
 * Simple data structure to store file information. 
 * Synchronizes access using three separate objects for date, path, and size fields.
 * The node set is converted to a synchronized set and must not be null or contain null values.
 * @author Kyler Tracy
 */
public class FileInfo {

	private String date;
	private String path;
	private Set<String> nodes;
	private long size; // not required for masterfiles.txt, but might be useful

	private final Object dateLock = new Object(); // simple thread safety
	private final Object pathLock = new Object(); // each field has own lock, lower contention
	private final Object sizeLock = new Object();
	/**
	 * Class constructor.
	 * @param nodeSet		the set of data nodes associated with this file. Must not be null or contain null values.
	 */
	public FileInfo(Set<String> nodeSet) { // must not be null
		this.nodes = Collections.synchronizedSet(nodeSet);
	}
	/**
	 * Class constructor.
	 * @param date			the String representation of the date associated with this file
	 * @param path			the file system path associated with this file
	 * @param nodeSet		the set of data nodes associated with this file. Must not be null or contain null values.
	 */
	public FileInfo(String date, String path, Set<String> nodeSet) {
		this.date = date;
		this.path = path;
		this.nodes = Collections.synchronizedSet(nodeSet);
	}
	/**
	 * Class constructor.
	 * @param date			the String representation of the date associated with this file
	 * @param path			the path associated with this file
	 * @param size			the syze in bytes of this file
	 * @param nodeSet		the set of data nodes associated with this file. Must not be null or contain null values.
	 */
	public FileInfo(String date, String path, long size, Set<String> nodeSet) {
		this.date = date;
		this.path = path;
		this.size = size;
		this.nodes = Collections.synchronizedSet(nodeSet);
	}
	/**
	 * @return 	the date associated with this file
	 */
	public String getDate() {
		synchronized(dateLock) {
			return this.date;
		}
	}
	/**
	 * @return	the path associated with this file
	 */
	public String getPath() {
		synchronized(pathLock) {
			return this.path;
		}
	}
	/**
	 * @return	the set of data nodes associated with this file
	 */
	public Set<String> getNodes() {
		return this.nodes;
	}
	/**
	 * @return the size of this file in bytes
	 */
	public long getSize() {
		synchronized(sizeLock) {
			return this.size;
		}
	}
	/**
	 * @param date	the date associated with this file
	 */
	public void setDate(String date) {
		synchronized(dateLock) {
			this.date = date;
		}
	}
	/**
	 * @param path the path associated with this file
	 */
	public void setPath(String path) {
		synchronized(pathLock) {
			this.path = path;
		}
	}
	/**
	 * @param size	the size of this file in bytes
	 */
	public void setSize(long size) {
		synchronized(sizeLock) {
			this.size = size;
		}
	}
	/**
	 * Add a node to existing set of data nodes associated with this file.
	 * @param node	a data node associated with this file
	 */
	public void addNode(String node) {
		this.nodes.add(node);
	}

}