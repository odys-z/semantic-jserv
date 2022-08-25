package io.odysz.semantic.jserv.file;

/**
 * Object (file) descriptor which is used by synchronizer between jservs. 
 * 
 * @author odys-z@github.com
 *
 */
public interface ISyncFile {

	String recId();

	boolean isPublic();

	/** Equivalent to client path */
	String clientpath();

	/** device name */
	String device();

	/** File uri */
	String uri();

	String mime();

}
