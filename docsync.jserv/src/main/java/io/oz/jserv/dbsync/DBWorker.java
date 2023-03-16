package io.oz.jserv.dbsync;

import java.io.IOException;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import io.odysz.anson.x.AnsonException;
import io.odysz.common.Utils;
import io.odysz.jclient.Clients;
import io.odysz.jclient.SessionClient;
import io.odysz.jclient.tier.ErrorCtx;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.jprotocol.AnsonHeader;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantics.meta.TableMeta;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.sql.PageInf;
import io.odysz.transact.x.TransException;
import io.oz.jserv.docsync.SynState;
import io.oz.jserv.docsync.SyncRobot;
import io.oz.jserv.docsync.SynodeMode;

/**
 * Worker for db synchronization.
 * 
 * @author odys-z@github.com
 */
public class DBWorker implements Runnable {
	
	ErrorCtx err;
	DATranscxt localSt;
	
	final String uri;

	/** synode id */
	String myId;

	/** upper synode id */
	String upperId;

	/** volume path */
	String volpath;
	String conn;
	SyncRobot robot;

	SynodeMode mode;
	
	TableMeta cleanMeta;

	protected HashMap<String, TableMeta> entities;

	/** clean session timestamps */
	TimeWindow window; 

	SessionClient client;

	public DBWorker(String synode, SynodeMode m) {
		myId = synode;
		mode = m;
		uri = "/ext/docsync";
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
			throws SQLException, AnsonException, IOException, TransException {
		DBSyncResp rpl = client.commit(null, err); // A = cleans
		List<CleanTask> tasks = rpl.cleanTasks();
			
		// select synodee res from syn_clean where filter-window
		// group by tabl, synoder, clientpath
		for (CleanTask task : tasks) {
			task.loadLocal(localSt, conn, robot, mode)
				.merge()
				.closeLocal(robot)
				.fireReqs(client, err);
		}
	}

	/**
	 * Close clean session.
	 * 
	 * @param window
	 */
	private void closeClean(TimeWindow window) {
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
				SynState localState = queryLocalExtabl(m, rs, wind);
				if (localState == null || localState.olderThan(rs.getDate(m.stamp)))
					pullExtrec(m, rs, wind);
				else if (localState != null) {
					pushExtrec(m, rs, wind);
				}
			}
			
			// finish / close this page
			resp = client.commit(null, null);
			page = resp.syncPage();
		}
		
	}

	private void pushExtrec(ExtableMeta m, AnResultset rs, TimeWindow wind) {
		DBSyncReq req = new DBSyncReq(uri)
				.pushEntity(rs.getString(m.synoder), rs.getString(m.clientpath), wind);
		
		String[] act = AnsonHeader.usrAct(uri, "db-sync", "u/pull-ent", m.entabl);

		AnsonMsg<DBSyncReq> q = client
				.<DBSyncReq>userReq(uri, Port.dbsyncer, req)
				.header(client.header().act(act)); 

		DBSyncResp resp = client.commit(q, err);
		
		// TODO What's the sync state?
	}

	/**
	 * <p>Pull an entity table record - only one record, and suitable for ext-file resource.</p>
	 * FIXME
	 * <p>FIXME What happens when downloading while modifying, moving the file?</p>
	 * <p>For Linux, it seams safe. But for windows, abruptly accessing same
	 * files are likely will happen.</p>
	 * @see https://unix.stackexchange.com/a/41719
	 * @param m
	 * @param rs
	 * @param wind
	 */
	private void pullExtrec(ExtableMeta m, AnResultset rs, TimeWindow wind) {
		
		DBSyncReq req = new DBSyncReq(uri)
				.askEntity(rs.getString(m.synoder), rs.getString(m.clientpath), wind);
		
		String[] act = AnsonHeader.usrAct(uri, "db-sync", "u/pull-ent", m.entabl);

		AnsonMsg<DBSyncReq> q = client
				.<DBSyncReq>userReq(uri, Port.dbsyncer, req)
				.header(client.header().act(act)); 

		DBSyncResp resp = client.commit(q, err);
		
		// TODO download then update DB
		// any previous module?
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
