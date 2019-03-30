package io.odysz.jsample.cheap;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.FilenameUtils;
import org.xml.sax.SAXException;

import io.odysz.common.Utils;
import io.odysz.jsample.protocol.Samport;
import io.odysz.jsample.utils.SampleFlags;
import io.odysz.semantic.jprotocol.IPort;
import io.odysz.semantic.jprotocol.JHelper;
import io.odysz.semantic.jprotocol.JMessage;
import io.odysz.semantic.jprotocol.JMessage.MsgCode;
import io.odysz.semantic.jprotocol.JProtocol;
import io.odysz.semantic.jserv.JSingleton;
import io.odysz.semantic.jserv.U.SUpdate;
import io.odysz.semantic.jserv.helper.Html;
import io.odysz.semantic.jserv.helper.ServletAdapter;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantics.IUser;
import io.odysz.semantics.SemanticObject;
import io.odysz.semantics.x.SemanticException;
import io.odysz.sworkflow.CheapApi;
import io.odysz.sworkflow.CheapEngin;
import io.odysz.sworkflow.CheapEvent;
import io.odysz.sworkflow.CheapException;
import io.odysz.sworkflow.EnginDesign.Req;
import io.odysz.sworkflow.ICheapEventHandler;
import io.odysz.transact.sql.Update;
import io.odysz.transact.x.TransException;

@WebServlet(description = "Handling work flow request", urlPatterns = { "/cheapflow.sample" })
public class CheapServ extends SUpdate {
	public static class WfProtocol {
		public static String reqBody = "wfreq";

		public static String ok = "ok";

		static final String wfid = "wfid";
		static final String cmd = "cmd";
		static final String busid = "busid";
		static final String current = "current";
		static final String ndesc = "nodeDesc";
		static final String nvs = "nvs";
		static final String busiMulti = "multi";
		static final String busiPostupdate = "post";
		
		static final String routes = "routes";

		static final String wfName = "wfname";
		static final String wfnode1 = "node1";
		static final String bTabl = "btabl";
	}

	private static final long serialVersionUID = 1L;
	private static IPort p;

	protected static JHelper<CheapReq> jcheapReq;

	/**
	 * path-to-workflow-meta.xml<br>
	 * It connId is the same as in connects.xml.
	 */
	public static final String confpath = "../../../../../../../../../git/repo/semantic-workflow/eclipse.workflow/semantic.workflow/src/test/res/workflow-meta.xml";

