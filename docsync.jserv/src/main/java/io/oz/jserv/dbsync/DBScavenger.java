package io.oz.jserv.dbsync;

import io.odysz.semantics.x.SemanticException;
import io.oz.jserv.docsync.SynodeMode;

/**
 * Helper utilities for synchronizing cleaning tasks, etc.
 * 
 * @author odys-z@github.com
 */
public class DBScavenger {
	
	/** synode id */
	String myId;

	/** upper synode id */
	String upperId;

	SynodeMode mode;

	public DBScavenger(String synode, SynodeMode m) {
		myId = synode;
		mode = m;
	}
	
	public void scanvage(String upperNode) throws SemanticException {
		upperId = upperNode;
		
		pullRejects(upperId);
		mergeDeletings(upperId);
	}
	
	protected void pullRejects(String upperId) {
		
	}
	
	protected void mergeDeletings(String upperId) {
		
	}
}
