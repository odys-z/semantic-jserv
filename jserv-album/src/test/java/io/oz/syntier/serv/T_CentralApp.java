package io.oz.syntier.serv;

import static io.odysz.common.Utils.awaitAll;
import static io.odysz.common.Utils.turngreen;
import static io.odysz.common.Utils.turnred;

import io.odysz.semantic.jprotocol.AnsonMsg.Port;
import io.odysz.semantic.jprotocol.JProtocol;
import io.oz.registier.central.CentralApp;
import io.oz.registier.central.CentralSettings;

public class T_CentralApp extends CentralApp {


	public static String central_jserv;

	public static Thread startCentral(boolean[] quit) throws InterruptedException {
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
		}, "SampleApp Jetty Server");

		t.start();
		awaitAll(ready, -1);
		central_jserv = app.jserv();
		return t;
	}
	
	
	public T_CentralApp(CentralSettings settings) throws Exception {
		super(settings);
	}
}
