package io.odysz.jsample.servs;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.xml.sax.SAXException;

import io.odysz.common.Utils;
import io.odysz.jsample.protocol.Samport;
import io.odysz.jsample.utils.SampleFlags;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.jprotocol.IPort;
import io.odysz.semantic.jprotocol.JHelper;
import io.odysz.semantic.jprotocol.JMessage;
import io.odysz.semantic.jprotocol.JMessage.MsgCode;
import io.odysz.semantic.jprotocol.JProtocol;
import io.odysz.semantic.jserv.JSingleton;
import io.odysz.semantic.jserv.helper.Html;
import io.odysz.semantic.jserv.helper.ServletAdapter;
import io.odysz.semantic.jserv.user.UserReq;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantics.IUser;
import io.odysz.semantics.SemanticObject;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.sql.Insert;
import io.odysz.transact.sql.Update;
import io.odysz.transact.x.TransException;

/**Sample serv (Port = user.serv)<br>
 * a = "A" | "B" | "C";<br>
 * The js client request should do something like this:<pre>
var conn = jconsts.conn;
function saveTooleA() {
	var dat = {borrowId: 'borrow-001', items: []};
	dat.items.push(['item001', 3]); // return 3 of tiem001

	var usrReq = new jvue.UserReq(conn, "r_tools_borrows")
						// turn back tools - or any function branch tag handled by tools.serv
						.a("A")

						// or reaplace these 2 set() with data(dat)
						.set('borrowId', 'borrow-001')
						.set('items', [['item001', 3]]);

	var jmsg = ssClient
		// ssClient's current user action is handled by jeasy when loading menu
		.usrCmd('save') // return ssClient itself
		.userReq(conn, engports.tools, usrReq); // return the JMessage<UserReq> object

	// You should get sqls at server side like this:
	// delete from r_tools_borrows where borrowId = 'borrow-001'
	// insert into detailsTbl  (item001) values ('3.0')
	// update borrowTbl  set total= where borrowId = 'borrow-001'
	ssClient.commit(jmsg, function(resp) {
				EasyMsger.ok(EasyMsger.m.saved);
			}, EasyMsger.error);
}</pre>
 * @author odys-z@github.com
 */
@WebServlet(description = "jserv.sample example/user.serv", urlPatterns = { "/tools.serv" })
public class Tools extends HttpServlet {
	private static final long serialVersionUID = 1L;

	static DATranscxt st;

	protected static JHelper<UserReq> jReq;

	private static final IPort p = Samport.tools;
	
	static {
		try {
			// this constructor can only been called after metas has been loaded
			// (Jsingleton got a chance to initialize)
			st = new DATranscxt("inet");
		} catch (SemanticException | SQLException | SAXException | IOException e) {
			e.printStackTrace();
		}
		jReq  = new JHelper<UserReq>();
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws IOException {
		if (SampleFlags.user)
			Utils.logi("---------- servs.Business A GET <- %s ----------", req.getRemoteAddr());
		try {
			resp.getWriter().write(Html.ok("Please visit POST."));
		} finally {
			resp.flushBuffer();
		}
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp)
			throws IOException {
		if (SampleFlags.user)
			Utils.logi("========== servs.Business A POST <= %s ==========", req.getRemoteAddr());

		resp.setCharacterEncoding("UTF-8");
		try {
			JMessage<UserReq> jmsg = ServletAdapter.<UserReq>read(req, jReq, UserReq.class);
			IUser usr = JSingleton.getSessionVerifier().verify(jmsg.header());

			UserReq jreq = jmsg.body(0);

			SemanticObject rsp = null;
			if ("A".equals(jreq.a()))
				rsp = A(jreq, usr);
			else if ("B".equals(jreq.a()))
				rsp = B(jreq, usr);
			else if ("C".equals(jreq.a()))
				rsp = C(jreq, usr);
			else throw new SemanticException("request.body.a can not handled: %s", jreq.a());

			rsp = JProtocol.ok(p, rsp);
			
			ServletAdapter.write(resp, rsp);
		} catch (SemanticException e) {
			ServletAdapter.write(resp, JProtocol.err(p, MsgCode.exSemantic, e.getMessage()));
		} catch (SQLException | TransException e) {
			if (SampleFlags.user)
				e.printStackTrace();
			ServletAdapter.write(resp, JProtocol.err(p, MsgCode.exTransct, e.getMessage()));
		} catch (ReflectiveOperationException e) {
			e.printStackTrace();
		} catch (SsException e) {
			ServletAdapter.write(resp, JProtocol.err(p, MsgCode.exSession, e.getMessage()));
		} finally {
			resp.flushBuffer();
		}
	}

	private SemanticObject A(UserReq req, IUser usr) throws TransException {
		String borrowId = (String) req.get("borrowId");
		@SuppressWarnings("unchecked")
		ArrayList<String[]> items = (ArrayList<String[]>) req.get("items");
		
		int total = 0;
		Insert ins = st.insert("detailsTbl", usr);
		for (String[] it : items) {
			ins.nv(it[0], it[1]);
			try { total += Integer.valueOf(it[1]); }
			catch (Exception e) {}
		}

		Update upd = st.update("borrowTbl", usr)
				.nv("total", String.valueOf(total))
				.where_("=", "borrowId", borrowId);
		
		
		ArrayList<String> sqls = new ArrayList<String>();
		st.delete((String)req.tabl(), usr)
				.where_("=", "borrowId", borrowId)
				.post(ins)
				.post(upd)
				// If calling D() instead of commit() here, should also committed logs to DB (cmd = save)
				.commit(sqls, usr);
		
		Utils.logi(sqls);

		return new SemanticObject().code(MsgCode.ok.name()).port(p.name());
	}

	private SemanticObject B(UserReq req, IUser usr) {
		return new SemanticObject()
				.code(MsgCode.ok.name()).port(p.name())
				.msg("B");
	}

	private SemanticObject C(UserReq req, IUser usr) {
		return new SemanticObject()
				.code(MsgCode.ok.name()).port(p.name())
				.msg("C");
	}
}
