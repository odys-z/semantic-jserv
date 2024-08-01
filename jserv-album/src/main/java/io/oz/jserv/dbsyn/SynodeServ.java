package io.oz.jserv.dbsyn;

import static io.odysz.common.LangExt.isblank;

import io.odysz.common.Configs;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.meta.SynodeMeta;
import io.odysz.semantic.syn.DBSyntableBuilder;
import io.odysz.semantic.syn.Exchanging;
import io.odysz.semantic.syn.SynodeMode;
import io.odysz.semantic.util.DAHelper;
import io.odysz.semantics.x.ExchangeException;

public class SynodeServ {

	/** Key in config.xml for synchronizing connection: "synode.conn". */
	public static final String synconnId = "synode.conn";
	/**
	 * Key in config.xml for initial synchronizing domain: "synode.domain.0".
	 * This configuration value is overriden by user's configuration command,
	 * e.g. merge into an existing domain.
	 */
	public static final String syndom0   = "synode.domain.0";

	DBSyntableBuilder trb;

	public SynodeServ(String node, SynodeMode mod) throws Exception {
		String conn = Configs.getCfg(synconnId);

		SynodeMeta synm = new SynodeMeta(conn);
		String domain = DAHelper.getValstr(DATranscxt.getBasicTrans(conn), conn, synm, synm.domain, synm.pk, node);
		if (isblank(domain))
			domain = Configs.getCfg(syndom0);
		
		if (isblank(domain))
			throw new ExchangeException(Exchanging.init, null, "The domain value is not configured.");
		

		trb = new DBSyntableBuilder("omni", conn, node, mod)
			  .loadNyquvect(conn);
		
	}
	
	public SynodeServ start(String domain) {
		return this;
	}

	public boolean shutdown() {
		return true;
	}
}
