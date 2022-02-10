package io.odysz.semantic.tier.docs;

import io.odysz.semantic.jprotocol.AnsonResp;

/**This structure is recommend used as Parcel between Android activities.
 * @author ody
 *
 */
public class DocsResp extends AnsonResp {
	long size;
	long size() { return size; } 

	String recId;
	public DocsResp recId(String recid) {
		recId = recid;
		return this;
	}
	public String recId() { return recId; }

	String fullpath;
	public String fullpath() {
		return fullpath;
	}

	String filename;
	public String clientname() { return filename; }

	public String mime() { return null; }
	public String doctype() { return null; }

//	DocsResp put(String name, Object val) {
//		data().put(name, val);
//		return this;
//	}
}
