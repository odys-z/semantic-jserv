package io.odysz.semantic.jserv.helper;

import static j2html.TagCreator.body;
import static j2html.TagCreator.each;
import static j2html.TagCreator.h1;
import static j2html.TagCreator.head;
import static j2html.TagCreator.html;
import static j2html.TagCreator.meta;
import static j2html.TagCreator.table;
import static j2html.TagCreator.tbody;
import static j2html.TagCreator.td;
import static j2html.TagCreator.th;
import static j2html.TagCreator.tr;
import static j2html.TagCreator.span;

import io.odysz.module.rs.SResultset;

/**Helper for coverting modules to readable html.
 * @author ody
 *
 */
public class Html {
	/**Change rs to html.
	 * @param rs
	 * @return
	 */
	public static String rs(SResultset rs) {
		// html() is a simple HTML composer, see
		// https://github.com/tipsy/j2html 
		return "<!DOCTYPE HTML>" + html(
			head(meta().withCharset("utf-8")),
			body(
				h1("echo.test"),
					table(tbody(
						tr(each(rs.getColnames().keySet(), col -> th(span(col)))),
						each(rs.getRows(), row -> tr(each(row, cell -> td(cell.toString()))))))
				)).render();
	}

}
