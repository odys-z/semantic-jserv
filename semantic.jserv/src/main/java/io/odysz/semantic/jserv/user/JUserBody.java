package io.odysz.semantic.jserv.user;

import java.io.IOException;

import com.google.gson.stream.JsonWriter;

import io.odysz.semantic.jprotocol.JBody;
import io.odysz.semantic.jprotocol.JMessage;

/**A stub for user extension
 * @author ody
 *
 */
public class JUserBody extends JBody {

	public JUserBody(JMessage<? extends JBody> parent) {
		super(parent);
	}

	@Override
	public void toJson(JsonWriter writer) throws IOException {
		writer.beginObject();
		writer.name("data").value("user");
		writer.endObject();
	}
}
