package io.odysz.semantic.jserv.x;

import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;

/**Semantic session exception
 * @author ody
 *
 */
public class SsException extends Exception {
	private static final long serialVersionUID = 1L;
	public final MsgCode code = MsgCode.exSession;

	public SsException(String format, Object... args) {
		super(String.format(format, args));
	}


}
