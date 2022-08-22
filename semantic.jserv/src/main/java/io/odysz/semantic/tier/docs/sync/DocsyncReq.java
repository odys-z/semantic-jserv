package io.odysz.semantic.tier.docs.sync;

import io.odysz.semantic.jprotocol.AnsonBody;
import io.odysz.semantic.jprotocol.AnsonMsg;

public class DocsyncReq extends AnsonBody {

	protected DocsyncReq(AnsonMsg<? extends AnsonBody> parent, String uri) {
		super(parent, uri);
	}

}
