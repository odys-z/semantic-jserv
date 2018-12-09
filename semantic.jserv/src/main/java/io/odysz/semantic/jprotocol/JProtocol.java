package io.odysz.semantic.jprotocol;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import io.odysz.semantics.SemanticObject;

public class JProtocol {

	static Gson gson = new Gson();
	
	public static class HeartBeat extends JMessage {
		private String ssid;

		protected HeartBeat(String ssid) {
			super(Port.heartbeat);
			this.ssid = ssid;
		}

		public static String Req(String ssid) {
			HeartBeat beat = new HeartBeat(ssid);
			String req = gson.toJson(beat);
			return req;
		}
		
		public static HeartBeat onReq(String req) {
			HeartBeat msg = gson.fromJson(req, HeartBeat.class);
			return msg;
		}
		
		public static HeartBeat respond (String ssid) {
			return new HeartBeat(ssid);
		}

		public static void onResp (String resp) { }
	}

	public static class Session {
		static JHelper<Msg> jhelper = new JHelper<Msg>();

		public static class Msg extends JMessage {
			Port typ = Port.seesion;
			MsgCode code;
			private SemanticObject functions;

			public Msg ok() {
				code = MsgCode.ok;
				return this;
			}

			public void respond(SemanticObject functions) {
				this.functions = functions;
			}
		}

		public static String req(String t, Session.Msg req) {
			return gson.toJson(req);
		}
		
		public static void respond(OutputStream os, Msg msg) throws IOException {
			jhelper.writeJsonStream(os, msg);
		}
	}
	
	
	public static <T> List<T> convert(String str) {
		Type t = new TypeToken<ArrayList<T>>() {}.getType();
//		Type t = new TypeToken<List<JMessage>>() {}.getType();
		List<T> j = gson.fromJson(str, t);
		return j;
	}

	public static <T> String parse(List<T> s) {
		Type t = new TypeToken<List<T>>() {}.getType();
		String j = gson.toJson(s, t);
		return j;
	}
}
