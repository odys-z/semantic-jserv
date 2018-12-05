package io.odysz.semantic.jprotocol;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.odysz.common.Utils;
import io.odysz.semantics.SemanticObject;

class JProtocolTest {

	@BeforeEach
	void setUp() throws Exception {
	}

	@Test
	void testConvert() {
		List<Axby> s = JProtocol.<Axby>convert("[{\"a\": \"1\", \"b\": \"y\"}, {\"a\": \"x\", b: \"y\", c: [1,2]}]");
		Utils.<Axby>logi((ArrayList<Axby>)s);
		String str = JProtocol.<Axby>parse(s);
		Utils.logi(str);
		List<Axby> t = JProtocol.<Axby>convert(str);
		Utils.<Axby>logi((ArrayList<Axby>)t);
	}

	static class Axby {
		String a;
		String b;
		
		/**In <a href='https://howtodoinjava.com/apache-commons/google-gson-tutorial-convert-java-object-to-from-json/'>
		 * [Google Gson Tutorial : Convert Java Object to / from JSON]</a> 
		 * section 6. Gson custom serialization and deserialization, 
		 * the author may providing a inner conversion method for generic type.
		 */
		ArrayList<Integer> c;
	}
}
