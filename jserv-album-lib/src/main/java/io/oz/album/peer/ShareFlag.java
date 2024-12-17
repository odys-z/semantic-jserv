package io.oz.album.peer;

/**
 * @since 0.5.5
 */
public class ShareFlag {
	/** Kept as private file ('🔒') at private node. */
	public static final String priv = "🔒";

	/** 
	 * To be pushed (shared) to hub ('⇈')
	 * @deprecated confusing with synchronizing state.
	 */
	public static final String pushing = "⇈";

	/**
	 * synchronized (shared) with a synode ('🌎')
	 */
	public static final String publish = "🌎";
	
	/**created at a device (client) node ('📱') */
	public static final String device = "📱";
	
	/**
	 * The doc is locally removed, and the task is waiting to push to a synode ('Ⓛ')
	 * @deprecated confusing with synchronizing state.
	 */
	public static final String loc_remove = "Ⓛ";

	/** what's this for ? */
	public static final String deny = "⛔";
}
