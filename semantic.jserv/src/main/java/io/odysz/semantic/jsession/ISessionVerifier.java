package io.odysz.semantic.jsession;

import java.sql.SQLException;

import io.odysz.semantic.jprotocol.JHeader;
import io.odysz.semantic.jserv.x.SsException;

public interface ISessionVerifier {

	SUser verify(JHeader jHeader) throws SsException, SQLException;


}
