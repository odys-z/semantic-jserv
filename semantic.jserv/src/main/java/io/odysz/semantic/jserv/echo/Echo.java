package io.odysz.semantic.jserv.echo;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletResponse;

import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
import io.odysz.semantic.jprotocol.AnsonResp;
import io.odysz.semantic.jserv.ServPort;
import io.odysz.semantic.jserv.echo.EchoReq.A;
import io.odysz.semantics.x.SemanticException;

/**
 * <p>Echo serv, on Port {@link Port#echo}.</p>
 * 
 * url pattern: /echo.less.
 * 
 * @author ody
 */
@WebServlet(description = "service echo", urlPatterns = { "/echo.less" })
public class Echo extends ServPort<EchoReq> {

	private static ArrayList<String> interfaces;

	public Echo() { super(Port.echo); }

	/** * */
	private static final long serialVersionUID = 1L;


	@Override
	protected void onGet(AnsonMsg<EchoReq> req, HttpServletResponse resp)
			throws ServletException, IOException {
		resp(req.body(0), resp, req.addr());
	}

	@Override
	protected void onPost(AnsonMsg<EchoReq> req, HttpServletResponse resp)
			throws ServletException, IOException {
		resp(req.body(0), resp, req.addr());
	}

	protected void resp(EchoReq echoReq, HttpServletResponse resp, String remote) throws IOException {
		try {
			resp.setCharacterEncoding("UTF-8");
			
			if (A.inet.equals(echoReq.a())) {
				AnsonResp rep = inet(resp, echoReq, remote);
				write(resp, ok(rep));
			}
			else
				write(resp, ok(echoReq.a()));
			resp.flushBuffer();
		} catch (SemanticException e) {
			write(resp, err(MsgCode.exSemantic, e.getMessage()));
		} catch (IOException e) {
			write(resp, err(MsgCode.exGeneral, e.getMessage()));
			e.printStackTrace();
		}
	}

    protected AnsonResp inet(HttpServletResponse resp, EchoReq req, String remote) throws SocketException, SemanticException {
    	if ("localhost".equals(remote)) {
    		if (interfaces == null)
    			listInet();
    		return new AnsonResp().data("interfaces", interfaces);
    	}
    	throw new SemanticException("Remote resource querying is not allowed.");
	}

	public static void listInet() throws SocketException {
		interfaces = new ArrayList<String>();
        Enumeration<NetworkInterface> nets = NetworkInterface.getNetworkInterfaces();
        for (NetworkInterface netint : Collections.list(nets)) {
			// System.out.printf("Display name: %s\n", netint.getDisplayName());
			// System.out.printf("Name: %s\n", netint.getName());
			Enumeration<InetAddress> inetAddresses = netint.getInetAddresses();
			for (InetAddress inetAddress : Collections.list(inetAddresses)) {
				interfaces.add(inetAddress.toString());
			}
        }
     }
}
