package io.oz.jserv.sync;

import io.odysz.semantic.ext.DocTableMeta.Share;
import io.odysz.semantics.x.SemanticException;
import io.oz.jserv.sync.SyncWorker.SyncMode;

/**
 * <img src='sync-states.jpg'/>
 * @author odys-z@github.com
 *
 */
public final class SyncFlag {
	/** kept as private file ('🔒') at private node. */
	public static final String priv = "🔒";

	//** to be pushed (shared) to hub ('⇈') */
	// public static final String pushing = "⇈";

	/** synchronized (shared) with hub ('🌎') */
	public static final String publish = "🌎";
	/**created at cloud hub ('✩') by both client and jnode pushing, */
	public static final String hub = "✩";
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
		if (priv.equals(now)) {
//			if (e == SyncEvent.push)
//				return pushing;
//		}
//		else if (pushing.equals(now)) {
			if (e == SyncEvent.pushEnd && Share.isPub(share))
				return publish;
			else if (e == SyncEvent.pushEnd && Share.isPriv(share))
				return hub;
		}
		else if (publish.equals(now)) {
			if (e == SyncEvent.close)
				return end;
			else if (e == SyncEvent.hide)
				return hub;
			else if (e == SyncEvent.pull)
				return priv;
		}
		else if (hub.equals(now)) {
			if (e == SyncEvent.close && Share.isPub(share))
				return end;
			else if (e == SyncEvent.publish)
				return publish;
			else if (e == SyncEvent.pull)
				return priv;
		}
		return now;
	}

	public static String start(SyncMode mode, String share) throws SemanticException {
			if (SyncMode.hub == mode)
				return Share.isPub(share) ? publish : hub;
			else if (SyncMode.priv == mode || SyncMode.main == mode)
				return priv;
			throw new SemanticException("Unhandled state transition.");
	}
}