package io.odysz.semantic.jserv.crud;

import java.security.GeneralSecurityException;

import io.odysz.common.Utils;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;

/**
 * Semantic session exception
 * @author ody
 *
 * @since 1.5.17 replacing io.odysz.semantic.jserv.x.SsException
 */
public class SsException extends GeneralSecurityException {
	private static final long serialVersionUID = 1L;
	public final MsgCode code = MsgCode.exSession;

	public SsException(String format, Object... args) {
		super(tryFormat(format, args));
	}
	
	static String tryFormat(String format, Object ...args) {
		try { return String.format(format, args); }
		catch (Exception e) {
			try { Utils.warn(format); } catch (Exception f) {}
			e.printStackTrace();
			return format;
		}
	}
}
