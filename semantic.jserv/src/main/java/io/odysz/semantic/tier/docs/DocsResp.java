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

	SyncingPage syncing;
	public SyncingPage syncing() { return syncing; }
	public DocsResp syncing(SyncingPage page) {
		syncing = page;
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

}
