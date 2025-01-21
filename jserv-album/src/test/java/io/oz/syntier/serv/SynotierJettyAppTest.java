package io.oz.syntier.serv;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import static io.odysz.common.LangExt.isblank;
import static io.odysz.common.Utils.pause;
import static io.odysz.common.Utils.warn;

import org.apache.commons.io_odysz.FilenameUtils;
import org.junit.jupiter.api.Test;

import io.odysz.anson.x.AnsonException;
import io.odysz.common.Utils;
import io.odysz.jclient.Clients;
import io.odysz.jclient.SessionClient;
import io.odysz.jclient.tier.ErrorCtx;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantic.tier.docs.ExpSyncDoc;
import io.odysz.semantic.tier.docs.IFileDescriptor;
import io.odysz.semantic.tier.docs.PathsPage;
import io.odysz.semantics.SessionInf;
import io.odysz.transact.x.TransException;
import io.oz.jserv.docs.syn.singleton.AppSettings;
import io.oz.syndoc.client.PhotoSyntier;

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
	void testAppSettings() throws AnsonException, IOException {
		AppSettings hset = AppSettings.load("src/main/webapp/WEB-INF", "settings.json");
		String bindip = hset.bindip;
		assertEquals("127.0.0.1", bindip);

		assertEquals("../../../../volumes-0.7/volume-hub", hset.volume);
		
		
	    String ip;
	    try {
	        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
	        while (interfaces.hasMoreElements()) {
	            NetworkInterface iface = interfaces.nextElement();
	            // filters out 127.0.0.1 and inactive interfaces
	            if (iface.isLoopback() || !iface.isUp())
	                continue;

	            Enumeration<InetAddress> addresses = iface.getInetAddresses();
	            while(addresses.hasMoreElements()) {
	                InetAddress addr = addresses.nextElement();
	                ip = addr.getHostAddress();
	                Utils.logi("%s - %s", ip, iface.getDisplayName());
	            }
	        }
	    } catch (SocketException e) {
	        throw new RuntimeException(e);
	    }
	}

	@Test
	void testSyndocApp() throws Exception {
		String webinf = "src/main/webapp/WEB-INF";

		Utils.logi("Loading PRV settings: %s", "settings.hub.json");
		AppSettings hset = AppSettings.load(webinf, "settings.hub.json");
		String p = new File(FilenameUtils.concat(webinf, hset.volume)).getAbsolutePath();
		System.setProperty(hset.vol_name, p);
		Utils.logi("HUB settings: %s", p);

		Utils.logi("Loading PRV settings: %s", "settings.prv.json");
		AppSettings pset = AppSettings.load(webinf, "settings.prv.json");
		p = new File(FilenameUtils.concat(webinf, pset.volume)).getAbsolutePath();
		System.setProperty(pset.vol_name, p);
		Utils.logi("PRV settings: %s", p);

		SynotierJettyApp hub = SynotierJettyApp.main_(hset.vol_name,
				new String[] {"-ip", hset.bindip,
							"-urlpath", "/jserv-album",
							"-peer-jservs", hset.webroots,
							"-install-key", "0123456789ABCDEF"});
		// hub.print();

		SynotierJettyApp prv = SynotierJettyApp.main_(pset.vol_name,
				new String[] {"-ip", pset.bindip,
							"-urlpath", "/jserv-album",
							"-port", pset.port(),
							"-peer-jservs", hset.webroots,
							"-install-key", "0123456789ABCDEF"});
		hub.print();
		prv.print();

		warn("Multiple synodes initialed in a single process, of which only the first (%s) syn-worker is enabled.",
				hub.syngleton.synode());
		warn("See ExpSynodetier.syncIns(secondes).");
		
		if (System.getProperty("wait-clients") != null)
			pause("Press enter to quite ...");
		else Utils.warn("To wait for clients accessing, define 'wait-clients'.");
	}

	void testVideoUp(boolean[] lights) throws SsException, IOException, AnsonException, TransException {
		String localFolder = "test/res";
		 int bsize = 72 * 1024;
		 String filename = "my.jpg";

		PhotoSyntier tier = (PhotoSyntier) new PhotoSyntier("test/album", errCtx)
								.blockSize(bsize);
		SessionClient ssclient = Clients.loginWithUri(tier.uri(), "ody", "123456", "device-test");

		List<IFileDescriptor> videos = new ArrayList<IFileDescriptor>();
		videos.add((ExpSyncDoc) new ExpSyncDoc()
					.fullpath(FilenameUtils.concat(localFolder, filename)));

		SessionInf photoUser = ssclient.ssInfo();
		photoUser.device = "device-test";

		tier.asyVideos(null, videos,
			(ix, total, c, pth, resp) -> {
				fail("The duplicate checking is not working on " + pth);
			},
			null,
			new ErrorCtx() {
				@Override
				public void err(MsgCode c, String msg, String ...args) {
					if (!MsgCode.exGeneral.equals(c))
						fail("Not expected error for this handling.");

					tier.del("device-test", videos.get(0).fullpath());
					try {
						tier.asyVideos(null, videos, null, null);

						PathsPage page = new PathsPage();
						for (int i = 0; i < videos.size(); i++) {
							ExpSyncDoc p = videos.get((int)i).syndoc(null);
							if (isblank(p.fullpath()))
								continue;
							else page.add(p.fullpath());
						}

						tier.synQueryPathsPage(page, Port.docstier);
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
