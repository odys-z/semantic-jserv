package io.odysz.semantic.tier.docs;

import java.sql.SQLException;

import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.ext.DocTableMeta;
import io.odysz.semantic.jprotocol.AnsonResp;
import io.odysz.semantics.x.SemanticException;

/**
 * Docsyncer response.
 * 
 * This structure is recommend used as Parcel between Android activities.
 * @author ody
 *
 */
public class DocsResp extends AnsonResp {
	public SyncDoc doc;

	protected PathsPage syncing;

	protected String collectId;

	public PathsPage pathsPage() { return syncing; }
	
	/**
	 * <p>Set clientpaths page (rs).</p>
	 * Rs must have columns specified with {@link SyncDoc#synPageCols(DocTableMeta)}.
	 * @param rs
	 * @param meta
	 * @return this
	 * @throws SQLException
	 * @throws SemanticException 
	 */
	public DocsResp pathsPage(AnResultset rs, DocTableMeta meta) throws SQLException, SemanticException {
		syncing.paths(rs, meta);
		return this;
	}

	public PathsPage syncing() { return syncing; }
	public DocsResp syncing(PathsPage page) {
		syncing = page;
		return this;
	}
	public DocsResp syncing(DocsReq req) {
		syncing = req.syncing;
		return this;
	}
	
	public long blockSeqReply;

	public long blockSeq() { return blockSeqReply; }
	public DocsResp blockSeq(long seq) {
		blockSeqReply = seq;
		return this;
	}

	public DocsResp doc(SyncDoc d) {
		this.doc = d;
		return this;
	}

	public DocsResp doc(AnResultset rs, DocTableMeta meta) throws SQLException, SemanticException {
		if (rs != null && rs.total() > 1)
			throw new SemanticException("This method can only handling 1 record.");
		rs.beforeFirst().next();
		this.doc = new SyncDoc(rs, meta);
		return this;
	}

	String org;
	public String org() { return org; }
	public DocsResp org(String orgId) {
		this.org = orgId;
		return this;
	}

}
