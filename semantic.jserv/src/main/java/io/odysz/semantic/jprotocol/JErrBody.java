package io.odysz.semantic.jprotocol;

import java.io.IOException;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

public class JErrBody extends JBody {

	protected String err;
	
	/**
	 * @param parent perent can be any type of JMessage (to be refined)
	 * @param error
	 */
	public JErrBody(JMessage<?> parent, String error) {
		super(parent, null);
		err = error;
	}

	@Override
	public void toJson(JsonWriter writer) throws IOException {
		writer.beginObject().endObject();
	}

	@Override
	public void fromJson(JsonReader reader) throws IOException {
		// TODO Auto-generated method stub
		
	}

}
