package io.odysz.semantic.jserv;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.odysz.common.Utils;
import io.odysz.module.rs.SResultset;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.jserv.helper.Html;
import io.odysz.transact.sql.Transcxt;
import io.odysz.transact.x.TransException;

/**@deprecated All code samples are moved to semantic-sample.
 * @author ody
 */
@WebServlet(description = "querying db via Semantic.DA", urlPatterns = { "/query-ex.serv" })
public class SQuerySample extends HttpServlet {
	private static Transcxt st;
	static { st = JSingleton.defltScxt; }

	private static final long serialVersionUID = 1L;

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		ArrayList<String> sqls = new ArrayList<String>();
		
		try {
			// Transcxt is a sql builder, you will get:
			// select funcId Port, funcName name from a_functions f
			st.select("a_functions", "f")
				.col("funcId", "Port")
				.col("funcName", "name")
				.commit(sqls);
		
			// Print what's built.
			// Use a static final boolean before calling Utils.log(format, args)
			// Java compiler will dismiss this Port, same effect like c #define. 
			if (ServFlags.query)
				Utils.logi(sqls);

			// Using semantic-DA to query from default connection.
			// If the connection is not default, use another overloaded function select(connId, ...).
			SResultset rs = Connects.select(sqls.get(0));
			if (ServFlags.query)
				rs.printSomeData(false, 1, "fid");
			
			resp.setCharacterEncoding("UTF-8");
			resp.getWriter().write(Html.rs(rs));
			resp.flushBuffer();
		} catch (SQLException e) {
			e.printStackTrace();
		} catch (TransException e) {
			e.printStackTrace();
		}
	}

}
