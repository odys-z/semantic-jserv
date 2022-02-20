package io.odysz.semantic.tier.docs;

import java.io.IOException;

public interface IFileDescriptor {
	/** Read record Id */
	String recId();
	/** Set record Id */
	IFileDescriptor recId(String recId);

	String fullpath();
	IFileDescriptor fullpath(String clientpath) throws IOException;

	String clientname();

	default public String mime() { return null; }

	default public String doctype() { return null; }

	String cdate();
}
