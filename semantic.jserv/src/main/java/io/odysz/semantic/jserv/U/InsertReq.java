package io.odysz.semantic.jserv.U;

import java.util.ArrayList;

import io.odysz.semantic.jprotocol.JBody;
import io.odysz.semantic.jprotocol.JMessage;

/**Insert Request helper<br>
 * 
 * @author odys-z@github.com
 */
public class InsertReq extends UpdateReq {
	public InsertReq(JMessage<? extends JBody> parent, String conn) {
		super(parent, conn);
	}

	/** get columns for sql insert into (COLS) 
	 * @param msg */
	public static String cols(UpdateReq msg) {
		// parse columns from nvs
		return null;
	}

	/** get values in VALUE-CLAUSE for sql insert into (...) values VALUE-CLAUSE 
	 * @param msg */
	public static ArrayList<Object[]> values(UpdateReq msg) {
		// parse values from nvs
		return null;
	}

}
