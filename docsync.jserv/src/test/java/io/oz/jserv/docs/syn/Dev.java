package io.oz.jserv.docs.syn;

import static io.oz.jserv.docs.syn.SynoderTest.zsu;
import static io.oz.jserv.docs.syn.SynodetierJoinTest.slava;
import static io.oz.jserv.docs.syn.SynodetierJoinTest.syrskyi;

import io.odysz.semantic.meta.ExpDocTableMeta;
import io.odysz.transact.x.TransException;

public class Dev {
	static int bsize;

	public final String uri;
	public final String uid;
	public final String psw;
	public final String dev;
	public final String folder;

	public String res;
	public Doclientier client;

	static Dev[] devs;

	static final int X_0 = 0;
	static final int X_1 = 1;
	static final int Y_0 = 2;
	static final int Y_1 = 3;

	static final String clientconn = "main-sqlite";
	static ExpDocTableMeta docm;

	static {
		try {
			devs = new Dev[4];
			devs[X_0] = new Dev("client-at-00", syrskyi, slava, "X-0", zsu,
								"src/test/res/anclient.java/1-pdf.pdf");

			devs[X_1] = new Dev("client-at-00", "syrskyi", "слава україні", "X-1", zsu,
								"src/test/res/anclient.java/2-ontario.gif");

			devs[Y_0] = new Dev("client-at-01", "odyz", "8964", "Y-0", zsu,
								"src/test/res/anclient.java/3-birds.wav");

			devs[Y_1] = new Dev("client-at-01", "syrskyi", "слава україні", "Y-1", zsu,
								"src/test/res/anclient.java/Amelia Anisovych.mp4");

			bsize = 72 * 1024;
			docm = new T_PhotoMeta(clientconn);
		} catch (TransException e) {
			e.printStackTrace();
		}
	}
	Dev(String uri, String uid, String pswd, String device, String folder, String fres) {
		this.uri = uri;
		this.uid = uid;
		this.psw = pswd;
		this.dev = "test-doclient/" + device;
		this.folder = folder;
		this.res = fres;
	}
}