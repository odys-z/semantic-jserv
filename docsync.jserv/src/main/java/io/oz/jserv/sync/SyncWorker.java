package io.oz.jserv.sync;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.ArrayList;

import org.xml.sax.SAXException;

import io.odysz.anson.x.AnsonException;
import io.odysz.common.Utils;
import io.odysz.jclient.tier.ErrorCtx;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.ext.DocTableMeta;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.AnsonResp;
import io.odysz.semantic.jprotocol.JProtocol.OnProcess;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantic.tier.docs.DocsResp;
import io.odysz.semantic.tier.docs.SyncDoc;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.x.TransException;

public class SyncWorker implements Runnable {
	static int blocksize = 3 * 1024 * 1024;

	/**
	 * jserv-node states
	 */
	public enum SyncMode {
		/** jserv node mode: cloud hub, equivalent of {@link Docsyncer#cloudHub} */
		hub,
		/** jserv node mode: private main, equivalent of {@link Docsyncer#mainStorage} */
		main,
		/** jserv node mode: private , equivalent of {@link Docsyncer#privateStorage}*/
		priv
	};
	
	SyncMode mode;
	
	String mac;
	Synclientier synctier;
	
	public final String funcUri;
	String connPriv;
	String workerId;
	
	// String tempDir;
	// SyncRobot robot;
	ErrorCtx errLog;

	/**
	 * Local table meta of which records been synchronized as private jserv node.
	 */
	DocTableMeta localMeta;

	DATranscxt localSt;

	public boolean verbose = true;


	public SyncWorker(SyncMode mode, String device, String connId, String worker, DocTableMeta tablMeta)
			throws SemanticException, SQLException, SAXException, IOException {
		this.mode = mode;
		funcUri = "/sync/worker";
		connPriv = connId;
		workerId = worker;
		mac = device;

		localMeta = tablMeta;
		
		localSt = new DATranscxt(connId);
		
		errLog = new ErrorCtx() {
			@Override
			public void err(MsgCode code, String msg, String... args) {
				Utils.warn(msg);
			}
		};
	}

	/**
	 * Log into hub since this worker is working as a client of hub node,
	 * where hub root url is initialized with Clients.init(String, boolean).
	 * 
	 * @see Synclientier#login(String, String, String)
	 * 
	 * @param pswd
	 * @return this.
	 * @throws SemanticException
	 * @throws AnsonException
	 * @throws SsException
	 * @throws IOException
	 * @throws SQLException
	 * @throws SAXException
	 */
	public SyncWorker login(String pswd) throws SemanticException, AnsonException, SsException, IOException, SQLException, SAXException {
		synctier = new Synclientier(funcUri, connPriv, errLog)
				.login(workerId, mac, pswd);
		return this;
	}

	@Override
	public void run() {
		try {
			/*
			Docsyncer.lock.lock();

	        DocsResp resp = null;

			String[] act = AnsonHeader.usrAct("sync.jserv", "query", "r/tasks", "query tasks");
			AnsonHeader header = client.header().act(act);

			DocsReq req = (DocsReq) new DocsReq(null).a(A.records);

			AnsonMsg<DocsReq> q = client.<DocsReq>userReq("", AnsonMsg.Port.docsync, req)
									.header(header);

			resp = client.commit(q, errLog);
			
			pullDocs(resp);
			*/
		} catch (Exception ex) {
			// ex.printStackTrace();
		}
		finally {
			Docsyncer.lock.unlock();
		}
	}

	/**
	 * Pull files if any files at hub needing to be downward synchronized.
	 * @param tasks response of task querying, of which the result set (index 0) is the task list. 
	 * @return list of local file ids
	 * @throws SQLException
	 * @throws AnsonException
	 * @throws IOException
	 * @throws TransException
	 */
	ArrayList<DocsResp> pullDocs(DocsResp tasks)
			throws SQLException, AnsonException, IOException, TransException {
		
		ArrayList<DocsResp> res = new ArrayList<DocsResp>(tasks.rs(0).total());
		tasks.rs(0).beforeFirst();
		while (tasks.rs(0).next()) {
			try {
				SyncDoc p = new SyncDoc(tasks.rs(0), localMeta);
					
				res.add(synctier.synClose(
						synctier.synStreamPull(p, localMeta), localMeta));
			}
			catch(Exception e) {
				e.printStackTrace();
				fail(e.getMessage());
			}
		}
		
		return res;
	}

