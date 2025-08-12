package io.oz.syntier.serv;

import static io.odysz.common.Utils.turngreen;

import java.util.HashMap;

import io.oz.jserv.docs.syn.singleton.AppSettings;

public class T_WebservExposer extends WebsrvLocalExposer {
	public static final String hub = "HUB"; 
	public static final String prv = "PRV"; 
	public static final String mob = "MOB"; 

	@SuppressWarnings("serial")
	public static final HashMap<String, boolean[]> lights = new HashMap<String, boolean[]>() {
		{put(hub, new boolean[] {false});}
		{put(prv, new boolean[] {false});}
		{put(mob, new boolean[] {false});}
	};

	@Override
	public AppSettings onExpose(AppSettings settings, String domain, String synode, boolean https) {
		super.onExpose(settings, domain, synode, https);
		turngreen(lights.get(synode));
		return settings;
	}
}
