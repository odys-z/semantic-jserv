package io.odysz.semantic.tier.docs;

import io.odysz.semantic.meta.SyntityMeta;

/**
 * <pre>
drop table if exists doc_devices;
CREATE TABLE doc_devices (
  synode0 varchar(12)  NOT NULL, -- initial node a device is registered
  device  varchar(25)  NOT NULL, -- prefix synode0 + ak, generated when registering, but is used together with synode-0 for file identity.
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
	public final String synoder;
	public final String devname;
	/** owner or current user */
	public final String owner;
	public final String mac;
	public final String cdate;
	public final String org;

	public DeviceTableMeta(String conn) {
		super("doc_devices", "device", "device", conn);

		synoder = "synode0";
		devname = "devname";
		owner   = "owner";
		mac     = "mac";
		cdate   = "cdate";
		org     = "org";
		
		ddlSqlite= "CREATE TABLE if not exists doc_devices (\r\n"
			+ "  synode0 varchar(12)  NOT NULL, -- initial node a device is registered\r\n"
			+ "  device  varchar(25)  NOT NULL, -- prefix synode0 + ak, generated when registering, but is used together with synode-0 for file identity.\r\n"
			+ "  devname varchar(256) NOT NULL, -- set by user, warn on duplicate, use old device id if user confirmed, otherwise generate a new one.\r\n"
			+ "  mac     varchar(512),          -- an anciliary identity for recognize a device if there are supporting ways to automatically find out a device mac\r\n"
			+ "  org     varchar(12)  NOT NULL, -- fk-del, usually won't happen\r\n"
			+ "  owner   varchar(12),           -- or current user, not permenatly bound\r\n"
			+ "  cdate   datetime,\r\n"
			+ "  io_oz_synuid varchar25, \r\n"
			+ "  PRIMARY KEY (synode0, device)\r\n"
			+ "); -- registered device names. Name is set by user, prompt if he's device names are duplicated";
	}
}
