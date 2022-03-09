package io.odysz.semantic.jsession;

import io.odysz.semantic.jprotocol.AnsonBody;
import io.odysz.semantic.jprotocol.AnsonMsg;

@SuppressWarnings("unused")
public class HeartBeat extends AnsonBody {

	private String ssid;
	private String uid;

	public HeartBeat(AnsonMsg<AnsonBody> parent, String uri, String ssid, String uid) {
		super(parent, uri);
		this.ssid = ssid;
		this.uid = uid;
	}
}