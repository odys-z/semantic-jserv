package io.odysz.semantic.jserv.R;

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.odysz.common.Utils;
import io.odysz.module.rs.SResultset;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.jprotocol.JHelper;
import io.odysz.semantic.jserv.JSingleton;
import io.odysz.semantic.jserv.ServFlags;
import io.odysz.semantic.jserv.helper.Html;
import io.odysz.transact.sql.Query;
import io.odysz.transact.sql.Transcxt;
import io.odysz.transact.sql.parts.select.JoinTabl.join;
import io.odysz.transact.x.TransException;

@WebServlet(description = "querying db via Semantic.DA", urlPatterns = { "/query.serv" })
public class SQuery extends HttpServlet {
	private static Transcxt st;
	static JHelper<QueryMsg> jhelper;
	static {
		st = JSingleton.st;
		jhelper = new JHelper<QueryMsg>();
	}

	private static final long serialVersionUID = 1L;

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		
		try {
			InputStream in = req.getInputStream();
			List<QueryMsg> msgs = jhelper.readJsonStream(in, QueryMsg.class);
			in.close();
			
			QueryMsg msg = msgs.get(0);
//			// TODO let's use stream mode

			SResultset rs = query(msg);
			
			resp.setCharacterEncoding("UTF-8");
			resp.getWriter().write(Html.rs(rs));
			resp.flushBuffer();
		} catch (SQLException e) {
			e.printStackTrace();
		} catch (TransException e) {
			e.printStackTrace();
		}
	}
	
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		try {
			InputStream in = req.getInputStream();
			List<QueryMsg> msgs = jhelper.readJsonStream(in, QueryMsg.class);
			in.close();
			
			QueryMsg msg = msgs.get(0);
			SResultset rs = query(msg);
			
			resp.setCharacterEncoding("UTF-8");
			resp.getWriter().write(Html.rs(rs));
			resp.flushBuffer();
		} catch (SQLException e) {
			e.printStackTrace();
		} catch (TransException e) {
			e.printStackTrace();
		}

	}
	
	SResultset query(QueryMsg msg) throws SQLException, TransException {
		// TODO let's use stream mode
		ArrayList<String> sqls = new ArrayList<String>();
		Query selct = st.select(msg.mtabl, msg.mAlias);
		if (msg.exprs != null && msg.exprs.size() > 0)
			for (String[] col : msg.exprs)
				// TODO extend col()
				selct.col(col[0], col[2], col[1]);
		
		if (msg.joins != null && msg.joins.size() > 0) {
			for (String[] j : msg.joins)
				selct.j(join.parse(j[0]), j[1], j[2], j[3]);
		}
		
		if (msg.where != null && msg.where.size() > 0) {
			for (String[] cond : msg.where)
				selct.where(cond[1], cond[0], cond[2]);
		}
		
		// TODO: GROUP
		// TODO: ORDER
		selct.commit(sqls);

			
		if (ServFlags.query)
			Utils.logi(sqls);

		// Using semantic-DA to query from default connection.
		// If the connection is not default, use another overloaded function select(connId, ...).
		SResultset rs = Connects.select(sqls.get(0));

		if (ServFlags.query)
			try {rs.printSomeData(false, 1, rs.getColumnName(1), rs.getColumnName(2)); }
			catch (Exception e) {e.printStackTrace();}

		return rs;
	}

}
