package io.odysz.semantic.jserv.test;


import io.odysz.semantic.jprotocol.JBody;
import io.odysz.semantic.jprotocol.JMessage;

public class EchoReq extends JBody {

	public EchoReq(JMessage<? extends JBody> parent) {
		super(parent);
	}

//	public SemanticObject echo() {
//		return new SemanticObject().put("echo",
//				body.stream().map(m -> m.toString()).collect(Collectors.joining(", ", "[", "]")));
//	}

}
