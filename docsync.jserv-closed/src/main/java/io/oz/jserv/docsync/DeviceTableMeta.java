package io.oz.jserv.docsync;

import java.sql.SQLException;
import java.util.ArrayList;

import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.meta.SynChangeMeta;
import io.odysz.semantic.meta.SyntityMeta;
import io.odysz.transact.x.TransException;

/**
 * Document records' table meta.
 * <p>For Docsync.jserv, this meta is used for both client and server side.
 * But the client should never use it as a parameter of API - only use a
 * parameter of table name for specifying how the server should handle it.</p>
 *
 * FIXME shouldn't subclassed from SynTableMeta?
 * 
 * DDL v alpha
 * <pre>
drop table if exists doc_devices;
CREATE TABLE doc_devices (
  synode0 varchar(12)  NOT NULL, -- initial node a device is registered
  device  varchar(12)  NOT NULL, -- a-k, generated when registering, but is used together with synode-0 for global file identity.
  devname varchar(256) NOT NULL, -- set by user, warn on duplicate, use old device id if user confirmed, otherwise generate a new one.
  mac     varchar(512),          -- an anciliary identity for recognize a device if there are supporting ways to automatically find out a device mac
  org     varchar(12)  NOT NULL, -- fk-del, usually won't happen
  owner   varchar(12),           -- or current user, not permenatly bound
  cdate   datetime,
  PRIMARY KEY (synode0, device)
); -- registered device names. Name is set by user, prompt if he's device names are duplicated
 * </pre>
 * @author odys-z@github.com
 */
public class DeviceTableMeta extends SyntityMeta {

	/** initial node a device is registered */
	public final String synode0;
	public final String devname;
	/** owner or current user */
	public final String owner;
	public final String mac;
	public final String cdate  ;
	// public final String market;

	public DeviceTableMeta(String conn) {
		super("doc_devices", "device", "org", conn);

		synode0 = "synode0";
		devname = "devname";
		owner   = "owner";
		mac     = "mac";
		cdate   = "cdate";
	}

	@Override
	public ArrayList<Object[]> updateEntNvs(SynChangeMeta chgm, String entid, AnResultset entities,
			AnResultset challenges) throws TransException, SQLException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object[] insertSelectItems(SynChangeMeta chgm, String entid, AnResultset entities, AnResultset changes)
			throws TransException, SQLException {
		// TODO Auto-generated method stub
		return null;
	}
}