package io.oz.jserv.dbsync;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletResponse;

import io.odysz.anson.x.AnsonException;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
import io.odysz.semantic.jserv.ServPort;
import io.odysz.semantics.x.SemanticException;

@WebServlet(description = "Cleaning tasks manager", urlPatterns = { "/sync.db" })
public class DBSyncer extends ServPort<DBSyncReq> {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public DBSyncer() {
		super(Port.dbsyncer);
	}

	@Override
	protected void onGet(AnsonMsg<DBSyncReq> msg, HttpServletResponse resp)
			throws ServletException, IOException, AnsonException, SemanticException {
		// TODO Auto-generated method stub
		
	}

	@Override
	protected void onPost(AnsonMsg<DBSyncReq> msg, HttpServletResponse resp)
			throws ServletException, IOException, AnsonException, SemanticException {
		// TODO Auto-generated method stub
		
	}

}
