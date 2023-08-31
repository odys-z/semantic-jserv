package io.odysz.semantic.ext;

import io.odysz.semantic.meta.SyntityMeta;

/**
 * Document records' table meta.
 * <p>For Docsync.jserv, this meta is used for both client and server side.
 * But the client should never use it as a parameter of API - only use a
 * parameter of table name for specifying how the server should handle it.</p>
 *
 * FIXME shouldn't subclassed from SynTableMeta?
 * 
 * @author odys-z@github.com
 */
public class DeviceTableMeta extends SyntityMeta {

	public final String synode0;
	public final String devname;
	public final String owner;
	public final String mac;
	public final String createDate;
	public final String market;

	public DeviceTableMeta(String conn) {
		super("doc_devices", "device", "org", conn);

		synode0 = "syndoe0";
		devname = "devname";
		owner = "owner";
		mac = "mac";
		createDate = "cdate";
		market = "market";
	}
}