	/**
	 * Private nodes push docs to the cloud hub. 
	 * 
	 * @return
	 * @throws TransException
	 * @throws SQLException
	 * @throws IOException 
	 * @throws AnsonException 
	 */
	public SyncWorker push() throws TransException, SQLException, AnsonException, IOException {
		if (mode == SyncMode.main || mode == SyncMode.priv) {
			// find local records with shareflag = pub
			AnResultset rs = ((AnResultset) localSt
				.select(localMeta.tbl, "f")
				.cols(SyncDoc.nvCols(localMeta)).col(localMeta.syncflag)
				.whereEq(localMeta.syncflag, SyncFlag.priv)
				.limit(30)
				.rs(localSt.instancontxt(connPriv, synctier.robot))
				.rs(0)).beforeFirst();

			// upload
			synctier.syncUp(rs, workerId, localMeta,
			  new OnProcess() {
				@Override
				public void proc(int listIndx, int rows, int seq, int totalBlocks, AnsonResp blockResp)
						throws IOException, AnsonException, SemanticException {
					String clientpath;
					try {
						clientpath = rs.getString(listIndx, localMeta.fullpath);
						Utils.logi("[%s/%s] %s: %s / %s, reply: %s",
								listIndx, rows, clientpath, seq, totalBlocks, blockResp.msg());
					} catch (SQLException e) {
						e.printStackTrace();
					}
				}
			  });
		}
		return this;
	}

	public ArrayList<DocsResp> pull() throws AnsonException, IOException, SQLException, TransException {
		DocsResp rsp = synctier.queryTasks(localMeta, synctier.robot.orgId, synctier.robot.deviceId);
		return pullDocs(rsp);
	}

	/**
	 * Verifying each rec-id has a corresponding local file.
	 * 
	 * @param list length should limited. 
	 * @return this
	 * @throws TransException
	 * @throws SQLException
	 * @throws IOException 
	 */
	public SyncWorker verifyDocs(ArrayList<DocsResp> list) throws TransException, SQLException, IOException {

		/*
		AnResultset rs = (AnResultset) localSt
				.select(localMeta.tbl, "t")
				.cols(localMeta.pk, localMeta.fullpath, localMeta.uri, localMeta.mime, localMeta.size)
				.whereIn(localMeta.pk, ids)
				.rs(localSt.instancontxt(connPriv, synctier.robot))
				.rs(0);

		if (rs.total() != ids.size())
			throw new SemanticException("id count (%s) != file records' count (%s)", ids.size(), rs.total());

		while (rs.next()) {
			String p = synctier.resolvePrivRoot(rs.getString(localMeta.uri), localMeta);
			File f = new File(p);
			if (f.exists() && f.length() == rs.getLong(localMeta.size))
				continue;
			throw new SemanticException("Doc doesn't exists. id: %s, uri: %s, expecting path: %s",
					rs.getString(localMeta.pk), uri, p);
		}
		*/
		for (DocsResp r : list) {
			AnResultset rs = (AnResultset) localSt
				.select(localMeta.tbl, "t")
				.cols(localMeta.pk, localMeta.fullpath, localMeta.uri, localMeta.mime, localMeta.size)
				.whereEq(localMeta.org, r.org())
				.whereEq(localMeta.device, mac)
				.whereEq(localMeta.fullpath, r.doc.fullpath())
				.rs(localSt.instancontxt(connPriv, synctier.robot))
				.rs(0);

			rs.beforeFirst().next();
			String p = synctier.resolvePrivRoot(rs.getString(localMeta.uri), localMeta);
			File f = new File(p);

			if (!f.exists())
				throw new SemanticException("Doc doesn't exists.\nid: %s, uri: %s,\nexpecting path: %s",
					rs.getString(localMeta.pk), rs.getString(localMeta.uri), p);
			else if (f.length() != rs.getLong(localMeta.size))
				throw new SemanticException("Saved length (%s) doesn't equal file length (%s).\nid: %s, uri: %s,\nexpecting path: %s",
					rs.getLong(localMeta.size), f.length(), rs.getString(localMeta.pk), rs.getString(localMeta.uri), p);
			else if (!Files.probeContentType(Paths.get(p)).equals(rs.getString(localMeta.mime)))
				throw new SemanticException("Saved mime (%s) doesn't match with file's (%s).\nid: %s, uri: %s,\nexpecting path: %s",
					rs.getString(localMeta.mime), 
					Files.probeContentType(Paths.get(p)).equals(rs.getString(localMeta.mime)),
					rs.getString(localMeta.pk), rs.getString(localMeta.uri), p);
			else
				continue;
		}

		return this;
	}

	public String org() {
		return synctier.robot.orgId;
	}
	
	public SyncRobot robot() {
		return synctier.robot;
	}

	public DocsResp queryTasks()
			throws SemanticException, AnsonException, IOException {
		return synctier.queryTasks(localMeta, synctier.robot.orgId, workerId);
	}

	public String nodeId() {
		return synctier.robot.deviceId();
	}
}
