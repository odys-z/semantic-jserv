package io.odysz.semantic.jsession;

import io.odysz.semantic.jprotocol.AnsonHeader;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantics.IUser;

public interface ISessionVerifier {

	/**Verify session token
	 * @param AnsonHeader
	 * @return IUser instance
	 * @throws SsException
	 */
	default IUser verify(AnsonHeader AnsonHeader) throws SsException {
		// default function body for old version
		return null;
	};


}
