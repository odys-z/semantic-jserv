package io.odysz.semantic.jserv;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

import io.odysz.semantic.jprotocol.AnsonBody;
import io.odysz.semantic.jprotocol.AnsonMsg;

public abstract class ServHandler<T extends AnsonBody> {

	protected void onGet(AnsonMsg<T> msg, HttpServletResponse resp) throws ServletException, IOException {
	}

}
