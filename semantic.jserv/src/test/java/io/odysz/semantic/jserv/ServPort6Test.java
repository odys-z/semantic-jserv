package io.odysz.semantic.jserv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;

import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.odysz.anson.Anson;
import io.odysz.semantic.jprotocol.AnsonHeader;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
import io.odysz.semantic.jprotocol.AnsonResp;
import io.odysz.semantic.jserv.echo.EchoReq;
import io.odysz.semantic.jserv.x.SsException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;

/**
 * ServPort6 in a Jetty ee10 (Servlet 6.0) container.
 */
class ServPort6Test {

	/** An echo port on Servlet 6.0 */
	static class Echo6 extends ServPort6<EchoReq> {
		private static final long serialVersionUID = 1L;

		Echo6() { super(Port.echo); }

		@Override
		protected void onGet(AnsonMsg<EchoReq> msg, HttpServletResponse resp)
				throws ServletException, IOException {
			write(resp, ok("get: %s", msg.body(0).echo));
		}

		@Override
		protected void onPost(AnsonMsg<EchoReq> msg, HttpServletResponse resp)
				throws ServletException, IOException {
			write(resp, ok("post: %s", msg.body(0).echo));
		}
	}

	static Server server;
	static String echo_url;
	static Echo6 echo6;

	@BeforeAll
	static void startJetty() throws Exception {
		AnsonMsg.understandPorts(Port.echo);

		server = new Server();
		ServerConnector connector = new ServerConnector(server);
		connector.setHost("127.0.0.1");
		connector.setPort(0);
		server.addConnector(connector);

		ServletContextHandler ctx = new ServletContextHandler();
		ctx.setContextPath("/");
		echo6 = new Echo6();
		ctx.addServlet(new ServletHolder(echo6), "/echo.less");
		server.setHandler(ctx);
		server.start();

		echo_url = "http://127.0.0.1:" + connector.getLocalPort() + "/echo.less";
	}

	@AfterAll
	static void stopJetty() throws Exception {
		server.stop();
	}

	@Test
	void testPost() throws Exception {
		AnsonMsg<EchoReq> req = new AnsonMsg<EchoReq>(Port.echo).body(new EchoReq("hello 6"));
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		req.toBlock(bos);

		String json = HttpClient.newHttpClient().send(
				HttpRequest.newBuilder(URI.create(echo_url))
					.POST(BodyPublishers.ofByteArray(bos.toByteArray())).build(),
				BodyHandlers.ofString()).body();

		@SuppressWarnings("unchecked")
		AnsonMsg<AnsonResp> resp = (AnsonMsg<AnsonResp>) Anson.fromJson(new ByteArrayInputStream(json.getBytes()));
		assertEquals(MsgCode.ok, resp.code());
		assertEquals("post: hello 6", resp.body(0).msg());
	}

	@Test
	void testGetEmpty() throws Exception {
		String json = HttpClient.newHttpClient().send(
				HttpRequest.newBuilder(URI.create(echo_url)).GET().build(),
				BodyHandlers.ofString()).body();

		@SuppressWarnings("unchecked")
		AnsonMsg<AnsonResp> resp = (AnsonMsg<AnsonResp>) Anson.fromJson(new ByteArrayInputStream(json.getBytes()));
		assertEquals(MsgCode.exGeneral, resp.code());
	}

	@Test
	void testNoVerifier() {
		assertEquals("echo", echo6.port());
		assertThrows(SsException.class, () -> echo6.verify(new AnsonHeader()));
	}
}
