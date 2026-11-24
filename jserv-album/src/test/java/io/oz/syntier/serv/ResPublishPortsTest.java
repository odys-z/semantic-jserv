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
import io.oz.sdoc.tier.DoctreeReq;
import io.oz.sdoc.tier.DoctreeResp;
import io.oz.sdoc.tier.DoctreeTier;

/**
 * The synode's ports for publishing docs at the local html-service, see
 * SynotierJettyApp#createSyndoctierApp(): the doc-tree tier answers html-service's query,
 * r/published, without a session (localhost only).
 */
class ResPublishPortsTest {
	static Server server;
	static String jserv;

	@BeforeAll
	static void startSynodePort() throws Exception {
		AnsonMsg.understandPorts(SynDocollPort.doctree);

		// no working copy: no docs published
		DoctreeTier doctree = new DoctreeTier(SynDocollPort.doctree);

		server = new Server(new InetSocketAddress("127.0.0.1", 0));
		ServletContextHandler ctx = new ServletContextHandler(server, "/jserv-album");
		ctx.addServlet(new ServletHolder(doctree), "/" + DoctreeTier.url);
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
		assertEquals("doctree", SynDocollPort.doctree.name());
		assertEquals(DoctreeTier.url, SynDocollPort.doctree.url());

		assertEquals("respaths", SynDocollPort.respaths.name());
		assertEquals("respaths.serv", SynDocollPort.respaths.url());
	}

	/** What html-service does: query the published docs, no session. */
	@Test
	void testQueryPublished() throws Exception {
		AnsonMsg<DoctreeReq> req = new AnsonMsg<DoctreeReq>(SynDocollPort.doctree)
				.body(new DoctreeReq().a(DoctreeReq.A.published));

		AnsonMsg<?> resp = new HttpServClient().post(SynDocollPort.doctree.url(jserv), req);

		assertEquals(MsgCode.ok, resp.code());
		assertEquals(SynDocollPort.doctree, resp.port());
		assertEquals(0, ((DoctreeResp) resp.body(0)).docs.size());
	}
}
