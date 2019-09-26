package io.odysz.semantic.jprotocol;

import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;

/**Error response
 * @author odys-z@github.com
 */
public class AnsonResp extends AnsonBody {

	private IPort p;
	private MsgCode c;
	private String e;

	public AnsonResp(IPort p, MsgCode code, String err) {
		super(null, null);
		this.p = p;
		this.c = code;
		this.e = err;
	}

	public String error() {
		return e;
	}

	public MsgCode code() {
		return c;
	}
}
