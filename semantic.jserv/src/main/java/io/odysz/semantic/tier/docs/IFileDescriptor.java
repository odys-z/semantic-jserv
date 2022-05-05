package io.odysz.semantic.tier.docs;

import java.io.IOException;

/**This interface is for java client collect local file information and requesting
 * file information at server side. So no record Id can present here.
 *  
 * @author ody
 *
 */
public interface IFileDescriptor {
	/** Read record Id */
	// String recId();

	/** Set record Id */
	// IFileDescriptor recId(String recId);

	String fullpath();
	IFileDescriptor fullpath(String clientpath) throws IOException;

	String clientname();

	/** @deprecated */
	default public String mime() { return null; }

	/** @deprecated */
	default public String doctype() { return null; }

	String cdate();
}
