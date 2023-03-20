package io.oz.jserv.dbsync;

import io.odysz.semantic.tier.docs.SynEntity;

public interface IDBEntityResolver {

	SynEntity toEntity(DBSyncReq body);

}
