package io.odysz.semantic.jserv.echo;


import io.odysz.semantic.jprotocol.AnsonBody;
import io.odysz.semantic.jprotocol.AnsonMsg;

public class EchoReq extends AnsonBody {

	public EchoReq(AnsonMsg<? extends AnsonBody> parent) {
		super(parent, null);
	}

//	@Override
//	public void toJson(JsonWriter writer, JOpts opts) throws IOException {
//		writer.beginObject();
//		writer.name("echo").value("ping");
//		writer.endObject();
//	}
//
//	@Override
//	public void fromJson(JsonReader reader) throws IOException {
//		// TODO Auto-generated method stub
//		
//	}
}
