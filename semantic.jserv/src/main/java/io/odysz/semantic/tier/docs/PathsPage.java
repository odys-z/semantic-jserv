package io.odysz.semantic.tier.docs;

import java.sql.SQLException;
import java.util.HashMap;

import io.odysz.anson.Anson;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.ext.DocTableMeta;
import io.odysz.semantics.x.SemanticException;

/**
 * Task page to update synchronizing information.
 * 
 * @author odys-z@github.com
 *
 */
public class PathsPage extends Anson {
	public String device;
    protected long start;
    protected long end;
    
    public PathsPage() {}

    public PathsPage(int begin, int afterLast) {
        start = begin;
        end = afterLast;
    }

    public PathsPage(String device, long start, long end) {
    	this.device = device;
    	this.start = start;
    	this.end = end;
	}

	public PathsPage nextPage(long size) {
        start = end;
        end += size;
        return this;
    }

	protected HashMap<String, String[]> clientPaths;
	/**
	 * @see #paths(AnResultset, DocTableMeta)
	 * @return paths' flags
	 */
	public HashMap<String,String[]> paths() { return clientPaths; }
	
	/**
	 * Set paths's flags: [meta.syncflag, share-flag, share-by, share-date].
	 * 
	 * @param rs must have columns of meta.syncflg, meta.shareflag, meta.shareDate.
	 * @param meta
	 * @return this
	 * @throws SQLException accessing rs failed.
	 */
	public PathsPage paths(AnResultset rs, DocTableMeta meta) throws SQLException {
		clientPaths = new HashMap<String, String[]>();

		rs.beforeFirst();
		while(rs.next()) {
			clientPaths.put(
				rs.getString(meta.fullpath),
				new String[] {
					rs.getString(meta.syncflag),
					rs.getString(meta.shareflag),
					rs.getString(meta.shareby),
					rs.getString(meta.shareDate)
				});
		}
		
		return this;
	}

	public PathsPage clear() {
		if (clientPaths == null)
			clientPaths = new HashMap<String, String[]>();
		clientPaths.clear();
		return this;
	}
	
	public PathsPage add(String path) {
		if (clientPaths == null)
			clientPaths = new HashMap<String, String[]>();
		clientPaths.put(path, null);
		return this;
	}
	
	public int start() throws SemanticException {
		if (start < 0 || start > Integer.MAX_VALUE)
			throw new SemanticException("Illegal long value to convert to int: %d", start);
		return (int) start;
	}
	
	public int end() throws SemanticException {
		if (end < 0 || end > Integer.MAX_VALUE)
			throw new SemanticException("Illegal long value to convert to int: %d", end);
		return (int) end;
	}
}
