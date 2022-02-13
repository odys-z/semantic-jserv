package io.odysz.semantic.jprotocol;

import java.io.IOException;
import java.sql.SQLException;

import io.odysz.anson.x.AnsonException;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
import io.odysz.semantics.SemanticObject;
import io.odysz.semantics.x.SemanticException;

public class JProtocol {
	/** Typical operation's common names */
	public static class CRUD {
		public static final String C = "I";
		public static final String R = "R";
		public static final String U = "U";
		public static final String D = "D";
	}

	/**Function interface: 
	 * see {@link SCallbackV11#onCallback(MsgCode, AnsonResp)}
	 * @author ody
	 *
	 */
	@FunctionalInterface
	public interface SCallbackV11 {
		/**call back function called by semantic.transact
		 * @param msgCode 'ok' | 'ex...'
		 * @param resp response message
		 * @throws IOException
		 * @throws SQLException
		 * @throws SemanticException
		 */
		void onCallback(MsgCode msgCode, AnsonResp resp)
				throws IOException, SQLException, AnsonException, SemanticException;
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

	//////////////////////// version 1.1 with support of Anson //////////////////////
	public static AnsonMsg<AnsonResp> err(Port port, AnsonMsg.MsgCode code, String err) {
		AnsonResp obj = new AnsonResp(err);
		AnsonMsg<AnsonResp> msg = new AnsonMsg<AnsonResp>(port, code)
									.body(obj);
		return msg;
	}

}
