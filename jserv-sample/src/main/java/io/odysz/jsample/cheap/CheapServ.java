package io.odysz.jsample.cheap;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io_odysz.FilenameUtils;
import org.xml.sax.SAXException;

import io.odysz.common.Utils;
import io.odysz.jsample.protocol.Samport;
import io.odysz.jsample.utils.SampleFlags;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.AnsonResp;
import io.odysz.semantic.jserv.JSingleton;
import io.odysz.semantic.jserv.ServPort;
import io.odysz.semantic.jserv.helper.Html;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantics.IUser;
import io.odysz.semantics.x.SemanticException;
import io.odysz.sworkflow.CheapApi;
import io.odysz.sworkflow.CheapEnginv1;
import io.odysz.sworkflow.CheapEvent;
import io.odysz.sworkflow.CheapException;
import io.odysz.sworkflow.CheapResp;
import io.odysz.sworkflow.EnginDesign.Req;
import io.odysz.sworkflow.ICheapEventHandler;
import io.odysz.transact.sql.Statement;
import io.odysz.transact.x.TransException;

@WebServlet(description = "Handling work flow request", urlPatterns = { "/cheapflow.sample" })
public class CheapServ extends ServPort<CheapReq> {
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
	private static final boolean logFlag = SampleFlags.cheapflow;

	public CheapServ() {
		super(null);
		p = Samport.cheapflow;
	}

	static {
		// Because of the java enum limitation, or maybe the author's knowledge limitation, 
		// AnsonMsg needing a IPort instance to handle ports that implemented a new version of valof() method handling all ports.<br>
		// E.g. {@link Samport#menu#valof(name)} can handling both {@link Port} and Samport's enums.
		//
		// If the same in SysMenu is surely called before this servlet going to work, this line can be comment out. 
		AnsonMsg.understandPorts(Samport.cheapflow);
	}

	@Override
	protected void onGet(AnsonMsg<CheapReq> jmsg, HttpServletResponse resp) throws IOException {
		if (logFlag)
			Utils.logi("---------- cheapflow.sample get ----------");

		try {
			// String t = req.getParameter("t");
			String a = jmsg.body(0).a();
			if ("reload-cheap".equals(a)) {
				try {
					CheapEnginv1.initCheap(FilenameUtils.concat(JSingleton.rootINF(), CheapEnginv1.confpath), null);
					resp.getWriter().write(Html.ok("cheap reloaded"));
				} catch (SAXException e) {
					e.printStackTrace();
					resp.getWriter().write(Html.err(e.getMessage()));
				}
				return;
			}

//			AnsonMsg<CheapReq> jmsg = ServletAdapter.<CheapReq>read(req, jcheapReq, CheapReq.class);
			IUser usr = verifier.verify(jmsg.header());

			CheapReq jreq = jmsg.body(0);
			CheapResp cheap = handle(Req.parse(a), jreq, usr);
			AnsonMsg<AnsonResp> rs = ok(cheap.rs());
			write(resp, rs);
		} catch (SemanticException e) {
			write(resp, err(MsgCode.exSemantic, e.getMessage()));
		} catch (SQLException e) {
			e.printStackTrace();
			write(resp, err(MsgCode.exTransct, e.getMessage()));
		} catch (SsException e) {
			if (logFlag)
				e.printStackTrace();
			write(resp, err(MsgCode.exSession, e.getMessage()));
		} catch (TransException e) {
			if (logFlag)
				e.printStackTrace();
			write(resp, err(MsgCode.ext, e.getMessage()));
		} finally {
			resp.flushBuffer();
		}
	}

