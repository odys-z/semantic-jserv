package io.oz.jserv.docs.syn;

import static io.odysz.common.LangExt.eq;
import static io.odysz.common.LangExt.isNull;
import static io.odysz.common.LangExt.mustlt;
import static io.odysz.common.LangExt.mustnoBlankAny;
import static io.odysz.common.Utils.logT;
import static io.oz.syn.ExessionAct.close;
import static io.oz.syn.ExessionAct.deny;

import java.io.IOException;
import java.sql.SQLException;

import io.odysz.anson.AnsonException;
import io.odysz.common.Utils;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantics.x.ExchangeException;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.x.TransException;
import io.oz.jserv.docs.syn.SyncReq.A;
import io.oz.jserv.docs.syn.singleton.AppSettings;
import io.oz.jserv.docs.syn.singleton.Syngleton;
import io.oz.syn.DBSyntableBuilder;
import io.oz.syn.ExchangeBlock;
import io.oz.syn.registry.SynodeConfig;

/**
 * The testing version of {@link SynDomanager}.
 * 
 * Enable {@link #T_SynDomanager(Syngleton, SynodeConfig, AppSettings, boolean)}'s break0-4
 * to test broken exchange resuming.
 */
public class T_SynDomanager extends SynDomanager {
	int emulate_break0_4;
	/**
	 * [0: ending, 1: init, 2..4: following exchanges]
	 */
	final boolean[] breakpoints;

	OnDomainUpdate onDomUpdate;

	public T_SynDomanager(Syngleton syngleton, SynodeConfig c, AppSettings s, boolean break0_4)
			throws Exception {
		super(syngleton, c, s);
		emulate_break0_4 = break0_4 ? 0 : -1;
		breakpoints = new boolean[5];
	}
	
	public T_SynDomanager domUpdater(OnDomainUpdate upd) {
		this.onDomUpdate = upd;
		return this;
	}

	@Override
	public SynDomanager synUpdateDomain(SynssionPeer peer, OnDomainUpdate... onok)
			throws AnsonException, SsException, IOException, TransException  {
		if (emulate_break0_4 >= 0) {
			logT(new Object(){}, "-X-X- [%s <- %s] break -X-X-", peer.peer, synode);
			emulate_break0_4 = synUpdateDomx_break(peer, emulate_break0_4);
			
			// if (onDomUpdate != null) onDomUpdate.ok(domain(), synode, peer.peer);
			return this;
		}
		else return super.synUpdateDomain(peer, (d, s, per, persist) -> {
			if (!isNull(onok))
				onok[0].ok(d, s, per, persist);
			if (onDomUpdate != null)
				onDomUpdate.ok(d, s, per, persist);
		});
	}

	/**
	 * Similar to {@link SynssionPeer#synwith_peer(OnMutexLock)}, but break at breakpoint.
	 * @param c the Syn-session client
	 * @param breakpoint
	 * @return next breakpoint
	 * @throws SemanticException
	 * @throws AnsonException
	 * @throws SsException
	 * @throws IOException
	 * @throws TransException
	 */
	private int synUpdateDomx_break(SynssionPeer c, int breakpoint)
			throws SemanticException, AnsonException, SsException, IOException, TransException {

		if (!eq(c.peer, synode)) {
			c.checkLogin(admin);
			// peer.synwith_peer((lockby) -> Math.random());

			mustnoBlankAny(c, domain());

			try {
				Utils.logi("Locking and starting thread on domain updating: %s : %s -> %s"
						+ "\n=============================================================\n",
						domain(), synode, c.peer);

				lockme((u)-> Math.random());

				ExchangeBlock reqb = c.exesrestore();
				SyncResp rep = null;
				if (reqb != null) {
					rep = c.ex_lockpeer(c.peer, A.exrestore, reqb, (lockby) -> Math.random());
				}
				else {
					reqb = c.exesinit();
					rep = c.ex_lockpeer(c.peer, A.exinit, reqb, (lockby) -> Math.random());

					if (rep.exblock != null && rep.exblock.synact() != deny) 
						c.onsyninitRep(rep.exblock, rep.domain);
				}
//				if (breakpoint == 0) {
//					breakpoints[1] = true;
//					return breakpoint + 1; // 1
//				}
				
				int exchanges = 0;
				while (rep.synact() != close) {
					ExchangeBlock exb = c.syncdb(rep.exblock);
					rep = c.exespush(c.peer, A.exchange, exb);
					if (rep == null)
						throw new ExchangeException(exb.synact(), c.xp,
								"Got null reply for exchange session. %s : %s -> %s",
								domain(), synode, c);
					
					if (breakpoint == exchanges) {
						mustlt(exchanges + 1, breakpoints.length);
						breakpoints[exchanges + 1] = true;
						return breakpoint + 1; // 2, 3, 4
					}
					else ++exchanges;
				}
				
				while (c.xp.hasNextChpages(c.xp.trb)) {
					ExchangeBlock exb = c.syncdb(rep.exblock);
					rep = c.exespush(c.peer, A.exchange, exb);
				}
				
				// close
				reqb = c.synclose(rep.exblock);

				if (!breakpoints[0]) {
					breakpoints[0] = true;
					return -1; 
				}

				rep = c.exespush(c.peer, A.exclose, reqb);
				
				if (!SynssionPeer.testDisableAutoDocRef) {
					DBSyntableBuilder tb = new DBSyntableBuilder(this);
					c.resolveRef206Stream(tb);
					c.pushDocRef2me(tb, c.peer);
				}
				else
					Utils.warn("[%s : %s - SynssionPeer] Update to peer %s, auto-resolving doc-refs is disabled.",
							synode, domain(), c.peer);

			} catch (TransException | SQLException e) {
				e.printStackTrace();
			} catch (Exception e) {
				e.printStackTrace();
			}
			finally { unlockme(); }
		}
		return breakpoint;
	}

	@Override
	public boolean enableRegistryClient() {
		return false;
	}
}
