package io.odysz.semantic.tier.docs;

import java.sql.SQLException;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.jprotocol.AnsonResp;
import io.odysz.semantic.meta.ExpDocTableMeta;
import io.odysz.semantics.x.SemanticException;

/**
 * Docsyncer response.
 * 
 * This structure is recommend used as Parcel between Android activities.
 * @author ody
 *
 */
public class DocsResp extends AnsonResp {
	public ExpSyncDoc xdoc;

	protected PathsPage syncingPage;

	protected String collectId;

	public PathsPage pathsPage() { return syncingPage; }
	
	/**
	 * <p>Set clientpaths page (rs).</p>
	 * Arg {@code rs} must have columns specified with {@link ExpSyncDoc#synPageCols(ExpDocTableMeta)}.
	 * @param rs
	 * @param meta
	 * @return this
	 * @throws SQLException
	 * @throws SemanticException 
	 */
	public DocsResp pathsPage(AnResultset rs, ExpDocTableMeta meta)
			throws SQLException, SemanticException {
		if (syncingPage == null)
			syncingPage = new PathsPage();
		syncingPage.paths(rs, meta);
		return this;
	}

	public PathsPage syncing() { return syncingPage; }
	public DocsResp syncing(PathsPage page) {
		syncingPage = page;
		return this;
	}
	public DocsResp syncingPage(DocsReq req) {
		syncingPage = req.syncingPage;
		return this;
	}
	
	public long blockSeqReply;

	public long blockSeq() { return blockSeqReply; }
	public DocsResp blockSeq(long seq) {
		blockSeqReply = seq;
		return this;
	}

	public DocsResp doc(ExpSyncDoc d) {
		this.xdoc = d;
		return this;
	}

	public DocsResp doc(AnResultset rs, ExpDocTableMeta meta)
			throws SQLException, SemanticException {
		if (rs != null && rs.total() > 1)
			throw new SemanticException("This method can only handling 1 record.");
		rs.beforeFirst().next();
		this.xdoc = new ExpSyncDoc(rs, meta);
		return this;
	}

	String org;
	public String org() { return org; }
	public DocsResp org(String orgId) {
		this.org = orgId;
		return this;
	}

	Device device;
	public Device device() { return device; }
	public DocsResp device(Device device) {
		this.device = device;
		return this;
	}
	public DocsResp device(String deviceId) {
		this.device = new Device(deviceId, null);
		return this;
	}

	String stamp;
	public DocsResp stamp(String s) {
		stamp = s;
		return this;
	}

	String syndomain;
	/**
	 * Tell the client the request is handled in the {@code domain}.
	 * @param domain
	 * @return this
	 */
	public DocsResp syndomain(String dom) {
		syndomain = dom;
		return this;
	}

}