	static {
		jcheapReq  = new JHelper<CheapReq>();

		p = Samport.cheapflow;
			
		// Because of the java enum limitation, or maybe the author's knowledge limitation, 
		// JMessage needing a IPort instance to handle ports that implemented a new version of valof() method handling all ports.<br>
		// E.g. {@link Samport#menu#valof(name)} can handling both {@link Port} and Samport's enums.
		//
		// If the same in SysMenu is surely called before this servlet going to work, this line can be comment out. 
		JMessage.understandPorts(Samport.cheapflow);
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		if (SampleFlags.cheapflow)
			Utils.logi("---------- cheapflow.sample get <- %s ----------", req.getRemoteAddr());

		try {
			String t = req.getParameter("t");
			if ("reload-cheap".equals(t)) {
				try {
					CheapEngin.initCheap(FilenameUtils.concat(JSingleton.rootINF(), confpath), null);
					resp.getWriter().write(Html.ok("cheap reloaded"));
				} catch (SAXException e) {
					e.printStackTrace();
					resp.getWriter().write(Html.err(e.getMessage()));
				}
				return;
			}

			JMessage<CheapReq> jmsg = ServletAdapter.<CheapReq>read(req, jcheapReq, CheapReq.class);
			IUser usr = verifier.verify(jmsg.header());

			CheapReq jreq = jmsg.body(0);
			SemanticObject cheap = handle(Req.parse(t), jreq, usr);
			SemanticObject rs = JProtocol.ok(p, cheap);
			ServletAdapter.write(resp, rs);
		} catch (SemanticException e) {
			ServletAdapter.write(resp, JProtocol.err(p, MsgCode.exSemantic, e.getMessage()));
		} catch (SQLException e) {
			e.printStackTrace();
			ServletAdapter.write(resp, JProtocol.err(p, MsgCode.exTransct, e.getMessage()));
		} catch (SsException e) {
			if (SampleFlags.cheapflow)
				e.printStackTrace();
			ServletAdapter.write(resp, JProtocol.err(p, MsgCode.exSession, e.getMessage()));
		} catch (ReflectiveOperationException e) {
			e.printStackTrace();
		} catch (TransException e) {
			if (SampleFlags.cheapflow)
				e.printStackTrace();
			ServletAdapter.write(resp, JProtocol.err(p, MsgCode.ext, e.getMessage()));
		} finally {
			resp.flushBuffer();
		}
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		if (SampleFlags.cheapflow)
			Utils.logi("========== cheapflow.sample post <= %s ==========", req.getRemoteAddr());

		resp.setCharacterEncoding("UTF-8");
		try {
			JMessage<CheapReq> jmsg = ServletAdapter.<CheapReq>read(req, jcheapReq, CheapReq.class);
			IUser usr = verifier.verify(jmsg.header());

			CheapReq jreq = jmsg.body(0);
			String a = jreq.req();

			SemanticObject cheap = handle(Req.parse(a), jreq, usr);
			SemanticObject rs = JProtocol.ok(p, cheap);
			ServletAdapter.write(resp, rs);
		} catch (SemanticException e) {
			ServletAdapter.write(resp, JProtocol.err(p, MsgCode.exSemantic, e.getMessage()));
		} catch (SQLException e) {
			if (SampleFlags.cheapflow)
				e.printStackTrace();
			ServletAdapter.write(resp, JProtocol.err(p, MsgCode.exTransct, e.getMessage()));
		} catch (ReflectiveOperationException e) {
			e.printStackTrace();
		} catch (SsException e) {
			if (SampleFlags.cheapflow)
				e.printStackTrace();
			ServletAdapter.write(resp, JProtocol.err(p, MsgCode.exSession, e.getMessage()));
		} catch (TransException e) {
			if (SampleFlags.cheapflow)
				e.printStackTrace();
			ServletAdapter.write(resp, JProtocol.err(p, MsgCode.ext, e.getMessage()));
		} finally {
			resp.flushBuffer();
		}
	}

	private SemanticObject handle(Req req, CheapReq jobj, IUser usr) throws SQLException, TransException {
		if (Req.start == req)
			return start(jobj, usr);
		else if (Req.cmd == req)
			return cmd(jobj, usr);
		else if (Req.cmdsRight == req) 
			return right(jobj, usr);
		else throw new CheapException("t can not handlered: %s", req);
	}

	private SemanticObject right(CheapReq jobj, IUser usr) throws SemanticException, SQLException {
		String nid = jobj.args()[0];
		String tid = jobj.args()[1];
		return CheapApi.right(jobj.wftype, usr.uid(), nid, tid);
	}

	private SemanticObject start(CheapReq jobj, IUser usr) throws SQLException, TransException {
		ArrayList<ArrayList<String[]>> inserts = jobj.childInserts;
		
		// testTrans = CheapEngin.trcs;
		Update postups = null;
		SemanticObject res = CheapApi.start(jobj.wftype)
				.nodeDesc(jobj.nodeDesc)
				.taskNv("remarks", "testing")
				.taskChildMulti("task_details", null, inserts)
				.postupdates(postups)
				.commit(usr.logAct("Start Flow " + jobj.wftype, "cheap.start"));

		// simulating business layer handling events
		ICheapEventHandler eh = (ICheapEventHandler) res.remove("stepHandler");
		if (eh != null) {
			CheapEvent evt = (CheapEvent) res.get("evt");
			eh.onCmd(evt);
		}

		eh = (ICheapEventHandler) res.remove("arriHandler");
		if (eh != null)
			eh.onArrive(((CheapEvent) res.get("evt")));

		return res;
	}
	
	private SemanticObject cmd(CheapReq jobj, IUser usr) {
		return null;
	}

}
