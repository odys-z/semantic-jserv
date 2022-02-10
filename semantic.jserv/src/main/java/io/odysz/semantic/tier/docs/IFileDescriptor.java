package io.odysz.semantic.tier.docs;

public interface IFileDescriptor {
	/** Read record Id */
	String recId();
	/** Set record Id */
	IFileDescriptor recId(String recId);

	String fullpath();
	String clientname();

	default public String mime() { return null; }

	default public String doctype() { return null; }
}
