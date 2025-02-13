package io.odysz.semantic.jsession;

import io.odysz.semantic.DASemantics;
import io.odysz.semantic.DATranscxt;

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
	 * @throws Exception 
	 */
	public LogTranscxt(String conn)
			throws Exception {
		 super(conn);

		initConfigs(conn, loadSemanticsXml(conn), 
					(c) -> new SemanticsMap(c));
	}
}
