package io.odysz.semantic.tier.docs;

import java.io.IOException;

import io.odysz.semantic.ext.DocTableMeta;

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

	/** Either {@link io.odysz.semantic.ext.DocTableMeta.Share#pub pub} or {@link io.odysz.semantic.ext.DocTableMeta.Share#pub priv}. */
	default String shareflag() { return DocTableMeta.Share.priv; }
	
//	/**
//	 * Set statement semantics context (for resulving pkval etc.)
//	 * @since v1.4.12
//	 * @param insCtx
//	 * @return
//	 */
//	default public IFileDescriptor semantext(ISemantext stmtCtx) { return this; }
//	
//	/**
//	 * Get statement semantics context (for resulving pkval etc.)
//	 * @since v1.4.12
//	 * @return
//	 */
//	default public ISemantext semantext() { return null; }
}
