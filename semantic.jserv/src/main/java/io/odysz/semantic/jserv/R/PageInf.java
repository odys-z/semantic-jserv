package io.odysz.semantic.jserv.R;

import java.util.ArrayList;

import io.odysz.anson.Anson;

public class PageInf extends Anson {

	public long page;
	public long size;
	public ArrayList<String[]> condts;
}
