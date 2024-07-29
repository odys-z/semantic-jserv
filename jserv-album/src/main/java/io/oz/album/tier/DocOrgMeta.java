package io.oz.album.tier;

import java.sql.SQLException;

import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.meta.SynChangeMeta;
import io.odysz.semantic.meta.SyntityMeta;
import io.odysz.transact.x.TransException;

public class DocOrgMeta extends SyntityMeta {

	protected final String orgName;
	protected final String orgType;
	protected final String webroot;
	protected final String album0 ;

	public DocOrgMeta(String conn) {
		super("a_orgs", "orgId", "todo: synid", conn);

		this.pk = "orgId";
		this.orgName = "orgName";
		this.orgType = "orgType";
		this.webroot = "webroot";
		this.album0  = "album0";
	}

	@Override
	public Object[] insertSelectItems(SynChangeMeta chgm, String entid, AnResultset entities, AnResultset changes)
			throws TransException, SQLException {
		// TODO Auto-generated method stub
		return null;
	}

}
