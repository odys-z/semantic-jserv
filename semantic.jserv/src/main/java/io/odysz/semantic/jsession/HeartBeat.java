package io.odysz.semantic.jsession;

import io.odysz.semantic.jprotocol.AnsonBody;
import io.odysz.semantic.jprotocol.AnsonMsg;

@SuppressWarnings("unused")
public class HeartBeat extends AnsonBody {

	private String ssid;
	private String uid;

	protected HeartBeat(AnsonMsg<AnsonBody> parent, String ssid, String uid) {
		super(parent, null); // Heartbeats don't need db access
		this.ssid = ssid;
		this.uid = uid;
	}
}