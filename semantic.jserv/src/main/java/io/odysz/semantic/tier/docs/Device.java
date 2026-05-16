package io.odysz.semantic.tier.docs;

import static io.odysz.common.LangExt.isNull;

import io.odysz.anson.Anson;
import io.odysz.anson.AnsonCtor;

public class Device extends Anson {
	public String id;
	public String synode0;
	public String devname;
	public String tofolder;

	public Device() {
		this(null, null);
	}

	@AnsonCtor(base={}, initialist={"string id = id", "string synode0 = synode0", "string devname = end"})
	public Device(String id, String synode0, String... devname) {
		super();
		this.id = id;
		this.synode0 = synode0;
		this.devname = isNull(devname) ? null : devname[0];
	}
	
	public Device folder(String f) {
		tofolder = f;
		return this;
	}
}
