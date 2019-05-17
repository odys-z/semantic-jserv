package io.odysz.semantic.jsession;

import java.io.IOException;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import io.odysz.semantic.jprotocol.JBody;
import io.odysz.semantic.jprotocol.JMessage;
import io.odysz.semantic.jprotocol.JOpts;

public class HeartBeat extends JBody {
//	static Gson gson = new Gson();

	private String ssid;
	private String uid;

	protected HeartBeat(JMessage<JBody> parent, String ssid, String uid) {
		super(parent, null); // Heartbeats don't need db access
		this.ssid = ssid;
		this.uid = uid;
	}

	@Override
	public void toJson(JsonWriter writer, JOpts opts) throws IOException {
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