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
import java.sql.SQLException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletResponse;

import org.xml.sax.SAXException;

import io.odysz.common.Utils;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.jprotocol.AnsonBody;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
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
	 * @since 0.7.0
	 */
	public ExpSynodetier syncIn(float syncIns) {
		this.syncInSnds = syncIns;
		if ((int)(this.syncInSnds) <= 0)
			return this;

		worker[0] = () -> {
			try {
				if (running)
					return;
				running = true;

				if (debug)
				Utils.logi("[%s] : Checking Syndomain ...", synid);

				DATranscxt syntb = new DATranscxt(domanager0.synconn);

				if (len(this.domanager0.sessions) == 0) {

					this.domanager0.loadSynclients(syntb);

					try {
						// Memo: joining behaviour can impacting here
						if (this.domanager0.sessions.get(domain).client == null)
						this.domanager0
							// .loadSynclients(synt0)
							// .opendomain();
							.openSynssions(domanager0.admin);
					} catch (IOException e) {
						syncInSnds = Math.min(maxSyncInSnds, syncInSnds + 5);
						schedualed.cancel(false);
						schedualed = scheduler.scheduleWithFixedDelay(
								worker[0], (int) (syncInSnds * 1000), (int) (syncInSnds * 1000),
								TimeUnit.MILLISECONDS);
					} catch (Exception e) {
						// ISSUE
						// TODO FXIME we need overhaul the ServPort.err()
						// e.msg = Code: exSession, Message:\nCannot find user <id>
						// if (e.ex().toString() == MsgCode.exSession.name())
						// login denied

						schedualed.cancel(false);
						scheduler.shutdown();
						e.printStackTrace();
					}
				}

				if (len(this.domanager0.sessions) > 0)
				this.domanager0.updomain(
					(dom, synode, peer, xp) -> {
						if (debug) Utils.logi("[%s] On update: %s", synid, dom);
					},
					(synlocker) -> Math.random());
			} catch (FileNotFoundException e) {
				// v 0.2.0, something wrong in url, such as wrong configurations, and etc.
				schedualed.cancel(false);
				scheduler.shutdown();
				e.printStackTrace();
			} catch (IOException e) {
				syncInSnds = Math.min(maxSyncInSnds, syncInSnds + 5);
				schedualed.cancel(false);
				schedualed = scheduler.scheduleWithFixedDelay(
						worker[0], (int) (syncInSnds * 1000), (int) (syncInSnds * 1000),
						TimeUnit.MILLISECONDS);
			} catch (Exception e) {
				e.printStackTrace();
			}
			finally { running = false; }
		};

		scheduler = Executors.newSingleThreadScheduledExecutor(
				(r) -> new Thread(r, f("synworker-%s", synid)));

		// scheduler.submit(worker[0]);
        schedualed = scheduler.scheduleWithFixedDelay(
        		worker[0], 5000, (int)(syncInSnds * 1000), TimeUnit.MILLISECONDS);

        running = false;
		return this;
	}

	public void stopScheduled(int sDelay) {
		Utils.logi("[%s] cancling sync-worker ... ", synid);
		schedualed.cancel(true);
		scheduler.shutdown();
		try {
		    if (!scheduler.awaitTermination(sDelay, TimeUnit.SECONDS)) {
		        scheduler.shutdownNow();
		    }
		} catch (InterruptedException e) {
		    scheduler.shutdownNow();
		}
		finally { running = false; }
	}
}
