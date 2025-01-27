package io.oz.jserv.docs.syn;

import static io.odysz.common.LangExt.eq;
import static io.odysz.common.LangExt.f;
import static io.odysz.common.LangExt.isblank;
import static io.odysz.common.LangExt.len;
import static io.odysz.common.LangExt.notNull;
import static io.odysz.semantic.syn.ExessionAct.deny;
import static io.odysz.semantic.syn.ExessionAct.mode_server;
import static io.odysz.semantic.syn.ExessionAct.ready;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketException;
import java.sql.SQLException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletResponse;

import org.xml.sax.SAXException;

import io.odysz.anson.x.AnsonException;
import io.odysz.common.Utils;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.jprotocol.AnsonBody;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
import io.odysz.semantic.jprotocol.JProtocol.OnError;
import io.odysz.semantic.jserv.JSingleton;
import io.odysz.semantic.jserv.ServPort;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantic.syn.ExchangeBlock;
import io.odysz.semantic.syn.ExessionAct;
import io.odysz.semantic.syn.SynodeMode;
import io.odysz.semantics.x.ExchangeException;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.x.TransException;
import io.oz.jserv.docs.syn.SyncReq.A;

@WebServlet(description = "Synode Tier Workder", urlPatterns = { "/sync.tier" })
public class ExpSynodetier extends ServPort<SyncReq> {
	private static final long serialVersionUID = 1L;
	
	final String domain;
	final String synid;
	
	/** peer | non-syn | leaf */
	final SynodeMode mode;

	SynDomanager domanager0;
	
	public boolean debug;

	// private DATranscxt synt0;

	ExpSynodetier(String org, String domain, String synode, String conn, SynodeMode mode)
			throws SQLException, SAXException, IOException, TransException {
		super(Port.syntier);
		this.domain = domain;
		this.synid  = synode;
		this.mode   = mode;
		this.debug  = Connects.getDebug(conn);
	}

