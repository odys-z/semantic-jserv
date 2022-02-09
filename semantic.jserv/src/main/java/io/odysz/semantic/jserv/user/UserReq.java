package io.odysz.semantic.jserv.user;

import io.odysz.semantic.jprotocol.AnsonBody;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantics.SemanticObject;

/**A stub for user's message body extension - subclassing {@link AnsonBody}.
 * @author ody
 *
 */
public class UserReq extends AnsonBody {
	// private String code;
	
	public UserReq() {
		super(null, null);
		// code = "";
	}

	private SemanticObject data;
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

	public UserReq(AnsonMsg<? extends AnsonBody> parent, String conn) {
		super(parent, conn);
	}
	
	public Object get(String prop) {
		return data == null ? null : data.get(prop);
	}
}
