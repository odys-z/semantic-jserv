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
	/** kept as private file ('🔒') at private node.
	 * TODO rename as jnode */
	public static final String priv = "🔒";

	/** to be pushed (shared) to hub ('⇈')
	 * <p>This is a temporary state and is handled the same as the {@link #priv}
	 * for {@link io.odysz.semantic.tier.docs.SyncDoc SyncDoc}'s state.
	 * The only difference is the UI and broken link handling.
	 * It's complicate but nothing about FSM.</p> */
	public static final String pushing = "⇈";

	/**
	 * synchronized (shared) with hub ('🌎')
	 * @deprecated At positive-sync branch, now sync-state is orthogonal to sharing privilege.
	 * */
	public static final String publish = "🌎";
	/**created at cloud hub ('✩') by both client and jnode pushing, */
	public static final String hub = "✩";
	
	//////////////// branch positive-sync /////
	/**created at a device (client) node ('📱') */
	public static final String device = "📱";
	/**The doc is removed, and this record is a propagating record for
	 * worldwide synchronizing ('Ⓓ')*/
	public static final String deleting = "Ⓓ";
	/**The doc is locally removed, and the task is waiting to push to a jnode ('Ⓛ') */
	public static final String loc_remove = "Ⓛ";
	/**The deleting task is denied by a device ('ⓧ')*/
	public static final String del_deny = "ⓧ";
	/** hub buffering expired or finished ('Ⓒ') */
	public static final String close = "Ⓒ";
	//////////////// branch positive-sync /////

	/** This state can not present in database */ 
	public static final String end = "";
	
	public static enum SyncAction {
		
	}
	
	public static enum SyncEvent {
		create,
		/** Start pushing by a jnode or a device. */
		push,
		/** A jnode pushing to hub ended or a device is notified by a jnode. */ 
		pushubEnd,
		/** A device pushing to jnode ended. */ 
		pushJnodend,
		/** A jnode pulling finished */
		jnodePull,
		/** device pulling finished */
		devPull,
		/** published at hub
		 * @deprecated
		 * */
		publish,
		/**@deprecated deleting a doc worldwidely */
		deleting,
		/**@deprecated removed a local doc */
		loc_remove,
		close, hide
	};
	
	/**
	 * @deprecated at branch positive-sync, this method is replaced by SyncState#to() 
	 * @param now current state (SyncFlag constants)
	 * @param e
	 * @param share either {@link Share#pub} or {@link Share#priv}.
	 * @return next state
	 */
	public static String to(String now, SyncEvent e, String share) {
		if (priv.equals(now) || pushing.equals(now)) {
			if (e == SyncEvent.pushubEnd && Share.isPub(share))
				return publish;
			else if (e == SyncEvent.pushubEnd && Share.isPriv(share))
				return hub;
		}
		else if (publish.equals(now)) {
			if (e == SyncEvent.close)
				return close;
			else if (e == SyncEvent.hide)
				return hub;
			else if (e == SyncEvent.jnodePull)
				return priv;
		}
		else if (hub.equals(now)) {
			if (e == SyncEvent.close)
				return close;
			else if (e == SyncEvent.publish)
				return publish;
			else if (e == SyncEvent.jnodePull)
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