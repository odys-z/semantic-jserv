package io.odysz.semantic.tier.docs;

import java.io.IOException;
import java.io.OutputStream;

import io.odysz.anson.AnsonException;
import io.odysz.anson.IJsonable;
import io.odysz.anson.JsonOpt;

/**
 * @since 1.5.16
 * File synchronizing task states are nothing about these flags.
 */
public enum ShareFlag implements IJsonable  {
	/** Kept as private file ('🔒') at private node. */
	prv("🔒"),
	
	/** 
	 * To be pushed (shared) to hub ('⇈')
	 * Temporary state. File synchronizing state is nothing about this.
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
	deny("⛔"),
	
	/** what's this for ? */
	unknown("⚠");

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
