package io.oz.jserv.docs.syn;

import static io.oz.jserv.docs.syn.SynodetierJoinTest.slava;
import static io.oz.jserv.docs.syn.SynodetierJoinTest.syrskyi;
import static io.oz.jserv.docs.syn.singleton.SynotierJettyApp.zsu;

import java.io.IOException;

import io.odysz.anson.AnsonException;
import io.odysz.jclient.syn.Doclientier;
import io.odysz.jclient.tier.ErrorCtx;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantic.meta.ExpDocTableMeta;
import io.odysz.semantic.tier.docs.Device;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.x.TransException;

public class Dev {
	static int bsize;

	public final String sysuri;
	public final String synuri;

	public final String uid;
	public final String psw;
	public final Device device;

	public String res;
	public Doclientier client;


	public static Dev[] devs;

	public static final int X_0 = 0;
	public static final int X_1 = 1;
	public static final int Y_0 = 2;
	public static final int Y_1 = 3;

	static final String clientconn = "main-sqlite";
	public static ExpDocTableMeta docm;

	static {
		try {
			devs = new Dev[4];
			devs[X_0] = new Dev("sys-X", "doclient-00", syrskyi, slava, "X-0", zsu,
								"src/test/res/anclient.java/1-pdf.pdf");

			devs[X_1] = new Dev("sys-Y", "doclient-00", "syrskyi", "слава україні", "X-1", zsu,
								"src/test/res/anclient.java/2-ontario.gif");

			devs[Y_0] = new Dev("sys-Z", "doclient-01", "ody", "8964", "Y-0", zsu,
								"src/test/res/anclient.java/3-birds.wav");

			devs[Y_1] = new Dev("sys-W", "doclient-01", "syrskyi", "слава україні", "Y-1", zsu,
								"src/test/res/anclient.java/Amelia Anisovych.mp4");

			bsize = 72 * 1024;
			docm = new T_PhotoMeta(clientconn);
		} catch (TransException e) {
			e.printStackTrace();
		}
	}
	
	Dev(String sysuri, String synuri, String uid, String pswd, String device, String folder, String fres) {
		this.sysuri = sysuri;
		this.synuri = synuri;
		this.uid = uid;
		this.psw = pswd;
		this.res = fres;
		this.device = new Device(device, device, "test-doclient/" + device)
				.folder(folder);
	}

	public void login(ErrorCtx errLog) throws SemanticException, AnsonException, SsException, IOException {
		client = new Doclientier(docm.tbl, sysuri, synuri, errLog)
				.tempRoot(sysuri)
				.loginWithUri(uid, device.id, psw)
				; // .blockSize(bsize);
	}
}