package io.oz.jserv.docsync;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

import io.odysz.anson.x.AnsonException;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.IPort;
import io.odysz.semantic.jserv.ServPort;
import io.odysz.semantic.tier.docs.DocsReq;
import io.odysz.semantics.x.SemanticException;

public class Synode extends ServPort<DocsReq> {
	private static final long serialVersionUID = 1L;

	public Synode(IPort port) {
		super(port);
	}

	@Override
	protected void onGet(AnsonMsg<DocsReq> msg, HttpServletResponse resp)
			throws ServletException, IOException, AnsonException, SemanticException {
		
	}

	@Override
	protected void onPost(AnsonMsg<DocsReq> msg, HttpServletResponse resp)
			throws ServletException, IOException, AnsonException, SemanticException {
	}

}
