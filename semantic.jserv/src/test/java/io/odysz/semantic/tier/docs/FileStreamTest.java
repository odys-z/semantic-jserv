package io.odysz.semantic.tier.docs;

import static org.junit.jupiter.api.Assertions.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;

import org.junit.jupiter.api.Test;

import io.odysz.common.EnvPath;

class FileStreamTest {
	
	@Test
	void testWriteRead() throws IOException {
		File f = new File("src/test/res");
		String dir = f.getAbsolutePath();
		
		String path = EnvPath.decodeUri(dir, "test.in");
		FileInputStream in = new FileInputStream(path);
		path = EnvPath.decodeUri(dir, "test.saved");
		FileStream.writeFile(in, path);

		path = EnvPath.decodeUri(dir, "test.response");
		OutputStream target = new FileOutputStream(path);
		path = EnvPath.decodeUri(dir, "test.saved");
		FileStream.sendFile(target, path);
		
		path = EnvPath.decodeUri(dir, "test.response");
		f = new File(path);
		FileReader fr = new FileReader(f);
		BufferedReader br = new BufferedReader(fr);
		String l = br.readLine();
		assertEquals(l, "USACO");
	}

}
