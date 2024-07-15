package io.oz.jserv.docsync;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import io.odysz.semantic.syn.SynodeMode;
import io.odysz.semantics.x.SemanticException;
import io.oz.jserv.docsync.SyncFlag.SyncEvent;

/**
 * <img src='../../../../../../main/java/io/oz/jserv/docsync/sync-states.jpg'/>
 * @author odys-z@github.com
 */
class SynStateTest {

	@Test
	void testTo() throws SemanticException {
		assertEquals(SyncFlag.hub, new SynState(SynodeMode.hub, SyncFlag.priv).to(SyncEvent.pushubEnd));
		
		SynState s = new SynState(SynodeMode.hub, SyncFlag.priv).to(SyncEvent.push);
		assertEquals(SyncFlag.pushing, s);
		assertEquals(SyncFlag.hub, s.to(SyncEvent.pushubEnd));

		s = new SynState(SynodeMode.child, SyncFlag.device).to(SyncEvent.push);
		assertEquals(SyncFlag.pushing, s);
		try {
			assertEquals(SyncFlag.hub, s.to(SyncEvent.pushubEnd));
			fail("device on push-hub-end");
		}
		catch (SemanticException e) { }
		assertEquals(SyncFlag.hub, s.to(SyncEvent.pushubEnd));

		s.state(SyncFlag.pushing);
		assertEquals(SyncFlag.priv, s.to(SyncEvent.pushJnodend));

	}

}
