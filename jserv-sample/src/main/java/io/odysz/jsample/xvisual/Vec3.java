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
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.DA.DatasetCfg;
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

	@Override
	protected void onGet(AnsonMsg<UserReq> jmsg, HttpServletResponse resp)
			throws IOException {
		if (SampleFlags.xvisual)
			Utils.logi("---------- x-visual/vec3.serv GET ----------");
		try {
			UserReq jreq = jmsg.body(0);
			if ("query".equals(jreq.a()))
				resp.getWriter().write(Html.ok("a = xyz | vec, xyz: find posssible axis labels; vec: get vectors."));
			else
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

			AnsonMsg<? extends AnsonResp> rsp = null;
			if ("xyz".equals(jreq.a()))
				rsp = xyz(jmsg, usr);
			else if ("vec".equals(jreq.a()))
				rsp = vectors(jmsg, usr);
			else if ("cube".equals(jreq.a()))
				rsp = cubes(jmsg, usr);
			else
				throw new SemanticException("request.body.a can not handled: %s\n" +
						"Only a = xyz | vec are supported. Please use GET a=query to find what's latest are supported ()", jreq.a());

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
		DATranscxt st = getContext(Connects.uri2conn(req.uri()));
		SemanticObject rs = st .select("s_domain", "d")
				.nv("title", "txt")
				.orderby(serialsOrder) // TODO add ui order
				.rs(st.instancontxt(Connects.uri2conn(req.uri()), usr));

		return ok((ArrayList<AnResultset>) rs.rs(0));
	}

	static DATranscxt getContext(String connId) throws SemanticException {
		// TODO & FIXME you can create a context buffer here
		try {
			return new DATranscxt(connId);
		} catch (SQLException | SAXException | IOException e) {
			throw new SemanticException("Can't create DATranscxt. Caused by: " + e.getMessage());
		}
	}

	protected AnsonMsg<AnsonResp> vectors(AnsonMsg<UserReq> jmsg, IUser usr) 
			throws TransException, SQLException {
		UserReq req = jmsg.body(0);
		DATranscxt st = getContext(Connects.uri2conn(req.uri()));
		
		// actually a direct sql statement is more comfortable
		// you can configure it as a dataset:
		// select sum(val) amount, dim1 age from vector v join s_domain d on v.dim3 = d.did group by age, dim1, dim2 order by parent asc, did asc
		SemanticObject rs = st.select("vector", "v")
				.j("s_domain", "d", "v.dim3 = d.did)")
				.col("sum(val)", "amount")   // name can be an expr - let's extend this later
				.col("dim1", "age")
				.groupby(serialsGroup)
				.orderby(serialsOrder) // TODO add s_domain.ui_order
				.rs(st.instancontxt(Connects.uri2conn(req.uri()), usr));

		return ok((AnResultset)rs.rs(0));
	}

	static String cube_legend = "legend.cube.vec3";
	static String cube_max = "max.cube.vec3";
	static String cube_x = "x.cube.vec3";
	static String cube_z = "z.cube.vec3";
	static String cube_y = "xzy.cube.vec3"; // only debugging

	@SuppressWarnings({ "unchecked", "static-access" })
	protected AnsonMsg<XChartResp> cubes(AnsonMsg<UserReq> jmsg, IUser usr) 
			throws TransException, SQLException {
		UserReq req = jmsg.body(0);

		AnResultset x = DatasetCfg.loadDataset(Connects.uri2conn(req.uri()), cube_x);
		AnResultset z = DatasetCfg.loadDataset(Connects.uri2conn(req.uri()), cube_z);
	
		/* TODO Case_expression of select_element is not implemented in semantic.transact.
		 * It can be queried like:
		DATranscxt st = getContext(req.conn());
		SemanticObject y = st.select("vector", "v")
				.j("s_domain", "x", "v.dim3 = x.did)")
				.j("s_domain", "z", "v.dim7 = z.did)")
				.col("sum(val)", "amount")   // name can be an expr - let's extend this later
				.col("dim1", "age")
				.groupby(cubeGroups)
				.orderby(cubeOrder)
				.rs(st.instancontxt(req.conn(), usr));
		 */

		AnResultset legend = DatasetCfg.loadDataset(Connects.uri2conn(req.uri()), cube_legend); 
		AnResultset maxmin = DatasetCfg.loadDataset(Connects.uri2conn(req.uri()), cube_max); 

		AnResultset y = DatasetCfg.loadDataset(Connects.uri2conn(req.uri()), cube_y, -1, -1, caseElem(x, z));

		// tested only for sqlite
		XChartResp cube = new XChartResp(x, z)
				.axis("x", "z", "y")
				.legend(legend)
				.range(maxmin)
				.vector(y);
		return (AnsonMsg<XChartResp>) new AnsonMsg<XChartResp>().ok(Samport.vec3, cube);
	}

	/**Get case-when select element string - a temporary solution
	 * @param x
	 * @param z
	 * @return
	 */
	String[] caseElem(AnResultset x, AnResultset z) {
		// TODO iterate through x, z to generate case statement
		String casex = "case dim3 when 'GICS-15103020' then 0 when 'GICS-15101010' then 1 else 2 end";
		String casez = "case dim7 when 'own-1' then 0 when 'own-2' then 1 when 'own-3' then 2 when 'own-4' then 3 else 4 end";
		return new String[] {casex, casez};
	}
}
