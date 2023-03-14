package io.oz.jserv.dbsync;

import java.io.IOException;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.Date;
import java.util.HashMap;

import io.odysz.anson.x.AnsonException;
import io.odysz.common.Utils;
import io.odysz.jclient.Clients;
import io.odysz.jclient.SessionClient;
import io.odysz.jclient.tier.ErrorCtx;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantics.meta.TableMeta;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.sql.PageInf;
import io.odysz.transact.x.TransException;
import io.oz.jserv.docsync.SynState;
import io.oz.jserv.docsync.SyncFlag;
import io.oz.jserv.docsync.SyncRobot;
import io.oz.jserv.docsync.SynodeMode;

/**
 * Worker for db synchronization.
 * 
 * @author odys-z@github.com
 */
public class DBWorker implements Runnable {
	
	protected ErrorCtx err;
	protected DATranscxt localSt;

	/** synode id */
	String myId;

	/** upper synode id */
	String upperId;

	/** volume path */
	String volpath;

	String conn;

	protected SyncRobot robot;

	SynodeMode mode;
	
	TableMeta cleanMeta;

	private HashMap<String, TableMeta> entities;

	/** clean session timestamps */
	TimeWindow window; 

	SessionClient client;

	public DBWorker(String synode, SynodeMode m) {
		myId = synode;
		mode = m;
	}
	
	public DBWorker volume(String path) {
		volpath = path;
		return this;
	}
	
	public DBWorker login(String pswd)
			throws SemanticException, AnsonException, SsException, IOException {
		client = Clients.login(myId, pswd, myId);
		return this;
	}
	
	/**
	 * Merge syn_clean
	 * 
	 * @param upperNode
	 * @throws SQLException 
	 * @throws IOException 
	 * @throws AnsonException 
	 * @throws TransException 
	 */
	public void scanvage(String upperNode)
			throws SQLException, AnsonException, IOException, TransException {
		upperId = upperNode;
		
		// 1. Open a clean session by
		//    getting the filter: timestamp & last-stamp from upper synode,
		//    where last-stamp = upper.syn_stamp.stamp where tabl = 'syn_clean' & synode = me
		// (Can be an error if multiple upper nodes exist. Only 1 level of synode can working in offline?)
		//
		// 2. Request with time window filter
		// 3. Merge (reduce clean tasks)
		// 4. Close the clean session with timestamp
		
		window = openClean();
		
		meargeCleans(window);

		closeClean(window);
		
		for (String etbl : entities.keySet()) {
			syncExtabl(etbl, window);
		}
	}
	
	private TimeWindow openClean() {
		// TODO Auto-generated method stub
		return null;
	}

	private void meargeCleans(TimeWindow window)
			throws SQLException, SemanticException, AnsonException, IOException {
		DBSyncResp rpl = client.commit(null, err); // A = cleans
		AnResultset tasks = rpl.tasks().beforeFirst();
			
		// order by tabl, synoder, clientpath, synodee
		while (tasks.next()) {
			/*
			rec.flag | rep.syn  |
			---------+----------+---------------------------------------------
			D/R/E    | 'synode' |   -> req.delete/reject/close (trigger -> NULL, check rep.NULL?)
			D        | R/E      |   local rec.flag = R/E
			D        | NULL     | # local close 
			R        | E-dev    |   error (E can only recorded on devices)
			---------+----------+---------------------------------------------
			R        |(main/prv)|   User rejected and then erased at somewhere else
			         | NULL     |   * This only happens when a device fire multiple req.erase
			         |          | # local close
			---------+----------+---------------------------------------------
			R        | D            -> req.reject
			E-dev    | D            -> req.erase (upper: D -> E, trigger -> NULL, check rep.NULL?)
			E-dev    | R            -> req.erase (upper: R -> E, trigger -> NULL, check rep.NULL?)
			E-dev    | E-dev
			E-dev    | NULL       # local close 
			NULL     | R/D          -> req.close
			*/
		}
	}

	/**
	 * Close clean session.
	 * 
	 * @param window
	 */
	private void closeClean(TimeWindow window) {
		// TODO Auto-generated method stub
	}

	/**
	 * Synchronize a ext-resource table.
	 * Records in ex-resource is pushed / pulled one by one.
	 * @param entity
	 * @param wind 
	 * @throws IOException 
	 * @throws AnsonException 
	 * @throws SQLException 
	 * @throws TransException 
	 */
	private void syncExtabl(String entity, TimeWindow wind)
			throws AnsonException, IOException, SQLException, TransException {
		
		Date dbnow = localSt.now(conn);
		
		try {
			if (dbnow.compareTo(wind.right()) < 0)
				throw new SemanticException("Time difference is to large to synchronize");
			// otherwise users won't be able to feel it
		} catch (ParseException e) {
			e.printStackTrace();
			throw new SemanticException("Unable to findout time window.");
		}
		
		ExtableMeta m = (ExtableMeta) entities.get(entity); 

		// query 
		DBSyncResp resp = client.commit(DBSyncReq.extabl(entity, myId, wind), null);
		PageInf page = resp.syncPage();
		while (page != null && page.size > 0) {
			if (page.size < page.total)
				Utils.warn("Synchronizing pages are more than 1. This will result in error in concurrent service.");
			
			AnResultset rs = resp.rs(0).beforeFirst();

			while (rs.next()) {
				SynState sync = queryLocalExtabl(m, rs, wind);
				if (sync == null || sync.olderThan(rs.getDate(m.stamp)))
					pullExtrec(m, rs, wind);
				else if (sync != null) {
					pushExtrec(m, rs, wind);
				}
			}
			
			resp = client.commit(null, null);
			page = resp.syncPage();
		}
		
	}

	private SynState queryLocalExtabl(ExtableMeta m, AnResultset rs, TimeWindow wind)
			throws SQLException, TransException {
		AnResultset ls = ((AnResultset)localSt
			.select(m.tbl, "l")
			.col(m.syncFlag).col(m.clientpath).col(m.stamp)
			.whereEq(m.synoder, rs.getString(m.synoder))
			.whereEq(m.clientpath, rs.getString(m.clientpath))
			.rs(localSt.instancontxt(conn, robot))
			.rs(0))
			.beforeFirst();
		
		if (ls.next())
			return new SynState(mode, ls.getString(m.syncFlag))
					.stamp(ls.getDate(m.stamp));
		else	
			return null;
	}

	@Override
	public void run() {
		// TODO Auto-generated method stub
		
	}

}
