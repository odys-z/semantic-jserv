package io.odysz.semantic.tier.docs.sync;

public class SyncWorker implements Runnable {

	public static final int hub = 0;
	public static final int main = 1;
	public static final int priv = 2;
	
	int mode;

	public SyncWorker(int mode) {
		this.mode = mode;
	}

	@Override
	public void run() {
		try {
//			HashSet<String> ss = new HashSet<String>();
//			long timestamp = System.currentTimeMillis();
//			for (String ssid : sspool.keySet()) {
//				IUser usr = sspool.get(ssid);
//				if (timestamp > usr.touchedMs() + timeout)
//					ss.add(ssid);
//			}
//
//			if (ss.size() > 0) {
//				Utils.logi ("Sesssion refeshed, expired session(s):");
//			}
//
//			for (String ssid : ss) {
//				IUser s = sspool.remove(ssid);
//				s.logout();
//				Utils.logi("[%s, %s]", ssid, s.uid());
//			}

		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

}
