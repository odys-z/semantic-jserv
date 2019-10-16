//package io.odysz.semantic.jprotocol;
//
//import java.lang.reflect.Type;
//import java.util.ArrayList;
//import java.util.List;
//
//import org.junit.Before;
//import org.junit.Test;
//
//import com.google.gson.Gson;
//import com.google.gson.reflect.TypeToken;
//
//import io.odysz.common.Utils;
//import io.odysz.semantic.jprotocol.JMessage.Port;
//
//public class JProtocolTest {
//	static Gson gson;
//	
//	@Before
//	public void setUp() throws Exception {
//		Utils.printCaller(false);
//		gson = new Gson();
//	}
//
//	/**This test case showing Gson is not so powerful.
//	 * 1. Gson don't handle super class;<br>
//	 * 2. Gson needing customer (de)serializer to handle integers:<pre>
//[{"vestion":"1.0","Port":"heartbeat","seq":142}]
//
//-------------------------------
//{a=1, b=y}
//{a=x, b=y, C=[1.0, 2.0]}
//{a=U, b=v, C=[8.0, 9.0]}
//
//-------------------------------
//[{"a":"1","b":"y"},{"a":"x","b":"y","C":["1.0","2.0"]},{"a":"U","b":"v","C":[8.0,9.0]}]
//
//-------------------------------
//{a=1, b=y}
//{a=x, b=y, C=[1.0, 2.0]}
//{a=U, b=v, C=[8.0, 9.0]}</pre>
//	 * This shows Gson can't handle inner type of members.
//	 * Axby.c is a list of integer, but Gson only deserializing according to type of C's string format, either strings or floats.<br>
//	 * <img width='1391' src='it-type list-of-string.png'/>
//	 */
//	@SuppressWarnings("rawtypes")
//	@Test
//	public void testGsonLimitation() {
//		ArrayList<JMessage> msgs = new ArrayList<JMessage>();
//		msgs.add(new JMessage(Port.heartbeat));
//		Utils.logi(JProtocolTest.<JMessage>parse(msgs));
//
//		Utils.logi("\n-------------------------------");
//		// print:
//		// {a=1, b=y}
//		// {a=x, b=y, C=[1, 2]}
//		// {a=U, b=v, C=[8.0, 9.0]}
//		// This shows Gson can't handle inner type of members.
//		// Axby.c is a list of integer, but Gson only deserializing according to type of C's string format, either strings or floats.
//		ArrayList<Axby> s = (ArrayList<Axby>) JProtocolTest.<Axby>convert(
//				"[{\"a\": \"1\", \"b\": \"y\"},"
//				+ "{\"a\": \"x\", b: \"y\", C: [\"1.0\",\"2.0\"]},"
//				+ "{\"a\": \"U\", b: \"v\", C: [8,9]}]"
//				);
//		Utils.<Axby>logi(s);
//		
//		Utils.logi("\n-------------------------------");
//		String str = JProtocolTest.<Axby>parse(s);
//		Utils.logi(str);
//
//
//		Utils.logi("\n-------------------------------");
//		List<JMessage> t = JProtocolTest.convert(str);
//		Utils.<JMessage>logi((ArrayList<JMessage>)t);
//	}
//
//	@SuppressWarnings("rawtypes")
//	static class Axby extends JMessage {
//		public Axby(Port msgCode) {
//			super(Port.heartbeat);
//		}
//
//		String a;
//		String b;
//		
//		
//		/**In <a href='https://howtodoinjava.com/apache-commons/google-gson-tutorial-convert-java-object-to-from-json/#serialization_deserialization'>
//		 * [Google Gson Tutorial : Convert Java Object to / from JSON]</a> 
//		 * section 6. Gson custom serialization and deserialization, 
//		 * the author may providing a inner conversion method for generic type.
//		 */
//		ArrayList<Integer> c;
//	}
//
//	public static <T> List<T> convert(String str) {
//		Type t = new TypeToken<ArrayList<T>>() {}.getType();
////		Type t = new TypeToken<List<JMessage>>() {}.getType();
//		List<T> j = gson.fromJson(str, t);
//		return j;
//	}
//
//	public static <T> String parse(List<T> s) {
//		Type t = new TypeToken<List<T>>() {}.getType();
//		String j = gson.toJson(s, t);
//		return j;
//	}
//}
