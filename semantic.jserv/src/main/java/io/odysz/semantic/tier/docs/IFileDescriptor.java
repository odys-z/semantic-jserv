package io.odysz.semantic.tier.docs;

import java.io.IOException;

/**
 * Since jserv 1.4.11, a file is an object shared across nodes, so this
 * interface is also used for synchronizing.
 * 
 * @author ody
 *
 */
public interface IFileDescriptor {
	/** doc/file record Id - different for each jserv node */
	String recId();

	/**
	 * Get full client path 
	 * @return path
	 */
	String fullpath();

	/**Set client full path.
	 * @param clientpath
	 * @return
	 * @throws IOException
	 */
	IFileDescriptor fullpath(String clientpath) throws IOException;

	String clientname();

	String mime();

	/** @deprecated */
	default public String doctype() { return null; }

	String cdate();

	/** @return device name */
	String device();

	/** @return file uri */
	String uri();

	boolean isPublic();
}
