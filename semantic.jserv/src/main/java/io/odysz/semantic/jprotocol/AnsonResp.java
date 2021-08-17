package io.odysz.semantic.jprotocol;

import java.util.ArrayList;
import java.util.HashMap;

import io.odysz.module.rs.AnResultset;

/**Anson message response body
 * @author odys-z@github.com
 */
public class AnsonResp extends AnsonBody {

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
		this.m = txt;
	}

	public AnsonResp(String txt) {
		super(null, null);
		this.m = txt;
	}

	public String msg() { return m; }

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
	
	public HashMap<String, Object> data () {
		return map;
	}
}
