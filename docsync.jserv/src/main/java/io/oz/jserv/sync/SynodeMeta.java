package io.oz.jserv.sync;

import io.odysz.semantics.meta.TableMeta;

public class SynodeMeta extends TableMeta {

	public final String org;

	public SynodeMeta(String... conn) {
		super("a_synodes", conn);
		
		this.org = "orgId";
	}

}
