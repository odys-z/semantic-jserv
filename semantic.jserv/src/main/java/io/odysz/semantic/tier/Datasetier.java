package io.odysz.semantic.tier;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletResponse;

import io.odysz.anson.AnsonException;
import io.odysz.semantic.DA.DatasetCfg;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
import io.odysz.semantic.jprotocol.AnsonResp;
import io.odysz.semantic.jserv.JSingleton;
import io.odysz.semantic.jserv.ServPort;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantic.tier.DatasetierReq.A;
import io.odysz.semantics.IUser;
import io.odysz.semantics.x.SemanticException;

@WebServlet(description = "load dataset meta configured in dataset.xml", urlPatterns = { "/ds.tier" })
public class Datasetier extends ServPort<DatasetierReq> {

	public Datasetier() {
		super(Port.datasetier);
	}

	@Override
	protected void onGet(AnsonMsg<DatasetierReq> msg, HttpServletResponse resp)
			throws ServletException, IOException, AnsonException, SemanticException {
	}

	@Override
	protected void onPost(AnsonMsg<DatasetierReq> jmsg, HttpServletResponse resp)
			throws ServletException, IOException, AnsonException, SemanticException {
		
		resp.setCharacterEncoding("UTF-8");
		try {
			IUser usr = JSingleton.getSessionVerifier().verify(jmsg.header());

			DatasetierReq jreq = jmsg.body(0);

			AnsonResp rsp = null;
			if (A.sks.equals(jreq.a()))
				rsp = sks(jreq, usr);
			else throw new SemanticException(String.format(
						"request.body.a can not handled: %s\\n" +
						"Only a = [%s] are supported.",
						jreq.a(), A.sks));

			write(resp, ok(rsp));
		} catch (SemanticException e) {
			write(resp, err(MsgCode.exSemantic, e.getMessage()));
		} catch (SsException e) {
			write(resp, err(MsgCode.exSession, e.getMessage()));
		} finally {
			resp.flushBuffer();
		}
	}

	private AnsonResp sks(DatasetierReq jreq, IUser usr) {
		return new DatasetierResp().sks(DatasetCfg.sks());
	}

}
