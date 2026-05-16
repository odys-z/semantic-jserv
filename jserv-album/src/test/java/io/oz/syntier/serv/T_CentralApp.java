package io.oz.syntier.serv;

import static io.odysz.common.LangExt.mustnonull;
import static io.odysz.common.Utils.awaitAll;
import static io.odysz.common.Utils.turngreen;
import static io.odysz.common.Utils.turnred;

import java.sql.SQLException;

import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
import io.odysz.semantic.jprotocol.JProtocol;
import io.odysz.semantic.jsession.JUser;
import io.odysz.semantic.jsession.JUser.JUserMeta;
import io.odysz.semantic.util.DAHelper;
import io.odysz.transact.x.TransException;
import io.oz.registier.central.CentralApp;
import io.oz.registier.central.CentralSettings;

public class T_CentralApp extends CentralApp {
	static final String zsu = "zsu";

	public static String central_jserv;
	public static String admin_pswd;

	public static final String admin = "admin";

	static final String cent_conn = "t-central-sqlite";

	@SuppressWarnings("deprecation")
	public static Thread startCentral(boolean[] quit)
			throws InterruptedException, SQLException, TransException {
		boolean[] ready = new boolean[] {false};
		turnred(ready);

		JProtocol.setup("jserv-central", Port.echo);

		Thread t = new Thread(() -> {
			main(new String[] {"t_central-settings.json"});
			turngreen(ready);
			try {
				awaitAll(quit, -1);
				app.stop();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}, "T_CentralApp Jetty Server");

		t.start();
		awaitAll(ready, -1);
		central_jserv = app.jserv();
		
		JUserMeta um = new JUser.JUserMeta();
		DATranscxt cb = new DATranscxt(cent_conn);


		if (DAHelper.count(cb, cent_conn, um.tbl, um.pk, admin) == 0)
			DAHelper.insert(DATranscxt.dummyUser(), cb, cent_conn, um,
					um.pk, admin, um.pswd, "8964", um.uname, "Admin", um.org, "zsu");

		admin_pswd = DAHelper.getValstr(cb, cent_conn, um, um.pswd, um.pk, admin);
		mustnonull(admin_pswd);
		return t;
	}
	
	public T_CentralApp(CentralSettings settings) throws Exception {
		super(settings);
	}
}
