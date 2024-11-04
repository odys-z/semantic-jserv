package io.oz.jserv.docs.syn;

import static io.odysz.common.LangExt.eq;
import static io.odysz.semantic.syn.ExessionAct.init;

import java.sql.SQLException;

import io.odysz.semantic.syn.DBSyntableBuilder;
import io.odysz.semantic.syn.ExchangeBlock;
import io.odysz.semantic.syn.ExessionPersist;
import io.odysz.semantic.syn.SyncUser;
import io.odysz.semantics.x.ExchangeException;
import io.odysz.transact.x.TransException;

public class SynssionServ {
	final String peer;
	final SynDomanager syndomxer;
	final SyncUser usr;

	public boolean debug;

	ExessionPersist srvp;

	public SynssionServ(SynDomanager syndomanager, SyncUser usr, String peer, boolean debug) {
		this.syndomxer = syndomanager;
		this.peer  = peer;
		this.usr   = usr;
		this.debug = debug;
	}

	public SynssionServ(SynDomanager syndomanager, SyncUser usr) {
		this.srvp = usr.xp;
		this.peer = usr.xp.peer();
		this.syndomxer = syndomanager;
		this.usr = usr;
	}

	public SyncResp onsyninit(ExchangeBlock ini, String domain) throws Exception {
		if (!eq(ini.srcnode, peer))
			throw new ExchangeException(init, null, "Request.srcnode(%s) != peer (%s)", ini.srcnode, peer);


		DBSyntableBuilder b0 = new DBSyntableBuilder(syndomxer);
		srvp = new ExessionPersist(b0, peer, ini);
		ExchangeBlock b = b0.onInit(srvp, ini);
		usr.servPersist(srvp);

		return new SyncResp(syndomxer.domain()).exblock(b);
	}

	SyncResp onsynclose(ExchangeBlock reqb)
			throws TransException, SQLException {
		ExchangeBlock b = srvp.trb.onclosexchange(srvp, reqb);
		return new SyncResp(syndomxer.domain()).exblock(b);
	}

	public ExchangeBlock onsyncdb(ExchangeBlock reqb)
			throws SQLException, TransException {
		ExchangeBlock repb = srvp.nextExchange(reqb);
		return repb;
	}
}
