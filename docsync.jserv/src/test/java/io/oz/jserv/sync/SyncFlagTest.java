package io.oz.jserv.sync;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import io.odysz.semantic.ext.DocTableMeta.Share;
import io.odysz.semantics.x.SemanticException;
import io.oz.jserv.sync.SyncFlag.SyncEvent;

/**
 * <img src='../../../../../../main/java/io/oz/jserv/sync/sync-states.jpg'/>
 * @author odys-z@github.com
 *
 */
class SyncFlagTest {

	@Test
	void testTo() {
		assertEquals(SyncFlag.hub, SyncFlag.to(SyncFlag.priv, SyncEvent.pushEnd, Share.priv));
		assertEquals(SyncFlag.hub, SyncFlag.to(SyncFlag.pushing, SyncEvent.pushEnd, Share.priv));
		assertEquals(SyncFlag.publish, SyncFlag.to(SyncFlag.priv, SyncEvent.pushEnd, Share.pub));
		assertEquals(SyncFlag.publish, SyncFlag.to(SyncFlag.pushing, SyncEvent.pushEnd, Share.pub));
		assertEquals(null, SyncFlag.to(null, SyncEvent.pushEnd, Share.pub));
		
		assertEquals(SyncFlag.priv, SyncFlag.to(SyncFlag.publish, SyncEvent.pull, Share.pub));
		assertEquals(SyncFlag.priv, SyncFlag.to(SyncFlag.publish, SyncEvent.pull, Share.priv)); // won't happen

		assertEquals(SyncFlag.priv, SyncFlag.to(SyncFlag.hub, SyncEvent.pull, Share.pub)); // won't happen
		assertEquals(SyncFlag.priv, SyncFlag.to(SyncFlag.hub, SyncEvent.pull, Share.priv));

		assertEquals(SyncFlag.publish, SyncFlag.to(SyncFlag.hub, SyncEvent.publish, null));
		assertEquals(SyncFlag.hub, SyncFlag.to(SyncFlag.publish, SyncEvent.hide, null));

		assertEquals(SyncFlag.close, SyncFlag.to(SyncFlag.publish, SyncEvent.close, Share.pub));
		assertEquals(SyncFlag.close, SyncFlag.to(SyncFlag.publish, SyncEvent.close, Share.priv)); // won't happen
		assertEquals(SyncFlag.close, SyncFlag.to(SyncFlag.hub, SyncEvent.close, Share.pub)); // won't happen
		assertEquals(SyncFlag.close, SyncFlag.to(SyncFlag.hub, SyncEvent.close, Share.priv));
	}

	@Test
	void testStart() throws SemanticException {
		assertEquals(SyncFlag.priv, SyncFlag.start(SynodeMode.priv, Share.pub));
		assertEquals(SyncFlag.priv, SyncFlag.start(SynodeMode.priv, Share.priv));

		assertEquals(SyncFlag.hub, SyncFlag.start(SynodeMode.hub, Share.priv));
		assertEquals(SyncFlag.publish, SyncFlag.start(SynodeMode.hub, Share.pub));
	}

}