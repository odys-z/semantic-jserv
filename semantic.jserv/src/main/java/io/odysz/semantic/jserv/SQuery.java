package io.odysz.semantic.jserv;

import static j2html.TagCreator.body;
import static j2html.TagCreator.each;
import static j2html.TagCreator.h1;
import static j2html.TagCreator.head;
import static j2html.TagCreator.html;
import static j2html.TagCreator.meta;
import static j2html.TagCreator.table;
import static j2html.TagCreator.tbody;
import static j2html.TagCreator.td;
import static j2html.TagCreator.th;
import static j2html.TagCreator.tr;
import static j2html.TagCreator.span;

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
import io.odysz.transact.sql.Transcxt;
import io.odysz.transact.x.TransException;

@WebServlet(description = "querying db via Semantic.DA", urlPatterns = { "/query.serv" })
public class SQuery extends HttpServlet {
	private static Transcxt st;
	static { st = JSingleton.st; }

	private static final long serialVersionUID = 1L;

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		ArrayList<String> sqls = new ArrayList<String>();
		
		try {
			// Transcxt is a sql builder, you will get:
			// select funcId code, funcName name from a_functions f
			st.select("a_functions", "f")
				.col("funcId", "code")
				.col("funcName", "name")
				.commit(sqls);
		
			// Print what's built.
			// Use a static final boolean before calling Utils.log(format, args)
			// Java compiler will dismiss this code, same effect like c #define. 
			if (ServFlags.query)
				Utils.logi(sqls);

			// Using semantic-DA to query from default connection.
			// If the connection is not default, use another overloaded function select(connId, ...).
			SResultset rs = Connects.select(sqls.get(0));
			if (ServFlags.query)
				rs.printSomeData(false, 1, "fid");
			
			resp.setCharacterEncoding("UTF-8");
			resp.getWriter().write(htmlRs(rs));
			resp.flushBuffer();
		} catch (SQLException e) {
			e.printStackTrace();
		} catch (TransException e) {
			e.printStackTrace();
		}
	}

	/**Change rs to html.
	 * @param rs
	 * @return
	 */
	public static String htmlRs(SResultset rs) {
		// html() is a simple HTML composer, see
		// https://github.com/tipsy/j2html 
		return "<!DOCTYPE HTML>" + html(
			head(meta().withCharset("utf-8")),
			body(
				h1("echo.test"),
					table(tbody(
						tr(each(rs.getColnames().keySet(), col -> th(span(col)))),
						each(rs.getRows(), row -> tr(each(row, cell -> td(cell.toString()))))))
				)).render();
	}

}
