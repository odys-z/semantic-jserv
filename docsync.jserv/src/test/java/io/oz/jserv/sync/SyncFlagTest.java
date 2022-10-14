package io.oz.jserv.sync;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import io.odysz.semantic.ext.DocTableMeta.Share;
import io.odysz.semantics.x.SemanticException;
import io.oz.jserv.sync.SyncFlag.SyncEvent;
import io.oz.jserv.sync.SyncWorker.SyncMode;

class SyncFlagTest {

	@Test
	void testTo() {
		assertEquals(SyncFlag.hub, SyncFlag.to(SyncFlag.pushing, SyncEvent.pushEnd, Share.priv));
		assertEquals(SyncFlag.publish, SyncFlag.to(SyncFlag.pushing, SyncEvent.pushEnd, Share.pub));
		assertEquals(SyncFlag.pushing, SyncFlag.to(SyncFlag.pushing, null, Share.pub));
		assertEquals(null, SyncFlag.to(null, SyncEvent.pushEnd, Share.pub));
		assertEquals(SyncFlag.pushing, SyncFlag.to(SyncFlag.pushing, SyncEvent.pull, Share.pub));
	}

	@Test
	void testStart() throws SemanticException {
		assertEquals(SyncFlag.priv, SyncFlag.start(SyncMode.priv, Share.pub));
		assertEquals(SyncFlag.priv, SyncFlag.start(SyncMode.priv, Share.priv));

		assertEquals(SyncFlag.hub, SyncFlag.start(SyncMode.hub, Share.priv));
		assertEquals(SyncFlag.publish, SyncFlag.start(SyncMode.hub, Share.pub));
	}

}
