package io.odysz.semantic.jsession;

import java.io.IOException;
import java.sql.SQLException;

import org.xml.sax.SAXException;

import io.odysz.semantic.DASemantics;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantics.x.SemanticException;

/**
 * LogTranscxt also uses {@link DASemantics}.
 * 
 * @author ody
 */
public class LogTranscxt extends DATranscxt {

	/**
	 * Log {@link DATranscxt} is a special transxct, 
	 * which use a special semantic-log.xml for semantics and use 
	 * different connId for a_log datatable sql generating.  
	 * @param conn e.g. the defualt system connection Id, the a_log table will be used for meta checking.
	 * @param xml
	 * @param logTabl 
	 * @throws Exception 
	 */
	public LogTranscxt(String conn)
			throws Exception {
		 super(conn);

		initConfigs(conn, loadSemantics(conn), 
					(c) -> new SemanticsMap(c));
	}
}
