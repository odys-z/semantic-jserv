package io.oz.syntier.serv;

import static org.junit.jupiter.api.Assertions.*;

import static io.odysz.common.LangExt.prefixWith;
import static io.odysz.common.Utils.pause;
import static io.odysz.common.Utils.warn;
import static io.oz.syntier.serv.SynotierJettyApp.boot;

import java.io.File;
import java.io.IOException;
import org.apache.commons.io_odysz.FilenameUtils;
import org.junit.jupiter.api.Test;

import io.odysz.anson.x.AnsonException;
import io.odysz.common.EnvPath;
import io.odysz.common.Utils;
import io.odysz.jclient.tier.ErrorCtx;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.meta.SynodeMeta;
import io.odysz.semantic.syn.Synode;
import io.odysz.semantic.util.DAHelper;
import io.odysz.transact.sql.Transcxt;
import io.oz.jserv.docs.syn.singleton.AppSettings;
import io.oz.syn.SynodeConfig;
import io.oz.syn.YellowPages;

class SynotierJettyAppTest {

	static final String webinf = "./src/main/webapp/WEB-INF";
	static final String config_xml = "config.xml";

	static final String settings_hub = "settings.hub.json";
	static final String settings_prv = "settings.prv.json";

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
	void testAppSettings() throws Exception {
		AppSettings hset = AppSettings.load(webinf, "settings.json");
		// String bindip = hset.bindip;
		// assertTrue(eq("127.0.0.1", bindip) || eq("0.0.0.0", bindip));
		assertEquals("../../../../volumes-0.7/volume-hub", hset.volume);

//	    String ip;
//	    try {
//	        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
//	        while (interfaces.hasMoreElements()) {
//	            NetworkInterface iface = interfaces.nextElement();
//
//	            Enumeration<InetAddress> addresses = iface.getInetAddresses();
////                InetAddress addr = addresses.nextElement();
////                ip = addr.getHostAddress();
//
//	            if (iface.isLoopback() || !iface.isUp() || iface.isVirtual())
//	                continue;
//	            if (addresses.hasMoreElements())
//	            	Utils.logi("Iface: %s", iface.getDisplayName());
//
//	            while(addresses.hasMoreElements()) {
//	                InetAddress addr = addresses.nextElement();
//	                ip = addr.getHostAddress();
//	                Utils.logi("\n%s [%s] %s - %s - %s",
//	                		iface.isUp() ? "UP" : "--", 
//	                		iface.isVirtual() ? "virtual" : "physic", ip,
//	                		iface.getHardwareAddress(), iface.getDisplayName());
//	            }
//	            Utils.logi("=========== ===========");
//	        }
//	    } catch (SocketException e) {
//	        throw new RuntimeException(e);
//	    }
	    

	   Utils.logi("Thanks to https://stackoverflow.com/a/38342964/7362888: %s",
			   AppSettings.getLocalIp());
	}
	
	@Test
	void testInstallJervs() throws Exception {
		resettingsKeys("settings.json");

		AppSettings settings = AppSettings.load(webinf, "settings.json");
		assertNull(settings.rootkey);
		assertEquals("0123456789ABCDEF", settings.installkey);

		@SuppressWarnings("unused")
		String jserv = AppSettings.checkInstall(SynotierJettyApp.servpath, webinf, config_xml, "settings.json");

		settings = AppSettings.load(webinf, "settings.json");
		assertNull(settings.installkey);
		assertEquals("0123456789ABCDEF", settings.rootkey);

		String $vol_home = "$" + settings.vol_name;
		YellowPages.load(FilenameUtils.concat(
				new File(".").getAbsolutePath(),
				webinf,
				EnvPath.replaceEnv($vol_home)));

		SynodeConfig cfg = YellowPages.synconfig();
		settings.setupJserv(cfg, SynotierJettyApp.servpath);

		Transcxt st = new DATranscxt(cfg.synconn);
		SynodeMeta m = new SynodeMeta(cfg.synconn);
		
		for (Synode peer : cfg.peers)
			assertTrue(prefixWith(
				DAHelper.getValstr(st, cfg.synconn, m, m.jserv, m.domain, cfg.domain, m.synoder, peer.synid), "http:"),
				"See WEB-INF/settings.json for what's expected.");
	}

	@Test
	void testSetupRunApp() throws Exception {
		resettingsKeys(settings_hub);
		resettingsKeys(settings_prv);

		preventSettingsError("settings.prv.json");

		AppSettings.checkInstall(SynotierJettyApp.servpath, webinf, config_xml, settings_hub);
		AppSettings.checkInstall(SynotierJettyApp.servpath, webinf, config_xml, settings_prv);
		/*
		Configs.init(webinf);
		AppSettings hubset = AppSettings
							.load(webinf, settings_hub)
							.setEnvs(true);
		AppSettings prvset = AppSettings
							.load(webinf, settings_prv)
							.setEnvs(true);

		Connects.init(webinf);

		hubset.setupdb(config_xml).save();
		prvset.setupdb(config_xml).save();
		*/
	
		SynotierJettyApp hub = boot(webinf, config_xml, settings_hub);
		SynotierJettyApp prv = boot(webinf, config_xml, settings_prv);

		hub.print();
		prv.print();

		warn("Multiple synodes initialed in a single process, of which only the first (%s) syn-worker is enabled.",
				hub.syngleton.synode());
		warn("See ExpSynodetier.syncIns(secondes).");
		
		if (System.getProperty("wait-clients") != null)
			pause("Press enter to quite ...");
		else Utils.warn("Quit test running. To wait for clients accessing, define 'wait-clients'.");
	}

	private void resettingsKeys(String settings_json) throws AnsonException, IOException {
		AppSettings s = AppSettings.load(webinf, settings_json);
		s.installkey = "0123456789ABCDEF";
		s.rootkey = null;
		s.save();
	}

	/**
	 * Prevent error of lack of environment variables when main_() is calling Connects.init().
	 * @throws IOException 
	 * @throws AnsonException 
	 */
	private void preventSettingsError(String settings_prefly) throws AnsonException, IOException {
		AppSettings set = AppSettings.load(webinf, settings_prefly);
		System.setProperty(set.vol_name, set.volume);
	}

	/*
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
	*/
}
