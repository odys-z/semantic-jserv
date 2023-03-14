package io.oz.jserv.docsync;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import io.odysz.anson.Anson;
import io.odysz.semantics.x.SemanticException;
import io.oz.jserv.docsync.SyncFlag.SyncEvent;

import static io.odysz.common.LangExt.isNull;
import static io.oz.jserv.docsync.SyncFlag.*;

public class SynState extends Anson {
	
	List<SyncEvent> firings;
	
	List<SyncAction> triggerings;

	private String now;
	
	private SynodeMode me;

	private Date stamp;
	
	public SynState (SynodeMode mode, String start) {
		this.now = start;
		this.me = mode;
		firings = new ArrayList<SyncEvent>(1);
		triggerings = new ArrayList<SyncAction>(1);
	}
	
	SynState to(SyncEvent e) throws SemanticException {
		clear();
		
		if (priv.equals(now) || (pushing.equals(now) && me == SynodeMode.bridge)) {
			if (e == SyncEvent.pushubEnd)
				now = hub;
			else if (e == SyncEvent.push)
				now = pushing;
		}
		else if (hub.equals(now)) {
			if (e == SyncEvent.close)
				now = close;
			else if (e == SyncEvent.pull && me == SynodeMode.bridge)
				now = priv;
		}
		else if (device.equals(now) || (pushing.equals(now) && me == SynodeMode.device)) {
			if (me != SynodeMode.device)
				throw new SemanticException("SyncState = device can only exists on a device.");

			// me == device
			if (e == SyncEvent.pushubEnd)
				now = hub;
			else if (e == SyncEvent.pushJnodend)
				now = priv;
			else if (e == SyncEvent.push)
				now = pushing;
		}

		return this;
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

	public boolean olderThan(Date date) {
		// TODO Auto-generated method stub
		return false;
	}

	public SynState stamp(Date date) {
		this.stamp = date;
		return this;
	}

}
