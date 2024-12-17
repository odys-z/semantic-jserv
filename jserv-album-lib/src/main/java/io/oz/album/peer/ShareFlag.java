package io.oz.album.peer;

import java.io.IOException;
import java.io.OutputStream;

import io.odysz.anson.IJsonable;
import io.odysz.anson.JsonOpt;
import io.odysz.anson.x.AnsonException;

/**
 * @since 0.5.5
 */
public enum ShareFlag implements IJsonable  {
	/** Kept as private file ('🔒') at private node. */
	prv("🔒"),
	
	/** 
	 * To be pushed (shared) to hub ('⇈')
	 * @deprecated confusing with synchronizing state.
	 */
	pushing("⇈"),


	/** synchronized (shared) with a synode ('🌎') */
	publish("🌎"),
	
	/**created at a device (client) node ('📱') */
	device("📱"),
	
	/**
	 * The doc is locally removed, and the task is waiting to push to a synode ('Ⓛ')
	 * @deprecated confusing with synchronizing state.
	 */
	loc_remove("Ⓛ"),

	/** what's this for ? */
	deny("⛔");
	
	final String v;
	// public String name() { return v; }
	ShareFlag(String f) { v = f; }

	@Override
	public IJsonable toBlock(OutputStream stream, JsonOpt... opts) throws AnsonException, IOException {
		stream.write('\"');
		stream.write(name().getBytes());
		stream.write('\"');
		return this;
	}

	@Override
	public IJsonable toJson(StringBuffer buf) throws IOException, AnsonException {
		buf.append('\"');
		buf.append(name());
		buf.append('\"');
		return this;
	}	
}
