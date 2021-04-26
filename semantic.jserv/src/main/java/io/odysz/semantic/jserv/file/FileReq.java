package io.odysz.semantic.jserv.file;

import io.odysz.semantic.jprotocol.AnsonBody;
import io.odysz.semantic.jprotocol.AnsonMsg;

public class FileReq extends AnsonBody {
	String file;
	int len;
	String payload;

	protected FileReq(AnsonMsg<? extends AnsonBody> parent, String filename) {
		super(parent, null);
		file = filename;
	}

	public String file() { return file; }

}
