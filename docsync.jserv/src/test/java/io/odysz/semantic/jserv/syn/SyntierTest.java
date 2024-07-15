package io.odysz.semantic.jserv.syn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.Date;

import org.apache.commons.io_odysz.FilenameUtils;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import io.odysz.anson.x.AnsonException;
import io.odysz.common.Configs;
import io.odysz.common.LangExt;
import io.odysz.jclient.tier.ErrorCtx;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.ext.DocTableMeta;
import io.odysz.semantic.ext.DocTableMeta.Share;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
import io.odysz.semantic.jprotocol.AnsonResp;
import io.odysz.semantic.jprotocol.JProtocol.OnOk;
import io.odysz.semantic.jsession.AnSession;
import io.odysz.semantic.tier.docs.DocsResp;
import io.odysz.semantic.tier.docs.SyncDoc;
import io.odysz.transact.x.TransException;
import io.oz.jserv.docsync.ZSUNodes.AnDevice;
import io.oz.jserv.docsync.ZSUNodes.Kharkiv;

/**
 * 4 Syntiers running on a static DA helper, but communicate over
 * Semnatic.jserv protocol layer.
 * 
 * @author odys-z@github.com
 */
class SyntierTest {
	public static final String clientUri = "/jnode";
	public static final String webRoot = "./src/test/res/WEB-INF";
	public static final String volumeDir = "./src/test/res/volume";


	static String conn;
	static ErrorCtx errLog;

	static DATranscxt defltSt;
	static DocTableMeta docm;
	
	static String passwd = "abc";
	static String domain   = "zsu";
	
	static final int X = 0;
	static final int Y = 1;
	static final int Z = 2;
	static final int W = 3;
	
	static Syntier[] tiers = new Syntier[4];
	
	static {
		try {
			conn = "main-sqlite";

			Path currentRelativePath = Paths.get("");
			String p = currentRelativePath.toAbsolutePath().toString();
			System.setProperty("VOLUME_HOME", FilenameUtils.concat(p, volumeDir));

			// Test with docker?
			String wwwinf = FilenameUtils.concat(p, webRoot);
			Configs.init(wwwinf);
			Connects.init(wwwinf);
			defltSt = new DATranscxt(Connects.defltConn());
			AnsonMsg.understandPorts(Port.docsync);
			AnSession.init(defltSt);
			
			for (int tx = 0; tx < tiers.length; tx++) {
				tiers[tx] = new Syntier()
						.regist(docm);
			}

			errLog = new ErrorCtx() {
				@Override
				public void err(MsgCode code, String msg, String...args) {
					fail(msg);
				}
			};
		} catch (TransException | SQLException | SAXException | IOException e) {
			e.printStackTrace();
		}
	}

	@Test
	void testJoin() {
		Syntier x = tiers[X];

		Syntier y = tiers[Y].joinpeer(tiers[X].jserv,
				tiers[Y].synode, passwd);

		assertEquals(x.nyquence(domain, y).n, y.n0(domain).n);

		Syntier z = tiers[Z].joinpeer( x.jserv,
				tiers[Z].synode, passwd);
		
		assertEquals(x.nyquence(domain, z).n, z.n0(domain).n);
	}

	@Test
	void testPhotos() {
		if (tiers[X].nyquence(domain).size() <= 1)
			testJoin();
	}
	
	static String videoUpByApp(DocTableMeta meta) throws Exception {
		int bsize = 72 * 1024;

		// app is using Synclientier for synchronizing 
		Doclientier apptier = new Doclientier(clientUri, errLog)
				.tempRoot("app.kharkiv")
				.login(AnDevice.userId, AnDevice.device, AnDevice.passwd)
				.blockSize(bsize);

		apptier.synDel(meta.tbl, AnDevice.device, AnDevice.localFile);
		
		SyncDoc doc = (SyncDoc) new SyncDoc()
					.share(apptier.robot.uid(), Share.pub, new Date())
					.folder(Kharkiv.folder)
					.fullpath(AnDevice.localFile);
		DocsResp resp = apptier.synInsertDoc(meta.tbl, doc, new OnOk() {
			@Override
			public void ok(AnsonResp resp)
					throws IOException, AnsonException {

			}
		});

		assertNotNull(resp);

		String docId = resp.doc.recId();
		assertEquals(8, docId.length());

		DocsResp rp = apptier.selectDoc(meta.tbl, docId);

		assertTrue(LangExt.isblank(rp.msg()));
		assertEquals(AnDevice.device, rp.doc.device());
		assertEquals(AnDevice.localFile, rp.doc.fullpath());

		return AnDevice.localFile;
	}

}
