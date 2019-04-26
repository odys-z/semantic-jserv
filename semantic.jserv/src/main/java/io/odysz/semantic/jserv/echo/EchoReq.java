package io.odysz.semantic.jserv.echo;


import java.io.IOException;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import io.odysz.semantic.jprotocol.JBody;
import io.odysz.semantic.jprotocol.JMessage;

public class EchoReq extends JBody {

	public EchoReq(JMessage<? extends JBody> parent) {
		super(parent, null);
	}

	@Override
	public void toJson(JsonWriter writer) throws IOException {
		writer.beginObject();
		writer.name("echo").value("ping");
		writer.endObject();
	}

	@Override
	public void fromJson(JsonReader reader) throws IOException {
		// TODO Auto-generated method stub
		
	}
}
