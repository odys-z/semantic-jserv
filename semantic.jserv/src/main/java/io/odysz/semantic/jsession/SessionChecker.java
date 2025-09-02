package io.odysz.semantic.jsession;

import java.util.HashMap;
import java.util.HashSet;

import io.odysz.common.Utils;
import io.odysz.semantic.jserv.ServFlags;
import io.odysz.semantics.IUser;

/**
 * Session cleaner
 * 
 * @author Ody
 */
public class SessionChecker implements Runnable {
	private final long timeout;
	private HashMap<String, IUser> sspool;

	public SessionChecker(HashMap<String, IUser> ssPool, long timeoutMin) {
		this.sspool = ssPool;
		timeout = timeoutMin * 60 * 1000;
		if (ServFlags.session)
			Utils.logi("SessionChecker: timeout = %d minute", timeoutMin);
	}

	@Override
	public void run() {
		HashSet<String> ss = new HashSet<String>();
		try {
			long timestamp = System.currentTimeMillis();
			for (String ssid : sspool.keySet()) {
				IUser usr = sspool.get(ssid);
				if (timestamp > usr.touchedMs() + timeout)
					ss.add(ssid);
			}

			if (ss.size() > 0) {
				Utils.logi ("Sesssion refeshed. Session(s) idled (expired) in last %s minutes:", timeout / 60000);
			}

			for (String ssid : ss) {
				IUser s = sspool.remove(ssid);
				if (s != null) {
					s.logout();
					Utils.logi("[%s, %s : %s : %s]", ssid, s.orgId(), s.deviceId(), s.uid());
				}
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}
}
