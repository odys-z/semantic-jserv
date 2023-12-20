package io.oz.album.tier;

import io.odysz.semantics.meta.TreeTableMeta;

public class DocOrgMeta extends TreeTableMeta {

	protected final String orgName;
	protected final String orgType;
	protected final String webroot;
	protected final String album0 ;

	public DocOrgMeta(String... conn) {
		super("a_orgs", "orgId", "parent", conn);

		this.pk = "orgId";
		this.orgName = "orgName";
		this.orgType = "orgType";
		this.webroot = "webroot";
		this.album0  = "album0";
	}

}
