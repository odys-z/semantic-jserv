package io.odysz.semantic.ext;

import java.io.IOException;
import java.sql.SQLException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletResponse;

import io.odysz.common.Utils;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.DA.DatasetCfg;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
import io.odysz.semantic.jprotocol.AnsonResp;
import io.odysz.semantic.jprotocol.IPort;
import io.odysz.semantic.jprotocol.JOpts;
import io.odysz.semantic.jserv.JSingleton;
import io.odysz.semantic.jserv.ServFlags;
import io.odysz.semantic.jserv.ServPort;
import io.odysz.semantic.jserv.helper.Html;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantic.jsession.ISessionVerifier;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.sql.Transcxt;
import io.odysz.transact.x.TransException;

/**CRUD read service extension: dataset.<br>
 * This port use a configure file (dataset.xml) as data definition.
 * The client request ({@link DatasetReq}) provide configure key and parameter, the port answer with queried results.
 * @author odys-z@github.com
 */
@WebServlet(description = "load dataset configured in dataset.xml", urlPatterns = { "/ds.serv" })
public class Dataset extends ServPort<AnDatasetReq> {
	public Dataset() {
		super(Port.dataset);
	}

	private static final long serialVersionUID = 1L;

	protected static ISessionVerifier verifier;
	protected static Transcxt st;

	static IPort p = Port.dataset;
	static JOpts _opts = new JOpts();

	static {
		st = JSingleton.defltScxt;
	}

	@Override
	protected void onGet(AnsonMsg<AnDatasetReq> msg, HttpServletResponse resp)
			throws ServletException, IOException {
		if (ServFlags.query)
			Utils.logi("---------- query (ds.serv) get ----------");
		resp.setCharacterEncoding("UTF-8");
		try {
			String conn = msg.body(0).uri();
			conn = Connects.uri2conn(conn);

			verifier.verify(msg.header());

			AnsonResp rs = dataset(conn, msg);
			resp.getWriter().write(Html.rs((AnResultset)rs.rs(0)));
		} catch (SQLException e) {
			e.printStackTrace();
		} catch (TransException e) {
			e.printStackTrace();
		} catch (SsException e) {
			e.printStackTrace();
		} finally {
			resp.flushBuffer();
		}
	}
	
	protected void onPost(AnsonMsg<AnDatasetReq> msg, HttpServletResponse resp)
			throws IOException {
		resp.setCharacterEncoding("UTF-8");
		if (ServFlags.query)
			Utils.logi("========== query (ds.serv) post ==========");
		try {
			String uri = msg.body(0).uri();
			if (uri == null)
				write(resp, err(MsgCode.exSemantic, "Since v1.3.0, Dataset request must specify an uri."));
			else {
				String conn = Connects.uri2conn(uri);
				AnsonResp rs = dataset(conn, msg);
				write(resp, ok(rs));
			}
		} catch (SemanticException e) {
			write(resp, err(MsgCode.exSemantic, e.getMessage()));
		} catch (SQLException | TransException e) {
			e.printStackTrace();
			write(resp, err(MsgCode.exTransct, e.getMessage()));
		} catch (Exception e) {
			e.printStackTrace();
			write(resp, err(MsgCode.exGeneral, e.getMessage()));
		} finally {
			resp.flushBuffer();
		}
	}
	
	/**
	 * @param msgBody
	 * @return {code: "ok", port: {@link AnsonMsg.Port#query}, rs: [{@link AnResultset}, ...]}
	 * @throws SQLException
	 * @throws TransException
	 */
	protected AnsonResp dataset(String conn, AnsonMsg<AnDatasetReq> msgBody)
			throws SQLException, TransException {
		AnDatasetReq msg = msgBody.body().get(0);
		// List<SemanticObject> ds = DatasetCfg.loadStree(conn, msg.sk, msg.page(), msg.size(), msg.sqlArgs);		
		AnResultset ds = DatasetCfg.dataset(conn, msg.sk, msg.page(), msg.size(), msg.sqlArgs);		

		// Shall be moved to Protocol?
		AnDatasetResp respMsg = new AnDatasetResp();
		respMsg.rs(ds, ds.total());
		return respMsg;
	}
}
