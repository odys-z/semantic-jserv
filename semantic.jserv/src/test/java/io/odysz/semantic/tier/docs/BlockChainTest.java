package io.odysz.semantic.tier.docs;

import static org.junit.jupiter.api.Assertions.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

import io.odysz.anson.x.AnsonException;
import io.odysz.common.AESHelper;
import io.odysz.semantic.jsession.SessionInf;
import io.odysz.semantics.IUser;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.x.TransException;

class BlockChainTest {
	BlockChain chain;
	
	/** This test may failed on Windows.
	 * @throws IOException
	 * @throws SemanticException 
	 * @throws AnsonException 
	 * @throws InterruptedException 
	 */
	@Test
	void testBlockChain() throws IOException, TransException, InterruptedException, AnsonException {
		System.setProperty("VOLUME_HOME", "src/test/results");

		// server side
		String clientpath = "/sdcard/0/Downloads/test.3gp";
		String ssid = "64A+B=C02";
		String uid = "tester";
		// chain = new BlockChain("$VOLUME_HOME", uid, ssid, clientpath, "1911-10-10 10:10:10");
		String tempDir = IUser.tempDir("$VOLUME_HOME", uid, "uploading-temp", ssid);
		chain = new BlockChain(tempDir, clientpath, "1911-10-10 10:10:10", "");

		assertEquals("src/test/results/tester/uploading-temp/64A+B=C02/sdcard/0/Downloads/test.3gp", chain.outputPath);
		assertEquals("/sdcard/0/Downloads/test.3gp", chain.clientpath);

		DocsResp resp = (DocsResp) new DocsResp().fullpath(clientpath);

		// client side 
		String b64;
		SessionInf ssinf = new SessionInf(uid, ssid, "local device");
		ssinf.device = "local junit";
		
		b64 = AESHelper.encode64("1. Hello\n".getBytes());
		DocsReq b0 = new DocsReq().blockUp(0, resp, b64, ssinf);
		b64 = AESHelper.encode64("2. Bonjour\n".getBytes());
		@SuppressWarnings("unused")
		DocsReq b1 = new DocsReq().blockUp(1, resp, b64, ssinf);
		b64 = AESHelper.encode64("3. こんにちは\n".getBytes());
		DocsReq b2 = new DocsReq().blockUp(2, resp, b64, ssinf);
		b64 = AESHelper.encode64("4. Привет\n".getBytes());
		DocsReq b3 = new DocsReq().blockUp(3, resp, b64, ssinf);
		b64 = AESHelper.encode64("5. 안녕하세요\n".getBytes());
		DocsReq b4 = new DocsReq().blockUp(4, resp, b64, ssinf);
		b64 = AESHelper.encode64("6. नमस्ते \n".getBytes());
		DocsReq b5 = new DocsReq().blockUp(5, resp, b64, ssinf);

		chain.appendBlock(b0)
			.appendBlock(b4)
			.appendBlock(b3)
			.appendBlock(b5) // lost: .appendBlock(b1)
			.appendBlock(b2);
		
		try {
			chain.closeChain();
			fail("Not reporting package lost.");
		} catch (TransException e) {
			assertFalse(Files.exists(Paths.get(chain.outputPath)));
			assertTrue(Files.exists(Paths.get(chain.outputPath).getParent()));
		}
	}

	@Test
	void tesBlockLost() throws IOException, TransException, InterruptedException, AnsonException {
		System.setProperty("VOLUME_HOME", "src/test/results");

		// server side
		String clientpath = "/sdcard/0/Downloads/test.3gp";
		String ssid = "64A+B=C02";
		String uid = "tester";
		chain = new BlockChain(IUser.tempDir("$VOLUME_HOME", uid, "temp", ssid), clientpath, "1911-10-10 10:10:10", "");

		assertEquals("src/test/results/tester/temp/64A+B=C02/sdcard/0/Downloads/test.3gp", chain.outputPath);
		assertEquals("/sdcard/0/Downloads/test.3gp", chain.clientpath);

		DocsResp resp = (DocsResp) new DocsResp().fullpath(clientpath);

		// client side 
		String b64;
		SessionInf ssinf = new SessionInf(uid, ssid, "local device");
		ssinf.device = "local junit";
		
		b64 = AESHelper.encode64("1. Hello\n".getBytes());
		DocsReq b0 = new DocsReq().blockUp(0, resp, b64, ssinf);
		b64 = AESHelper.encode64("2. Bonjour\n".getBytes());
		DocsReq b1 = new DocsReq().blockUp(1, resp, b64, ssinf);
		b64 = AESHelper.encode64("3. こんにちは\n".getBytes());
		DocsReq b2 = new DocsReq().blockUp(2, resp, b64, ssinf);
		b64 = AESHelper.encode64("4. Привет\n".getBytes());
		DocsReq b3 = new DocsReq().blockUp(3, resp, b64, ssinf);
		b64 = AESHelper.encode64("5. 안녕하세요\n".getBytes());
		DocsReq b4 = new DocsReq().blockUp(4, resp, b64, ssinf);
		b64 = AESHelper.encode64("6. नमस्ते \n".getBytes());
		DocsReq b5 = new DocsReq().blockUp(5, resp, b64, ssinf);

		chain.appendBlock(b0)
			.appendBlock(b4)
			.appendBlock(b3)
			.appendBlock(b5)
			.appendBlock(b1)
			.appendBlock(b2);
		
		chain.closeChain();

		String path = chain.outputPath;
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

	@Test
	void testAbortChain() throws IOException, TransException, InterruptedException, AnsonException {
		System.setProperty("VOLUME_HOME", "src/test/results");

		// server side
		String clientpath = "/sdcard/0/Downloads/test.aborting";
		String ssid = "64A+B=C02";
		String uid = "tester";
		chain = new BlockChain(IUser.tempDir("$VOLUME_HOME", uid, "uploading-temp", ssid), clientpath, "1911-10-10 10:10:10", "");

		assertEquals("src/test/results/tester/uploading-temp/64A+B=C02/sdcard/0/Downloads/test.aborting", chain.outputPath);
		assertEquals("/sdcard/0/Downloads/test.aborting", chain.clientpath);

		String b64;
		SessionInf ssinf = new SessionInf(uid, ssid, "local device");
		ssinf.device = "local junit";
		
		DocsResp resp = (DocsResp) new DocsResp().fullpath(clientpath);

		b64 = AESHelper.encode64("1. Hello\n".getBytes());
		DocsReq b0 = new DocsReq().blockUp(0, resp, b64, ssinf);
		chain.appendBlock(b0);
		DocsReq b2 = new DocsReq().blockUp(2, resp, b64, ssinf);
		chain.appendBlock(b2);
		
		assertTrue(Files.exists(Paths.get(chain.outputPath)));

		try {
			chain.abortChain();
			fail("not reporting the package lost error.");
		} catch (TransException e) {
			assertFalse(Files.exists(Paths.get(chain.outputPath)));
			assertTrue(Files.exists(Paths.get(chain.outputPath).getParent()));
		}
	}
}
