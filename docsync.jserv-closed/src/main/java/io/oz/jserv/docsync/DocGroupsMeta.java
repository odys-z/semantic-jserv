package io.oz.jserv.docsync;

import io.odysz.semantics.meta.TableMeta;

/**
 * <pre>drop table if exists doc_groups;
  CREATE TABLE doc_groups (
    gid   varchar(12) NOT NULL,
    gname varchar(256),           -- DEFAULT NULL,
    owner varchar(12) NOT NULL,   -- comment 'shared by / creator',
    cdate datetime not null,      -- comment 'create date time',
    tags  varchar(512),
    oper  varchar(12) not null,
    opertime datetime not null,
  
    org    varchar(12) NOT NULL,  -- ??? intend org set by creator or his org?
    market varchar(12) NOT NULL,  -- ??? usually the lowest level org-id for all the groups, default org-0

    PRIMARY KEY (gid)
  ); -- sharing groups

  drop table if exists doc_group_users;
  CREATE TABLE doc_group_users (
    market varchar(12) NOT NULL,    -- filter for paerformance
    gid    varchar(12) NOT NULL,    -- fk-del
    uid    varchar(12) NOT NULL
  ); -- sharing relationships of groups vs users</pre>
 * 
 * @author odys-z@github.com
 */
public class DocGroupsMeta extends TableMeta {

	public final String gid;
	public final String gname;
	public final String owner;
	public final String cdate;
	public final String tags;
	public final String oper;
	public final String opertime;
	public final String version;
	public final String org;
	public final String market;

	public DocGroupsMeta(String... conn) {
		super("doc_grops", conn);
		
		this.gid     = "gid";
		this.gname   = "gname";
		this.owner   = "owner";
		this.cdate   = "cdate";
		this.tags    = "tags";
		this.oper    = "oper";
		this.opertime= "opertime";
		this.version = "version";
		this.org     = "org";
		this.market  = "market";
	}

}
