drop table if exists a_synodes;
CREATE TABLE a_synodes (
	synid   varchar2(64) NOT NULL,-- user input
	org     varchar2(12) NOT NULL,
	mac     varchar2(256),        -- if possible
	remarks varchar2(256),        -- empty?
	startup DATETIME,             -- last bring up time
	os      varchar2(256),        -- android, browser, etc.
	version varchar2(64),         -- docsync.jserv version
	extra   varchar2(512),
	PRIMARY KEY (synid)
);
delete from a_synodes where org = 'f/zsu';
insert into a_synodes (synid, org) values
("jnode-kharkiv", "f/zsu"),
("jnode-kyiv", "f/zsu"),
("app.syrskyi", "f/zsu")
;


drop table if exists a_sharelog;
create table a_sharelog (
	org    varchar2(12) NOT NULL, -- family, fk-on-del
	tabl   varchar2(64) NOT NULL, -- doc's business name, e.g. 'h_photos'
	docId  varchar2(12) NOT NULL, -- fk-on-del
	synid  varchar2(64) NOT NULL, -- fk-on-del, synode & device
	clientpath text     NOT NULL, -- clientpath
	dstpath text,
	expire date,
	oper   varchar2(12),
	optime datetime
);

select * from a_sharelog;
select * from a_synodes;

insert into a_sharelog  (synid, org, tabl, docId) select synid, org, tabl, '000000GP' from a_synodes n where org = 'f/zsu';
