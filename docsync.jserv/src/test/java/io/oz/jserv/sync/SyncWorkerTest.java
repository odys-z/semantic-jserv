package io.oz.jserv.sync;

import static io.oz.jserv.sync.ZSUNodes.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;

import org.apache.commons.io_odysz.FilenameUtils;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import io.odysz.anson.x.AnsonException;
import io.odysz.common.Configs;
import io.odysz.common.LangExt;
import io.odysz.common.Utils;
import io.odysz.jclient.Clients;
import io.odysz.jclient.tier.ErrorCtx;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.DASemantics.smtype;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.ext.DocTableMeta;
import io.odysz.semantic.ext.DocTableMeta.Share;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
import io.odysz.semantic.jprotocol.AnsonResp;
import io.odysz.semantic.jprotocol.JProtocol.OnDocOk;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantic.jsession.AnSession;
import io.odysz.semantic.tier.docs.DocUtils;
import io.odysz.semantic.tier.docs.DocsResp;
import io.odysz.semantic.tier.docs.SyncDoc;
import io.odysz.semantics.IUser;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.sql.Update;
import io.odysz.transact.x.TransException;
import io.oz.album.tier.Photo;
import io.oz.album.tier.PhotoMeta;
import io.oz.jserv.sync.SyncFlag.SyncEvent;
import io.oz.jserv.sync.ZSUNodes.AnDevice;
import io.oz.jserv.sync.ZSUNodes.Kharkiv;
import io.oz.jserv.sync.ZSUNodes.Kyiv;

class SyncWorkerTest {

	static String conn;

	static ErrorCtx errLog;
	static DATranscxt defltSt;
	static DocTableMeta meta;
	
	static {
		try {
			conn = "main-sqlite";

			Path currentRelativePath = Paths.get("");
			String p = currentRelativePath.toAbsolutePath().toString();
			System.setProperty("VOLUME_HOME", FilenameUtils.concat(p, volumeDir));

			String wwwinf = FilenameUtils.concat(p, webRoot);
			Configs.init(wwwinf);
			Connects.init(wwwinf);
			defltSt = new DATranscxt(Connects.defltConn());
			AnsonMsg.understandPorts(Port.docsync);
			AnSession.init(defltSt);
			
			// This worker for test is a client of Album hub.
			Clients.init(jservHub, false);
			
			meta = new PhotoMeta(conn);

			errLog = new ErrorCtx() {
				@Override
				public void err(MsgCode code, String msg, String...args) {
					// Utils.warn(msg);
					fail(msg);
				}
			};
		} catch (SemanticException | SQLException | SAXException | IOException e) {
			e.printStackTrace();
		}
	}

	@Test
	void testIds() throws SemanticException, SQLException, SAXException, IOException, AnsonException, SsException {
		SyncWorker worker = new SyncWorker(Kyiv.Synode.mode, Kyiv.Synode.nodeId, conn, Kyiv.Synode.worker, meta)
				.stop()
				.login(Kyiv.Synode.passwd);
		assertEquals("/sync/worker", worker.funcUri);
		assertEquals(family, worker.org());
		assertEquals(Kyiv.Synode.worker, worker.workerId);
		assertEquals(Kyiv.Synode.worker, worker.robot().userId);
		assertEquals(Kyiv.Synode.nodeId, worker.mac);
		assertEquals(Kyiv.Synode.nodeId, worker.robot().deviceId);
		
		DocsResp rsp = worker.listNodes(family);
		assertEquals(4, rsp.rs(0).total());
	}

