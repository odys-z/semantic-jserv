package io.odysz.semantic.jserv.helper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.servlet.http.HttpServletRequest;

import io.odysz.semantic.jprotocol.JHelper;
import io.odysz.semantic.jprotocol.JMessage;
import io.odysz.semantics.x.SemanticException;

public class ServletAdapter {

	public static <T extends JMessage> T read(HttpServletRequest req,
			JHelper<?> jreqHelper, Class<? extends JMessage> bodyItemclazz)
				throws IOException, SemanticException, ReflectiveOperationException {
		InputStream in = null; 
		String headstr = req.getParameter("req");
		if (headstr == null || headstr.length() < 5) {
			byte[] b = headstr.getBytes();
			in = new ByteArrayInputStream(b);
		}
		else in = req.getInputStream();
		
		@SuppressWarnings("unchecked")
		T msg = (T) jreqHelper.readJson(in, bodyItemclazz);
		in.close();

		return msg;
	}

}
