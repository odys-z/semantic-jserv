package io.odysz.semantic.jprotocol;

import static io.odysz.common.LangExt.ix;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import io.odysz.anson.Anson;
import io.odysz.anson.AnsonException;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantics.SemanticObject;

import static io.odysz.common.LangExt.isblank;

/**
 * Anson message response body
 * @author odys-z@github.com
 */
public class AnsonResp extends AnsonBody {
	public static class R {
		public static final String ok = "ok";
		public static final String domexists = "domexists";
		public static final String invalid = "invalid";
		public static final String error = "error";
	}

	protected String m;
	protected ArrayList<AnResultset> rs;
	protected HashMap<String, Object> map;

	public AnsonResp() {
		super(null, null);
	}

	public AnsonResp(AnsonMsg<? extends AnsonResp> parent) {
		super(parent, null);
	}

	public AnsonResp(AnsonMsg<? extends AnsonResp> parent, String txt) {
		super(parent, null);
		this.m = new String(Anson.escape(txt));
	}

	public AnsonResp(String txt) {
		super(null, null);
		this.m = new String(Anson.escape(txt));
	}

	public String msg() { return m; }

	/**
	 * @since 1.4.35 
	 * @param toMsg message object to be converted to text, and escaped
	 * @return this
	 * @throws IOException 
	 * @throws AnsonException 
	 */
	public AnsonResp msg(Anson toMsg) throws AnsonException, IOException {
		m = new String(Anson.escape(toMsg.toBlock()));
		return this;
	}

	public AnsonResp msg(String txt) {
		this.m = txt;
		return this;
	}

	public AnsonResp rs(AnResultset rs) {
		if (this.rs == null)
			this.rs = new ArrayList<AnResultset>(1);
		this.rs.add(rs);
		return this;
	}

	/**Add a resultset to list.
	 * @param rs
	 * @param totalRows total row count for a paged query (only a page of rows is actually in rs).
	 * @return this
	 */
	public AnsonResp rs(AnResultset rs, int totalRows) {
		if (this.rs == null)
			this.rs = new ArrayList<AnResultset>();
		this.rs.add(rs.total(totalRows));
		return this;
	}

	public AnsonBody rs(ArrayList<AnResultset> rsLst) {
		this.rs = rsLst;
		return this;
	}

	public ArrayList<AnResultset> rs() { return this.rs; }

	public AnResultset rs(int ix) {
		return this.rs == null ? null : this.rs.get(ix);
	}

	public AnsonResp data(HashMap<String, Object> props) {
		this.map = props;
		return this;
	}
	
	public AnsonResp data(String k, Object v) {
		if (this.map == null)
			this.map = new HashMap<String, Object>();
		this.map.put(k, v);
		return this;
	}
	
	public HashMap<String, Object> data () {
		return map;
	}

	/**
	 * Find resulved value in data, similar to {@link SemanticObject#resulve(String, String)}. 
	 * 
	 * @since 1.4.25
	 * @since 1.5.16 returns a list, see {@link #resulvedata(String, String, int)}
	 * @param tbl
	 * @param autok
	 * @return resulved auto-key
	 */
	@SuppressWarnings("unchecked")
	public ArrayList<String> resulvedata(String tbl, String autok) {
		if (!isblank(data())) {
			SemanticObject reslv = ((SemanticObject)data().get("resulved")); 
			if (reslv != null)
				return (ArrayList<String>) ((SemanticObject) reslv.get(tbl)).get(autok);
		}
		return null;
	}

	/**
	 * @since 1.5.16
	 * @param tbl
	 * @param autok
	 * @param x
	 * @return the auto-key
	 */
	public String resulvedata(String tbl, String autok, int x) {
		ArrayList<String> lst = resulvedata(tbl, autok);
		return ix(lst, x);
	}
	
	/**
	 * Get {@code total} results from data map.
	 * 
	 * @param ix
	 * @return integer
	 * @since 1.5.5
	 */
	public int total(int ix) {
		return (int) ((ArrayList<?>)data().get("total")).get(ix);
	}
}
