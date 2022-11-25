package io.odysz.semantic.tier.docs;

import java.sql.SQLException;
import java.util.HashMap;

import io.odysz.anson.Anson;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.ext.DocTableMeta;

/**
 * Task page to update synchronizing information.
 * 
 * @author odys-z@github.com
 *
 */
public class DocsPage extends Anson {
	public String device;
    public long start;
    public long end;
    
    public SyncFlag flag;
    
    public DocsPage() {}

    public DocsPage(int begin, int afterLast) {
        start = begin;
        end = afterLast;
    }

    public DocsPage(String device, long start, long end) {
    	this.device = device;
    	// this.taskNo = tasknum;
    	this.start = start;
    	this.end = end;
	}

	public DocsPage nextPage(long size) {
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
	 * Set paths's flags: [meta.syncflag, shareflag, shareDate].
	 * 
	 * @param rs must have columns of meta.syncflg, meta.shareflag, meta.shareDate.
	 * @param meta
	 * @return this
	 * @throws SQLException accessing rs failed.
	 */
	public DocsPage paths(AnResultset rs, DocTableMeta meta) throws SQLException {
		clientPaths = new HashMap<String, String[]>();

		rs.beforeFirst();
		while(rs.next()) {
			clientPaths.put(
				rs.getString(meta.fullpath),
				new String[] {
					rs.getString(meta.syncflag),
					rs.getString(meta.shareflag),
					rs.getString(meta.shareDate)
				});
		}
		
		return this;
	}
}
