package io.odysz.semantic.tier.docs;

import io.odysz.semantics.IUser;

/**
 * Resolving file's saving folder, e.g. parse "yyyy_MM" as folder from
 * file's sharing date or creating date.
 * 
 * @author odys-z@github.com
 *
 */
@FunctionalInterface
public interface IFolderResolver {
	String resolve(DocsReq req, IUser usr);
}
