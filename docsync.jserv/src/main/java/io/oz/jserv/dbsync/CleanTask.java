package io.oz.jserv.dbsync;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;

import io.odysz.anson.Anson;
import io.odysz.anson.AnsonField;
import io.odysz.anson.x.AnsonException;
import io.odysz.jclient.SessionClient;
import io.odysz.jclient.tier.ErrorCtx;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.ext.DocTableMeta;
import io.odysz.semantic.jprotocol.AnsonHeader;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
import io.odysz.semantics.IUser;
import io.odysz.transact.x.TransException;
import io.oz.jserv.docsync.SynodeMode;

// import static io.odysz.common.LangExt.isblank;

/**
 * A clean tasks page corresponding to an entity's cleaning tasks for ext-table.
 * <pre>
 * select synodee res from syn_clean where filter-window
 * group by tabl, synoder, clientpath
 * </pre>
 * @author odys-z@github.com
 *
 */
public class CleanTask extends Anson {

	DocTableMeta met;
	CleantablMeta syn_clean;

	/** entity category */
	String entabl;

	String synoder;

	String clientpath;
	
	/** Clean targets [[synodee, syn-flag]] */
	ArrayList<CleanState> synodees;

	/** Local entity records (with external resources) to be cleaned. */
	@AnsonField(ignoreTo=true, ignoreFrom=true)
	HashMap<String, CleanState> res2clean;

	ArrayList<String> deletes;

	ArrayList<String> rejects;

	ArrayList<String> erasings;

	@AnsonField(ignoreTo=true, ignoreFrom=true)
	ArrayList<String> loclosings;

	DATranscxt st;
	String conn;
	IUser robot;

	final String uri;

	/**
	 * <p>Synodees for local NULL and resp.syn = R/D/E, which means,
	 * if the entity.syncflag != 'delete', then cleaning tasks must be
	 * checked again after the entity table is synchronized.
	 */
	ArrayList<String> winChecks;
	
	public CleanTask(String uri) {
		this.uri = uri;
	}
	
	/**
	 * Load cleaning page from local DB.syn_clean.
	 * 
	 * <p>output:</p>
	 * {@link #res2clean}
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
				.select(met.tbl, "e")
				.j(syn_clean.tbl, "c", String.format("e.%s = c.%s and e.%s = c.%s",
						met.synoder, syn_clean.synoder, met.fullpath, syn_clean.clientpath))
				.col("c", syn_clean.synodee, "synodee")
				.col("c", syn_clean.flag, "syn")
				.col("e", met.syncflag, "entag")
				.whereEq(met.tbl, entabl)
				.whereEq(met.synoder, synoder)
				.whereEq(met.fullpath, clientpath)
				.rs(st.instancontxt(conn, usr))
				.<AnResultset, CleanState>map("synodee", (currow) -> {
					return new CleanState(
							currow.getString("synodee"),
							currow.getString("syn"),
							currow.getString("entag"));
				});
		return this;
	}

	/**
	 * <pre>
	rec.flag| rep.syn |
	--------+---------+---------------------------------------------
	E-dev   | NULL    |  local close [1]
	D       | NULL    |  local close [1]
	R       | NULL    |  local close [1]
	        |         |   User rejected and then erased at somewhere else
	        |         |   * This can only happen when a device fire multiple req.erase;
	        |         |  If entity.syncflag != 'delete', error
	--------+---------+---------------------------------------------
	x R/E   | synode? |   -> req.reject/close (trigger -> NULL, check rep.NULL?)
	x D     | synode? |
	x NULL  | syndoe? |   local delete ?  - req.close?
	--------+---------+---------------------------------------------
	D       | R/E     |   local rec.flag = R/E
	R       | D       |   -> req.reject
	R       | E-dev   |   error (E can only recorded on devices)
	--------+---------+---------------------------------------------
	E-dev   | D       |   -> req.erase (upper: D -> E, trigger -> NULL, check rep.NULL?)
	E-dev   | R       |   -> req.erase (upper: R -> E, trigger -> NULL, check rep.NULL?)
	E-dev   | E-dev   |
	--------+---------+---------------------------------------------
	NULL    | R/D/E   |  local insert, entity.syncflag = syn.flag
			|         |  * in case of entity.syncflag != 'delete': 
			|         |     table synchronizing required, window can not be moved forward
	
	[1] Impossible for the first request as this synodee at least must exists 
	 * </pre>
	 * @return this
	 */
	public CleanTask merge() {
		if (this.loclosings != null) loclosings.clear();
		else loclosings = new ArrayList<String>();

		if (this.rejects != null) rejects.clear();
		else rejects = new ArrayList<String>();

		if (this.erasings != null) erasings.clear();
		else erasings = new ArrayList<String>();

		if (this.deletes != null) deletes.clear();
		else deletes = new ArrayList<String>();

		for (CleanState remote : synodees ) {
			CleanState loc = res2clean.remove(remote.synodee);
			if (loc == null) {
				// NULL - R/D/E
				if (winChecks == null)
					winChecks = new ArrayList<String>();
				winChecks.add(remote.synodee);
			}
			else if (loc.is(CleanState.E)) {
				// E  -  D/R/E
				if (!loc.is(CleanState.E))
					erasings.add(loc.synodee);
			}
		}

		if (res2clean.size() > 0) {
			// Now res2cleans is that of rep.syn is NULL
			// - entity.sync != 'delete' shouldn't happen in this round
			for (CleanState close : res2clean.values())
				loclosings.add(close.synodee);
		}
		
		return this;
	}


	public boolean checkEntities() {
		winChecks;
		return this;
	}

	/**
	 * Close local entity (delete permanently) if merge results required.
	 * 
	 * @param usr
	 * @return this
	 * @throws TransException
	 * @throws SQLException
	 */
	public CleanTask closeLocal(IUser usr)
			throws TransException, SQLException {
		this.robot = usr;
		if(loclosings != null) {
			st.delete(syn_clean.tabl, usr)
			  .whereEq(syn_clean.synoder, synoder)
			  .whereEq(syn_clean.clientpath, clientpath)
			  .whereIn(syn_clean.synodee, loclosings)
			  // FIXME trigger post entity deletion?
			  .d(st.instancontxt(conn, usr));
		}
		return this;
	}

	public CleanTask fileReqs(SessionClient client, ErrorCtx err)
			throws AnsonException, IOException, TransException, SQLException {
		DBSyncReq req = new DBSyncReq(uri, this)
				.mergeResults(deletes, rejects, erasings);

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
