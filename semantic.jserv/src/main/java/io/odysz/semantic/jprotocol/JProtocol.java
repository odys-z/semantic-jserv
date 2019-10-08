package io.odysz.semantic.jprotocol;

import java.io.IOException;
import java.sql.SQLException;

import com.google.gson.Gson;

import io.odysz.anson.x.AnsonException;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
import io.odysz.semantics.SemanticObject;
import io.odysz.semantics.x.SemanticException;

public class JProtocol {
	public static class CRUD {
		public static final String C = "I";
		public static final String R = "R";
		public static final String U = "U";
		public static final String D = "D";
	}

	/**Interface of lambd expression, using method {@link #onCallback(String, SemanticObject)}
	 * for callback on events happened,
	 * e.g. on success when an http post request finished. */
	@FunctionalInterface
	public interface SCallback {
		/**call back function called by semantic.transact
		 * @param code 'ok' | 'ex...'
		 * @param Data response message
		 * @throws IOException
		 * @throws SQLException
		 * @throws SemanticException
		 */
		void onCallback(String code, SemanticObject Data) throws IOException, SQLException, SemanticException;
	}

	@FunctionalInterface
	public interface SCallbackV11 {
		/**call back function called by semantic.transact
		 * @param msgCode 'ok' | 'ex...'
		 * @param resp response message
		 * @throws IOException
		 * @throws SQLException
		 * @throws SemanticException
		 */
		void onCallback(io.odysz.semantic.jprotocol.AnsonMsg.MsgCode msgCode,
				AnsonResp resp) throws IOException, SQLException, AnsonException, SemanticException;
	}

	public static Gson gson = new Gson();
	
	public static SemanticObject err(IPort port, JMessage.MsgCode code, String err) {
		return err (port, code.name(), err);
	}

	public static SemanticObject err(IPort port, String code, String err) {
		SemanticObject obj = new SemanticObject();
		obj.put("code", code);
		obj.put("error", err);
		obj.put("port", port.name());
		return obj;
	}

	public static SemanticObject ok(IPort port, Object data) {
		SemanticObject obj = new SemanticObject();
		obj.put("code", MsgCode.ok.name());
		obj.put("data", data);
		obj.put("port", port.name());
		return obj;
	}

	public static SemanticObject ok(IPort port, String msg, Object... msgArgs) {
		return ok(port, String.format(msg, msgArgs));
	}

	public static SemanticObject err(JMessage.Port p, JMessage.MsgCode c, String err, SemanticObject ex) {
		return err(p, c, err).put("ex", ex);
	}
	
	//////////////////////// version 1.1 with support of Anson //////////////////////
	public static AnsonMsg<AnsonResp> err(Port port, AnsonMsg.MsgCode code, String err) {
		AnsonResp obj = new AnsonResp(err);
		AnsonMsg<AnsonResp> msg = new AnsonMsg<AnsonResp>(port, code)
									.body(obj);
		return msg;
	}

}
