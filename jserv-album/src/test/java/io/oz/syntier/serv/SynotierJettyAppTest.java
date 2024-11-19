package io.oz.syntier.serv;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;

import static io.odysz.common.Utils.pause;

import org.junit.jupiter.api.Test;

@SuppressWarnings("unused")
class SynotierJettyAppTest {

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

}
