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
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import io.odysz.common.Utils;
import io.odysz.semantic.jprotocol.JMessage.Port;
import io.odysz.semantic.jserv.R.QueryReq;
import io.odysz.semantics.x.SemanticException;

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
	void tryGsonStream() throws IOException, SemanticException, ReflectiveOperationException {
		Utils.logi("\n --------------- deserialized -------------------------------");
		StringBuffer sbf = new StringBuffer("{\"header\":{}, body:[{\"vestion\":\"1.0\",\"Port\":\"heartbeat\",\"seq\":142}]}") ;
        byte[] bytes = sbf.toString().getBytes();
        InputStream in = new ByteArrayInputStream(bytes);
		JHelper<JMessage> jhelper = new JHelper<JMessage>();
		JMessage msgs = new JHelper<JMessage>().readJson(in, JMessage.class);
        Utils.<JMessage>logi((List<JMessage>)msgs.body);
        
		Utils.logi("\n -------------- output stream -------------------------------");
		msgs.body.get(0).incSeq().incSeq().incSeq();
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
        writeJsonStream(output, (List<JMessage>)msgs.body);
        Utils.logi(output.toString());
        
		Utils.logi("\n --------------- subclass Axby ------------------------------");
		sbf = new StringBuffer("{\"header\": {}, "
				+ "\"body\": [{\"a\":\"1\",\"b\":\"y\"},{\"a\":\"x\",\"b\":\"y\",\"c\":[\"1.0\",\"2.0\"]},{\"a\":\"u\",\"b\":\"v\",\"c\":[8.0,9.0]}]}");
        bytes = sbf.toString().getBytes();
        in = new ByteArrayInputStream(bytes);
		Axby xas = new JHelper<Axby>().readJson(in, Axby.class);
        Utils.<Axby>logi((List<Axby>)xas.body);
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
	
	@Test
	void tryPeek() throws SemanticException, IOException {
		Utils.logi("\n ------------------- try peek ---------------------------");
		StringBuffer sbf = new StringBuffer("{\"header\":{}, \"body\":[{}] }")  ;
        byte[] bytes = sbf.toString().getBytes();
        InputStream in = new ByteArrayInputStream(bytes);
		// JHelper<JMessage> jhelper = new JHelper<JMessage>();
		// JMessage msgs = new JHelper<JMessage>().readJson(in, JMessage.class)
        JHeader jheader = null; 
        JHeader jbody = null; 
		JsonReader reader = new JsonReader(new InputStreamReader(in, "UTF-8"));
		reader.beginObject();
		JsonToken token = reader.peek();
		while (token != null && token != JsonToken.END_DOCUMENT) {
			switch (token) {
			case NAME:
				String name = reader.nextName();
				if (name != null && "header".equals(name.trim().toLowerCase()))
					jheader = gson.fromJson(reader, JHeader.class);
				else if (name != null && "body".equals(name.trim().toLowerCase()))
//					jbody = gson.fromJson(reader, JHeader.class);
//					reader.nextName();
					;
				else {
					reader.close();
					throw new SemanticException("Can't parse json message: , ");
				}
				break;
			case BEGIN_ARRAY:
				reader.beginArray();
				break;
			case END_ARRAY:
				reader.endArray();
				break;
			case END_OBJECT:
				reader.endObject();
				break;
			default:
				reader.close();
				throw new SemanticException("Can't parse json message: , ");
			}
			token = reader.peek();
		}
//		reader.endObject();
		reader.close();

        Utils.<JMessage>logi(jheader.toString());
        Utils.<JMessage>logi(jbody.toString());
 	
	}
	
}
