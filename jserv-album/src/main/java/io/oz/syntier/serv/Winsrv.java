package io.oz.syntier.serv;

/**
 * Report to Procrun.exe
 */
public class Winsrv {

	@SuppressWarnings("unused")
	private String code;
	SynotierJettyApp app;
	@SuppressWarnings("unused")
	private String err;

	public Winsrv(String code, String err) {
		this.code = code;
		this.err = err;
	}

	public Winsrv(String code, SynotierJettyApp app) {
		this.code = code;
		this.app = app;
	}

}
