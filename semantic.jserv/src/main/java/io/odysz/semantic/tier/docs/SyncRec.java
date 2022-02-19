package io.odysz.semantic.tier.docs;

import io.odysz.anson.Anson;

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
	public IFileDescriptor fullpath(String clientpath) {
		this.clientpath = clientpath;
		return this;
	}

	@Override
	public String clientname() {
		return filename;
	}
	
}
