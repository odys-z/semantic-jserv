package io.oz.client.tier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io_odysz.FilenameUtils;
import org.junit.jupiter.api.Test;
import org.vishag.async.AsyncSupplier;

import io.odysz.anson.Anson;
import io.odysz.anson.JSONAnsonListener;
import io.odysz.anson.x.AnsonException;
import io.odysz.jclient.Clients;
import io.odysz.jclient.InsecureClient;
import io.odysz.jclient.SessionClient;
import io.odysz.jclient.tier.ErrorCtx;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.IPort;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantic.tier.docs.DocsResp;
import io.odysz.semantic.tier.docs.ExpSyncDoc;
import io.odysz.semantics.IUser;
import io.odysz.semantics.SessionInf;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.x.TransException;
import io.oz.album.AlbumPort;
import io.oz.album.tier.AlbumResp;
import io.oz.album.tier.PhotoRec;
import io.oz.albumtier.PhotoSyntier;

/**
 * @deprecated
 * <pre>
 * INSERT INTO h_photos (pid,uri,pname,pdate,cdate,tags,oper,opertime) VALUES
	 ('test-00000','omni/ody/2019_08/DSC_0005.JPG','DSC_0005.JPG','2019-08-24','2021-08-24','#Qing Hai Lake','ody','2022-01-13'),
	 ('test-00001','omni/ody/2019_08/DSC_0124.JPG','DSC_0124.JPG','2019-08-24','2021-08-24','#Qing Hai Lake','ody','2022-01-13'),
	 ('test-00002','omni/ody/2021_08/IMG_20210826.jgp','IMG_20210826.jgp','2019-08-24 15:44:30','2021-08-26','#Lotus Lake','ody','2022-01-13'),
	 ('test-00003','omni/ody/2021_10/IMG_20211005.jgp','IMG_20211005.jgp','2019-10-05 11:19:18','2021-10-05','#Song Gong Fort','ody','2022-01-13'),
	 ('test-00004','omni/ody/2021_12/DSG_0753.JPG','DSG_0753.JPG','2021-12-05','2021-12-05','#Garze','ody','2022-01-13'),
	 ('test-00005','omni/ody/2021_12/DSG_0827.JPG','DSG_0827.JPG','2021-12-05','2021-12-05','#Garze','ody','2022-01-13'),
	 ('test-00006','omni/ody/2021_12/DSG_0880.JPG','DSG_0880.JPG','2021-12-31','2021-12-31','#Toronto','ody','2022-01-13');
 * </pre>
 * 
 * @author ody
 *
 */
class AlbumsTest {
	static String jserv;

	static IUser robot;
	/** local working dir */
	static String local;

	static InsecureClient client;

	static ErrorCtx errCtx;

	private static IPort defaultPortImpl;

