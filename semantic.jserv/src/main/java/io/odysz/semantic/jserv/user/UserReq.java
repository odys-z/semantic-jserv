package io.odysz.semantic.jserv.user;

import java.io.IOException;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import io.odysz.semantic.jprotocol.JBody;
import io.odysz.semantic.jprotocol.JMessage;

/**A stub for user's message body extension - subclassing {@link JBody}.
 * @author ody
 *
 */
public class UserReq extends JBody {

	public UserReq(JMessage<? extends JBody> parent, String conn) {
		super(parent, conn);
	}

	@Override
	public void toJson(JsonWriter writer) throws IOException {
		writer.beginObject();
		writer.name("data").value("user");
		writer.endObject();
	}

	@Override
	public void fromJson(JsonReader reader) throws IOException {
	}
}
