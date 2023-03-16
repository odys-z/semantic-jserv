/**
 * 
 */
package io.odysz.semantic.tier.docs;

import java.util.HashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @since 1.4.18
 * 
 * @author odys-z@github.com
 *
 */
public class DocLocks {

	private static HashMap<String, ReentrantReadWriteLock> locks;

	static {
		locks = new HashMap<String, ReentrantReadWriteLock>();
	}
	
	public static void reading(String fullpath) {
		if (!locks.containsKey(fullpath))
			locks.put(fullpath, new ReentrantReadWriteLock());
		locks.get(fullpath).readLock().lock();
	}

	public static void readed(String fullpath) {
		locks.get(fullpath).readLock().unlock();
	}

	public static void writing(String fullpath) {
		if (!locks.containsKey(fullpath))
			locks.put(fullpath, new ReentrantReadWriteLock());
		locks.get(fullpath).writeLock().lock();
	}

	public static void writen(String fullpath) {
		locks.get(fullpath).writeLock().unlock();
	}
}
