package io.oz.spreadsheet;

import io.odysz.transact.sql.Insert;
import io.odysz.transact.x.TransException;

public interface ISheetRec {

	/**
	 * Setup insert's nvs according to this spreadsheet record. 
	 * 
	 * @param ins
	 * @return this
	 * @throws TransException
	 */
	ISheetRec setNvs(Insert ins) throws TransException;

}
