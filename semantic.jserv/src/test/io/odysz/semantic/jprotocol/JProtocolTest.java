package io.odysz.semantic.jprotocol;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import io.odysz.common.Utils;
import io.odysz.semantic.jprotocol.JMessage.Port;
import io.odysz.semantic.jserv.R.QueryReq;

class JProtocolTest {
	static Gson gson;
	
	@BeforeEach
	void setUp() throws Exception {
		Utils.printCaller(false);
		gson = new Gson();
	}

	/**This test case showing Gson is not so powerful.
	 * 1. Gson don't handle super class;<br>
	 * 2. Gson needing customer (de)serializer to handle integers:<pre>
[{"vestion":"1.0","Port":"heartbeat","seq":142}]

-------------------------------
{a=1, b=y}
{a=x, b=y, c=[1.0, 2.0]}
{a=u, b=v, c=[8.0, 9.0]}

-------------------------------
[{"a":"1","b":"y"},{"a":"x","b":"y","c":["1.0","2.0"]},{"a":"u","b":"v","c":[8.0,9.0]}]

-------------------------------
{a=1, b=y}
{a=x, b=y, c=[1.0, 2.0]}
{a=u, b=v, c=[8.0, 9.0]}</pre>
	 * This shows Gson can't handle inner type of members.
	 * Axby.c is a list of integer, but Gson only deserializing according to type of c's string format, either strings or floats.<br>
	 * <img width='1391' src='it-type list-of-string.png'/>
	 */
	@Test
	void testGsonLimitation() {
		ArrayList<JMessage> msgs = new ArrayList<JMessage>();
		msgs.add(new JMessage(Port.heartbeat));
		Utils.logi(JProtocolTest.<JMessage>parse(msgs));

		Utils.logi("\n-------------------------------");
		// print:
		// {a=1, b=y}
		// {a=x, b=y, c=[1, 2]}
		// {a=u, b=v, c=[8.0, 9.0]}
		// This shows Gson can't handle inner type of members.
		// Axby.c is a list of integer, but Gson only deserializing according to type of c's string format, either strings or floats.
		ArrayList<Axby> s = (ArrayList<Axby>) JProtocolTest.<Axby>convert(
				"[{\"a\": \"1\", \"b\": \"y\"},"
				+ "{\"a\": \"x\", b: \"y\", c: [\"1.0\",\"2.0\"]},"
				+ "{\"a\": \"u\", b: \"v\", c: [8,9]}]"
				);
		Utils.<Axby>logi(s);
		
		Utils.logi("\n-------------------------------");
		String str = JProtocolTest.<Axby>parse(s);
		Utils.logi(str);


		Utils.logi("\n-------------------------------");
		List<JMessage> t = JProtocolTest.convert(str);
		Utils.<JMessage>logi((ArrayList<JMessage>)t);
	}

	static class Axby extends JMessage {
		public Axby(Port msgCode) {
			super(Port.heartbeat);
		}

		String a;
		String b;
		
		/**In <a href='https://howtodoinjava.com/apache-commons/google-gson-tutorial-convert-java-object-to-from-json/#serialization_deserialization'>
		 * [Google Gson Tutorial : Convert Java Object to / from JSON]</a> 
		 * section 6. Gson custom serialization and deserialization, 
		 * the author may providing a inner conversion method for generic type.
		 */
		ArrayList<Integer> c;
	}

	public static <T> List<T> convert(String str) {
		Type t = new TypeToken<ArrayList<T>>() {}.getType();
//		Type t = new TypeToken<List<JMessage>>() {}.getType();
		List<T> j = gson.fromJson(str, t);
		return j;
	}

	public static <T> String parse(List<T> s) {
		Type t = new TypeToken<List<T>>() {}.getType();
		String j = gson.toJson(s, t);
		return j;
	}
	
	
	@Test
	void tryGsonStream() throws IOException {
		Utils.logi("\n --------------- deserialized -------------------------------");
		StringBuffer sbf = new StringBuffer("[{\"vestion\":\"1.0\",\"Port\":\"heartbeat\",\"seq\":142}]");
        byte[] bytes = sbf.toString().getBytes();
        InputStream in = new ByteArrayInputStream(bytes);
		List<JMessage> msgs = new JHelper<JMessage>().readJsonStream(in, JMessage.class);
        Utils.<JMessage>logi(msgs);
        
		Utils.logi("\n -------------- output stream -------------------------------");
		msgs.get(0).incSeq().incSeq().incSeq();
        OutputStream output = new OutputStream() {
            private StringBuilder string = new StringBuilder();
            @Override
            public void write(int b) throws IOException {
                this.string.append((char) b );
            }

            //Netbeans IDE automatically overrides this toString()
            public String toString(){
                return this.string.toString();
            }
        };
        writeJsonStream(output, msgs);
        Utils.logi(output.toString());
        
		Utils.logi("\n --------------- subclass Axby ------------------------------");
		sbf = new StringBuffer("[{\"a\":\"1\",\"b\":\"y\"},{\"a\":\"x\",\"b\":\"y\",\"c\":[\"1.0\",\"2.0\"]},{\"a\":\"u\",\"b\":\"v\",\"c\":[8.0,9.0]}]");
        bytes = sbf.toString().getBytes();
        in = new ByteArrayInputStream(bytes);
		List<Axby> xas = new JHelper<Axby>().readJsonStream(in, Axby.class);
        Utils.<Axby>logi(xas);
	}
	
	public void writeJsonStream(OutputStream out, List<JMessage> messages) throws IOException {
        JsonWriter writer = new JsonWriter(new OutputStreamWriter(out, "UTF-8"));
        writer.setIndent("  ");
        writer.beginArray();
        for (JMessage message : messages) {
            gson.toJson(message, JMessage.class, writer);
        }
        writer.endArray();
        writer.close();
    }
	
}
