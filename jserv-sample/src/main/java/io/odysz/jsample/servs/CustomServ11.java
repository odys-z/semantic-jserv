package io.odysz.jsample.servs;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletResponse;

import io.odysz.anson.x.AnsonException;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jserv.ServPort;
import io.odysz.semantics.x.SemanticException;

/**<p>Sample serv (Port = user.serv) shows how user can extend basic serv API
 * with help of semantic-transact SQL builder<br>
 * This also shows how user can extend {@link ServPort} with typed message handler,
 * antson, which is new to v1.1.</p>
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
@WebServlet(description = "jserv.sample example: extend serv handler", urlPatterns = { "/custom.serv11" })
public class CustomServ11 extends ServPort<CustomReq> {
	private static final long serialVersionUID = 1L;

	@Override
	protected void onGet(AnsonMsg<CustomReq> msg, HttpServletResponse resp)
			throws ServletException, IOException, AnsonException, SemanticException {
	}

	@Override
	protected void onPost(AnsonMsg<CustomReq> msg, HttpServletResponse resp)
			throws ServletException, IOException, AnsonException, SemanticException {
	}

}
