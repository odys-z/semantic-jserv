package io.odysz.semantic.jprotocol;

import java.io.IOException;

import com.google.gson.stream.JsonWriter;

public class JErrBody extends JBody {

	protected String err;
	
	/**
	 * @param parent perent can be any type of JMessage (to be refined)
	 * @param error
	 */
	public JErrBody(JMessage<?> parent, String error) {
		super(parent);
		err = error;
	}

	@Override
	public void toJson(JsonWriter writer) throws IOException {
		writer.beginObject().endObject();
	}

}
