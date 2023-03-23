package io.oz.jserv.dbsync;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;

import io.odysz.anson.Anson;
import io.odysz.common.DateFormat;

/**
 * Should be used as [left, right) style (half open).
 * 
 * @author odys-z@github.com
 */
public class TimeWindow extends Anson {
	ArrayList<String> windw;

	String tabl;

	long size;
	
	public TimeWindow (String tabl, long size) {
		windw = new ArrayList<String>(2);
		windw.add(null);
		windw.add(null);
		this.size = size;
		this.tabl = tabl;
	}
	
	Date left() throws ParseException {
		return DateFormat.parseDateTime(windw.get(0));
	}

	Date right() throws ParseException {
		return DateFormat.parseDateTime(windw.get(windw.size() - 1));
	}

	public TimeWindow start(Date d0) {
		return start(DateFormat.formatime(d0));
	}

	public TimeWindow start(String d0) {
		windw.set(0, d0);
		return this;
	}

	public TimeWindow end(Date ddl) {
		return end(DateFormat.formatime(ddl));
	}

	public TimeWindow end(String ddl) {
		windw.set(1, ddl);
		return this;
	}
}
