package io.odysz.semantic.tier.docs;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.SQLException;

import org.apache.commons.io.IOUtils;
import org.xml.sax.SAXException;

import io.odysz.common.DocLocks;
import io.odysz.common.Utils;
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
 * 
 * @deprecated since 1.5.0, upload is replaced by PushBlocks(),
 * download is replaced by {@link Docs206}#get206().
 * 
 * @author Ody Zhou
 */
public class FileStream {
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
		return writeFile(in, targetFile);
	}
	
	protected static MsgCode writeFile(InputStream in, String target) throws IOException {
		FileOutputStream out = new FileOutputStream(target);
		IOUtils.copy(in, out);
		out.close();
		return MsgCode.ok;
	}

	public static MsgCode download(OutputStream out, String funcUri, String extUri, IUser usr)
			throws TransException, IOException, SQLException {
		String srcFile = filext(funcUri, extUri, usr);
		return sendFile(out, srcFile);
	}

	public static MsgCode sendFile(OutputStream out, String src)
			throws IOException {
		try {
			DocLocks.reading(src);
			Utils.logi(src);
			FileInputStream in = new FileInputStream(src);
			IOUtils.copy(in, out);
			in.close();
			return MsgCode.ok;
		} finally {
			DocLocks.readed(src);
		}
	}


	/**Map uri to file path, according to db records. Which means a doc record already exists.
	 * @param funcUri client function uri
	 * @param fileId file record id, of which uri usually handled by semantics file ext
	 * @param usr 
	 * @return resolved uri of file
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
