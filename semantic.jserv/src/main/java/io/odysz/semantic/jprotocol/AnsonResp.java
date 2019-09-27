package io.odysz.semantic.jprotocol;

import io.odysz.module.rs.SResultset;

/**Anson message response body
 * @author odys-z@github.com
 */
public class AnsonResp extends AnsonBody {

	private String m;
	private SResultset rs;

	public AnsonResp(AnsonMsg<AnsonResp> parent) {
		super(parent, null);
	}

	public AnsonResp(String txt) {
		super(null, null);
		this.m = txt;
	}

	public String msg() {
		return m;
	}

	public AnsonBody rs(SResultset rs) {
		this.rs = rs;
		return this;
	}

	public SResultset rs() {
		return this.rs;
	}
}
