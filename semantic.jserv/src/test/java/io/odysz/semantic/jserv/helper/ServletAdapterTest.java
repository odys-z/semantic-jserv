package io.odysz.semantic.jserv.helper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

import org.junit.jupiter.api.Test;

import com.google.gson.Gson;
import com.google.gson.stream.JsonWriter;

import io.odysz.common.Utils;
import io.odysz.semantic.jprotocol.JMessage.MsgCode;
import io.odysz.semantic.jprotocol.JMessage.Port;
import io.odysz.semantic.jprotocol.JProtocol;
import io.odysz.semantics.SemanticObject;

class ServletAdapterTest {
	static Gson gson = new Gson();

	@Test
	void test() throws IOException {
		Utils.printCaller(false);
		
		OutputStream os = new ByteArrayOutputStream();
		SemanticObject msg = JProtocol.err(Port.query, MsgCode.exGeneral, "errrrr");
		write(os, msg);
		os.close();
		Utils.logi(os.toString());
	}

	public static void write(OutputStream os, 
			SemanticObject msg) throws IOException {
		JsonWriter writer = new JsonWriter(new OutputStreamWriter(os, "UTF-8"));
		// gson.toJson(msg, msg.getClass(), writer);
		writer.beginObject();
		writer.name("port").value(msg.getString("port"));
		writer.name("code").value(msg.getString("code"));
		if (msg.getType("error") != null) {
			writer.name("error"); writer.value(msg.getString("error"));
		}
		writer.endObject();
		os.flush();
		writer.close();
	}
}
