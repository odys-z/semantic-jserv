package io.oz.jserv.docs.syn;

import static io.odysz.common.LangExt.eq;
import static io.odysz.common.LangExt.isblank;
import static io.oz.syn.ExessionAct.init;
import static io.oz.syn.ExessionAct.mode_server;

import java.sql.SQLException;

import io.odysz.semantics.x.ExchangeException;
import io.odysz.transact.x.TransException;
import io.oz.syn.DBSyntableBuilder;
import io.oz.syn.ExchangeBlock;
import io.oz.syn.ExessionAct;
import io.oz.syn.ExessionPersist;
import io.oz.syn.SyncUser;

public class SynssionServ {
	final String peer;
	final SynDomanager syndomxerv;
	final SyncUser usr;

	public boolean debug;

	ExessionPersist srvp;

	public SynssionServ(SynDomanager domx, String peer, SyncUser usr) throws Exception {

		DBSyntableBuilder admb = new DBSyntableBuilder(domx);
		srvp = new ExessionPersist(admb, peer);

		this.peer = srvp.peer();
		this.syndomxerv = domx;
		this.usr = usr;

		usr.synssion(this);
	}

	public SyncResp onsyninit(ExchangeBlock ini) throws Exception {
		try {
			if (!syndomxerv.relockx(usr))
				return trylater(peer);

			if (!eq(ini.srcnode, peer))
				throw new ExchangeException(init, null,
					"Request.srcnode(%s) != peer (%s)", ini.srcnode, peer);

			DBSyntableBuilder b0 = new DBSyntableBuilder(syndomxerv);
			srvp = new ExessionPersist(b0, peer, ini);

			b0.fix_closexchange(srvp, ini);
			ExchangeBlock b = b0.onInit(srvp, ini);

			return new SyncResp(syndomxerv.domain()).exblock(b);
		} catch (Exception e) {
			syndomxerv.unlockx(usr);
			throw new ExchangeException(init, null, e.getMessage());
		}
	}

	SyncResp onsynclose(ExchangeBlock reqb)
			throws TransException, SQLException {
		ExchangeBlock b = srvp.trb.onclosexchange(srvp, reqb);
		return new SyncResp(syndomxerv.domain()).exblock(b);
	}

	public SyncResp onsyncdb(ExchangeBlock reqb)
			throws SQLException, TransException {
		ExchangeBlock repb = srvp.nextExchange(reqb);
		return new SyncResp(syndomxerv.domain()).exblock(repb);
	}

	/**
	 * On restoring requests. The requesting / challenging Seq can be already answered or not yet.
	 * @param reqb
	 * @return reply
	 * @throws SQLException
	 * @throws TransException
	 * @since 1.5.18
	 */
	public SyncResp onsynrestore(ExchangeBlock reqb)
			throws SQLException, TransException {

		if (!syndomxerv.relockx(usr))
			return trylater(peer);

		srvp.loadsession(reqb.srcnode);

		// to be continued, calling nextExchange instead of onRestore()
		ExchangeBlock repb = srvp.onRestore(reqb);
		if (repb == null) repb = srvp.nextExchange(reqb);
		return new SyncResp(syndomxerv.domain()).exblock(repb);
	}

	public SyncResp onclosex(SyncReq req, SyncUser usr) throws TransException, SQLException {
		
		if (!eq(syndomxerv.lockSession(), usr.sessionId()))
			return lockerr(peer);
		else {
			try { return onsynclose(req.exblock); }
			finally { syndomxerv.unlockx(usr); usr.synssion = null; }
		}
	}

	public SyncResp onclosejoin(SyncReq req, SyncUser usr)
			throws TransException, SQLException {

		try {
			ExchangeBlock ack  = srvp.trb.domainCloseJoin(srvp, req.exblock);
			return new SyncResp(syndomxerv.domain()).exblock(ack);
		} finally {
			syndomxerv.closedServ = this;
			syndomxerv.unlockx(usr);
		}
	}
	
	private SyncResp lockerr(String peer) {
		return new SyncResp(syndomxerv.domain()).exblock(
				new ExchangeBlock(syndomxerv.domain(), syndomxerv.synode, peer, null,
				new ExessionAct(mode_server, ExessionAct.lockerr)));
	}

	/**
	 * 
	 * @param req
	 * @return response
	 * @throws ExchangeException synode or synssion user.org invalid
	 */
	public SyncResp onjoin(SyncReq req) throws Exception {

		String peer = req.exblock.srcnode;
		
		if (eq(peer, syndomxerv.synode))
			throw new ExchangeException(init, null,
					"Can't join by same synode id: %s.",
					syndomxerv.synode);

		if (isblank(usr.orgId()))
			throw new ExchangeException(init, null,
				"Client syn-user's org id must not be null to join this domain: %s, user: %s.",
				syndomxerv.domain(), usr.uid());
		
		try {
			if (syndomxerv.lockx(usr))  {
				DBSyntableBuilder admb = new DBSyntableBuilder(syndomxerv);
				ExessionPersist admp = new ExessionPersist(admb, peer);
				ExchangeBlock resp = admb.domainOnAdd(admp, req.exblock, usr.orgId());

				return new SyncResp(syndomxerv.domain()).exblock(resp);
			}
			else return trylater(peer);
		} catch (Exception e) {
			syndomxerv.unlockx(usr);
			throw e;
		}
	}
	
	private SyncResp trylater(String peer) {
		return new SyncResp(syndomxerv.domain()).exblock(
				new ExchangeBlock(syndomxerv.domain(), syndomxerv.synode, peer, null,
				new ExessionAct(mode_server, ExessionAct.trylater))
				.sleep(Math.random() + 0.1));

	}
}
