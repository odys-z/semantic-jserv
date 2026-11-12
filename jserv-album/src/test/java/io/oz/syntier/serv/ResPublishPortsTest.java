package io.oz.syntier.serv;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.InetSocketAddress;

import org.eclipse.jetty.ee8.servlet.ServletContextHandler;
import org.eclipse.jetty.ee8.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.odysz.jclient.HttpServClient;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.oz.album.peer.SynDocollPort;
import io.oz.sdoc.publish.ResPublishReq;
import io.oz.sdoc.publish.ResPublishReq.A;
import io.oz.sdoc.publish.ResPublishResp;
import io.oz.sdoc.publish.ResPublishTier;
import io.oz.sdoc.tier.DoctreeTier;

/**
 * The synode's ports for publishing docs at the local html-service, see
 * {@link SynotierJettyApp#createSyndoctierApp(io.oz.syn.registry.SynodeConfig, io.oz.jserv.docs.syn.singleton.AppSettings, io.oz.syn.SyncUser, String, String, String)}.
 */
class ResPublishPortsTest {
	static Server server;
	static String jserv;

	@BeforeAll
	static void startSynodePort() throws Exception {
		AnsonMsg.understandPorts(SynDocollPort.respub);

		// no working copy: no docs published
		DoctreeTier doctree = new DoctreeTier(SynDocollPort.doctree);

		server = new Server(new InetSocketAddress("127.0.0.1", 0));
		ServletContextHandler ctx = new ServletContextHandler(server, "/jserv-album");
		ctx.addServlet(new ServletHolder(new ResPublishTier(SynDocollPort.respub, doctree)), "/" + ResPublishTier.url);
		server.start();

		jserv = "http://127.0.0.1:" + server.getURI().getPort() + "/jserv-album";
	}

	@AfterAll
	static void stop() throws Exception {
		server.stop();
	}

	/** Names and urls are html-service's (io.oz.srv.respaths.HtmlPort), the two can't differ. */
	@Test
	void testPortNames() {
		assertEquals("respub", SynDocollPort.respub.name());
		assertEquals("respub.less", SynDocollPort.respub.url());
		assertEquals(ResPublishTier.url, SynDocollPort.respub.url());

		assertEquals("respaths", SynDocollPort.respaths.name());
		assertEquals("respaths.serv", SynDocollPort.respaths.url());
	}

	/** What html-service does: query the published docs, no session. */
	@Test
	void testQueryPublished() throws Exception {
		AnsonMsg<ResPublishReq> req = new AnsonMsg<ResPublishReq>(SynDocollPort.respub)
				.body(new ResPublishReq().a(A.refresh));

		AnsonMsg<?> resp = new HttpServClient().post(SynDocollPort.respub.url(jserv), req);

		assertEquals(MsgCode.ok, resp.code());
		assertEquals(SynDocollPort.respub, resp.port());
		assertEquals(0, ((ResPublishResp) resp.body(0)).respubs.size());
	}
}