	/**
	 * <p>Kyiv local -&gt; Kyiv (hub) -&gt; Kharkiv</p>
	 * 
	 * Test {@link SyncWorker}' pushing (synchronizing 'pub') with album-jserv as sync hub (8081).
	 * 
	 * @throws TransException 
	 * @throws IOException 
	 * @throws SQLException 
	 * @throws AnsonException 
	 * @throws GeneralSecurityException 
	 * @throws SsException 
	 * @throws SAXException 
	 * 
	 */
	@Test
	void testShareByKyiv() throws 
		AnsonException, SQLException, IOException, TransException,
		SsException, GeneralSecurityException, SAXException {
		
		String clientpath = Kyiv.png;
		SyncWorker worker = new SyncWorker(Kyiv.Synode.mode, Kyiv.Synode.nodeId, conn, Kyiv.Synode.worker, meta)
				.stop()
				.login(Kyiv.Synode.passwd);

		// 0. clean failed tests
		clean(worker, clientpath);
		worker.synctier.tempRoot("synode.kyiv");
		worker.synctier.synDel(meta.tbl, worker.nodeId(), clientpath);

		// 1. create a public file at this private node
		Photo photo = new Photo().create(clientpath);

		// 2. synchronize to cloud hub ( hub <- kyiv )
		Docsyncer.init(Kyiv.Synode.nodeId);
		SyncWorker.blocksize = 32 * 3;

		String pid = createPhoto(conn, photo, worker.robot(), new PhotoMeta(defltSt.getSysConnId()));
		Utils.logi("------ Saved Photo: %s ----------\n%s\n%s", photo.fullpath(), photo.pname, pid);

		worker.push();
		
		// 2.1 verify the sharing tasks been created
		// syndev: 
	
		// 3. query my tasks as another jnode (kharkiv)
		worker = new SyncWorker(Kharkiv.Synode.mode, Kharkiv.Synode.nodeId, conn, Kharkiv.Synode.worker, meta)
				.stop()
				.login(Kharkiv.Synode.passwd);
		DocsResp resp = worker.queryTasks();
		
		AnResultset tasks = resp.rs(0);
		if (tasks == null || tasks.total() == 0)
			fail("Shouldn't be here");

		tasks.beforeFirst();
		while (tasks.next()) {
			String dev = tasks.getString(meta.device);
			String pth = tasks.getString(meta.fullpath);
			assertTrue( AnDevice.device.equals(dev)
					|| Kharkiv.Synode.nodeId.equals(dev)
					|| Kyiv.Synode.nodeId.equals(dev)
					&& clientpath.equals(pth)
					|| "omni".equals(dev));	// Albumtier test
		}

		// 4. synchronize downwardly 
		// worker.pullDocs(resp);
	}

	private void clean(SyncWorker worker, String clientpath)
			throws TransException, SQLException {

		// String device = worker.synctier.robot.deviceId;

		defltSt
			.delete(meta.tbl, worker.robot())
			.whereEq(meta.org, worker.org())
			.post(Docsyncer.onClean(worker.org(), meta, worker.robot()))
			.d(defltSt.instancontxt(conn, worker.robot()));
		
		worker.synctier.synDel(meta.tbl, Kyiv.Synode.nodeId, null);
		worker.synctier.synDel(meta.tbl, Kharkiv.Synode.nodeId, null);
		worker.synctier.synDel(meta.tbl, AnDevice.device, null);
	}

	/**
	 * Simulates the processing of Albums.createFile(), creating a stub photo and having syncflag updated.
	 * 
	 * @see DocUtils#createFileB64(String, SyncDoc, IUser, DocTableMeta, DATranscxt, Update)
	 * @see Docsyncer#onDocreate(SyncDoc, DocTableMeta, IUser)
	 * @param conn
	 * @param photo
	 * @param usr
	 * @param meta table meta of h_photes
	 * @return doc id, e.g. pid
	 * @throws TransException
	 * @throws SQLException
	 * @throws IOException 
	 */
	String createPhoto(String conn, Photo photo, SyncRobot usr, PhotoMeta meta)
			throws TransException, SQLException, IOException {

		if (!DATranscxt.hasSemantics(conn, meta.tbl, smtype.extFilev2))
			throw new SemanticException("Semantics of ext-file2.0 for h_photos.uri can't be found");
		
		Update post = Docsyncer.onDocreate(photo, meta, usr);
		return DocUtils.createFileB64(conn, photo, usr, meta, defltSt, post);
	}

