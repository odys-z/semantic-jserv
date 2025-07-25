package io.odysz.semantic.tier.docs;

import static io.odysz.common.LangExt.len;

import io.odysz.semantics.SemanticObject;
import io.odysz.semantics.x.SemanticException;

public class DocsException extends SemanticException {

	private static final long serialVersionUID = 1L;

	public static final int Succeed = 0;
	public static final int IOError = 90;
	public static final int SemanticsError = 91;
	public static final int Duplicate = 99;

	/**
	 * ex(new SemanticObject().put("code", code).put("reason", reason));
	 * 
	 * @param code, {@link #Succeed}, ...
	 * @param reasons
	 */
	public DocsException(int code, String... reasons) {
		super("Details in #ex field.");
		ex(new SemanticObject()
			.put("code", code)
			.put("reasons", reasons));
	}

	public int code() {
		return (int) ex().get("code");
	}
	
	public String reason(int i) {
		return ex().get("reasons") == null || i >= len((String[])ex().get("reasons")) ?
				null : ((String[])ex().get("reasons"))[i];
	}
}