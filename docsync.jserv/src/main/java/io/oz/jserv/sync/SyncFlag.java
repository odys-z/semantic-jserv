package io.oz.jserv.sync;

import io.odysz.anson.Anson;
import io.odysz.semantic.ext.DocTableMeta.Share;
import io.odysz.semantics.x.SemanticException;

/**
 * <img src='sync-states.jpg'/>
 * @author odys-z@github.com
 *
 */
public final class SyncFlag extends Anson {
	/** kept as private file ('🔒') at private node. */
	public static final String priv = "🔒";

	/** to be pushed (shared) to hub ('⇈')
	 * <p>This is a temporary state and is handled the same as the {@link #priv}
	 * for {@link io.odysz.semantic.tier.docs.SyncDoc SyncDoc}'s state.
	 * The only difference is the UI and broken link handling.
	 * It's complicate but nothing about FSM.</p> */
	public static final String pushing = "⇈";

	/** synchronized (shared) with hub ('🌎') */
	public static final String publish = "🌎";
	/**created at cloud hub ('✩') by both client and jnode pushing, */
	public static final String hub = "✩";
	/** hub buffering expired or finished ('x') */
	public static final String close = "x";
	/** This state can not present in database */ 
	public static final String end = "";
	
	public static enum SyncEvent { create, push, pushEnd, pull, close, publish, hide };
	
	/**
	 * @param now current state (SyncFlag constants)
	 * @param e
	 * @param share either {@link Share#pub} or {@link Share#priv}.
	 * @return next state
	 */
	public static String to(String now, SyncEvent e, String share) {
		if (priv.equals(now) || pushing.equals(now)) {
			if (e == SyncEvent.pushEnd && Share.isPub(share))
				return publish;
			else if (e == SyncEvent.pushEnd && Share.isPriv(share))
				return hub;
		}
		else if (publish.equals(now)) {
			if (e == SyncEvent.close)
				return close;
			else if (e == SyncEvent.hide)
				return hub;
			else if (e == SyncEvent.pull)
				return priv;
		}
		else if (hub.equals(now)) {
			if (e == SyncEvent.close)
				return close;
			else if (e == SyncEvent.publish)
				return publish;
			else if (e == SyncEvent.pull)
				return priv;
		}
		return now;
	}

	public static String start(SynodeMode mode, String share) throws SemanticException {
			if (SynodeMode.hub == mode)
				return Share.isPub(share) ? publish : hub;
			else if (SynodeMode.priv == mode || SynodeMode.main == mode)
				return priv;
			throw new SemanticException("Unhandled state starting: mode %s : share %s.", mode, share);
	}
}