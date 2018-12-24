package io.odysz.semantic.jsession;

import java.sql.SQLException;

import io.odysz.semantic.jprotocol.JHeader;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantics.IUser;

public interface ISessionVerifier {

	IUser verify(JHeader jHeader) throws SsException, SQLException;


}
