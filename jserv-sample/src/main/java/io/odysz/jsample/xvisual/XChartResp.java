package io.odysz.jsample.xvisual;

import java.util.ArrayList;

import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.jprotocol.AnsonResp;

/**Response type for x-visual examples.
 * 
 * @author odys-z@github.com
 *
 */
public class XChartResp extends AnsonResp{

	ArrayList<ArrayList<Object>> vector;
	ArrayList<ArrayList<Object>> x;
	ArrayList<ArrayList<Object>> z;
	
	float max;
	float min;
	
	CubeChartConfig config;

	public XChartResp(AnResultset x, AnResultset z, AnResultset y) {
		vector = y.getRows();
		
		this.x = new ArrayList<ArrayList<Object>>(x.getRowCount()); 
		this.x = x.getRows();
		this.z = z.getRows();
	}
	
	public XChartResp range(float max, float min) {
		
		return this;
	}
	
	public XChartResp config(float[] yrange, String[] legend) {
		
		return this;
	}
}
