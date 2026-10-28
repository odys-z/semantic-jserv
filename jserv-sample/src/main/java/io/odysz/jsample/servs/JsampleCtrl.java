package io.odysz.jsample.servs;

import java.io.IOException;
import java.net.InetAddress;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletResponse;

import io.odysz.common.Utils;
import io.odysz.jsample.JSampleApp;
import io.odysz.jsample.protocol.Samport;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.UserReq;
import io.odysz.semantic.jserv.ServPort;

/**
 * JSample's control tier, for tests only, on port {@link Samport#ctrl}, url pattern /ctrl.less.
 *
 * <p>No session. Any post from localhost stops {@link JSampleApp} gracefully, see {@link JSampleApp#stop()};
 * posts from other addresses are refused.</p>
 *
 * @since 1.5.9
 */
@WebServlet(description = "JSample control: stop, localhost only", urlPatterns = { "/ctrl.less" })
public class JsampleCtrl extends ServPort<UserReq> {
	private static final long serialVersionUID = 1L;

	public JsampleCtrl() {
		super(Samport.ctrl);
	}

	@Override
	protected void onGet(AnsonMsg<UserReq> msg, HttpServletResponse resp)
			throws ServletException, IOException {
		write(resp, err(MsgCode.exGeneral, "ctrl.less only handles posts."));
		resp.flushBuffer();
	}

	@Override
	protected void onPost(AnsonMsg<UserReq> msg, HttpServletResponse resp)
			throws ServletException, IOException {
		resp.setCharacterEncoding("UTF-8");

		if (!isLocalhost(msg.addr())) {
			Utils.warn("[JsampleCtrl] Refused a request from %s.", msg.addr());
			write(resp, err(MsgCode.exSession, "JsampleCtrl only accepts requests from localhost."));
			resp.flushBuffer();
			return;
		}

		write(resp, ok("%s is stopping.", JSampleApp.sample_name));
		resp.flushBuffer();

		// Not on Jetty's request thread: server.stop() waits for this request to complete.
		new Thread(() -> JSampleApp.app.stop(), "jsample-stop").start();
	}

	/**
	 * @param addr remote address, e.g. 127.0.0.1, [0:0:0:0:0:0:0:1]
	 * @return true for a loopback address
	 */
	static boolean isLocalhost(String addr) {
		try {
			return InetAddress.getByName(addr.replaceAll("^\\[|\\]$", "")).isLoopbackAddress();
		} catch (Exception e) {
			return false;
		}
	}
}
