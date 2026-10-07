package io.oz.syntier.serv;

import java.io.IOException;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletResponse;

import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.AnsonResp;
import io.odysz.semantic.jserv.echo.Echo;
import io.odysz.semantic.jserv.echo.EchoReq;
import io.odysz.semantic.jserv.echo.EchoReq.A;
import io.odysz.semantics.x.SemanticException;
import io.oz.jserv.docs.syn.singleton.AppSettings;
import io.oz.syn.registry.SynodeConfig;


/**
 * This is only a temporary solution for branch portfolio 0.8?
 * @since 0.8.0
 */
@WebServlet(description = "Album echo since 0.8", urlPatterns = { "/echo.less" })
public class AlbumEcho extends Echo {

	private static final long serialVersionUID = 1L;
	
	
	AppSettings appSettings;
	SynodeConfig syncfg;
	
	public AlbumEcho(AppSettings settings, SynodeConfig cfg) {
		appSettings = settings;
		syncfg = cfg;
	}

	@Override
	protected void resp(EchoReq echoReq, HttpServletResponse resp, String remote) throws IOException {
		try {
			if (A.pubConfig.equals(echoReq.a())) {
				AnsonResp rep = pubConfg(resp, echoReq, remote);
				write(resp, ok(rep));
			}
			else super.resp(echoReq, resp, remote);
		} catch (SemanticException e) {
			write(resp, err(MsgCode.exSemantic, e.getMessage()));
		} catch (IOException e) {
			write(resp, err(MsgCode.exGeneral, e.getMessage()));
			e.printStackTrace();
		}
	}

	// ISSUE MERGE-WEBROOT, see Anclient/examples/example.slint/issues/i-2026-10-03.md
	protected AnsonResp pubConfg(HttpServletResponse resp, EchoReq echoReq, String remote) throws SemanticException {
		return new AnsonResp()
				// TODO to be fixed: the cpp's AnsonResp.m has a wrong correct type of Anson. Sure be VarType (Java Object).
				// .data("ip", appSettings.reverseIp())
				// .data("web-port", appSettings.reversedWebPort(syncfg.https));
				.msg(String.format("%s:%s", appSettings.reverseIp(), appSettings.reversedWebPort(syncfg.https)));
	}
}
