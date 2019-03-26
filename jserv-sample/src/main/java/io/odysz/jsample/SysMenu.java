package io.odysz.jsample;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.xml.sax.SAXException;

import io.odysz.common.Configs;
import io.odysz.common.Utils;
import io.odysz.jsample.protocol.Samport;
import io.odysz.jsample.utils.SampleFlags;
import io.odysz.semantic.DA.Connects;
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
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantics.IUser;
import io.odysz.semantics.SemanticObject;
import io.odysz.semantics.x.SemanticException;

@WebServlet(description = "Load Sample App's Functions", urlPatterns = { "/menu.sample" })
public class SysMenu  extends SemanticTree {
	private static final long serialVersionUID = 1L;
	
	protected static JHelper<DatasetReq> jmenuReq;

	/**Menu tree semantics */
	private static TreeSemantics menuSemtcs;

	/**sk in dataset.xml: menu tree */
	private static final String sk = "sys.menu.vue-sample";
	
	static {
		jmenuReq  = new JHelper<DatasetReq>();
		menuSemtcs = new TreeSemantics(Configs.getCfg("tree-semantics", sk));
		try {
			DatasetCfg.init(JSingleton.rootINF());
			// Because of the java enum limitation, or maybe the author's knowledge limitation, 
			// JMessage needing a IPort instance to handle ports that implemented a new version of valof() method handling all ports.<br>
			// E.g. {@link Samport#menu#valof(name)} can handling both {@link Port} and Samport's enums.
			JMessage.understandPorts(Samport.menu);
		} catch (SAXException | IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws IOException {
		if (SampleFlags.menu)
			Utils.logi("---------- menu.sample get <- %s ----------", req.getRemoteAddr());

		try {
			String connId = req.getParameter("conn");
			String sk = req.getParameter("sk");

			List<SemanticObject> lst = DatasetCfg.loadStree(connId,
					sk, 0, -1, "admin");

			resp.getWriter().write(Html.listSemtcs(lst));
		} catch (SemanticException e) {
			ServletAdapter.write(resp, JProtocol.err(Samport.menu, MsgCode.exSemantic, e.getMessage()));
		} catch (SQLException e) {
			e.printStackTrace();
			ServletAdapter.write(resp, JProtocol.err(Samport.menu, MsgCode.exTransct, e.getMessage()));
//		} catch (SsException e) {
//			e.printStackTrace();
//			ServletAdapter.write(resp, JProtocol.err(Port.query, MsgCode.exSession, e.getMessage()));
		} finally {
			resp.flushBuffer();
		}
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
			
			// String connId = req.getParameter("conn");

			List<SemanticObject> lst = DatasetCfg.loadStree(Connects.defltConn(),
					sk, jreq.page(), jreq.size(), jreq.sqlArgs);
			SemanticObject menu = new SemanticObject();
			menu.put("menu", lst);
			SemanticObject rs = JProtocol.ok(Port.stree, menu);
			
			ServletAdapter.write(resp, rs);
		} catch (SemanticException e) {
			ServletAdapter.write(resp, JProtocol.err(Samport.menu, MsgCode.exSemantic, e.getMessage()));
		} catch (SQLException e) {
			if (SampleFlags.menu)
				e.printStackTrace();
			ServletAdapter.write(resp, JProtocol.err(Port.query, MsgCode.exTransct, e.getMessage()));
		} catch (ReflectiveOperationException e) {
			e.printStackTrace();
		} catch (SsException e) {
			if (SampleFlags.menu)
				e.printStackTrace();
			ServletAdapter.write(resp, JProtocol.err(Port.query, MsgCode.exSession, e.getMessage()));
		} finally {
			resp.flushBuffer();
		}
	}

}
