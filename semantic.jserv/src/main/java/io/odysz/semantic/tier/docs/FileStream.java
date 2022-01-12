package io.odysz.semantic.tier.docs;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.SQLException;

import org.apache.commons.io.IOUtils;
import org.xml.sax.SAXException;

import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantics.IUser;
import io.odysz.semantics.x.SemanticException;
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

	protected static DATranscxt st;

	/** file table name */
	protected static String tabl;

	static void init(String uriTabl) {
		try {
			st = new DATranscxt(null);
			tabl = uriTabl;
		} catch (SemanticException | SQLException | SAXException | IOException e) {
			e.printStackTrace();
		}
	}
	
	/**TODO LRU buffer manager mapping uri to file path - A way of performance optimizing.
	 * @param uri
	 * @return path
	 */
	public static String touch(String uri) {
		return null;
	}

	public static MsgCode upload(String funcUri, String extUri, InputStream in, IUser usr)
			throws TransException, IOException, SQLException {
		String targetFile = filext(funcUri, extUri, usr);
		FileOutputStream out = new FileOutputStream(targetFile);
		IOUtils.copy(in, out);
		out.close();
		return MsgCode.ok;
	}

	public static MsgCode download(OutputStream out, String funcUri, String extUri, IUser usr)
			throws TransException, IOException, SQLException {
		String srcFile = filext(funcUri, extUri, usr);
		FileInputStream in = new FileInputStream(srcFile);
		IOUtils.copy(in, out);
		in.close();
		return MsgCode.ok;
	}

	/**Map uri to file path
	 * @param funcUri client function uri
	 * @param fileId file id, of which uri usually handled by semantics file ext
	 * @param usr 
	 * @return
	 * @throws SemanticException can't find path of fileId 
	 * @throws TransException 
	 * @throws SQLException 
	 */
	protected static String filext(String funcUri, String fileId, IUser usr)
			throws SemanticException, TransException, SQLException {
		AnResultset rs = (AnResultset) st.select(tabl)
			.col("uri")
			.whereEq("id", fileId)
			.rs(st.instancontxt(Connects.uri2conn(funcUri), usr))
			.rs(0);

		if (rs.next())
			return rs.getString("uri");
		else throw new SemanticException("Can't find file for id: %s (permission of %s)", fileId, usr.uid());
	}

}
