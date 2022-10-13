package io.oz.jserv.sync;

import io.odysz.semantic.ext.DocTableMeta.Share;
import io.odysz.semantics.x.SemanticException;
import io.oz.jserv.sync.SyncWorker.SyncMode;

/**
 * <img src='sync-states.png'/>
 * @author odys-z@github.com
 *
 */
public final class SyncFlag {
	/**
	 * created at cloud hub ('✩') by client (not jnode),
	 * and to be synchronized by private / main jnode.
	 * 
	 * <p> pub &amp; {@link #hubInit} -- [sync request] --&gt; {@link #publish}
	 * <p> prv &amp; {@link #hubInit} -- [sync request] --&gt; null
	 */
	public static final String hubInit = "✩";
	/**
	 * <p>kept as private file ('🔒')</p>
	 * If the jnode is working on hub mode, the file record can be removed later
	 * according expire and storage limitation. 
	 */
	public static final String priv = "🔒";
	/**
	 * to be pushed (shared) to hub ('⇈')
	 */
	public static final String pushing = "⇈";

	/**
	 * synchronized (shared) with hub ('🌎')
	 */
	public static final String publish = "🌎";
	
	/** This state can not present in database */ 
	public static final String end = "";
	
	
	public static enum SyncEvent { push, pushEnd, pull, close, hide };
	
	/**
	 * @param now current state, one of {@link #publish}, {@link #priv}, {@link #hubInit}, {@link #pushing} and {@link #end}.
	 * @param e
	 * @param share either {@link Share#pub} or {@link Share#priv}.
	 * @return next state
	 */
	public static String to(String now, SyncEvent e, String share) {
		if (priv.equals(now)) {
			if (e == SyncEvent.push)
				return pushing;
		}
		else if (pushing.equals(now)) {
			if (e == SyncEvent.pushEnd && Share.isPub(share))
				return pushing;
			else if (e == SyncEvent.pushEnd && Share.isPriv(share))
				return hubInit;
		}
		else if (publish.equals(now)) {
			if (e == SyncEvent.close && Share.isPub(share))
				return end;
			else if (e == SyncEvent.hide && Share.isPriv(share))
				return hubInit;
		}
		else if (hubInit.equals(now)) {
			if (e == SyncEvent.pull && Share.isPub(share))
				return end;
			else if (e == SyncEvent.pull && Share.isPriv(share))
				return hubInit;
		}
		return now;
	}

	public static String start(SyncMode mode) throws SemanticException {
			if (SyncMode.hub == mode)
				return hubInit;
			else if (SyncMode.priv == mode || SyncMode.main == mode)
				return priv;
			throw new SemanticException("Unhandled state transition.");
	}
}