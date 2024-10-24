package io.odysz.semantic.jprotocol;

import io.odysz.anson.Anson;
import io.odysz.anson.AnsonField;

public abstract class AnsonBody extends Anson {
	@AnsonField(ref=AnsonField.enclosing)
	protected AnsonMsg<? extends AnsonBody> parent;

	protected String uri;
	/** Get client function uri. */
	public String uri() { return uri; }
	public AnsonBody uri(String uri) {
		if (this.uri == null)
			this.uri = uri;
		return this;
	}

	/** Action: login | pswd, and any serv port extension */
	protected String a;
	/** @return Action: login | pswd and any serv port extension */
	public String a() { return a; }

	public AnsonBody a(String act) {
		this.a = act;
		return this;
	}

	/**
	 * @param parent
	 * @param uri see <a href='https://odys-z.github.io/Anclient/guide/func-uri.html#uri-mapping'>Anclient Doc</a>
	 */
	protected AnsonBody(AnsonMsg<? extends AnsonBody> parent, String uri) {
		this.parent = parent;
		this.uri = uri;
	}

}
