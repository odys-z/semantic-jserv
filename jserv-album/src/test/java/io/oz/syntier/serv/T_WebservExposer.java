package io.oz.syntier.serv;

import static io.odysz.common.Utils.turngreen;

import io.oz.jserv.docs.syn.singleton.AppSettings;

public class T_WebservExposer extends WebsrvLocalExposer {
	public static final boolean[] lightExposed = new boolean[] {false};

	@Override
	public AppSettings onExpose(AppSettings settings, String domain, String synode, boolean https) {
		super.onExpose(settings, domain, synode, https);
		turngreen(lightExposed);
		return settings;
	}
}
