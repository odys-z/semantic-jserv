package org.omnifaces.servlet;

import io.odysz.semantic.ext.DocTableMeta;

@FunctionalInterface
public interface IGetMeta {
	/**
	 * example:
	 * <pre>
	(uri) -> new PhotoMeta(Connects.uri2conn(uri))
	 * </pre>
	 * @param uri
	 * @return
	 */
	DocTableMeta get(String uri);
}
