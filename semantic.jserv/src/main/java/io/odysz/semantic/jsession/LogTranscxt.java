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
	 * @throws SQLException
	 * @throws SAXException
	 * @throws IOException
	 * @throws SemanticException
	 */
	public LogTranscxt(String conn, String xml, String logTabl)
			throws SQLException, SAXException, IOException, SemanticException {
		 super(conn);

		// get sys samantics, then apply to all connections
		// loadVirtualSemantics(xml);

		initConfigs(conn, loadSemantics(conn), 
						(trb, tbl, pk, debug) -> new DASemantics(trb, tbl, pk, debug));
	}
	
	/**
	 * </p>Get sys samantics, then apply to all connections.</p>
	 * This method also initialize table meta by calling {@link Connects}.
	 * @param TFactory 
	 * @param xmlpath
	 * @return configurations
	 * @throws SAXException
	 * @throws IOException
	 * @throws SQLException 
	 * @throws SemanticException 
	public static HashMap<String,SemanticsMap> loadVirtualSemantics(String xmlpath)
			throws SAXException, IOException, SQLException, SemanticException {
		Utils.logi("Loading Semantics of logging, fullpath:\n\t%s", xmlpath);

		String fpath = FilenameUtils.concat(cfgroot, xmlpath);

		if (isblank(fpath, "\\."))
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
				String pk   = xcfg.getString("pk");
				String smtc = xcfg.getString("smtc");
				String args = xcfg.getString("args");
				addSemantics(conn, tabl, pk, smtc, args, Connects.getDebug(conn));
			}
		}
		return smtConfigs;
	}

	public static void addSemantics(String conn, String tabl, String sm,
				String pk, String args, boolean ... debug) {
		SemanticsMap smap = smtConfigs.get(conn); 
		if (smap == null) {
			smap = new SemanticsMap(conn);
			smtConfigs.put(conn, smap);
		}
		
		//smap.addHandler(sm, tabl, pk, split(args, ","));
	}
	 */
	
}
