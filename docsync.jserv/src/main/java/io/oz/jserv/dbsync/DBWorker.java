package io.oz.jserv.dbsync;

import java.sql.SQLException;
import java.util.HashMap;

import io.odysz.module.rs.AnResultset;
import io.odysz.semantics.meta.TableMeta;
import io.odysz.semantics.x.SemanticException;
import io.oz.jserv.docsync.SynodeMode;

/**
 * Worker for db synchronization.
 * 
 * @author odys-z@github.com
 */
public class DBWorker implements Runnable {
	
	/** synode id */
	String myId;

	/** upper synode id */
	String upperId;

	/** volume path */
	String volpath;

	/** clean session timestamps */
	TimeWindow window; 

	SynodeMode mode;
	
	TableMeta cleanMeta;

	private HashMap<String, TableMeta> entities;

	public DBWorker(String synode, SynodeMode m) {
		myId = synode;
		mode = m;
	}
	
	public DBWorker volume(String path) {
		volpath = path;
		return this;
	}
	
	/**
	 * Merge syn_clean
	 * 
	 * @param upperNode
	 * @throws SemanticException
	 * @throws SQLException 
	 */
	public void scanvage(String upperNode) throws SemanticException, SQLException {
		upperId = upperNode;
		
		// 1. Open a clean session by
		//    getting the filter: timestamp & last-stamp from upper synode,
		//    where last-stamp = upper.syn_stamp.stamp where tabl = 'syn_clean' & synode = me
		// (Can be an error if multiple upper nodes exist. Only 1 level of synode can working in offline?)
		//
		// 2. Request with time window filter
		// 3. Merge (reduce clean tasks)
		// 4. Close the clean session with timestamp
		
		window = openClean();
		
		meargeCleans(window);

		closeClean(window);
		
		for (String etbl : entities.keySet()) {
			DBSyncResp rep = syncTabl(etbl);
		}
	}
	
	private TimeWindow openClean() {
		// TODO Auto-generated method stub
		return null;
	}

	private void meargeCleans(TimeWindow window) throws SQLException {
		DBSyncResp rpl = null;
		AnResultset tasks = rpl.tasks().beforeFirst();
			
		// order by tabl, synoder, clientpath, synodee
		while (tasks.next()) {
		}
	}

	/**
	 * Close clean session.
	 * 
	 * @param window
	 */
	private void closeClean(TimeWindow window) {
		// TODO Auto-generated method stub
	}

	private DBSyncResp syncTabl(String etbl) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void run() {
		// TODO Auto-generated method stub
		
	}

}
