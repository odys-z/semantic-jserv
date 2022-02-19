package io.odysz.semantic.tier.docs;

import static org.junit.jupiter.api.Assertions.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import org.junit.jupiter.api.Test;

import io.odysz.anson.x.AnsonException;
import io.odysz.common.AESHelper;
import io.odysz.semantics.x.SemanticException;

class BlockChainTest {
	BlockChain chain;
	
	/** This test may failed on Windows.
	 * @throws IOException
	 * @throws SemanticException 
	 * @throws AnsonException 
	 * @throws InterruptedException 
	 */
	@Test
	void testBlockChain() throws IOException, SemanticException, InterruptedException, AnsonException {
		ClientDocUser user = new ClientDocUser("tester", "not used", "local device");
		
		chain = new BlockChain("src/test/results", "session-test", "/sdcard/0/Downloads/test.3gp");
		// FIXME security breach?
		String id = chain.id();

		assertEquals("src/test/results/session-test/sdcard/0/Downloads/test.3gp", id);

		DocsResp resp = new DocsResp().chainId(id);
		String b64;
		b64 = AESHelper.encode64("1. Hello\n".getBytes());
		DocsReq b0 = new DocsReq().blockUp(0, resp, b64, user);
		b64 = AESHelper.encode64("2. Bonjour\n".getBytes());
		DocsReq b1 = new DocsReq().blockUp(1, resp, b64, user);
		b64 = AESHelper.encode64("3. こんにちは\n".getBytes());
		DocsReq b2 = new DocsReq().blockUp(2, resp, b64, user);
		b64 = AESHelper.encode64("4. Привет\n".getBytes());
		DocsReq b3 = new DocsReq().blockUp(3, resp, b64, user);
		b64 = AESHelper.encode64("5. 안녕하세요\n".getBytes());
		DocsReq b4 = new DocsReq().blockUp(4, resp, b64, user);
		b64 = AESHelper.encode64("6. नमस्ते \n".getBytes());
		DocsReq b5 = new DocsReq().blockUp(5, resp, b64, user);

		chain.appendBlock(b0)
			.appendBlock(b4)
			.appendBlock(b3)
			.appendBlock(b5)
			.appendBlock(b1)
			.appendBlock(b2);
		
		chain.closeChain();

		String path = chain.id();
		File f = new File(path);
		FileReader fr = new FileReader(f);
		BufferedReader br = new BufferedReader(fr);

		try {
			String l;
			l = br.readLine();
			assertEquals(l, "1. Hello");
			l = br.readLine();
			assertEquals(l, "2. Bonjour");
			l = br.readLine();
			assertEquals(l, "3. こんにちは");
			l = br.readLine();
			assertEquals(l, "4. Привет");
			l = br.readLine();
			assertEquals(l, "5. 안녕하세요");
			l = br.readLine();
			assertEquals(l, "6. नमस्ते ");
		} finally {
			br.close();
			fr.close();
		}
	}

}
