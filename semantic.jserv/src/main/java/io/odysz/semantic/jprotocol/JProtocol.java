package io.odysz.semantic.jprotocol;

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;

import com.google.gson.Gson;

import io.odysz.semantic.jprotocol.JMessage.MsgCode;
import io.odysz.semantic.jprotocol.JMessage.Port;
import io.odysz.semantics.SemanticObject;
import io.odysz.semantics.x.SemanticException;

public class JProtocol {
	/**Interface of lambd expression, using method {@link #onCallback(String, Object)}
	 * for callback on events happened,
	 * e.g. on success when an http post request finished. */
	@FunctionalInterface
	public interface SCallback {
		void onCallback(String code, SemanticObject Data) throws IOException, SQLException, SemanticException;
	}
	

	public static Gson gson = new Gson();
	
	public static SemanticObject err(Port port, MsgCode code, String err) {
		SemanticObject obj = new SemanticObject();
		obj.put("code", code.name());
		obj.put("error", err);
		obj.put("port", port.name());
		return obj;
	}

	public static SemanticObject ok(Port port, SemanticObject msg) {
		SemanticObject obj = new SemanticObject();
		obj.put("code", MsgCode.ok.name());
		obj.put("msg", msg);
		obj.put("port", port.name());
		return obj;
	}
}