	/**
	 * <p> device -&gt; hub -&gt; synode[Kharkiv]</p>
	 * 
	 * <h5>A word about sharer and path at jserv:</h5>
	 * <p>
	 * - video is uploaded as owned by "odys-z.github.io"<br>
	 * - uri is handled by {@link io.odysz.semantic.DASemantics.ShExtFile ShExtFile} and using shareby as user-id/sub-folder name, like:<br>
	 * [volume]/[family]/[shareby]/[folder]/[pid] filename, e.g.<br>
	 * /.../jserv-album/volume/odys-z.github.io/synctest/0000009P ...<br>
	 * </p>
	 * @throws Exception
	 */
	@Test
	void testKharivPull() throws Exception {
		SyncWorker worker = new SyncWorker(Kharkiv.Synode.mode, Kharkiv.Synode.nodeId, conn, Kharkiv.Synode.worker, meta)
				.stop()
				.login(Kharkiv.Synode.passwd);

		// 0. clean failed tests
		worker.synctier.tempRoot("synode.kharkiv");
		clean(worker, Kyiv.png);

		videoUpByApp(meta);

		Docsyncer.init(Kharkiv.Synode.nodeId);


		// downward synchronize the file, hub -> Kharkiv (working as main node)
		SyncWorker.blocksize = 32 * 3;
		DocTableMeta meta = new PhotoMeta(conn);
		worker = new SyncWorker(SynodeMode.main, Kharkiv.Synode.nodeId, conn, Kyiv.Synode.worker, meta)
				.stop();
		ArrayList<DocsResp> ids = worker
				.login(Kharkiv.Synode.passwd)
				.pull();
		
		if (ids == null || ids.size() == 0)
			fail("No pull tasks are completed.");

		worker.verifyDocs(ids);
	}

	/**
	 * 1. Clean video {@link AnDevice#localFile} of device {@link AnDevice#device} <br>
	 * 2. Upload the video from device to the private synode, Kharkiv.
	 * 
	 * @param photoMeta
	 * @return
	 * @throws SsException
	 * @throws IOException
	 * @throws GeneralSecurityException
	 * @throws AnsonException
	 * @throws SQLException
	 * @throws SAXException
	 * @throws TransException 
	 */
	static String videoUpByApp(DocTableMeta photoMeta)
			throws SsException, IOException, GeneralSecurityException, AnsonException, SQLException, SAXException, TransException {
		int bsize = 72 * 1024;

		// app is using Synclientier for synchronizing 
		Synclientier apptier = new Synclientier(clientUri, errLog)
				.tempRoot("app.kharkiv")
				.login(AnDevice.userId, AnDevice.device, AnDevice.passwd)
				.blockSize(bsize);

		apptier.synDel(photoMeta.tbl, AnDevice.device, AnDevice.localFile);
		
		SyncDoc doc = (SyncDoc) new SyncDoc()
					.share(apptier.robot.uid(), Share.pub, new Date())
					.folder(Kharkiv.folder)
					.fullpath(AnDevice.localFile);
		DocsResp resp = apptier.synInsertDoc(meta.tbl, doc, new OnDocOk() {
			@Override
			public void ok(SyncDoc doc, AnsonResp resp)
					throws IOException, AnsonException, TransException {
				// update synode (Kharkiv) flag - app device do nothing here
				String f = SyncFlag.to(Share.pub, SyncEvent.pushEnd, doc.shareflag());
				try {
					SynodeTier tier = (SynodeTier) new SynodeTier(clientUri, conn, errLog)
							.tempRoot("synode.kharkiv")
							.login(Kharkiv.Synode.worker, Kharkiv.Synode.nodeId, Kharkiv.Synode.passwd)
							.blockSize(bsize);
					Synclientier.setLocalSync(tier.localSt, tier.connPriv, meta, doc, f, tier.robot);
				} catch (SQLException | SsException e1) {
					e1.printStackTrace();
					fail(e1.getMessage());
				}

				// pushing again should fail
				// List<DocsResp> resps2 = null;
				@SuppressWarnings("unused")
				DocsResp resp2 = null;
				try {

					resp2 = apptier.synInsertDoc(meta.tbl, doc,
					// resps2 = tier.pushBlocks(meta, videos, ssInf, null,
					new OnDocOk() {
						@Override
						public void ok(SyncDoc doc, AnsonResp resp)
								throws IOException, AnsonException, TransException {
							fail("Double checking failed.");
						}
					},
					new ErrorCtx() {
						@Override
						public void err(MsgCode code, String msg, String...args) {
							// expected
						}
					});
				} catch (TransException | IOException | SQLException e) {
					e.printStackTrace();
					fail(e.getMessage());
				}
			}
		});

		assertNotNull(resp);

		String docId = resp.doc.recId();
		assertEquals(8, docId.length());

		DocsResp rp = apptier.selectDoc(meta.tbl, docId);

		assertTrue(LangExt.isblank(rp.msg()));
		assertEquals(AnDevice.device, rp.doc.device());
		assertEquals(AnDevice.localFile, rp.doc.fullpath());

		return AnDevice.localFile;
	}
}
