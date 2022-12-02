package io.oz.jserv.sync;

import io.odysz.semantics.meta.TableMeta;

public class SharelogMeta extends TableMeta {

	protected String synode;
	protected String docId;

	public final String parentbl;
	public final String parentpk;
	public final String familyTbl;
	public final String synodeTbl;

	public SharelogMeta(String parentbl, String parentpk, String... conn) {
		super("a_sharelog", conn);
		
		this.parentbl = parentbl;
		this.parentpk = parentpk;
		this.synode = "synode";
		this.docId = "docId";
		
		this.familyTbl = "a_orgs";
		this.synodeTbl = "a_synodes";
	}

	/**
	 * Specify select-element from synodes table 
	 * @return cols
	 */
	public String[] selectSynodeCols() {
		return new String[] {
			"to do"
		};
	}

}
