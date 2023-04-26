package io.odysz.semantic.jsession;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedHashMap;

import org.apache.commons.io_odysz.FilenameUtils;
import org.xml.sax.SAXException;

import io.odysz.common.LangExt;
import io.odysz.common.Utils;
import io.odysz.module.xtable.IXMLStruct;
import io.odysz.module.xtable.Log4jWrapper;
import io.odysz.module.xtable.XMLDataFactoryEx;
import io.odysz.module.xtable.XMLTable;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantics.x.SemanticException;

public class LogTranscxt extends DATranscxt {

	/**Log {@link DATranscxt} is a special transxct, 
	 * which use a special semantic-log.xml for semantics and use 
	 * different connId for a_log datatable sql generating.  
	 * @param sysConn e.g. the defualt system connection Id, the a_log table will be used for meta checking.
	 * @param xml
	 * @param logTabl 
	 * @throws SQLException
	 * @throws SAXException
	 * @throws IOException
	 * @throws SemanticException
	 */
	public LogTranscxt(String sysConn, String xml, String logTabl)
			throws SQLException, SAXException, IOException, SemanticException {
		super(sysConn);

		// get sys samantics, then apply to all connections
		loadVirtualSemantics(xml);
	}
	
	/**</p>Get sys samantics, then apply to all connections.</p>
	 * This method also initialize table meta by calling {@link Connects}.
	 * @param xmlpath
	 * @return configurations
	 * @throws SAXException
	 * @throws IOException
	 * @throws SQLException 
	 * @throws SemanticException 
	 */
	public static HashMap<String,SemanticsMap> loadVirtualSemantics(String xmlpath)
			throws SAXException, IOException, SQLException, SemanticException {
		Utils.logi("Loading Semantics of logging, fullpath:\n\t%s", xmlpath);

		// String fpath = Connects.getSmtcs(sysConn);
		// String fpath = Connects.getSmtcs(xmlpath);

		String fpath = FilenameUtils.concat(cfgroot, xmlpath);

		if (LangExt.isblank(fpath, "\\."))
			throw new SemanticException(
				"Log Transxct loading failed.\n   xml %1$s\n    path %2$s",
				xmlpath, fpath);
		

		LinkedHashMap<String, XMLTable> xtabs = XMLDataFactoryEx.getXtables(
				new Log4jWrapper("").setDebugMode(false), fpath, new IXMLStruct() {
						@Override public String rootTag() { return "semantics"; }
						@Override public String tableTag() { return "t"; }
						@Override public String recordTag() { return "s"; }});

		XMLTable xcfg = xtabs.get("semantics");
		
		for (String conn : Connects.getAllConnIds()) {
			xcfg.beforeFirst();
			// smtConfigs shouldn't be null now
			if (smtConfigs == null)
				// smtConfigs = new HashMap<String, HashMap<String, DASemantics>>();
				smtConfigs = new HashMap<String, SemanticsMap>();
			while (xcfg.next()) {
				String tabl = xcfg.getString("tabl");
				String pk = xcfg.getString("pk");
				String smtc = xcfg.getString("smtc");
				String args = xcfg.getString("args");
				try {
					addSemantics(conn, tabl, pk, smtc, args, Connects.getDebug(conn));
				} catch (SemanticException e) {
					// some configuration error
					// continue
					Utils.warn(e.getMessage());
				}
			}
		}
		return smtConfigs;
	}

}
