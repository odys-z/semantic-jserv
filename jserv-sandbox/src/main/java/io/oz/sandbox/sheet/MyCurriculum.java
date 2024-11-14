package io.oz.sandbox.sheet;

import io.odysz.anson.Anson;
import io.odysz.transact.sql.Insert;
import io.odysz.transact.x.TransException;
import io.oz.spreadsheet.ISheetRec;

public class MyCurriculum extends Anson implements ISheetRec {

	public static final String tabl = "b_curriculums";

    String cid;
    String cate;
    String module;
    String subject;
    String parentId;
	String clevel;
	String currName;
	String sort;

	@Override
	public ISheetRec setNvs(Insert inst) throws TransException {
		if (currName == null)
			currName = "курс";
		if (cate == null)
			cate = "";
		if (clevel == null)
			clevel = "";
		if (subject == null)
			subject = "";
		if (module == null)
			module = "";
		if (sort == null)
			sort = "0";

		inst.nv("currName", currName)
			.nv("cate", cate)
			.nv("clevel", clevel)
			.nv("parentId", parentId)
			.nv("subject", subject)
			.nv("module", module)
			.nv("sort", sort);
		
		return this;
	}
}
