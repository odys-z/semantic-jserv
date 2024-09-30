package io.oz.jserv.docsync;

import io.odysz.semantics.meta.TableMeta;

/**
 * @deprecated replaced by {@link io.odysz.semantic.meta.SynodeMeta}
 * a_synodes table meta
 * 
 * <pre>DDL
drop table if exists a_synodes;
CREATE TABLE a_synodes (
	synid   varchar2(64) NOT NULL,-- user input
	org     varchar2(12) NOT NULL,
	mac     varchar2(256),        -- if possible
	snycstamp datetime,           -- timestamp
	remarks varchar2(256),        -- empty?
	startup DATETIME,             -- last bring up time
	os      varchar2(256),        -- android, browser, etc.
	version varchar2(64),         -- docsync.jserv version
	extra   varchar2(512),
	PRIMARY KEY (synid)
);</pre>
 * @author odys-z@github.com
 *
 */
public class SynodeMeta extends TableMeta {

	public final String org;
	public final String synid;
	public final String mac;
	public final String synycstamp;
	public final String remarks;
	public final String startup;
	public final String os;
	public final String version;
	public final String extra;

	public SynodeMeta(String... conn) {
		super("a_synodes", conn);
		
		this.synid = "synid";
		this.org = "org";
		this.mac = "mac";
		this.synycstamp = "synycstamp";
		this.remarks = "remarks";
		this.startup = "startup";
		this.os = "os";
		this.version = "version";
		this.extra = "extra";
	}

}
