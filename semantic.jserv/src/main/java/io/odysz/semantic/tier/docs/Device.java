package io.odysz.semantic.tier.docs;

import static io.odysz.common.LangExt.isNull;

import io.odysz.anson.Anson;

public class Device extends Anson {
	public Device() {
		super();
	}

	public Device(String id, String synode0, String... devname) {
		this.id = id;
		this.synode0 = synode0;
		this.devname = isNull(devname) ? null : devname[0];
	}
	public String id;
	public String synode0;
	public String devname;
}
