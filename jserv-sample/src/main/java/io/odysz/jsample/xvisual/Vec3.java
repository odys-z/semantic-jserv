package io.odysz.jsample.xvisual;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletResponse;

import org.xml.sax.SAXException;

import io.odysz.common.Utils;
import io.odysz.jsample.protocol.Samport;
import io.odysz.jsample.utils.SampleFlags;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.AnsonResp;
import io.odysz.semantic.jserv.JSingleton;
import io.odysz.semantic.jserv.ServPort;
import io.odysz.semantic.jserv.helper.Html;
import io.odysz.semantic.jserv.user.UserReq;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantics.IUser;
import io.odysz.semantics.SemanticObject;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.x.TransException;

/**<p>Sample serv (Port = user.serv) shows how to implement data service for x-visual/bar-chart
 * with help of semantic-transact SQL builder</p>
 * function branch: a = "A" | "B" | "C";<br>
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
		.userReq(conn, engports.tools, usrReq); // return the AnsonMsg<UserReq> object

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
@WebServlet(description = "jserv.sample example/vec3.serv", urlPatterns = { "/vec3.serv" })
public class Vec3 extends ServPort<UserReq> {
	public Vec3() {
		super(null);
		p = Samport.vec3;
	}

	private static final long serialVersionUID = 1L;

	static DATranscxt st;

	static {
		try {
			// this constructor can only been called after metas has been loaded
			// (Jsingleton got a chance to initialize)
			st = new DATranscxt("inet");
		} catch (SemanticException | SQLException | SAXException | IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	protected void onGet(AnsonMsg<UserReq> req, HttpServletResponse resp)
			throws IOException {
		if (SampleFlags.xvisual)
			Utils.logi("---------- x-visual/vec3.serv GET ----------");
		try {
			resp.getWriter().write(Html.ok("Please visit POST."));
		} finally {
			resp.flushBuffer();
		}
	}

	@Override
	protected void onPost(AnsonMsg<UserReq> jmsg, HttpServletResponse resp)
			throws IOException {
		if (SampleFlags.xvisual)
			Utils.logi("========== x-visual/vec3.serv POST ==========");

		resp.setCharacterEncoding("UTF-8");
		try {
			IUser usr = JSingleton.getSessionVerifier().verify(jmsg.header());

			UserReq jreq = jmsg.body(0);

			AnsonMsg<AnsonResp> rsp = null;
			if ("xyz".equals(jreq.a()))
				rsp = xyz(jmsg, usr);
			else if ("vec".equals(jreq.a()))
				rsp = vectors(jmsg, usr);
			else throw new SemanticException("request.body.a can not handled: %s", jreq.a());

			write(resp, rsp);
		} catch (SemanticException e) {
			write(resp, err(MsgCode.exSemantic, e.getMessage()));
		} catch (SQLException | TransException e) {
			if (SampleFlags.user)
				e.printStackTrace();
			write(resp, err(MsgCode.exTransct, e.getMessage()));
		} catch (SsException e) {
			write(resp, err(MsgCode.exSession, e.getMessage()));
		} finally {
			resp.flushBuffer();
		}
	}

	private String[] serialsGroup = new String[] {"age", "dim1", "dim2"};

	@SuppressWarnings("serial")
	protected static ArrayList<String[]> serialsOrder = new ArrayList<String[]>() {
		{add(new String[] {"parent"});};
		{add(new String[] {"did"});}
	};

	/**Get available x, y, z labels
	 * @param jmsg
	 * @param usr
	 * @return
	 * @throws TransException
	 * @throws SQLException
	 */
	@SuppressWarnings("unchecked")
	protected AnsonMsg<AnsonResp> xyz(AnsonMsg<UserReq> jmsg, IUser usr)
			throws TransException, SQLException {
		UserReq req = jmsg.body(0);
		SemanticObject rs = st.select("s_domain", "d")
				.nv("title", "txt")
				.orderby(serialsOrder) // TODO add ui order
				.rs(st.instancontxt(req.conn(), usr));

		return ok((ArrayList<AnResultset>) rs.rs(0));
	}

	@SuppressWarnings("unchecked")
	protected AnsonMsg<AnsonResp> vectors(AnsonMsg<UserReq> jmsg, IUser usr) 
			throws TransException, SQLException {
		UserReq req = jmsg.body(0);
		SemanticObject rs = st.select("vector", "v")
				.nv("sum(val)", "amount")   // name can be an expr - let's extend this later

				.groupby(serialsGroup)
				// probably needing a new req type
				// .groupby((String[])req.get("group"))

				.orderby(serialsOrder) // TODO add ui order
				.rs(st.instancontxt(req.conn(), usr));

		return ok((ArrayList<AnResultset>) rs.rs(0));
	}
}
