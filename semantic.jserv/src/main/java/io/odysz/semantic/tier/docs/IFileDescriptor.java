package io.odysz.semantic.tier.docs;

import java.io.IOException;

import io.odysz.semantic.meta.ExpDocTableMeta;

/**
 * A bridge (interface) between semantic.jserv nodes, {@link ExpSyncDoc},
 * and local file information, e. g. file object on Android.
 * 
 * <h5>Note:</h5>
 * <p>Do not confused with IFileProvider in Albumtier,
 * an interface bridging local file system for unified access across rumtime
 * platfoms, e. g. Android file content provider vs. the general JDK file system. </p>
 * 
 * @since 1.4.11, a file is an object shared across nodes, so this
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
	 * @since this method will convert windows paths to linux paths, 
	 * since a windows path is not a valid json string, which will makes
	 * {@link io.odysz.anson.Anson} serializing into trouble.
	 * @param clientpath
	 * @return file descriptor
	 * @throws IOException
	 */
	IFileDescriptor fullpath(String clientpath) throws IOException;

	String clientname();

	// String mime();

	/** @deprecated */
	default public String doctype() { return null; }

	String cdate();

	String device();

	/** @return file uri */
	String uri64();

	/** Either {@link io.odysz.semantic.ext.DocTableMeta.Share#pub pub} or {@link io.odysz.semantic.ext.DocTableMeta.Share#pub priv}. */
	default String shareflag() { return ExpDocTableMeta.Share.priv; }

	default ExpSyncDoc syndoc() { return (ExpSyncDoc) this; }
	
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