	@Override
	protected void onPost(AnsonMsg<CheapReq> jmsg, HttpServletResponse resp) throws IOException {
		if (logFlag)
			Utils.logi("========== cheapflow.sample post ==========");

		resp.setCharacterEncoding("UTF-8");
		try {
			// AnsonMsg<CheapReq> jmsg = ServletAdapter.<CheapReq>read(req, jcheapReq, CheapReq.class);
			IUser usr = verifier.verify(jmsg.header());

			CheapReq jreq = jmsg.body(0);
			String a = jreq.req();

			CheapResp cheap = handle(Req.parse(a), jreq, usr);
			AnsonMsg<AnsonResp> rs = ok(cheap.rs());
			write(resp, rs);
		} catch (CheapException e) {
			write(resp, err(MsgCode.ext, e.getMessage()));
		} catch (SemanticException e) {
			write(resp, err(MsgCode.exSemantic, e.getMessage()));
		} catch (SQLException e) {
			if (logFlag)
				e.printStackTrace();
			write(resp, err(MsgCode.exTransct, e.getMessage()));
		} catch (SsException e) {
			write(resp, err(MsgCode.exSession, e.getMessage()));
		} catch (TransException e) {
			if (logFlag)
				e.printStackTrace();
			write(resp, err(MsgCode.ext, e.getMessage()));
		} finally {
			resp.flushBuffer();
		}
	}

	private CheapResp handle(Req req, CheapReq jobj, IUser usr) throws SQLException, TransException {
		if (Req.start == req)
			return start(jobj, usr);
		else if (Req.cmd == req)
			return cmd(jobj, usr);
		else if (Req.rights == req) 
			return right(jobj, usr);
		else if (Req.load == req) 
			return loadFlow(jobj, usr);
		else if (Req.nodeCmds == req) 
			return loadCmds(jobj, usr);
		else throw new CheapException("Req(body.a) can not been handled: %s", req);
	}

	private CheapResp right(CheapReq jobj, IUser usr) throws SemanticException, SQLException {
		String nid = jobj.nodeId();
		String tid = jobj.taskId();
		return CheapApi.right(jobj.wftype, usr.uid(), nid, tid);
	}

	private CheapResp loadFlow(CheapReq jobj, IUser usr) throws SQLException, TransException {
		String wfid = jobj.wftype();
		String tid = jobj.taskId();
		return CheapApi.loadFlow(wfid, tid, usr);
	}

	private CheapResp loadCmds(CheapReq jobj, IUser usr) throws TransException, SQLException {
		String wfid = jobj.wftype();
		String nId = jobj.nodeId();
		String iId = jobj.instId();
		return CheapApi.loadCmds(wfid, nId, iId, usr.uid());
	}
	
	private CheapResp start(CheapReq jobj, IUser usr) throws SQLException, TransException {
		ArrayList<Statement<?>> postups = jobj.posts(usr);
		CheapResp res = CheapApi.start(jobj.wftype, jobj.taskId())
				.nodeDesc(jobj.ndescpt)
				.taskNv(jobj.taskNvs)
				// .taskChildMulti(insertabl, null, inserts)
				.postupdates(postups)
				.commitReq(usr.logAct("Start Flow " + jobj.wftype, "cheap.start"));

		// simulating business layer handling events
		// FIXME why events handling is here?
		ICheapEventHandler eh = res.rmStepHandler();
		if (eh != null) {
			CheapEvent evt = (CheapEvent) res.event();
			eh.onCmd(evt);
		}

		eh = (ICheapEventHandler) res.rmArriveHandler();
		if (eh != null)
			eh.onArrive(res.event());

		return res;
	}
	
	private CheapResp cmd(CheapReq jobj, IUser usr) throws SQLException, TransException {
		String wftype = jobj.wftype();
		String taskId = jobj.taskId();
		String cmd = jobj.cmd();

		ArrayList<Statement<?>> postups = jobj.posts(usr);
		CheapResp res = CheapApi.next(wftype, taskId, cmd)
				.nodeDesc(jobj.ndescpt)
				// .taskChildMulti(jobj.childInsertabl, null, jobj.childInserts)
				.postupdates(postups)
				.commitReq(usr.logAct(String.format("Req %s - %s", jobj.wftype, cmd), "cheap.cmd"));

		// FIXME why events handling is here?
		// ICheapEventHandler eh = (ICheapEventHandler) res.remove("stepHandler");
		ICheapEventHandler eh = res.rmStepHandler();

		if (eh != null) {
			// CheapEvent evt = (CheapEvent) res.get("evt");
			eh.onCmd(res.event());
		}

		eh = res.rmArriveHandler();
		if (eh != null)
			eh.onArrive(res.event());

		return res;
	}

}
