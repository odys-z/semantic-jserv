package io.odysz.semantic.tier.docs;

import java.io.IOException;

/**This interface is for java client collect local file information and requesting
 * file information at server side. So no record Id can present here.
 *  
 * @author ody
 *
 */
public interface IFileDescriptor {
	/** doc/file record Id - different for each jserv node */
	String recId();

	String fullpath();
	IFileDescriptor fullpath(String clientpath) throws IOException;

	String clientname();

	String mime();

	/** @deprecated */
	default public String doctype() { return null; }

	String cdate();

	/** device name */
	String device();

	/** File uri */
	String uri();

	boolean isPublic();
}
