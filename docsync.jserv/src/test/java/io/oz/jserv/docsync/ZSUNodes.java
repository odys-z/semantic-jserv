package io.oz.jserv.docsync;

import io.odysz.semantic.syn.SynodeMode;

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
	public static final String webRoot   = "./src/test/res/WEB-INF";
	public static final String volumeDir = "./src/test/res/volume";

	public static final String jservHub = "http://localhost:8081/jserv-album";
	/** shouldn't be used directly - configured at album-jserv */
	public static final String family = "f/zsu";

	public static class Kyiv {
		public static final String folder = "red-forest";
		public static final String png = "src/test/res/182x121.png";

		public static class Synode {
			public static final String nodeId = "jnode-kyiv";
			public static final SynodeMode mode = SynodeMode.peer;

			public static final String worker = "odys-z.github.io";
			public static final String passwd = "слава україні";
		}
	}

	public static class Kharkiv {
		public static final String folder = "tractor-brigade";

		public static class Synode {
			public static final String nodeId = "jnode-kharkiv";
			public static final SynodeMode mode = SynodeMode.peer;

			public static final String worker = "syrskyi";
			public static final String passwd = "слава україні";
		}
	}

	public static class AnDevice {
		public static final String userId = "syrskyi";
		public static final String passwd = "слава україні";
		public static final String device = "app.syrskyi";
		public static final String localFile = "src/test/res/anclient.java/Amelia Anisovych.mp4";
	}
}
