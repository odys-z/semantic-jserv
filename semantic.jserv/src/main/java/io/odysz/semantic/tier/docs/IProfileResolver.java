package io.odysz.semantic.tier.docs;

import io.odysz.semantic.jprotocol.AnsonBody;
import io.odysz.semantics.IUser;

/**
 * Resolving file's saving folder etc., e.g.<br>
 * parse "yyyy_MM" as folder from file's sharing date or creating date;<br>
 * or handle sharer on ending of chain push.<br>
 * 
 * @author odys-z@github.com
 *
 */
public interface IProfileResolver {
	String synodeFolder(AnsonBody req, IUser usr);
	
	/**
	 * Modify request on chain ended.
	 * 
	 * @param req
	 * @param usr
	 * @return req itself
	 */
	DocsReq onStartPush(DocsReq req, IUser usr);
}
