package io.odysz.jsample.cheap;

import java.io.IOException;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import io.odysz.semantic.jprotocol.JBody;
import io.odysz.semantic.jprotocol.JMessage;
import io.odysz.semantics.x.SemanticException;

public class CheapReq extends JBody {

	public String wftype;
	public String nodeDesc;

	protected CheapReq(JMessage<? extends JBody> parent, String conn) {
		super(parent, conn);
	}

	@Override
	public void toJson(JsonWriter writer) throws IOException {
		// TODO Auto-generated method stub

	}

	@Override
	public void fromJson(JsonReader reader) throws IOException, SemanticException {
		// TODO Auto-generated method stub

	}

}
