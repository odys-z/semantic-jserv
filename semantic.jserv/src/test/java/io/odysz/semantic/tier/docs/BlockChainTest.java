package io.odysz.semantic.tier.docs;

import static io.odysz.common.LangExt.eq;

import static org.junit.jupiter.api.Assertions.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

import io.odysz.anson.AnsonException;
import io.odysz.common.AESHelper;
import io.odysz.semantics.IUser;
import io.odysz.semantics.SessionInf;
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
		/*
		 * 
		chain = new BlockChain("h_photos", tempDir, "dev-id", clientpath, "1911-10-10 10:10:10", "");
		 */
		chain = new BlockChain("h_photos", tempDir,
					"dev-id",
					new ExpSyncDoc()
						.clientpath(clientpath)
						.sharedate("1911-10-10 10:10:10"));

		assertTrue(eq("src/test/results/tester/uploading-temp/64A+B=C02/sdcard/0/Downloads/test.3gp", chain.outputPath)
				|| eq("src\\test\\results\\tester\\uploading-temp\\64A+B=C02\\sdcard\\0\\Downloads\\test.3gp", chain.outputPath));
		assertTrue(eq("/sdcard/0/Downloads/test.3gp", chain.doc.clientpath)
				|| eq("\\sdcard\\0\\Downloads\\test.3gp", chain.doc.clientpath));

		DocsResp resp = (DocsResp) new DocsResp()
				.doc( (ExpSyncDoc) new ExpSyncDoc()
						.fullpath(clientpath));

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

	/**
	 * FIXME this test won't work in Windows Maven CLI, via VS Code.
	 * 
	 * @throws IOException
	 * @throws TransException
	 * @throws InterruptedException
	 * @throws AnsonException
	 */
	@Test
	void tesBlockOrder() throws IOException, TransException, InterruptedException, AnsonException {
		System.setProperty("VOLUME_HOME", "src/test/results");

		// server side
		String clientpath = "/sdcard/0/Downloads/test.3gp";
		String ssid = "64A+B=C02";
		String uid = "tester";
		/*
		chain = new BlockChain("h_photos", IUser.tempDir("$VOLUME_HOME", uid, "temp", ssid),
				"dev-id2", clientpath, "1911-10-10 10:10:10", "");
		 */
		chain = new BlockChain(
					"h_photos",
					IUser.tempDir("$VOLUME_HOME", uid, "temp", ssid),
					"dev-id2",
					new ExpSyncDoc()
						.clientpath(clientpath)
						.sharedate("1911-10-10 10:10:10"));

		assertTrue(eq("src/test/results/tester/temp/64A+B=C02/sdcard/0/Downloads/test.3gp", chain.outputPath)
				|| eq("src\\test\\results\\tester\\temp\\64A+B=C02\\sdcard\\0\\Downloads\\test.3gp", chain.outputPath));
		assertTrue(eq("/sdcard/0/Downloads/test.3gp", chain.doc.clientpath)
				|| eq("\\sdcard\\0\\Downloads\\test.3gp", chain.doc.clientpath));

		DocsResp resp = (DocsResp) new DocsResp()
					.doc((ExpSyncDoc) new ExpSyncDoc().fullpath(clientpath));

		// client side 
		String b64;
		SessionInf ssinf = new SessionInf(uid, ssid, "local device");
		ssinf.device = "local junit";
		//  
		b64 = AESHelper.encode64("1. Hello\n".getBytes(StandardCharsets.UTF_8));
		DocsReq b0 = new DocsReq().blockUp(0, resp, new String(b64), ssinf);
		b64 = AESHelper.encode64("2. Bonjour\n".getBytes(StandardCharsets.UTF_8));
		DocsReq b1 = new DocsReq().blockUp(1, resp, new String(b64), ssinf);
		b64 = AESHelper.encode64("3. こんにちは\n".getBytes(StandardCharsets.UTF_8));
		DocsReq b2 = new DocsReq().blockUp(2, resp, new String(b64), ssinf);
		b64 = AESHelper.encode64("4. Привіт\n".getBytes(StandardCharsets.UTF_8));
		DocsReq b3 = new DocsReq().blockUp(3, resp, new String(b64), ssinf);
		b64 = AESHelper.encode64("5. 안녕하세요\n".getBytes(StandardCharsets.UTF_8));
		DocsReq b4 = new DocsReq().blockUp(4, resp, new String(b64), ssinf);
		b64 = AESHelper.encode64("6. שלום\n".getBytes(StandardCharsets.UTF_8));
		DocsReq b5 = new DocsReq().blockUp(5, resp, new String(b64), ssinf);

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
			assertEquals(l, "4. Привіт");
			l = br.readLine();
			assertEquals(l, "5. 안녕하세요");
			l = br.readLine();
			assertEquals(l, "6. שלום");
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
		/*
		chain = new BlockChain("h_photos",
				IUser.tempDir("$VOLUME_HOME", uid, "uploading-temp", ssid),
				"dev-id3", clientpath, "1911-10-10 10:10:10", "");
		*/
		chain = new BlockChain("h_photos",
					IUser.tempDir("$VOLUME_HOME", uid, "uploading-temp", ssid),
					"dev-id3",
					new ExpSyncDoc().clientpath(clientpath).sharedate("1911-10-10 10:10:10"));

		assertTrue(eq("src/test/results/tester/uploading-temp/64A+B=C02/sdcard/0/Downloads/test.aborting",
					  chain.outputPath)
				|| eq("src\\test\\results\\tester\\uploading-temp\\64A+B=C02\\sdcard\\0\\Downloads\\test.aborting",
					  chain.outputPath));
		assertTrue(eq("/sdcard/0/Downloads/test.aborting",
					  chain.doc.clientpath)
				|| eq("\\sdcard\\0\\Downloads\\test.aborting",
					  chain.doc.clientpath));

		String b64;
		SessionInf ssinf = new SessionInf(uid, ssid, "local device");
		ssinf.device = "local junit";
		
		DocsResp resp = (DocsResp) new DocsResp()
				.doc((ExpSyncDoc) new ExpSyncDoc().fullpath(clientpath));

		b64 = AESHelper.encode64("1. Hello\n".getBytes());
		DocsReq b0 = new DocsReq().blockUp(0, resp, b64, ssinf);
		chain.appendBlock(b0);
		DocsReq b2 = new DocsReq().blockUp(2, resp, b64, ssinf);
		chain.appendBlock(b2);
		
		assertTrue(Files.exists(Paths.get(chain.outputPath)));

		try {
			chain.abortChain();
			assertFalse(Files.exists(Paths.get(chain.outputPath)));
			assertTrue(Files.exists(Paths.get(chain.outputPath).getParent()));
		} catch (TransException e) {
			fail("not reporting the package lost error.");
		}
	}
}
