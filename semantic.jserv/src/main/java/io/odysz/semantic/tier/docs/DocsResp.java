package io.odysz.semantic.tier.docs;

import io.odysz.semantic.jprotocol.AnsonResp;

/**This structure is recommend used as Parcel between Android activities.
 * @author ody
 *
 */
public class DocsResp extends AnsonResp implements IFileDescriptor {
	long size;
	long size() { return size; } 

	SyncingPage syncing;
	public DocsResp syncing(SyncingPage page) {
		syncing = page;
		return this;
	}

	String recId;
	public DocsResp recId(String recid) {
		recId = recid;
		return this;
	}
	@Override
	public String recId() { return recId; }

	String fullpath;
	public String fullpath() { return fullpath; }
	public IFileDescriptor fullpath(String fullpath) {
		this.fullpath = fullpath;
		return this;
	}

	String filename;
	public String clientname() { return filename; }

	public long blockSeqReply;
	public DocsResp blockSeq(long seq) {
		blockSeqReply = seq;
		return this;
	}
	
	String chainId;
	public String chainId() { return chainId; }
	public DocsResp chainId(String blockChain) {
		chainId = blockChain;
		return this;
	}
}
