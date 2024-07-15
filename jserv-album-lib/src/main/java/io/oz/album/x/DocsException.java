package io.oz.album.x;

import io.odysz.semantics.SemanticObject;
import io.odysz.semantics.x.SemanticException;

public class DocsException extends SemanticException {

	private static final long serialVersionUID = 1L;

	public static final int Duplicate = 99;
	public static final int Succeed = 0;

	/**
	 * ex(new SemanticObject().put("code", code).put("reason", reason));
	 */
	public DocsException(int code, String... reasons) {
		super("Details in #ex field.");
		ex(new SemanticObject().put("code", code).put("reasons", reasons));
	}

	public int code() {
		return (int) ex().get("code");
	}
}
