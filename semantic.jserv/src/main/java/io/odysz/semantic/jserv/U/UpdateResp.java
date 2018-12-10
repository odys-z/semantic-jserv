package io.odysz.semantic.jserv.U;

import java.util.HashMap;

import io.odysz.semantic.jprotocol.JMessage;
import io.odysz.semantics.SemanticObject;

public class UpdateResp extends JMessage {

	private HashMap<String, SemanticObject> res;

	public UpdateResp(HashMap<String,SemanticObject> res) {
		super(Port.update);
		this.res = res;
	}

}
