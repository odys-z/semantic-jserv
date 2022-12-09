package io.odysz.semantic.ext;

import io.odysz.semantics.meta.TableMeta;

public class SharelogMeta extends TableMeta {

	protected String synode;

	public final String parentbl;
	public final String parentpk;
	public final String familyTbl;
	public final String synodeTbl;
	public final String docFk;

	public final String org;


	public SharelogMeta(String parentbl, String parentpk, String... conn) {
		super("a_sharelog", conn);
		
		this.parentbl = parentbl;
		this.parentpk = parentpk;
		this.synode = "synode";
		this.docFk = "docId";
		this.org = "org";
		
		this.familyTbl = "a_orgs";
		this.synodeTbl = "a_synodes";
	}

	/**
	 * Specify select-element from synodes table, for inserting into share-log.
	 * @return cols
	 */
	public String[] selectSynodeCols() {
		return new String[] {
			"to do"
		};
	}

}