	public ExpSynodetier(SynDomanager domanger)
			throws Exception {
		this(domanger.org, domanger.domain(), domanger.synode, domanger.synconn, domanger.mode);
		domanager0 = domanger;
		// synt0 = new DATranscxt(domanger.synconn);
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
			notNull(usr.deviceId());
			
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

			if (A.initjoin.equals(a)) {
				if (!eq(usr.orgId(), domanager0.org))
					rsp = (SyncResp) deny(req.exblock).msg(f(
							"User's org id, %s, is not matched for joining %s. Domain: %s",
							usr.orgId(), domanager0.org, domanager0.domain()));
				else
					rsp = new SynssionServ(domanager0, req.exblock.srcnode, usr)
						.onjoin(req);
			}

			else if (A.closejoin.equals(a))
				rsp = usr.<SynssionServ>synssion().onclosejoin(req, usr);

			else if (A.exinit.equals(a)) 
				rsp = new SynssionServ(domanager0, req.exblock.srcnode, usr)
					.onsyninit(req.exblock);

			else if (A.exchange.equals(a))
				rsp = usr.<SynssionServ>synssion().onsyncdb(req.exblock);

			else if (A.exclose.equals(a))
				rsp = usr.<SynssionServ>synssion().onclosex(req, usr);

			else 
				throw new SemanticException("Request.a, %s, can not be handled at port %s",
						jreq.a(), p.name());

			write(resp, ok(rsp.syndomain(domain)));
		} catch (ExchangeException e) {
			write(resp, err(MsgCode.exSemantic, e.getMessage()));
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

	private SyncResp deny(ExchangeBlock exblock) {
		return new SyncResp(domanager0.domain()).exblock(
				new ExchangeBlock(domanager0.domain(), domanager0.synode, exblock.srcnode, null,
				new ExessionAct(mode_server, deny)));
	}

	//////////////////////////////  worker    ///////////////////////////////////
	public static int maxSyncInSnds = (int) (60.0 * (5 + Math.random())); // 5-6 minutes

	final Runnable[] worker = new Runnable[] {null};

	float syncInSnds;

	private ScheduledExecutorService scheduler;
	private static ScheduledFuture<?> schedualed;

	@Override
	public void destroy() {
		super.destroy();
		stopScheduled(5);
	}

	boolean running;

	/**
	 * <h4>Debug Notes</h4>
	 * Multiple threads cannot be scheduled ad a single test running.
	 * 
	 * @param syncIns
	 * @return this
	 * @throws Exception 
	 * @since 0.7.0
	 */
	public ExpSynodetier syncIn(float syncIns, OnError err) throws Exception {
		this.syncInSnds = syncIns;
		if ((int)(this.syncInSnds) <= 0) {
			Utils.warn("Syn-worker is disabled (synching in = %s seconds). %s : %s [%s]",
					syncIns, domanager0.domain(), domanager0.synode, domanager0.synconn);
			return this;
		}

		DATranscxt syntb = new DATranscxt(domanager0.synconn);

		worker[0] = () -> {
			if (running)
				return;
			running = true;

			if (debug)
				Utils.logi("[%s] : Checking Syndomain ...", synid);

			try {
				if (len(this.domanager0.sessions) == 0)
					this.domanager0.loadSynclients(syntb);

				this.domanager0
					.openSynssions(domanager0.admin);

				this.domanager0.asyUpdomains(
					(dom, synode, peer, xp) -> {
						if (debug) Utils.logi("[%s] On update: %s [%s:%s]",
								synid, dom, domanager0.n0(), domanager0.stamp());
					},
					(synlocker) -> Math.random());

			} catch (ExchangeException e) {
				// e. g. login failed, try again
				if (debug) e.printStackTrace();
			} catch (InterruptedIOException | SocketException e) {
				// wait for network
				// TODO we need API for immediately trying
				if (debug)
					e.printStackTrace();
				Utils.logi("reschedule syn-worker with error: %s", e.getMessage());
				reschedule(5);
			} catch (TransException | SQLException e) {
				// local errors, stop for fixing
				e.printStackTrace();
				stopScheduled(2);
			} catch (FileNotFoundException e) {
				// configuration errors
				if (debug) e.printStackTrace();
				Utils.warn("Configure Error: synode %s, user %s. Syn-worker is shutting down.\n"
						+ " (Tip: jserv url must inclue root path, e. g. /jserv-album)",
						domanager0.synode, domanager0.admin.uid());
				stopScheduled(2);
			} catch (AnsonException | SsException e) {
				if (debug) e.printStackTrace();
				Utils.warn("(Login | Configure) Error: synode %s, user %s. Syn-worker is shutting down.",
						domanager0.synode, domanager0.admin.uid());
				stopScheduled(2);
			} catch (Exception e) {
				// error 1: male format url
				e.printStackTrace();
				stopScheduled(2);
			} finally {
				running = false;
			}
		};

		scheduler = Executors.newSingleThreadScheduledExecutor(
				(r) -> new Thread(r, f("synworker-%s", synid)));
		schedualed = reschedule(0);

        running = false;
		return this;
	}

	private ScheduledFuture<?> reschedule(int waitmore) {
		syncInSnds = Math.min(maxSyncInSnds, syncInSnds + waitmore);
		return scheduler.scheduleWithFixedDelay(
				worker[0], (int) (syncInSnds * 1000), (int) (syncInSnds * 1000),
				TimeUnit.MILLISECONDS);
	}

	public void stopScheduled(int sTimeout) {
		Utils.logi("[%s] cancling sync-worker ... ", synid);
		schedualed.cancel(true);
		scheduler.shutdown();
		try {
		    if (!scheduler.awaitTermination(sTimeout, TimeUnit.SECONDS)) {
		        scheduler.shutdownNow();
		    }
		} catch (InterruptedException e) {
		    scheduler.shutdownNow();
		}
		finally { running = false; }
	}
}
