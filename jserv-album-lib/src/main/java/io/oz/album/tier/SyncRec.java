package io.oz.album.tier;

import io.odysz.anson.Anson;
import io.odysz.semantic.tier.docs.IFileDescriptor;

public class SyncRec extends Anson implements IFileDescriptor {

	private String docId;
	private String clientpath;
	private String filename;

	public SyncRec() { }

	public SyncRec(IFileDescriptor p) {
		this.docId = p.recId();
		this.clientpath = p.fullpath();
		this.filename = p.clientname();
	}

	@Override
	public String recId() {
		return docId;
	}

	@Override
	public IFileDescriptor recId(String recId) {
		this.docId = recId;
		return this;
	}

	@Override
	public String fullpath() {
		return clientpath;
	}

	@Override
	public String clientname() {
		return filename;
	}
	
}
