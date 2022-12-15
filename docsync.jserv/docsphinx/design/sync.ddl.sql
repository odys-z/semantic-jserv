drop table if exists a_synodes;
CREATE TABLE a_synodes (
	synid   varchar2(12) NOT NULL,-- ak
	org     varchar2(12) NOT NULL,
	mac     varchar2(256),        -- if possible
	remarks varchar2(256),        -- empty?
	startup DATETIME,             -- last bring up time
	os      varchar2(256),        -- android, browser, etc.
	version varchar2(64),         -- docsync.jserv version
	extra   varchar2(512),
	PRIMARY KEY (synid)
);
insert into a_synodes (sid, org) values("jnode-kharkiv", "f/zsu");

drop table if exists a_sharelog;
create table a_sharelog (
	org    varchar2(12) NOT NULL, -- family, fk-on-del
	tabl   varchar2(64) NOT NULL, -- doc's business name, e.g. 'h_photos'
	docId  varchar2(12) NOT NULL, -- fk-on-del
	synode varchar2(12) NOT NULL, -- fk-on-del, synode & device
	clientpath text     NOT NULL,
	expire date
);
