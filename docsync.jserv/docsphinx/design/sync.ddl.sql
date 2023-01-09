drop table if exists a_synodes;
CREATE TABLE a_synodes (
	synid   varchar2(64) NOT NULL,-- user input
	org     varchar2(12) NOT NULL,
	mac     varchar2(256),        -- if possible
	snycstamp datetime,           -- timestamp for last successfully synchronizing (both up & down). So each time will merge records between last-stamp & got-stamp (now - 1s)
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

CREATE TABLE h_photos2 (
  pid varchar(12) NOT NULL,
  family varchar2(12) NOT NULL,
  folder varchar(256) NOT NULL,
  pname varchar(256),
  uri varchar(512) NOT NULL,   -- storage/userId/folder/recId-clientname
  pdate datetime,              -- picture taken time
  
  device varchar(12),          -- 'original device ID',
  clientpath TEXT DEFAULT '/' NOT NULL, -- shall we support 'moveTo' laterly?
  oper varchar(12) not null,
  opertime datetime not null,  -- this is the timestamp
  -- syncstamp DATETIME DEFAULT CURRENT_TIMESTAMP not NULL,

  shareby varchar(12),         -- 'shared by / creator',
  sharedate datetime not null, -- 'shared date time',
  tags varchar(512) DEFAULT NULL ,
  geox double DEFAULT 0,
  geoy double DEFAULT 0,
  exif text default null,
  mime TEXT(64), 
  filesize INTEGER, 
  css text,                    -- e.g. {"type":"io.oz.album.tier.PhotoCSS", "size":[3147,1461,1049,487]}
  shareflag varchar2(12) default 'prv' not null, 
  sync varchar2(4),

  PRIMARY KEY (pid)
);

insert into h_photos2(pid, family, folder, pname, uri, pdate, device, shareby, sharedate, tags, geox, geoy, exif, oper, opertime, clientpath, mime, filesize, css, shareflag, sync) SELECT
pid, family, folder, pname, uri, pdate, device, shareby, sharedate, tags, geox, geoy, exif, oper, opertime, clientpath, mime, filesize, css, shareflag, sync from h_photos;

drop table h_photos;
alter table h_photos2 rename to h_photos;

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
