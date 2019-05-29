package io.odysz.semantic.jserv.U;

import io.odysz.semantic.jprotocol.JBody;
import io.odysz.semantic.jprotocol.JMessage;

/**<p>Insert Request helper</p>
 * <b>Note:</b>
 * <p>InsertReq is a subclass of UpdateReq, and have no {@link #toJson(com.google.gson.stream.JsonWriter, io.odysz.semantic.jprotocol.JOpts) toJson()}
 * and {@link #fromJsonName(String, com.google.gson.stream.JsonReader) fromJson()} implementation.
 * Otherwise any post updating list in request won't work.</p>
 * Because all request element is deserialized a UpdateReq, so this can only work for Update/Insert request.</p>
 * see {@link UpdateReq#fromJsonName(String, com.google.gson.stream.JsonReader) super.fromJsonName()}<br>
 * and {@link io.odysz.semantic.jprotocol.JHelper#readLstUpdateReq(com.google.gson.stream.JsonReader) JHelper.readListUpdateReq()}
 * @author odys-z@github.com
 */
public class InsertReq extends UpdateReq {

	public InsertReq(JMessage<? extends JBody> parent, String conn) {
		super(parent, conn);
	}
}
