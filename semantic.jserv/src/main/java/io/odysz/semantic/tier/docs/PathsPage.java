package io.odysz.semantic.tier.docs;

import java.sql.SQLException;
import java.util.HashMap;

import io.odysz.anson.Anson;
import io.odysz.anson.AnsonField;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.meta.ExpDocTableMeta;
import io.odysz.semantics.x.SemanticException;

import static io.odysz.common.LangExt.isblank;

/**
 * Task page to be updated for synchronizing information.
 * 
 * The core data {@link #clientPaths} is a map, and the size should be limited for performance reason.
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

	/**
	 * {key: client-path, value: [sync-flag, share-flag, share-by, share-date, isRef]} 
	 */
	@AnsonField(valType="[Ljava.lang.String;")
	protected HashMap<String, Object[]> clientPaths;
	/**
	 * @see #paths(AnResultset, ExpDocTableMeta)
	 * @return paths' flags, see {@link #clientPaths}
	 */
	public HashMap<String, Object[]> paths() { return clientPaths; }
	
	/**
	 * Set paths's flags: [rs.device, rs.share-flag, rs.share-by, rs.share-date, rs.is-docref].
	 * 
	 * @param rs must have columns of meta.syncflg, meta.shareflag, meta.shareDate.
	 * @param meta
	 * @return this
	 * @throws SQLException accessing rs failed.
	 * @throws SemanticException 
	 */
	public PathsPage paths(AnResultset rs, ExpDocTableMeta meta) throws SQLException, SemanticException {
		clientPaths = new HashMap<String, Object[]>();

		rs.beforeFirst();
		while(rs.next()) {
			String dev = rs.getString(meta.device);
			if (isblank(device))
				device = dev;
			else if (!device.equals(dev))
				throw new SemanticException("Found different devices in a single page: %s : %s.", device, dev);

			clientPaths.put(rs.getString(meta.fullpath), meta.getPathInfo(rs));
		}
		
		return this;
	}

	public PathsPage clear() {
		if (clientPaths == null)
			clientPaths = new HashMap<String, Object[]>();
		clientPaths.clear();
		return this;
	}
	
	public PathsPage add(String path) {
		if (clientPaths == null)
			clientPaths = new HashMap<String, Object[]>();
		clientPaths.put(path, null);
		return this;
	}
	
	public int start() throws SemanticException {
		if (start < 0 || start > Integer.MAX_VALUE)
			throw new SemanticException("Illegal long value to convert to int: %d", start);
		return (int) start;
	}
	
	/**
	 * Index after last item, non-inclusive index, and size = end - start.
	 * 
	 * @return
	 * @throws SemanticException
	 */
	public int end() throws SemanticException {
		if (end < 0 || end > Integer.MAX_VALUE)
			throw new SemanticException("Illegal long value to convert to int: %d", end);
		return (int) end;
	}
}
