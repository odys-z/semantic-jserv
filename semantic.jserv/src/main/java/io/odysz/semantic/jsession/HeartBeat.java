package io.odysz.semantic.jsession;

import com.google.gson.Gson;

import io.odysz.semantic.jprotocol.JMessage;

public class HeartBeat extends JMessage {
	static Gson gson = new Gson();

	private String ssid;

	protected HeartBeat(String ssid) {
		super(Port.heartbeat);
		this.ssid = ssid;
	}

	public static String Req(String ssid) {
		HeartBeat beat = new HeartBeat(ssid);
		String req = gson.toJson(beat);
		return req;
	}
	
	public static HeartBeat onReq(String req) {
		HeartBeat msg = gson.fromJson(req, HeartBeat.class);
		return msg;
	}
	
	public static HeartBeat respond (String ssid) {
		return new HeartBeat(ssid);
	}

	public static void onResp (String resp) { }
}