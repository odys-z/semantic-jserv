package io.oz.jserv.sync;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import io.oz.jserv.sync.SyncFlag.SyncEvent;

/**
 * <img src='../../../../../../main/java/io/oz/jserv/sync/sync-states.jpg'/>
 * @author odys-z@github.com
 */
class SynStateTest {

	@Test
	void testTo() {
		assertEquals(SyncFlag.hub, new SynState(SyncFlag.priv).to(SyncEvent.pushubEnd));
		
		SynState s = new SynState(SyncFlag.priv).to(SyncEvent.push);
		assertEquals(SyncFlag.pushing, s);
		assertEquals(SyncFlag.hub, s.to(SyncEvent.pushubEnd));

		s = new SynState(SyncFlag.device).to(SyncEvent.push);
		assertEquals(SyncFlag.pushing, s);
		assertEquals(SyncFlag.hub, s.to(SyncEvent.pushubEnd));

		s.state(SyncFlag.pushing);
		assertEquals(SyncFlag.priv, s.to(SyncEvent.pushJnodend));

	}

}
