package io.odysz.semantic.jsession;

import java.sql.SQLException;

import io.odysz.semantic.jprotocol.AnsonHeader;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantics.IUser;

public interface ISessionVerifier {

	/**Verify session token
	 * @param AnsonHeader
	 * @return
	 * @throws SsException
	 * @throws SQLException
	 */
	default IUser verify(AnsonHeader AnsonHeader) throws SsException, SQLException {
		// default function body for old version
		return null;
	};


}
