package io.oz.jserv.sync;

import io.oz.jserv.sync.SyncWorker.SyncMode;

/**
 * Consts for testing.
 * <pre>
userId           |userName          |roleId |orgId |nationId |pswd          |iv |
-----------------|------------------|-------|------|---------|--------------|---|
ody              |Ody               |r02    |f/zsu |CN       |123456        |   |
syrskyi          |Oleksandr Syrskyi |r02    |f/zsu |UA       |слава україні |   |
odys-z.github.io |Ody Zelensky      |r02    |f/zsu |UA       |слава україні |   |
 * </pre>
 * @author odys-z@github.com
 */
public class ZSUNodes {
	public static final String clientUri = "/jnode";
	public static final String webRoot = "./src/test/res/WEB-INF";
	public static final String volumeDir = "./src/test/res/volume";

	public static final String jservUrl = "http://localhost:8081/jserv-album";
	public static final String family = "f/zsu";

	public static class Kyiv {
		public static final String folder = "red-forest";
		public static final String png = "src/test/res/182x121.png";

		public static class JNode {
			public static final String nodeId = "jnode-kyiv";
			public static final SyncMode mode = SyncMode.main;

			public static final String worker = "odys-z.github.io";
			public static final String passwd = "слава україні";
		}
	}

	public static class Kharkiv {
		public static final String folder = "trackor-brigade";

		public static class JNode {
			public static final String nodeId = "jnode-kharkiv";
			public static final SyncMode mode = SyncMode.priv;

			public static final String worker = "syrskyi";
			public static final String passwd = "слава україні";
		}
	}

	public static class AnDevice {
		public static final String userId = "syrskyi";
		public static final String passwd = "слава україні";
		public static final String device = "app.kharkiv";
		public static final String localFile = "src/test/res/anclient.java/Amelia Anisovych.mp4";
	}
}