package io.oz.jserv.sync;

import static io.odysz.common.LangExt.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.commons.io.FileUtils;
import org.xml.sax.SAXException;

import io.odysz.anson.x.AnsonException;
import io.odysz.common.Utils;
import io.odysz.jclient.tier.ErrorCtx;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.ext.DocTableMeta;
import io.odysz.semantic.jprotocol.AnsonResp;
import io.odysz.semantic.jprotocol.AnsonHeader;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
import io.odysz.semantic.jprotocol.JProtocol.OnDocOk;
import io.odysz.semantic.jprotocol.JProtocol.OnProcess;
import io.odysz.semantic.tier.docs.DocUtils;
import io.odysz.semantic.tier.docs.DocsReq;
import io.odysz.semantic.tier.docs.DocsResp;
import io.odysz.semantic.tier.docs.SyncDoc;
import io.odysz.semantic.tier.docs.DocsReq.A;
import io.odysz.semantics.SessionInf;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.x.TransException;
import io.oz.jserv.sync.SyncFlag.SyncEvent;

public class SynodeTier extends Synclientier {

	protected DATranscxt localSt;
	protected String connPriv;

	protected HashMap<String, DeviceLock> deviceLocks;

	private final ReentrantLock lock;

	public SynodeTier(String clientUri, String connId, ErrorCtx errCtx)
			throws SemanticException, IOException {
		super(clientUri, errCtx);
		try {
			lock = new ReentrantLock();
			localSt = new DATranscxt(connId);
		} catch (SQLException | SAXException e) {
			throw new SemanticException(
					"Accessing local DB failed with conn %s. Only jnode should throw this."
					+ "\nex: %s,\nmessage: %s",
					connId, e.getClass().getName(), e.getMessage());
		}
		connPriv = connId;
	}
	
	/**
	 * @param tbl task table name 
	 * @param family 
	 * @param deviceId 
	 * @return response
	 * @throws IOException 
	 * @throws AnsonException 
	 * @throws SemanticException 
	 */
	DocsResp queryTasks(String tbl, String family, String deviceId)
			throws SemanticException, AnsonException, IOException {

		DocsReq req = (DocsReq) new DocsReq(tbl)
				.org(family)
				.a(A.syncdocs)
				;

		String[] act = AnsonHeader.usrAct("sync", "list", tbl, deviceId);
		AnsonHeader header = client.header().act(act);

		AnsonMsg<DocsReq> q = client
				.<DocsReq>userReq(uri, Port.docsync, req)
				.header(header);

		return client.<DocsReq, DocsResp>commit(q, errCtx);
	}
	
	public List<DocsResp> syncUp(DocTableMeta meta, List<? extends SyncDoc> videos, String workerId,
			OnProcess onProc, OnDocOk... docOk)
			throws SQLException, TransException, AnsonException, IOException {
		SessionInf photoUser = client.ssInfo();
		photoUser.device = workerId;

		return pushBlocks(meta.tbl,
				videos, photoUser, onProc,
				isNull(docOk) ? new OnDocOk() {
					@Override
					public void ok(SyncDoc doc, AnsonResp resp)
							throws IOException, AnsonException, TransException {
						String sync0 = doc.syncFlag;
						String share = doc.shareflag;
						String f = SyncFlag.to(sync0, SyncEvent.pushubEnd, share);
						try {
							setLocalSync(localSt, connPriv, meta, doc, f, robot);
						} catch (SQLException e) {
							e.printStackTrace();
						}
					}
				} : docOk[0],
				errCtx);
	}

	/**
	 * Downward synchronize a doc. If succeed, also insert a record into local DB.
	 * @param p
	 * @param meta 
	 * @return doc record (e.g. h_photos)
	 * @throws AnsonException
	 * @throws IOException
	 * @throws TransException
	 * @throws SQLException
	 */
	SyncDoc synStreamPull(SyncDoc p, DocTableMeta meta)
			throws AnsonException, IOException, TransException, SQLException {
		if (!verifyDel(p, meta)) {
			DocsReq req = (DocsReq) new DocsReq()
					.org(robot.orgId)
					.docTabl(meta.tbl)
					.queryPath(p.device(), p.fullpath())
					.a(A.download);

			String tempath = tempath(p);
			String path = client.download(uri, Port.docsync, req, tempath);
			
			String dstpath = onCreateLocalFile(p, path, meta);
			
			// TODO: synClosePull(p, dstpath);
			if (verbose)
				Utils.logi("file downloaded is saved to %s", dstpath);;
		}
		return p;
	}
	
	protected String onCreateLocalFile(SyncDoc p, String path, DocTableMeta meta)
			throws TransException, SQLException, IOException {
		// suppress uri handling, but create a stub file
		p.uri = "";

		String pid = insertLocalFile(localSt, connPriv, path, p, robot, meta);
		
		// move
		String targetPath = DocUtils.resolvExtroot(localSt, connPriv, pid, robot, meta);
		if (verbose)
			Utils.logi("   [SyncWorker.verbose: end stream download] %s\n-> %s", path, targetPath);
		Files.move(Paths.get(path), Paths.get(targetPath), StandardCopyOption.REPLACE_EXISTING);

		return targetPath;
	}
	
	/** 
	 * <p>Verify the local file.</p>
	 * <p>If it is not expected, delete it.</p>
	 * Two cases need this verification<br>
	 * 1. the file was downloaded but the task of closing had failed<br>
	 * 2. the previous downloading resulted in the error message and been saved as a file<br>
	 * 
	 * <p>This is differnet from the super one, and can not be called if this is
	 * client doesn't works as jnode, because the uri target path can not be found
	 * without a DB semantics handler.</p>
	 * 
	 * @param f
	 * @param meta doc's table name, e.g. h_photos, used to resolve target file path if needed
	 * @return true if file exists and mime and size can match (file moved to uri);
	 * or false if file size and mime doesn't match (tempath deleted)
	 * @throws IOException 
	 */
	@Override
	protected boolean verifyDel(SyncDoc f, DocTableMeta meta) throws IOException {
		String pth = tempath(f);
		File file = new File(pth);
		if (!file.exists())
			return false;

		long size = f.size;
		long length = file.length();

		if ( size == length ) {
			// move temporary file
			String targetPath = resolvePrivRoot(f.uri, meta);
			if (Docsyncer.verbose)
				Utils.logi("   %s\n-> %s", pth, targetPath);
			try {
				Files.move(Paths.get(pth), Paths.get(targetPath), StandardCopyOption.ATOMIC_MOVE);
			} catch (Throwable t) {
				Utils.warn("Moving temporary file failed: %s\n->%s\n  %s\n  %s",
							pth, targetPath, f.device(), f.clientpath);
			}
			return true;
		}
		else {
			try { FileUtils.delete(new File(pth)); }
			catch (Exception ex) {}
			return false;
		}
	}

	public String resolvePrivRoot(String uri, DocTableMeta localMeta) {
		return DocUtils.resolvePrivRoot(uri, localMeta, connPriv);
	}

	DeviceLock getDeviceLock(String device) {
		lock.lock();
		try {
			DeviceLock lck = deviceLocks.get(device);
			if (isblank(lck)) {
				lck = new DeviceLock(device);
				deviceLocks.put(device, lck);
			}
			return lck;
		} finally {
			lock.unlock();
		}
	}
}
