package io.odysz.semantic.jsession;

import java.io.IOException;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import io.odysz.semantic.jprotocol.JBody;
import io.odysz.semantic.jprotocol.JMessage;

public class HeartBeat extends JBody {
//	static Gson gson = new Gson();

	private String ssid;
	private String uid;

	protected HeartBeat(JMessage<JBody> parent, String ssid, String uid) {
		super(parent);
//		super(Port.heartbeat);
		this.ssid = ssid;
		this.uid = uid;
	}

//	public static String Req(String ssid) {
//		HeartBeat beat = new HeartBeat(ssid);
//		String req = gson.toJson(beat);
//		return req;
//	}
	
//	public static HeartBeat onReq(String req) {
//		HeartBeat msg = gson.fromJson(req, HeartBeat.class);
//		return msg;
//	}
	
//	public static HeartBeat respond (String ssid) {
//		return new HeartBeat(ssid);
//	}

//	public static void onResp (String resp) { }

	@Override
	public void toJson(JsonWriter writer) throws IOException {
		writer.beginObject();
		writer.name("ssid").value(ssid);
		writer.name("uid").value(uid);
		writer.endObject();
	}

	@Override
	public void fromJson(JsonReader reader) throws IOException {
		reader.beginObject();
		readNv(reader.nextName(), reader.nextString());
		readNv(reader.nextName(), reader.nextString());
		reader.endObject();
	}

	private void readNv(String n, String v) {
		if ("ssid".equals(n))
			ssid = v;
		else if ("uid".equals(n))
			uid = v;
	}
}