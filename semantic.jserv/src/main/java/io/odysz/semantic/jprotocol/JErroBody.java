package io.odysz.semantic.jprotocol;

public class JErroBody extends JBody {

	protected String err;
	
	public JErroBody(String error) {
		err = error;
	}

}
