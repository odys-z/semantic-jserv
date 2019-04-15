package io.odysz.semantic.jserv.file;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;

import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;

import org.apache.commons.io.FilenameUtils;

import io.odysz.common.Configs;
import io.odysz.common.Regex;
import io.odysz.common.Utils;
import io.odysz.semantic.jprotocol.IPort;
import io.odysz.semantic.jprotocol.JMessage.MsgCode;
import io.odysz.semantic.jprotocol.JMessage.Port;
import io.odysz.semantic.jprotocol.JProtocol;
import io.odysz.semantic.jserv.JSingleton;
import io.odysz.semantic.jserv.ServFlags;
import io.odysz.semantic.jserv.helper.ServletAdapter;
import io.odysz.semantics.SemanticObject;
import io.odysz.semantics.x.SemanticException;

/**File download / upload serv.<br>
 * 
 * @author odys-z@github.com
 */
@MultipartConfig
@WebServlet(description = "Serving text files", urlPatterns = { "/file.serv" })
public class JFileServ extends HttpServlet {
	private static final long serialVersionUID = 1L;
	static IPort p = Port.file;

	private static Regex regex = new Regex(".*filename\\s*=\\s*\"(.*)\"");

	private String uploadPath;

	protected void doGet(HttpServletRequest req, HttpServletResponse response) throws ServletException, IOException {
		if (ServFlags.file)
			System.out.println("file.serv get ------");
		try {resp(req, response, req.getParameter("t"), req.getParameter("file"));
		} catch (SemanticException e) {
			ServletAdapter.write(response, JProtocol.err(p, MsgCode.exSemantic, e.getMessage()));
		}
	}

	protected void doPost(HttpServletRequest req, HttpServletResponse response)
			throws ServletException, IOException {
		if (ServFlags.file)
			System.out.println("file.serv post ========");
		try {
			resp(req, response, req.getParameter("t"), req.getParameter("file"));
		} catch (SemanticException e) {
			ServletAdapter.write(response, JProtocol.err(p, MsgCode.exSemantic, e.getMessage()));
		}
	}
	
	protected void resp(HttpServletRequest req, HttpServletResponse resp, String t, String file)
			throws IOException, SemanticException, ServletException {
		resp.setContentType("text/html;charset=UTF-8");
		if ("jx".equals(t))
			jtxt(resp, file);
		else if ("upload".equals(t))
			uploadForm(req, resp, file);

		resp.flushBuffer();
	}

	/**Ajax POST:
	 * <pre>xhr:  $.ajaxSettings.xhr();
data: formData </pre>
     * HTTP:
	 * <pre>-----------------------------1717650729922805713297348857
Content-Disposition: form-data; name="fileContent"; filename="t1.png"
Content-Type: image/png

.PNG
.
IHDR...
-----------------------------1717650729922805713297348857--</pre>
	 * @param req
	 * @param resp
	 * @param file
	 * @return
	 * @throws IOException
	 * @throws SemanticException 
	 * @throws ServletException 
	 */
	private String uploadForm(HttpServletRequest req, HttpServletResponse resp, String file) throws IOException, SemanticException, ServletException {
		if (uploadPath == null) {
			uploadPath = Configs.getCfg("upload.file.serv");
			if (uploadPath == null)
				uploadPath = "upload";
			uploadPath = JSingleton.getFileInfPath(uploadPath);
			File f = new File (uploadPath);
			if (!f.exists()) {
				boolean folder = f.mkdir();
				Utils.logi("Upload Path (folder %s): %s", folder, uploadPath);
			}
		}
		
		// For TROUBLESHOOTINGS: @MultipartConfig
		// https://stackoverflow.com/questions/7445296/httpservletrequestgetparts-returns-an-empty-list
		// https://stackoverflow.com/questions/2422468/how-to-upload-files-to-server-using-jsp-servlet
		Part part = req.getPart("file");
		if (part != null) {
			file = part.getHeader("content-disposition");//form-data; name="file"; filename="t1.png"
			String fileId = fileId(req, part);
			String filepath = FilenameUtils.concat(uploadPath, fileId);
			FileOutputStream outs = new FileOutputStream(filepath);
			ServletAdapter.copy(outs, part.getInputStream());
    		outs.close();
    		SemanticObject rs = JProtocol.ok(p, fileId);
    		ServletAdapter.write(resp, rs);
    		return fileId;
		}
		throw new SemanticException("No file part found.");
	}

	/**
https://developer.mozilla.org/en-US/docs/Web/HTML/Element/input/file
https://developer.mozilla.org/en-US/docs/Web/API/File/Using_files_from_web_applications
https://vuejsexamples.com/file-upload-component-for-vue-js/
https://stackoverflow.com/questions/4006520/using-html5-file-uploads-with-ajax-and-jquery
https://wisdmlabs.com/blog/access-file-before-upload-using-jquery-ajax/

	 * @param req
	 * @param resp
	 * @param file
	 * @return
	 * @throws IOException
	private String upload(HttpServletRequest req, HttpServletResponse resp, String file) throws IOException {
		if (uploadPath == null) {
			uploadPath = Configs.getCfg("upload.file.serv");
			if (uploadPath == null)
				uploadPath = "upload";
			uploadPath = JSingleton.getFileInfPath(uploadPath);
			File f = new File (uploadPath);
			if (!f.exists()) {
				boolean folder = f.mkdir();
				Utils.logi("Upload Path (folder %s): %s", folder, uploadPath);
			}
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
			// OutputStream os = Base64.getEncoder().wrap(outs);
			InputStream ins = Base64.getDecoder().wrap(req.getInputStream());
			ServletAdapter.copy(outs, ins);
			outs.close();
		}
		else  {
			ServletAdapter.copy(outs, req.getInputStream());
			outs.close();
		}

		SemanticObject rs = JProtocol.ok(p, fileId);
		ServletAdapter.write(resp, rs);
		return fileId;
	}
	 */

	/**get fileId = ip + filename (client) + ms + .ext
	 * @param req
	 * @param clientFileName
	 * @return fileId = ip + filename (client) + ms + .ext
	 */
	private String fileId(HttpServletRequest req, Part part) {
		String hd = part.getHeader("content-disposition");
		return parsefileId(req.getRemoteAddr(), hd);
	}
	
	static String parsefileId(String remote, String head) {
		ArrayList<String> g = regex.findGroups(head);
		if (g != null && g.size() > 0) {
			String clientFileName = g.get(0);
			String ext = FilenameUtils.getExtension(clientFileName);
			String name = FilenameUtils.removeExtension(clientFileName);
			return String.format("%s %s-%s.%s", remote, name, System.currentTimeMillis(),
					ext == null || ext.length() == 0 ? "upload" : ext);
		}
		else 
			return String.format("%s unknown-%s.upload", remote, System.currentTimeMillis());
	}

	private void jtxt(HttpServletResponse response, String file) throws IOException {
		String jsonfile  = JSingleton.getFileInfPath(file);
		FileInputStream fis = new FileInputStream(jsonfile);
		ServletAdapter.write(response, fis);
		fis.close();
	}
}
