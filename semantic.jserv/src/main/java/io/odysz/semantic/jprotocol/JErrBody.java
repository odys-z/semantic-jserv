package io.odysz.semantic.jprotocol;

public class JErrBody extends JBody {

	protected String err;
	
	/**
	 * @param parent perent can be any type of JMessage (to be refined)
	 * @param error
	 */
	public JErrBody(JMessage<?> parent, String error) {
		super(parent);
		err = error;
	}

}
