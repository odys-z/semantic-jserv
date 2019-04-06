package io.odysz.semantic.jserv.file;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Base64;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.FilenameUtils;

import io.odysz.common.Configs;
import io.odysz.semantic.jprotocol.IPort;
import io.odysz.semantic.jprotocol.JMessage.Port;
import io.odysz.semantic.jprotocol.JProtocol;
import io.odysz.semantic.jserv.JSingleton;
import io.odysz.semantic.jserv.ServFlags;
import io.odysz.semantic.jserv.helper.ServletAdapter;
import io.odysz.semantics.SemanticObject;

@WebServlet(description = "Serving text files", urlPatterns = { "/file.serv" })
public class JFileServ extends HttpServlet {
	private static final long serialVersionUID = 1L;
	static IPort p = Port.file;

	private String uploadPath;

	protected void doGet(HttpServletRequest req, HttpServletResponse response) throws ServletException, IOException {
		if (ServFlags.file)
			System.out.println("file.serv get ------");
		resp(req, response, req.getParameter("t"), req.getParameter("file"));
	}

	protected void doPost(HttpServletRequest req, HttpServletResponse response) throws ServletException, IOException {
		if (ServFlags.file)
			System.out.println("file.serv post ========");
		resp(req, response, req.getParameter("t"), req.getParameter("file"));
	}
	
	protected void resp(HttpServletRequest req, HttpServletResponse resp, String t, String file) throws IOException {
		resp.setContentType("text/html;charset=UTF-8");
		if ("jx".equals(t))
			jx(resp, file);
		else if ("upload".equals(t))
			upload(req, resp, file);

		resp.flushBuffer();
	}

	private String upload(HttpServletRequest req, HttpServletResponse resp, String file) throws IOException {
		if (uploadPath == null) {
			uploadPath = Configs.getCfg("upload.file.serv");
			if (uploadPath == null)
				uploadPath = "upload";
		}

		boolean decode = false;
		String decode64 = req.getParameter("b64");
		if (decode64 != null)
			try {decode = Boolean.valueOf(decode64);
			} catch (Exception e) {}
		
		String fileId = fileId(req, file);
		String filepath = FilenameUtils.concat(uploadPath, fileId);
		FileOutputStream outs = new FileOutputStream(filepath);
		if (decode) {
			OutputStream os = Base64.getEncoder().wrap(outs);
			ServletAdapter.copy(os, req.getInputStream());
			os.close();
		}
		else  {
			ServletAdapter.copy(outs, req.getInputStream());
			outs.close();
		}

		SemanticObject rs = JProtocol.ok(p, fileId);
		ServletAdapter.write(resp, rs);
		return fileId;
	}

	/**get fileId = ip + filename (client) + ms + .ext
	 * @param req
	 * @param clientFileName
	 * @return fileId = ip + filename (client) + ms + .ext
	 */
	private String fileId(HttpServletRequest req, String clientFileName) {
		String ext = FilenameUtils.getExtension(clientFileName);
		String name = FilenameUtils.removeExtension(clientFileName);
		return String.format("%s %s-%s.%s", req.getRemoteAddr(), name, System.currentTimeMillis(), ext);
	}

	private void jx(HttpServletResponse response, String file) throws IOException {
		String jsonfile  = JSingleton.getFileInfPath(file);
		FileInputStream fis = new FileInputStream(jsonfile);
		ServletAdapter.write(response, fis);
		fis.close();
	}
}
