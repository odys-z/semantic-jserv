package io.odysz.semantic.jsession;

import java.util.HashMap;
import java.util.HashSet;

import io.odysz.semantics.IUser;

public class SessionChecker implements Runnable {
	private final long timeout;
	private HashMap<String, IUser> sspool;

	public SessionChecker(HashMap<String, IUser> ssPool, long timeoutMin) {
		this.sspool = ssPool;
		timeout = timeoutMin * 60 * 1000;
	}

	@Override
	public void run() {
		HashSet<String> ss = new HashSet<String>();
//		IrSession.lock.lock();
		try {
			long timestamp = System.currentTimeMillis();
			for (String ssid : sspool.keySet()) {
				IUser usr = sspool.get(ssid);
				if (timestamp > usr.touchedMs() + timeout)
					ss.add(ssid);
			}
			for (String ssid : ss) {
				sspool.remove(ssid);
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
//		if (ServFlags.session)
//			Utils.logi ("[ServFlags.session] Sesssion refeshed, remain ssid(s):\n%s",
//					sspool.keySet());
	}
}
