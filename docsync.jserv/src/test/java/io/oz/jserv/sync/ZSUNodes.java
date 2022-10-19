package io.oz.jserv.sync;

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
	public static final String volumeDir = "/src/test/res/volume";

	public static final String jservUrl = "http://localhost:8081/jserv-album";
	public static final String family = "f/zsu";

	public static class Kyiv {
		public static final String folder = "red-forest";

		public static class JNode {
			public static final String worker = "odys-z.github.io";
			public static final String nodeId = "kyiv";
		}
	}

	public static class Kharkiv {
		public static final String folder = "trackor-brigade";

		public static class JNode {
			public static final String worker = "syrskyi";
			public static final String nodeId = "kharkiv";
		}

		public static class Anclient {
			public static final String userId = "syrskyi";
			public static final String device = "starlink.kharkiv";
			public static final String pswd = "слава україні";
		}
	}
}
