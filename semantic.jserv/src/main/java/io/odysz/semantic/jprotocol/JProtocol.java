package io.odysz.semantic.jprotocol;

import java.io.IOException;
import java.util.List;

import io.odysz.anson.x.AnsonException;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
import io.odysz.semantic.tier.docs.DocsResp;
import io.odysz.semantic.tier.docs.SyncDoc;
import io.odysz.semantics.SemanticObject;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.x.TransException;

public class JProtocol {
	/**
	 * Typical operation's common names
	 * @deprecated replaced by Semantic.DA/{@link io.odysz.semantic.CRUD}.
	 * @since v1.4.12 requires semantic.DA v1.4.12
	 * */
	public static class CRUD {
		public static final String C = io.odysz.semantic.CRUD.C;
		public static final String R = io.odysz.semantic.CRUD.R;
		public static final String U = io.odysz.semantic.CRUD.U;
		public static final String D = io.odysz.semantic.CRUD.D;
	}

	@FunctionalInterface
	public interface OnOk {
		void ok(AnsonResp resp) throws IOException, AnsonException, SemanticException;
	}
	
	/**
	 * Progress notifier called by block chain.
	 * Parameter blockResp provide the last uploaded block's sequence number.
	 */
	@FunctionalInterface
	public interface OnProcess {
		// void proc(int listIndx, int totalBlocks, DocsResp blockResp) throws IOException, AnsonException, SemanticException;

		void proc(int rows, int rx, int seqBlock, int totalBlocks, AnsonResp resp)
			throws IOException, AnsonException, SemanticException;
	}

	/**
	 * @deprecated @since 1.4.39 
	 */
	@FunctionalInterface
	public interface OnDocOk {
		void ok(SyncDoc doc, AnsonResp resp) throws IOException, AnsonException, TransException;
	}
	
	/**
	 * On multiple requests finished, e. g. push multiple videos.
	 * @since 1.4.39
	 */
	@FunctionalInterface
	public interface OnDocsOk {
		void ok(List<DocsResp> resps) throws IOException, AnsonException, TransException;
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
