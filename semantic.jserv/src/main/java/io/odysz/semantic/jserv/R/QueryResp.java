//package io.odysz.semantic.jserv.R;
//
//import java.util.ArrayList;
//
//import io.odysz.module.rs.SResultset;
//import io.odysz.semantic.jprotocol.JMessage;
//
//public class QueryResp extends JMessage {
//
//	private ArrayList<SResultset> rses;
//
//	public QueryResp() {
//		super(Port.query);
//	}
//
//	public QueryResp rs(SResultset rs) {
//		rses = new ArrayList<SResultset>();
//		rses.add(rs);
//		return this;
//	}
//
//	public void add(SResultset rs2) {
//		if (rses == null)
//			rses = new ArrayList<SResultset>();
//	}
//
//	public ArrayList<SResultset> rs() {
//		return rses;
//	}
//
////	public void write(OutputStream os) {
////		JHelper.writeJson(os, rses);
////	}
//
//}
