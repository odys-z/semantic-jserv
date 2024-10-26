package io.oz.jserv.docs.syn;

import static io.oz.jserv.docs.syn.SyncReq.A;
import static io.odysz.common.LangExt.eq;
import static io.odysz.common.LangExt.isblank;
import static io.odysz.semantic.syn.ExessionAct.ready;

import java.io.IOException;
import java.sql.SQLException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletResponse;

import org.xml.sax.SAXException;

import io.odysz.common.Utils;
import io.odysz.semantic.jprotocol.AnsonBody;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
import io.odysz.semantic.jserv.JSingleton;
import io.odysz.semantic.jserv.ServPort;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantic.syn.ExchangeBlock;
import io.odysz.semantic.syn.SynodeMode;
import io.odysz.semantics.x.ExchangeException;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.x.TransException;

@WebServlet(description = "Synode Tier Workder", urlPatterns = { "/sync.tier" })
public class ExpSynodetier extends ServPort<SyncReq> {
	private static final long serialVersionUID = 1L;
	
	final String domain;
	final String synid;
	final SynodeMode mode;

	SynDomanager domanager0;

	/** {domain: {jserv: exession-persist}} */
//	public HashMap<String, SynDomanager> domains;
//	public ExpSynodetier domains(HashMap<String, SynDomanager> domains) throws Exception {
//		this.domains = domains;
//		if (len(domains) > 1)
//			Utils.warnT(new Object() {}, "Multiple domains is an issue for v 2.0.0.");
//
//		for (SynDomanager dm : domains.values()) {
//			// domanager0 = SynDomanager.clone(dm);
//			domanager0 = dm; // FIXME Error prone!
//			break;
//		}
//		return this;
//	}

	ExpSynodetier(String org, String domain, String synode, String conn, SynodeMode mode)
			throws SQLException, SAXException, IOException, TransException {
		super(Port.syntier);
		this.domain = domain;
		this.synid  = synode;
		this.mode   = mode;
	}

	public ExpSynodetier(SynDomanager domanger) throws SQLException, SAXException, IOException, TransException {
		this(domanger.org, domanger.domain(), domanger.synode, domanger.synconn, domanger.mode);
		domanager0 = domanger;
	}

	@Override
	protected void onGet(AnsonMsg<SyncReq> msg, HttpServletResponse resp) {
		Utils.warnT(new Object() {}, "Targeted? Source header: %s\nbody: %s",
				msg.header().toString(),
				msg.body(0).toString());
	}

	@Override
	protected void onPost(AnsonMsg<SyncReq> jmsg, HttpServletResponse resp)
			throws ServletException, IOException {
		AnsonBody jreq = jmsg.body(0); // SyncReq | DocsReq
		String a = jreq.a();
		SyncResp rsp = null;
		try {
			SyncReq req = jmsg.body(0);

			DocUser usr = (DocUser) JSingleton.getSessionVerifier().verify(jmsg.header());
			
			if (req.exblock != null) {
				if (!isblank(req.exblock.domain) && !eq(req.exblock.domain, domain))
					throw new ExchangeException(ready, null,
							"domain is %s, while expecting %s", req.exblock.domain, domain);
				else if (isblank(req.exblock.srcnode))
					throw new ExchangeException(ready, null,
							"req.exblock.srcnode is null. Requests to (Exp)Synodetier must present the souce identity.");
				else if (eq(req.exblock.srcnode, synid))
					throw new ExchangeException(ready, null,
							"req.exblock.srcnode is identical to this synode's.");
			}

			if (A.initjoin.equals(a))
				rsp = domanager0.onjoin(req, usr);

			else if (A.closejoin.equals(a))
				rsp = domanager0.onclosejoin(req, usr);

			else if (A.exinit.equals(a)) 
				rsp = domanager0.onsyninit(req, usr);

			else if (A.exchange.equals(a)) {
				if (domanager0.synssion(req.exblock.srcnode) == null)
					throw new SemanticException(
						"The sync-session for %s to exchange pages at %s desen't exist. A = %s, conn %s, domain %s.",
						req.exblock.srcnode, domanager0.synode, A.exchange, domanager0.synconn, domanager0.domain());

				ExchangeBlock b = domanager0
						.synssion(req.exblock.srcnode)
						.syncdb(req.exblock);

				rsp = new SyncResp(domain).exblock(b);
			}

			else if (A.exclose.equals(a)) {
				if (domanager0.synssion(req.exblock.srcnode) == null)
					throw new SemanticException(
						"The sync-session for %s to exchange pages at %s desen't exist. A = %s, conn %s, domain %s.",
						req.exblock.srcnode, domanager0.synode, A.exchange, domanager0.synconn, domanager0.domain());
				rsp = domanager0.onclosex(req, usr);
			}

			else 
				throw new SemanticException("Request.a, %s, can not be handled at port %s",
						jreq.a(), p.name());

			write(resp, ok(rsp.syndomain(domain)));
		} catch (SsException e) {
			write(resp, err(MsgCode.exSession, e.getMessage()));
		} catch (SemanticException e) {
			write(resp, err(MsgCode.exTransct, e.getMessage()));
		} catch (SQLException | TransException e) {
			e.printStackTrace();
			write(resp, err(MsgCode.exTransct, e.getMessage()));
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
