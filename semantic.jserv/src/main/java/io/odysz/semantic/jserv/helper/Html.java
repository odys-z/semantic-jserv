package io.odysz.semantic.jserv.helper;

import static j2html.TagCreator.body;
import static j2html.TagCreator.each;
import static j2html.TagCreator.h1;
import static j2html.TagCreator.head;
import static j2html.TagCreator.html;
import static j2html.TagCreator.meta;
import static j2html.TagCreator.li;
import static j2html.TagCreator.table;
import static j2html.TagCreator.tbody;
import static j2html.TagCreator.td;
import static j2html.TagCreator.th;
import static j2html.TagCreator.tr;
import static j2html.TagCreator.ul;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import static j2html.TagCreator.span;

import io.odysz.module.rs.SResultset;
import io.odysz.semantics.SemanticObject;
import j2html.tags.ContainerTag;

/**Helper for coverting modules to readable html.
 * @author ody
 *
 */
public class Html {
	/**Change rs to html.
	 * @param rs
	 * @param msgs
	 * @return html doc
	 */
	public static String rs(SResultset rs, String... msgs) {
		// html() is a simple HTML composer, see
		// https://github.com/tipsy/j2html 
		return "<!DOCTYPE HTML>" + html(
			head(meta().withCharset("utf-8")),
			body(
				h1("Html.rs()"),
					ul(each(Arrays.asList(msgs), pmsg -> li(""))),
					table(tbody(
						tr(each(rs.getColnames().keySet(), col -> th(span(col)))),
						each(rs.getRows(), row -> tr(each(row, cell -> td(cell.toString()))))))
				)).render();
	}

	public static String list(List<String> list) {
		return "<!DOCTYPE HTML>" + html(
			head(meta().withCharset("utf-8")),
			body(
				h1("Html.list()"),
					table(tbody(
						tr(th(""), th("")),
						list == null ? tr(td("null"), td("")) : each(list, cell -> tr(td(""), td(cell)))))
				)).render(); // FIXME use render(Appendable)
	}

	public static String listSemtcs(List<SemanticObject> list) {
		return "<!DOCTYPE HTML>" + html(
			head(meta().withCharset("utf-8")),
			body(
				h1("Html.list()"),
					table(tbody(
						tr(th(""), th("")),
						list == null ? tr(td("null"), td("")) : each(list, cell -> trEx(cell))))
				)).render();
	}

	private static ContainerTag trEx(SemanticObject cell) {
		ContainerTag tr = tr("'");
		for (String p : cell.props().keySet()) {
			tr.with(td(p), td(cell.get(p).toString()));
		}
		return tr;
	}

	/**TODO can html writing outputStream?
	 * @param res
	 * @return html doc
	 */
	public static String map(HashMap<String, SemanticObject> res) {
		return "<!DOCTYPE HTML>" + html(
			head(meta().withCharset("utf-8")),
			body(
				h1("Html.map()"),
					table(tbody(
						tr(th("key"), th("value")),
						each(res.keySet(), k -> tr(td(res.get(k).toString())))))
				)).render();
	}

	public static String map(SemanticObject res) {
		// TODO Auto-generated method stub
		return null;
	}

	public static String ok(String msg) {
		return "<!DOCTYPE HTML>" + html(
			head(meta().withCharset("utf-8")),
			body(h1(msg)))
		.render();
	}

	public static String err(String msg) {
		return "<!DOCTYPE HTML>" + html(
			head(meta().withCharset("utf-8")),
			body(h1(msg).withSrc("color:red")))
		.render();
	}

}
