package io.odysz.semantic.jserv.x;

import java.security.GeneralSecurityException;

import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;

/**Semantic session exception
 * @author ody
 *
 */
public class SsException extends GeneralSecurityException {
	private static final long serialVersionUID = 1L;
	public final MsgCode code = MsgCode.exSession;

	public SsException(String format, Object... args) {
		super(String.format(format, args));
	}


}
