package io.odysz.jsample.xvisual;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;

import io.odysz.anson.AnsonField;
import io.odysz.anson.utils.AnsonNvs;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.jprotocol.AnsonResp;

/**Response type for x-visual examples.
 * 
 * @author odys-z@github.com
 *
 */
public class XChartResp extends AnsonResp{

	@AnsonField(ignoreTo = true)
	boolean ready = false;

	String[] axises;
	String[] legend;
	String[] yrange;

	ArrayList<AnsonNvs> x;
	ArrayList<AnsonNvs> z;

	@AnsonField(valType="[Ljava.lang.Integer;")
	ArrayList<ArrayList<Integer>> vector;
	
	float max;
	float min;
	
	CubeChartConfig config;

	public XChartResp(AnResultset x, AnResultset z) throws SQLException {
		this.x = toMap(x);
		this.z = toMap(z);
		ready = true;
	}
	
	protected ArrayList<AnsonNvs> toMap(AnResultset labels) throws SQLException {
		HashMap<String, AnsonNvs>mp = new HashMap<String, AnsonNvs>();
		labels.beforeFirst();
		while (labels.next()) {
			String cat = labels.getString("cate");
			if (!mp.containsKey(cat)) {
				mp.put(cat, new AnsonNvs().name(cat));
			}
			mp.get(cat).value(labels.getString("indust"));
		}
		return new ArrayList<AnsonNvs>(mp.values());
	}
	
	public XChartResp axis(String u, String v, String w) {
		this.axises = new String[] {u, v, w};
		return this;
	}

	@SuppressWarnings("unchecked")
	public XChartResp vector(AnResultset y) {
		vector = (ArrayList<ArrayList<Integer>>) y.getRowsInt();
		return this;
	}
	
	public XChartResp range(AnResultset mxmn) throws SQLException {
		mxmn.beforeFirst();
		mxmn.next();
		this.max = mxmn.getLong("max");
		this.min = mxmn.getLong("min");
		return this;
	}
	
	public XChartResp yrange(String[] yrange) {
		this.yrange = yrange;
		return this;
	}

	public XChartResp legend(AnResultset legend) throws SQLException {
		this.legend = new String[legend.getRowCount()];
		legend.beforeFirst();
		int i = 0;
		while (legend.next()) {
			legend.getRows();
			this.legend[i] = legend.getString("legend");

			i++;
		}
		return this;
	}
}
