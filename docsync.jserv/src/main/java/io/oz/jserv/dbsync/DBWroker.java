package io.oz.jserv.dbsync;

import io.odysz.semantics.x.SemanticException;
import io.oz.jserv.docsync.SynodeMode;

/**
 * Worker for db synchronization.
 * 
 * @author odys-z@github.com
 */
public class DBWroker implements Runnable {
	
	/** synode id */
	String myId;

	/** upper synode id */
	String upperId;

	/** clean session timestamps */
	TimeWindow window; 

	SynodeMode mode;

	public DBWroker(String synode, SynodeMode m) {
		myId = synode;
		mode = m;
	}
	
	/**
	 * Merge syn_clean
	 * 
	 * @param upperNode
	 * @throws SemanticException
	 */
	public void scanvage(String upperNode) throws SemanticException {
		upperId = upperNode;
		
		// 1. Open a clean session by
		//    getting the filter: timestamp & last-stamp from upper synode,
		//    where last-stamp = upper.syn_stamp.stamp where tabl = 'syn_clean' & synode = me
		//    from upper synode.
		//    (Can be an error if multiple upper nodes exist. Only 1 level of synode can working in offline?)
		// 2. Request with time window filter
		// 3. Merge (reduce clean tasks)
		// 4. Close the clean session with timestamp
		
		window = openClean();
		
		meargeCleans(window);

		close(window);
	}
	
	/**
	 * Close clean session.
	 * 
	 * @param window
	 */
	private void close(TimeWindow window) {
		// TODO Auto-generated method stub
	}

	private void meargeCleans(TimeWindow window) {
		// TODO Auto-generated method stub
	}

	private TimeWindow openClean() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void run() {
		// TODO Auto-generated method stub
		
	}

}
