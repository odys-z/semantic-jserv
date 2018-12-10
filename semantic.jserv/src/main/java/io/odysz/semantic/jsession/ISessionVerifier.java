package io.odysz.semantic.jsession;

import java.sql.SQLException;

import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantics.SemanticObject;

public interface ISessionVerifier {

	SUser verify(SemanticObject jHeader) throws SsException, SQLException;

}
