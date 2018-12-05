package io.odysz.semantic.jprotocol;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import io.odysz.semantic.jprotocol.JProtocolTest.Axby;

public class JProtocol {

	static Gson gson = new Gson();
	
	public static <T> List<T> convert(String str) {
//		Type t = new TypeToken<ArrayList<T>>() {}.getType();
		Type t = new TypeToken<T>() {}.getType();
		List<T> j = gson.fromJson(str, t);
		return j;
	}

	public static <T> String parse(List<T> s) {
		Type t = new TypeToken<T>() {}.getType();
		String j = gson.toJson(s, t);
		return j;
	}
}
