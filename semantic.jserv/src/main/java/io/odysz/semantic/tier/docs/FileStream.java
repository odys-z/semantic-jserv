package io.odysz.semantic.tier.docs;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.commons.io.IOUtils;

import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantics.IUser;
import io.odysz.transact.x.TransException;

/**File helper
 * - file upload / download in stream
 * 
 * <h6>This should be moved to framework with test</h6>
 * @author Ody Zhou
 *
 */
public class FileStream {
//	static final String tabl = "n_docs";
//	static final String uri = "uri";
//	static final String state = "state";
//
//	/** Docs state */
//	static class S {
//		static final String uploading = "uploading";
//		static final String valid = "valid";
//		static final String synchronizing = "synching";
//		static final String archive = "archive";
//		static final String shared = "shared";
//	}

	public static MsgCode upload(String targetFile, InputStream in, IUser usr) throws TransException, IOException {
		FileOutputStream out = new FileOutputStream(targetFile);
		IOUtils.copy(in, out);
		out.close();
		return MsgCode.ok;
	}

	public static MsgCode download(OutputStream out, String srcFile, IUser usr) throws TransException, IOException {
		FileInputStream in = new FileInputStream(srcFile);
		IOUtils.copy(in, out);
		in.close();
		return MsgCode.ok;
	}

}
