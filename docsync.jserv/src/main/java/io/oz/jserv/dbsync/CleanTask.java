package io.oz.jserv.dbsync;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;

import io.odysz.anson.Anson;
import io.odysz.anson.x.AnsonException;
import io.odysz.jclient.SessionClient;
import io.odysz.jclient.tier.ErrorCtx;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.jprotocol.AnsonHeader;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
import io.odysz.semantics.IUser;
import io.odysz.transact.x.TransException;
import io.oz.jserv.docsync.SynState;
import io.oz.jserv.docsync.SynodeMode;

/**
 * A clean tasks page corresponding to a resource's cleaning tasks for ext-table.
 * <pre>
 * select synodee res from syn_clean where filter-window
 * group by tabl, synoder, clientpath
 * </pre>
 * @author odys-z@github.com
 *
 */
public class CleanTask extends Anson {

	ExtableMeta met;
	CleantablMeta syn_clean;

	/** entity category */
	String entabl;

	String synoder;

	String clientpath;

	ArrayList<String> synodees;

	HashMap<String, SynState> res2clean;

	ArrayList<String> deletings;
	ArrayList<String> rejects;
	ArrayList<String> erasings;
	ArrayList<String> closings;

	DATranscxt st;
	String conn;
	IUser robot;

	final String uri;
	
	public CleanTask(String uri) {
		this.uri = uri;
	}
	
	/**
	 * Load cleaning page from local DB.syn_clean.
	 * 
	 * @param st defualt tansact context instance to local DB.
	 * @param conn 
	 * @param usr 
	 * @param uri 
	 * @return this
	 * @throws SQLException 
	 * @throws TransException 
	 */
 	public CleanTask loadLocal(DATranscxt st, String conn, IUser usr, SynodeMode mod)
			throws TransException, SQLException {
		this.st = st;
		this.conn = conn;
		res2clean = this.st
				.select(met.tbl, "l")
				.whereEq(met.entabl, entabl)
				.whereEq(met.synoder, synoder)
				.whereEq(met.clientpath, clientpath)
				.rs(st.instancontxt(conn, usr))
				.<AnResultset, SynState>map((currow) -> {
					return new SynState(mod, currow.getString("flag"));
				});
		return this;
	}

	/**
	 * <pre>
	rec.flag | rep.syn  |
	---------+----------+---------------------------------------------
	R/E      | synode   |   -> req.reject/close (trigger -> NULL, check rep.NULL?)
	D        | synode   |
	D        | R/E      |   local rec.flag = R/E
	D        | NULL     | # local close 
	R        | E-dev    |   error (E can only recorded on devices)
	---------+----------+---------------------------------------------
	R        | NULL     | # local close  
	         |          |   User rejected and then erased at somewhere else
	         |          |   * This can only happen when a device fire multiple req.erase
	---------+----------+---------------------------------------------
	R        | D        |   -> req.reject
	E-dev    | D        |   -> req.erase (upper: D -> E, trigger -> NULL, check rep.NULL?)
	E-dev    | R        |   -> req.erase (upper: R -> E, trigger -> NULL, check rep.NULL?)
	E-dev    | E-dev    |
	E-dev    | NULL     | # local close 
	NULL     | syndoe   |   local delete
	NULL     | R/D      |   -> req.close
	 * </pre>
	 * @return
	 */
	public CleanTask merge() {
		if (this.closings != null) closings.clear();
		else closings = new ArrayList<String>();

		if (this.rejects != null) rejects.clear();
		else rejects = new ArrayList<String>();

		if (this.erasings != null) erasings.clear();
		else erasings = new ArrayList<String>();

		if (this.deletings != null) deletings.clear();
		else deletings = new ArrayList<String>();

		return this;
	}

	public CleanTask closeLocal(IUser usr)
			throws TransException, SQLException {
		this.robot = usr;
		if(closings != null) {
			st.delete(syn_clean.tabl, usr)
			  .whereIn(syn_clean.synodee, closings)
			  .d(st.instancontxt(conn, usr));
		}
		return this;
	}

	public CleanTask fireReqs(SessionClient client, ErrorCtx err)
			throws AnsonException, IOException, TransException, SQLException {
		DBSyncReq req = new DBSyncReq(uri, this)
				.mergeResults(deletings, rejects, erasings);

		String[] act = AnsonHeader.usrAct(uri, "db-sync", "u/clean", "push merged");
		AnsonHeader header = client.header().act(act);

		AnsonMsg<DBSyncReq> q = client
				.<DBSyncReq>userReq(uri, Port.dbsyncer, req)
				.header(header); 

		DBSyncResp resp = client.commit(q, err);
		closeByReply(resp);

		return closeLocal(robot);
	}

	void closeByReply(DBSyncResp resp) {
		// TODO Auto-generated method stub
		
	}

}
