package io.odysz.semantic.tier.docs;

import java.sql.SQLException;
import java.util.HashMap;

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

	DocsPage syncing;

	HashMap<String, Object> clientPaths;
	public HashMap<String, Object> syncPaths() { return clientPaths; }
	
	public DocsResp syncRecords(String collectId, AnResultset rs) throws SQLException {
		clientPaths = new HashMap<String, Object>();

		rs.beforeFirst();
		while(rs.next()) {
			clientPaths.put(rs.getString("clientpath"), rs.getString("syncFlag"));
		}

		return this;
	}

	public DocsPage syncing() { return syncing; }
	public DocsResp syncing(DocsPage page) {
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

	String org;
	public String org() { return org; }
	public DocsResp org(String orgId) {
		this.org = orgId;
		return this;
	}

}
