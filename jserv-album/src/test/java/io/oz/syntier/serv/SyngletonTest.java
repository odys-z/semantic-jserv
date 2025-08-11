package io.oz.syntier.serv;

import static io.odysz.common.Utils.awaitAll;
import static io.odysz.common.Utils.turnred;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.odysz.anson.AnsonException;
import io.odysz.common.FilenameUtils;
import io.oz.jserv.docs.syn.singleton.AppSettings;

class SyngletonTest {
	static final String settings_0 = "settings-0.json";
	
	boolean[] light = new boolean[] {false};

	@BeforeAll
	static void initEnv() throws IOException {
		System.setProperty("VOLUME_HUB", "../../../../volumes-0.7/volume-hub");
		System.setProperty("VOLUME_PRV", "../../../../volumes-0.7/volume-prv");
		
		// settings.json
		String backup = FilenameUtils.rel2abs(SynotierSettingsTest.webinf, "settings-test-app.json");
		String tsting = FilenameUtils.rel2abs(SynotierSettingsTest.webinf, "settings.json");
		Files.copy(Paths.get(backup), Paths.get(tsting), StandardCopyOption.REPLACE_EXISTING);
	}
	
	@Test
	void testExposeIP() throws InterruptedException, AnsonException, IOException {

		turnred(T_WebservExposer.lightExposed);

		AppSettings settings0 = AppSettings
				.load(SynotierSettingsTest.webinf, "settings.json");
		assertEquals(null, settings0.localIp);
		
		SynotierJettyApp app = SynotierJettyApp._main(null);
		
		app.afterboot();
		awaitAll(T_WebservExposer.lightExposed, -1);
		
		assertNotEquals(null, app.syngleton.settings.localIp);
	}

}
