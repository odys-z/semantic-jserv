package io.oz.syntier.serv;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;

import static io.odysz.common.LangExt.isblank;
import static io.odysz.common.Utils.pause;

import org.apache.commons.io_odysz.FilenameUtils;
import org.junit.jupiter.api.Test;

import io.odysz.anson.x.AnsonException;
import io.odysz.jclient.Clients;
import io.odysz.jclient.SessionClient;
import io.odysz.jclient.tier.ErrorCtx;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantic.tier.docs.DocsResp;
import io.odysz.semantic.tier.docs.ExpSyncDoc;
import io.odysz.semantic.tier.docs.PathsPage;
import io.odysz.semantics.SessionInf;
import io.odysz.transact.x.TransException;
import io.oz.album.tier.AlbumResp;
import io.oz.albumtier.PhotoSyntierDel;
import io.oz.syndoc.client.PhotoSyntier;

@SuppressWarnings("unused")
class SynotierJettyAppTest {

	static ErrorCtx errCtx;
	
	static {
		errCtx = new ErrorCtx() {
			@Override
			public void err(MsgCode c, String rep, String...args) {
				fail(String.format("code %s, msg: %s", c.name(), rep));
			}
		};
	}

	@Test
	void testSyndocApp() throws Exception {
		final String vhub = "VOLUME_HUB";
		final String vprv = "VOLUME_PRV";
		String p = new File("src/main/webapp/vol_hub").getAbsolutePath();
    	System.setProperty(vhub, p);
		p = new File("src/main/webapp/vol_prv").getAbsolutePath();
    	System.setProperty(vprv, p);

		SynotierJettyApp hub = SynotierJettyApp.main_("$" + vhub,  new String[] {});
		SynotierJettyApp prv = SynotierJettyApp.main_("$" + vprv,  new String[] {});
		pause("...");
	}

	void testVideoUp(boolean[] lights) throws SsException, IOException, GeneralSecurityException, AnsonException, TransException {
		String localFolder = "test/res";
		 int bsize = 72 * 1024;
		 String filename = "my.jpg";

		PhotoSyntier tier = (PhotoSyntier) new PhotoSyntier("h_photos", "test/album", "device-test", errCtx)
								.blockSize(bsize);
		SessionClient ssclient = Clients.loginWithUri(tier.uri(), "ody", "123456", "device-test");

		List<ExpSyncDoc> videos = new ArrayList<ExpSyncDoc>();
		videos.add((ExpSyncDoc) new ExpSyncDoc()
					.fullpath(FilenameUtils.concat(localFolder, filename)));

		SessionInf photoUser = ssclient.ssInfo();
		photoUser.device = "device-test";

		tier.asyVideos(videos,
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
						tier.asyVideos(videos, null, null);
						// assertNotNull(resps);
						// assertEquals(1, resps.size());

//						for (DocsResp d : resps) {
//							String docId = d.xdoc.recId();
//							assertEquals(8, docId.length());
//
//							AlbumResp rp = tier.selectPhotoRec(docId);
//							assertNotNull(rp.xdoc.pname);
//							assertEquals(rp.xdoc.pname, filename);
//						}

						PathsPage page = new PathsPage();
						for (int i = 0; i < videos.size(); i++) {
							ExpSyncDoc p = videos.get((int)i);
							if (isblank(p.fullpath()))
								continue;
							else page.add(p.fullpath());
						}

						DocsResp rp = tier.synQueryPathsPage(page, Port.docsync);
						for (int i = page.start(); i < page.end(); i++) {
							assertNotNull(page.paths().get(videos.get(i).fullpath()));
						}

					} catch (TransException | IOException e) {
						e.printStackTrace();
						fail(msg);
					}
				}
			});
	}
}