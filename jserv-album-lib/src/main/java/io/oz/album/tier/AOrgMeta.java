package io.oz.album.tier;

import io.odysz.semantics.meta.TreeTableMeta;

public class AOrgMeta extends TreeTableMeta {

	protected final String orgName;
	protected final String orgType;
	protected final String webroot;

	public AOrgMeta(String... conn) {
		super("a_orgs", conn);

		this.pk = "orgId";
		this.orgName = "orgName";
		this.orgType = "orgType";
		this.webroot = "webroot";
	}

}