	static {
		try {
			AnsonMsg.understandPorts(AlbumPort.album);
			defaultPortImpl = AlbumPort.album;

			JSONAnsonListener.registFactory(IPort.class, 
				(s) -> {
					try {
						return defaultPortImpl.valof(s);
					} catch (SemanticException e) {
						e.printStackTrace();
						return null;
					}
				});

			jserv = "http://localhost:8080/jserv-album";
			Clients.init(jserv);

			client = new InsecureClient(jserv);
			local = new File("src/test/local").getAbsolutePath();

			SessionClient.verbose(false);
			Anson.verbose = false;

			errCtx = new ErrorCtx() {
				@Override
				public void err(MsgCode c, String rep, String...args) {
					fail(String.format("code %s, msg: %s", c.name(), rep));
				}
			};
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	void testDownload() throws SemanticException, TransException, IOException {
		String localFolder = "test/results";

		AlbumResp resp = getCollection("c-001");
		PhotoRec[] collect = resp.photos(0);
		PhotoRec ph1 = collect[0];
		PhotoRec ph2 = collect[1];
		PhotoRec ph3 = collect[2];
		try { FileUtils.delete(new File(ph1.pname)); } catch (Exception ex) {}
		try { FileUtils.delete(new File(ph2.pname)); } catch (Exception ex) {}
		try { FileUtils.delete(new File(ph3.pname)); } catch (Exception ex) {}
	
		File f = new File(localFolder);
		if (!f.exists()) {
			f.mkdirs();
		}

		Supplier<String>[] resultSuppliers = null;
		try {
			resultSuppliers = AsyncSupplier.getDefault().submitSuppliers(
				     () -> getDownloadResult(ph1, FilenameUtils.concat(localFolder, ph1.pname)),
				     () -> getDownloadResult(ph2, FilenameUtils.concat(localFolder, ph2.pname)),
				     () -> getDownloadResult(ph3, FilenameUtils.concat(localFolder, ph3.pname))
				   );
		}
		catch (Exception ex) {
			fail(ex.getMessage());
		}

		String a = resultSuppliers[0].get();
		assertTrue(a.toLowerCase().contains(".jpg"));
		assertFalse(a.toLowerCase().contains("msg:"));
		assertTrue(new File(a).exists());
		assertTrue(FileUtils.sizeOf(new File(a)) > 5000);

		String b = resultSuppliers[1].get();
		assertTrue(b.toLowerCase().contains(".jpg"));
		assertTrue(FileUtils.sizeOf(new File(b)) > 5000);

		String c = resultSuppliers[2].get();
		assertTrue(c.toLowerCase().contains(".jpg"));
		assertTrue(FileUtils.sizeOf(new File(c)) > 5000);
	}

	AlbumResp getCollection(String collectId) {
		try {
			PhotoSyntier tier = new PhotoSyntier("test/album", client.ssInfo().device, errCtx);
			return tier.getCollect(collectId);
		} catch (SemanticException | IOException | AnsonException e) {
			e.printStackTrace();
			fail(e.getMessage());
			return null;
		}
	}

	String getDownloadResult(PhotoRec photo, String filepath) {
		try {
			PhotoSyntier tier = new PhotoSyntier("test/album", client.ssInfo().device, errCtx);
			return tier.download(photo, filepath);
		} catch (IOException | AnsonException | SemanticException e) {
			e.printStackTrace();
			return e.getMessage();
		}
	}
	
	/**
	 * Test append to collection with a pic.
	 * @throws TransException
	 * @throws IOException
	 * @throws AnsonException
	 * @throws GeneralSecurityException
	 * @throws SsException
	@Test
	void testAppend2Collect() throws TransException, IOException, AnsonException, GeneralSecurityException, SsException {
		String localFolder = "test/res";
		String filename = "my.jpg";

		SessionClient ssclient = Clients.login("ody", "123456", "device-test");
		PhotoSyntier tier = new PhotoSyntier("test/album", ssclient.ssInfo().device, errCtx);

		try {
			tier.insertPhoto("c-001", FilenameUtils.concat(localFolder, filename), filename);
			fail("should failed on duplicate file found");
		}
		catch (SemanticException e) { }

		tier.del("device-test", FilenameUtils.concat(localFolder, filename));
		AlbumResp rep = tier.insertPhoto("c-001", FilenameUtils.concat(localFolder, filename), filename);

		assertEquals("c-001", rep.photo().collectId);

		assertEquals(8, rep.photo().recId.length());
	}
	 */
	
	/**
	 * Suppose multiple files are picked, then synchronize into repository.
	 * @throws AnsonException 
	 * @throws GeneralSecurityException 
	 * @throws IOException 
	 * @throws SsException 
	 * @throws TransException 
	 * @Test
	void testSyncInsertPhotos() throws SemanticException, IOException, GeneralSecurityException, AnsonException {
		String localFolder = "test/res";
		// String filename = "my.jpg";
		String filename = "no-exif.jpg";

		SessionClient ssclient = Clients.login("ody", "123456", "device-1");
		PhotoSyntier tier = new PhotoSyntier("test/album", ssclient.ssInfo().device, errCtx);
		try {
			tier.insertPhoto("c-001", FilenameUtils.concat(localFolder, filename), filename);
			fail("checking duplication failed.");
		}
		catch (SemanticException e) { }

		tier.del("device-1", FilenameUtils.concat(localFolder, filename));
		AlbumResp resp = tier.insertPhoto("c-001", FilenameUtils.concat(localFolder, filename), filename);

		assertEquals("c-001", resp.photo().collectId);
		assertEquals(8, resp.photo().recId.length());	
	}
	 */
	
	@Test
	void testVideoUp() throws SsException, IOException, GeneralSecurityException, AnsonException, TransException {
		String localFolder = "test/res";
		 int bsize = 72 * 1024;
		 String filename = "my.jpg";

		SessionClient ssclient = Clients.login("ody", "123456", "device-test");
		PhotoSyntier tier = (PhotoSyntier) new PhotoSyntier("test/album", "device-test", errCtx)
								.blockSize(bsize);

		List<ExpSyncDoc> videos = new ArrayList<ExpSyncDoc>();
		videos.add((ExpSyncDoc) new ExpSyncDoc()
					.fullpath(FilenameUtils.concat(localFolder, filename)));

		SessionInf photoUser = ssclient.ssInfo();
		photoUser.device = "device-test";

		tier.asyVideos( videos,
			(ix, total, c, pth, resp) -> {
				fail("Duplicate checking not working on " + pth);
			},
			null,
			new ErrorCtx() {
				@Override
				public void err(MsgCode c, String msg, String ...args) {
					if (!MsgCode.exGeneral.equals(c))
						fail("Not expected error for this handling.");

					tier.del("device-test", videos.get(0).fullpath());
					List<DocsResp> resps;
					try {
						resps = tier.syncVideos(videos, null, null, null);
						assertNotNull(resps);
						assertEquals(1, resps.size());

						for (DocsResp d : resps) {
							String docId = d.xdoc.recId();
							assertEquals(8, docId.length());

							AlbumResp rp = tier.selectPhotoRec(docId);
							assertNotNull(rp.photo().pname);
							assertEquals(rp.photo().pname, filename);
						}
					} catch (TransException | IOException e) {
						e.printStackTrace();
						fail(msg);
					}
				}
			});

	}
}
