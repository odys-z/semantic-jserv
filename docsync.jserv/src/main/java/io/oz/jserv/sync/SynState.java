package io.oz.jserv.sync;

import java.util.ArrayList;
import java.util.List;

import io.odysz.anson.Anson;
import io.oz.jserv.sync.SyncFlag.SyncEvent;

import static io.oz.jserv.sync.SyncFlag.*;

public class SynState extends Anson {
	
	List<SyncEvent> firings;
	
	List<SyncAction> triggerings;

	private String now;
	
	public SynState (String start) {
		this.now = start;
		firings = new ArrayList<SyncEvent>(1);
		triggerings = new ArrayList<SyncAction>(1);
	}
	
	SynState to(SyncEvent e) {
		clear();
		
		if (priv.equals(now) || pushing.equals(now)) {
			if (e == SyncEvent.pushubEnd)
				now = hub;
		}
		else if (hub.equals(now)) {
			if (e == SyncEvent.close)
				now = close;
			else if (e == SyncEvent.jnodePull)
				now = priv;
		}
		throw new NullPointerException("TODO");
		// return this;
	}
	
	SynState clear() {
		firings.clear();
		triggerings.clear();
		return this;
	}

	public SynState state(String next) {
		now = next;
		return this;
	}

}
