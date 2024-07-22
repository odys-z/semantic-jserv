package io.oz.jserv.docs.syn;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

import org.xml.sax.SAXException;

import io.odysz.anson.x.AnsonException;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
import io.odysz.semantic.jserv.ServPort;
import io.odysz.semantic.syn.SynodeMode;
import io.odysz.semantics.IUser;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.x.TransException;

public class Syntier extends ServPort<SyncReq> {
	/** {domain: {jserv: exession-persist}} */
	HashMap<String, Synoder> domains;

	public final DATranscxt doctrb;

	public final String myconn;

	public Syntier(String synoderId, String loconn)
			throws SemanticException, SQLException, SAXException, IOException {
		super(Port.dbsyncer);
		synode = synoderId;
		doctrb = new DATranscxt(loconn);
		myconn = loconn;
	}

	private static final long serialVersionUID = 1L;

	public static final int jservx = 0;
	public static final int myconx = 1;

	final String synode;

	protected Synodebot locrobot;
	public IUser locrobot() {
		if (locrobot == null)
			locrobot = new Synodebot(synode);
		return locrobot;
	}

	/** The domain id for client before joined a domain. */
	public static final String domain0 = "io.oz.jserv.syn.init";

	@Override
	protected void onGet(AnsonMsg<SyncReq> msg, HttpServletResponse resp)
			throws ServletException, IOException, AnsonException, SemanticException {
		// TODO Auto-generated method stub
	}

	@Override
	protected void onPost(AnsonMsg<SyncReq> msg, HttpServletResponse resp)
			throws ServletException, IOException, AnsonException, SemanticException {
		// TODO Auto-generated method stub
	}

	public Synoder start(String org, String domain, String conn, SynodeMode mod)
			throws SQLException, TransException, SAXException, IOException {
		if (domains == null)
			domains = new HashMap<String, Synoder>();
		if (!domains.containsKey(domain))
			domains.put(domain, new Synoder(org, domain, synode, conn, mod));

		return domains.get(domain).born(0, 0);
	}

	public Synoder synoder(String peer) {
		return domains.get(peer);
	}

	Synoder synssions(String domain) {
		return domains != null ? domains.get(domain) : null;
	}
}
