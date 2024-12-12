package io.oz.jserv.docs.meta;

import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.meta.SyntityMeta;
import io.odysz.semantics.IUser;
import io.odysz.semantics.SemanticObject;
import io.oz.syn.SynodeConfig;

public class DocOrgMeta extends SyntityMeta {

	public final String orgName;
	public final String orgType;
	public final String market;
	public final String webroot;
	public final String album0 ;

	public DocOrgMeta(String conn) {
		super("a_orgs", "orgId", "todo: synid", conn);

		this.pk = "orgId";
		this.orgName = "orgName";
		this.orgType = "orgType";
		this.market  = "market";
		this.webroot = "webroot";
		this.album0  = "album0";
	}

//	@Override
//	public Object[] insertSelectItems(SynChangeMeta chgm, String entid, AnResultset entities, AnResultset changes)
//			throws TransException, SQLException {
//		// TODO Auto-generated method stub
//		return null;
//	}

	public int generecord(SynodeConfig cfg) throws Exception {
		DATranscxt t = new DATranscxt(cfg.sysconn);
		IUser rob = DATranscxt.dummyUser();
		SemanticObject res = (SemanticObject) t.insert(tbl, rob)
				.nv(pk, cfg.org.orgId)
				.nv(orgName, cfg.org.orgName)
				.nv(orgType, cfg.org.orgType)
				.nv(webroot, cfg.org.webroot)
				.nv(album0, cfg.org.album0)
				.nv(market, cfg.org.market)
				.ins(t.instancontxt(cfg.sysconn, rob));
		return res.total();
	}

}
