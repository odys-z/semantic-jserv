//package io.oz.jserv.dbsync;
//
//import io.odysz.anson.Anson;
//
//public class CleanState extends Anson {
//	public static final String D = "D";
//	public static final String E = "E";
//	public static final String R = "R";
//	public static final String C = "C";
//
//	final String flag;
//	final String synodee;
//	final String entsync;
//
//	/**
//	 * @param sid synodee
//	 * @param flag clean flag, i.e. 'D', 'E', 'R', 'C'
//	 * @param entsync entity record's sync-flag, one of {@link SyncFlag}'s consts.
//	 */
//	public CleanState(String sid, String flag, String entsync) {
//		this.synodee = sid;
//		this.flag = flag;
//		this.entsync = entsync;
//	}
//	
//	/**
//	 * @param f one of the consts, see {@link #D} etc.
//	 * @return true if yes.
//	 */
//	public boolean is(String f) {
//		return this.flag.equals(f);
//	}
//}
