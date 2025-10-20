package io.odysz.semantic.jprotocol;

import io.odysz.semantics.SemanticObject;

/**A stub for user's message body extension - subclassing {@link AnsonBody}.
 * @author ody
 *
 */
public class UserReq extends AnsonBody {
	
	public UserReq() {
		super(null, null);
	}

	SemanticObject data;
	/**
	 * Tip: v cannot be complicate types, e.g. array, map, user types, etc.
	 * @param k
	 * @param v
	 * @return this
	 */
	public UserReq data(String k, Object v) {
		if (k == null) return this;

		if (data == null)
			data = new SemanticObject();
		data.put(k, v);
		return this;
	}

	public Object data(String k) {
		return data == null ? null : data.get(k);
	}

	String tabl;
	public String tabl() { return tabl; }

	public UserReq(AnsonMsg<? extends AnsonBody> parent, String uri) {
		super(parent, uri);
	}
	
	public Object get(String prop) {
		return data == null ? null : data.get(prop);
	}
}
