package io.oz.jserv.docs.syn;

import static io.oz.jserv.docs.syn.SyncReq.A;

import java.io.IOException;
import java.sql.SQLException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

import org.xml.sax.SAXException;

import io.odysz.anson.x.AnsonException;
import io.odysz.common.Utils;
import io.odysz.semantic.jprotocol.AnsonBody;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
import io.odysz.semantic.jserv.ServPort;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantic.syn.ExchangeBlock;
import io.odysz.semantic.syn.SynodeMode;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.x.TransException;

public class ExpSynodetier extends ServPort<SyncReq> {
	private static final long serialVersionUID = 1L;
	
	final String domain;
	final String synid;
	final SynodeMode mode;
	final SynDomanager domanager;

	public ExpSynodetier(String org, String domain, String synode, String conn, SynodeMode mode)
			throws SQLException, SAXException, IOException, TransException {
		super(Port.docsync);
		this.domain = domain;
		this.synid  = synode;
		this.mode   = mode;
		domanager   = new SynDomanager(org, domain, synode, conn, mode);
	}

	@Override
	protected void onGet(AnsonMsg<SyncReq> msg, HttpServletResponse resp)
			throws ServletException, IOException, AnsonException, SemanticException {
		Utils.warnT(new Object() {}, "hacked? source header: %s\nbody: %s",
				msg.header().toString(),
				msg.body(0).toString());
	}

	@Override
	protected void onPost(AnsonMsg<SyncReq> jmsg, HttpServletResponse resp)
			throws ServletException, IOException, AnsonException, SemanticException {
		AnsonBody jreq = jmsg.body(0); // SyncReq | DocsReq
		String a = jreq.a();
		SyncResp rsp = null;
		try {
			SyncReq req = jmsg.body(0);

			if (A.peering.equals(a)) {
			}
			else if (A.syncini.equals(a)) {
				rsp = domanager.onsyninit(req.exblock.srcnode, req.exblock);
				write(resp, ok(rsp.syndomain(domain)));
			}
			else if (A.syncent.equals(a)) {
				ExchangeBlock b = domanager.synssion(req.exblock.srcnode).syncdb(req.exblock);
				write(resp, ok(new SyncResp().exblock(b).syndomain(domain)));
			}
			else 
				throw new SemanticException("Request.a, %s, can not be handled. Port: %s",
						jreq.a(), p.name());
		} catch (SQLException | TransException e) {
			e.printStackTrace();
			write(resp, err(MsgCode.exTransct, e.getMessage()));
		} catch (SsException e) {
			write(resp, err(MsgCode.exSession, e.getMessage()));
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (Throwable e) {
			e.printStackTrace();
			write(resp, err(MsgCode.exGeneral, "%s\n%s", e.getClass().getName(), e.getMessage()));
		} finally {
			resp.flushBuffer();
		}
			
	}

}
