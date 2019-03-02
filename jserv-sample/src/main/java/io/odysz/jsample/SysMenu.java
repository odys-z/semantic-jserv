package io.odysz.jsample;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.odysz.common.Configs;
import io.odysz.common.Utils;
import io.odysz.jsample.utils.SampleFlags;
import io.odysz.semantic.DA.DatasetCfg;
import io.odysz.semantic.DA.DatasetCfg.TreeSemantics;
import io.odysz.semantic.ext.DatasetReq;
import io.odysz.semantic.ext.SemanticTree;
import io.odysz.semantic.jprotocol.JHelper;
import io.odysz.semantic.jprotocol.JMessage;
import io.odysz.semantic.jprotocol.JMessage.MsgCode;
import io.odysz.semantic.jprotocol.JMessage.Port;
import io.odysz.semantic.jprotocol.JProtocol;
import io.odysz.semantic.jserv.JSingleton;
import io.odysz.semantic.jserv.helper.Html;
import io.odysz.semantic.jserv.helper.ServletAdapter;
import io.odysz.semantics.IUser;
import io.odysz.semantics.SemanticObject;
import io.odysz.semantics.x.SemanticException;

@WebServlet(description = "Load Sample App's Functions", urlPatterns = { "/menu.sample" })
public class SysMenu  extends SemanticTree {
	private static final long serialVersionUID = 1L;
	
	protected static JHelper<DatasetReq> jmenuReq;

	/**Menu tree semantics */
	private static TreeSemantics menuSemtcs;
	
	static {
		jmenuReq  = new JHelper<DatasetReq>();
		menuSemtcs = new TreeSemantics(Configs.getCfg("tree-semantics", "sys.menu.vue-sample"));
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws IOException {
		if (SampleFlags.menu)
			Utils.logi("---------- menu.sample get <- %s ----------", req.getRemoteAddr());

		List<String> menu = null;
		resp.getWriter().write(Html.list(menu));
		resp.flushBuffer();
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp)
			throws IOException {
		if (SampleFlags.menu)
			Utils.logi("========== menu.sample post <= %s ==========", req.getRemoteAddr());

		resp.setCharacterEncoding("UTF-8");
		try {
			JMessage<DatasetReq> jmsg = ServletAdapter.<DatasetReq>read(req, jmenuReq, DatasetReq.class);
			IUser usr = JSingleton.getSessionVerifier().verify(jmsg.header());

			DatasetReq jreq = jmsg.body(0);
			jreq.treeSemtcs(menuSemtcs);
			jreq.sqlArgs = new String[] {usr.get("role")};
			
			String connId = req.getParameter("conn");

			List<SemanticObject> lst = DatasetCfg.loadStree(connId,
					jreq.sk(), jreq.page(), jreq.size(), jreq.sqlArgs);
			SemanticObject rs = JProtocol.ok(Port.stree, lst);
			
			ServletAdapter.write(resp, rs);
		} catch (SemanticException e) {
			ServletAdapter.write(resp, JProtocol.err(Port.query, MsgCode.exSemantic, e.getMessage()));
		} catch (SQLException e) {
			e.printStackTrace();
			ServletAdapter.write(resp, JProtocol.err(Port.query, MsgCode.exTransct, e.getMessage()));
		} catch (ReflectiveOperationException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
			ServletAdapter.write(resp, JProtocol.err(Port.query, MsgCode.exGeneral, e.getMessage()));
		} finally {
			resp.flushBuffer();
		}
	}

}
