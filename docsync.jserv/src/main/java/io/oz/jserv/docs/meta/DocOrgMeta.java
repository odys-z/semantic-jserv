package io.oz.jserv.docs.meta;

import io.odysz.common.EnvPath;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.meta.SemanticTableMeta;
import io.odysz.semantics.IUser;
import io.odysz.semantics.SemanticObject;
import io.oz.syn.registry.SynodeConfig;

public class DocOrgMeta extends SemanticTableMeta {

	public final String orgName;
	public final String orgType;
	public final String market;
	public final String album0 ;

	/**
	 * The web server's address.
	 * This field is a synode id pointing to where the web server is also deployed.
	 * 
	 * @since 0.2.4 (Portfolio 0.7.1),
	 */
	public final String webNode;

	/** Is differnet from {@link #webNode} */
	public final String homepage;

	public DocOrgMeta(String conn) {
		super("a_orgs", "orgId", "todo: synid", conn);

		this.pk = "orgId";
		this.orgName = "orgName";
		this.orgType = "orgType";
		this.market  = "market";
		this.webNode = "webroot";
		this.homepage= "homepage";
		this.album0  = "album0";
	}

	public int generecord(SynodeConfig cfg) throws Exception {
		DATranscxt t = new DATranscxt(cfg.sysconn);
		IUser rob = DATranscxt.dummyUser();
		SemanticObject res = (SemanticObject) t.insert(tbl, rob)
				.nv(pk, cfg.org.orgId)
				.nv(orgName, cfg.org.orgName)
				.nv(orgType, cfg.org.orgType)
				.nv(webNode,  EnvPath.replaceEnv(cfg.org.webroot))
				.nv(homepage, cfg.org.homepage)
				.nv(album0,   cfg.org.album0)
				.nv(market,   cfg.org.market)
				.ins(t.instancontxt(cfg.sysconn, rob));
		return res.total();
	}

}
