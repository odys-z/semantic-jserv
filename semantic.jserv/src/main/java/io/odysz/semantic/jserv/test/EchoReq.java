package io.odysz.semantic.jserv.test;


import java.io.IOException;

import com.google.gson.stream.JsonWriter;

import io.odysz.semantic.jprotocol.JBody;
import io.odysz.semantic.jprotocol.JMessage;

public class EchoReq extends JBody {

	public EchoReq(JMessage<? extends JBody> parent) {
		super(parent);
	}

	@Override
	public void toJson(JsonWriter writer) throws IOException {
		writer.beginObject();
		writer.name("echo").value("ping");
		writer.endObject();
	}
}
