package io.odysz.semantic.jprotocol;

import io.odysz.anson.Anson;
import io.odysz.anson.AnsonField;

public abstract class AnsonBody extends Anson {
	@AnsonField(ref=AnsonField.enclosing)
	protected AnsonMsg<? extends AnsonBody> parent;

	protected String uri;
	public String uri() { return uri; }

	/** Action: login | pswd, and any serv port extension */
	protected String a;
	/** @return Action: login | pswd and any serv port extension */
	public String a() { return a; }

	public AnsonBody a(String act) {
		this.a = act;
		return this;
	}

	protected AnsonBody(AnsonMsg<? extends AnsonBody> parent, String uri) {
		this.parent = parent;
		this.uri = uri;
	}

}
