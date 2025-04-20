package io.oz.syntier.serv;

import static io.odysz.common.LangExt.f;

import java.io.IOException;

import io.oz.jserv.docs.syn.singleton.AppSettings;
import io.oz.jserv.docs.syn.singleton.ISettingsLoaded;

public class WebsrvLocalHandler implements ISettingsLoaded {

	@Override
	public void onload(AppSettings settings) throws IOException {
		if (settings.envars != null) {
				String envkey = settings.onloadHandler[1];
				String ipport = f(settings.onloadHandler[2], AppSettings.getLocalIp());
				settings.envars.put(envkey, ipport);
			;
		}
	}

}
