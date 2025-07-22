package io.odysz.semantic.jsession;

import io.odysz.semantic.jprotocol.AnsonHeader;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantics.IUser;

public interface ISessionVerifier {

	/**Verify session token
	 * @since 1.4.36, requires seq number
	 * @param AnsonHeader
	 * @param seq message sequence, not used
	 * @return IUser instance
	 * @throws SsException
	 */
	default IUser verify(AnsonHeader AnsonHeader, int... seq) throws SsException {
		return null;
	};


}
