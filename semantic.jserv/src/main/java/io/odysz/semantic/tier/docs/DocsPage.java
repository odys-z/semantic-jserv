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
    public int taskNo = 0;
    public int start;
    public int end;
    
    public SyncFlag flag;
    
    public DocsPage() {}

    public DocsPage(int begin, int afterLast) {
        start = begin;
        end = afterLast;
    }

    public DocsPage nextPage(int size) {
        start = end;
        end += size;
        return this;
    }

	protected HashMap<String, String[]> clientPaths;
	public HashMap<String,String[]> paths() { return clientPaths; }
	
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
