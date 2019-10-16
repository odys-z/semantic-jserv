package io.odysz.jsample.servs;

import io.odysz.semantic.jprotocol.AnsonResp;
import io.odysz.semantic.jprotocol.IPort;

public class SampleResp extends AnsonResp {

	public SampleResp(IPort p) { }

	public SampleResp msg(String string) {
		m = string;
		return this;
	}

}
