package io.odysz.semantic.jserv.U;

import io.odysz.semantic.jprotocol.JBody;
import io.odysz.semantic.jprotocol.JMessage;

/**Insert Request helper<br>
 * 
 * @author odys-z@github.com
 */
public class InsertReq extends UpdateReq {
	/**3d array [[[n, v], ...]] */
//	private ArrayList<ArrayList<?>> nvss;
//	private String[] cols;


	public InsertReq(JMessage<? extends JBody> parent, String conn) {
		super(parent, conn);
	}

	/** get columns for sql insert into (COLS) 
	public String[] cols() {
		// parse columns from nvs
		return cols;
	}
	 * */

	/** get values in VALUE-CLAUSE for sql insert into (...) values VALUE-CLAUSE 
	 * @return [[[n, v], ...]]
	public ArrayList<ArrayList<?>> values() {
		return nvss;
	}
	 */
	
//	@Override
//	protected void child2Json(JsonWriter writer) throws SemanticException, IOException {
//		if (nvss != null) {
//			writer.name("nvss");
//			JHelper.writeLst(writer, nvss);
//		}
//		if (cols != null) {
//			writer.name("cols");
//			JHelper.writeStrings(writer, cols);
//		}
//	}
	
//	@Override
//	protected void readChild(String name, JsonReader reader) throws SemanticException, IOException {
//		if ("nvss".equals(name))
//			nvss = JHelper.readLstLstStrs(reader);
//		else if ("cols".equals(name))
//			cols = JHelper.readStrs(reader);
//	}
}
