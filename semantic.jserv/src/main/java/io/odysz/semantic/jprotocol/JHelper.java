package io.odysz.semantic.jprotocol;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import io.odysz.semantic.jprotocol.JProtocol.Session.Msg;
import io.odysz.semantic.jsession.SUser;
import io.odysz.semantics.SemanticObject;

public class JHelper<T> {

	public static SemanticObject OK(String code, SUser iruser, SemanticObject... msg) {
		return null;
	}

	public static SemanticObject err(String errChk, String... msg) {
		return null;
	}

	public static SemanticObject parse(String jstr) {
		// TODO Auto-generated method stub
		return null;
	}

	public static Object OK(String code, SUser login, String[] props) {
		return null;
	}

	private Gson gson = new Gson();

	public void writeJsonStream(OutputStream out, T msg) throws IOException {
        JsonWriter writer = new JsonWriter(new OutputStreamWriter(out, "UTF-8"));
        Type t = new TypeToken<T>() {}.getType();
        gson.toJson(msg, t, writer);
        writer.close();
	}

	public void writeJsonStream(OutputStream out, List<T> messages) throws IOException {
        JsonWriter writer = new JsonWriter(new OutputStreamWriter(out, "UTF-8"));
        writer.setIndent("  ");
        writer.beginArray();
        Type t = new TypeToken<T>() {}.getType();
        for (T message : messages) {
            gson.toJson(message, t, writer);
        }
        writer.endArray();
        writer.close();
    }

	/**read json stream int list of elemClass: ArrayList&lt;elemClass&gt;.<br>
	 * A tried version working but returned T is not JMessage:<pre>
	public List&lt;T&gt; readJsonStream(InputStream in) throws IOException {
        JsonReader reader = new JsonReader(new InputStreamReader(in, "UTF-8"));
        Type t = new TypeToken&lt;ArrayList&lt;T&gt;&gt;() {}.getType();
        List&lt;T&gt; messages = gson.fromJson(reader, t);
 		reader.close();
 		return messages;
 	}</pre>
	 * @param in
	 * @param elemClass
	 * @return
	 * @throws IOException
	 */
	public List<T> readJsonStream(InputStream in, Class<? extends T> elemClass) throws IOException {
        JsonReader reader = new JsonReader(new InputStreamReader(in, "UTF-8"));
        reader.beginArray();
        Type t = new TypeToken<T>() {}.getType();
        List<T> messages = new ArrayList<T>();
        while (reader.hasNext()) {
            T message = gson.fromJson(reader, elemClass);
            messages.add(message);
        }

        reader.endArray();
        reader.close();
        return messages;
 
    }

	public static void println(JMessage msg) {
		
	}

}

