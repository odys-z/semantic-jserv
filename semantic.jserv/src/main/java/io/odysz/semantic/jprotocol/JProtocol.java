package io.odysz.semantic.jprotocol;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import io.odysz.anson.AnsonException;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
import io.odysz.semantic.tier.docs.ExpSyncDoc;
import io.odysz.semantics.SemanticObject;
import io.odysz.transact.x.TransException;

public class JProtocol {
	/**
	 * Http request or repsond's header property names.
	 * @since 1.5.16
	 */
	public static class Headers {
		public static final String Error  = "Error";
		public static final String Server = "Server";
		public static final String Length = "Length";
		public static final String Range  = "Range";

		public static final String Expires = "Expires";
		public static final String Pragma  = "Pragma";
		public static final String Content_range  = "Content-Range";
		public static final String Content_length = "Content-Length";
		public static final String Cache_control  = "Cache-Control";

		public static final String If_none_match = "If-None-Match";
		public static final String If_modified_since = "If-Modified-Since";
		public static final String If_range = "If-Range";

		public static final String Reason   = "Reason";
		public static final String AnsonReq = "Anson-req";
	}

	@FunctionalInterface
	public interface OnOk {
		void ok(AnsonResp resp) throws IOException, AnsonException, TransException, SQLException;
	}
	
	/**
	 * Progress notifier called by block chain.
	 * Parameter {@code resp} provide the last uploaded block's sequence number.
	 * <p>
	 * rows: rx of total rows <br>
	 * file blocks: bx of total blocks</p>
	 * @return force breakup
	 */
	@FunctionalInterface
	public interface OnProcess {
		/**
		 * Progress notifier called by block chain.
		 * Parameter {@code resp} provide the last uploaded block's sequence number.
		 * 
		 * @param rx row index
		 * @param rows rows
		 * @param bx block index
		 * @param blocks blocks
		 * @param resp response
		 * @return force breakup
		 * @throws IOException
		 * @throws AnsonException
		 * @throws TransException
		 */
		boolean proc(int rx, int rows, int bx, int blocks, AnsonResp resp)
			throws IOException, AnsonException, TransException;
	}

	/**
	 * @deprecated @since 1.4.39 
	 */
	@FunctionalInterface
	public interface OnDocOk {
		void ok(ExpSyncDoc doc, AnsonResp resp) throws IOException, AnsonException, TransException;
	}
	
	/**
	 * On multiple requests finished, e. g. push multiple videos.
	 * @since 1.4.39
	 */
	@FunctionalInterface
	public interface OnDocsOk {
		void ok(List<? extends AnsonResp> resps) throws IOException, AnsonException, TransException, SQLException;
	}
	
	@FunctionalInterface
	public interface OnError { void err(MsgCode code, String msg, String ... args ); }

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
