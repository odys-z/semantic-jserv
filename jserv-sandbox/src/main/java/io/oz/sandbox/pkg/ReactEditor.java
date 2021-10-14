package io.oz.sandbox.pkg;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletResponse;

import io.odysz.anson.x.AnsonException;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jserv.ServPort;
import io.odysz.semantics.IUser;
import io.odysz.semantics.x.SemanticException;
import io.oz.sandbox.SandRobot;
import io.oz.sandbox.pkg.EditReq.A;
import io.oz.sandbox.protocol.Sandport;

@WebServlet(description = "Semantic tier: users", urlPatterns = { "/editor.less" })
public class ReactEditor extends ServPort<EditReq> {
	private static final long serialVersionUID = 1L;

	public ReactEditor() {
		super(Sandport.editor);
	}

	@Override
	protected void onGet(AnsonMsg<EditReq> msg, HttpServletResponse resp)
			throws ServletException, IOException, AnsonException, SemanticException {
		
	}

	@Override
	protected void onPost(AnsonMsg<EditReq> msg, HttpServletResponse resp)
			throws ServletException, IOException, AnsonException, SemanticException {
		
			EditReq jreq = msg.body(0);
			IUser usr = new SandRobot(msg.addr());
			
			EditorResp rsp = null;
			if (A.compile == jreq.a()) {
				rsp = compile(jreq, usr);
			}

			write(resp, ok(rsp));
	}

	private EditorResp compile(EditReq jreq, IUser usr) {
		return null;
	}

}
