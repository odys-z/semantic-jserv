package io.oz.jserv.docs.syn;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

import io.odysz.anson.x.AnsonException;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
import io.odysz.semantic.jserv.ServPort;
import io.odysz.semantic.syn.SynodeMode;
import io.odysz.semantics.x.SemanticException;

public class Syntier extends ServPort<SyncReq> {
	public Syntier(String synoderId) {
		super(Port.dbsyncer);
	}

	private static final long serialVersionUID = 1L;

	public static final int jservx = 0;
	public static final int myconx = 1;

	String synode;

	public Synoder synoder;

	/** The domain id for client before joined a domain. */
	public static final String domain0 = "io.oz.jserv.syn.init";


	@Override
	protected void onGet(AnsonMsg<SyncReq> msg, HttpServletResponse resp)
			throws ServletException, IOException, AnsonException, SemanticException {
		// TODO Auto-generated method stub
	}

	@Override
	protected void onPost(AnsonMsg<SyncReq> msg, HttpServletResponse resp)
			throws ServletException, IOException, AnsonException, SemanticException {
		// TODO Auto-generated method stub
		
	}

	public Synoder start(String domain, SynodeMode peer) {
		// TODO Auto-generated method stub
		return null;
	}

}
