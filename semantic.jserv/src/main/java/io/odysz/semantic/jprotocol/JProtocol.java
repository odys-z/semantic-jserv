package io.odysz.semantic.jprotocol;

import java.io.OutputStream;

import com.google.gson.Gson;

import io.odysz.semantic.jprotocol.JMessage.MsgCode;
import io.odysz.semantic.jprotocol.JMessage.Port;
import io.odysz.semantic.jsession.SUser;
import io.odysz.semantics.SemanticObject;

public class JProtocol {

	public static Gson gson = new Gson();
	
//	public static <T> List<T> convert(String str) {
//		Type t = new TypeToken<ArrayList<T>>() {}.getType();
////		Type t = new TypeToken<List<JMessage>>() {}.getType();
//		List<T> j = gson.fromJson(str, t);
//		return j;
//	}
//
//	public static <T> String parse(List<T> s) {
//		Type t = new TypeToken<List<T>>() {}.getType();
//		String j = gson.toJson(s, t);
//		return j;
//	}

	public static void err(OutputStream o, Port port, MsgCode code, String err) {
//		SemanticObject obj = new SemanticObject();
//		obj.put("code", code.name());
//		obj.put("error", err);
//		obj.put("port", port.name());
		SemanticObject obj = err(port, code, err);
		JHelper.writeJson(o, obj);
	}

	public static SemanticObject err(Port port, MsgCode code, String err) {
		SemanticObject obj = new SemanticObject();
		obj.put("code", code.name());
		obj.put("error", err);
		obj.put("port", port.name());
		return obj;
	}

	public static SemanticObject ok(Port port, SUser iruser) {
		SemanticObject obj = new SemanticObject();
		obj.put("code", MsgCode.ok.name());
		obj.put("msg", iruser);
		obj.put("port", port.name());
		return obj;
	}
}
