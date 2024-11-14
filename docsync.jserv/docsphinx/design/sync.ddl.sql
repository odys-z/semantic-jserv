drop table if exists syn_node;
CREATE TABLE syn_node (
	synid     varchar2(64) NOT NULL,-- user input
	org       varchar2(12) NOT NULL,
	mac       varchar2(256),        -- if possible
	snycstamp datetime,             -- timestamp for last successfully synchronizing (both up & down). So each time will merge records between last-stamp & got-stamp (now - 1s)
	remarks   varchar2(256),        -- empty?
	startup   DATETIME,             -- last bring up time
	os        varchar2(256),        -- android, browser, etc.
	anclient  varchar2(64),         -- docsync.jserv version
	extra     varchar2(512),
	oper      varchar2(12) NOT NULL,
	optime    datetime NOT NULL,
	PRIMARY KEY (synid)
);
delete from syn_nodes where org = 'f/zsu';
insert into syn_nodes (synid, org, oper, optime) values
("jnode-kharkiv", "f/zsu", 'ody', datetime('now')),
("jnode-kyiv", "f/zsu", 'ody', datetime('now')),
("app.syrskyi", "f/zsu", 'ody', datetime('now'))
;

CREATE TABLE h_photos2 (
  pid varchar(12) not null,
  family varchar2(12) not null,
  folder varchar(256) not null,
  pname varchar(256),
  uri varchar(512) not null,   -- storage/userId/folder/recId-clientname
  pdate datetime,              -- picture taken time

  device varchar(12),          -- 'original device ID',
  clientpath TEXT not null,    -- shall we support 'moveTo' laterly?
  oper varchar(12) not null,
  opertime datetime not null,  -- this is the timestamp
  syncstamp DATETIME default CURRENT_TIMESTAMP not null,

  shareby varchar(12),         -- 'shared by / creator',
  sharedate datetime not null, -- 'shared date time',
  tags varchar(512) default null,
  geox double default 0,
  geoy double default 0,
  exif text default null,
  mime TEXT(64),
  filesize INTEGER,
  css text,                    -- e.g. {"type":"io.oz.album.tier.PhotoCSS", "size":[3147,1461,1049,487]}
  shareflag varchar2(12) default 'prv' not null,
  sync varchar2(4),

  PRIMARY KEY (pid)
);

insert into h_photos2
(pid, family, folder, pname, uri, pdate, device, shareby, sharedate, tags,
 geox, geoy, exif, oper, opertime, clientpath, mime, filesize, css, shareflag, sync)
SELECT
 pid, family, folder, pname, uri, pdate, device, shareby, sharedate, tags,
 geox, geoy, exif, oper, opertime, clientpath, mime, filesize, css, shareflag, sync
from h_photos;

drop table h_photos;
alter table h_photos2 rename to h_photos;

drop trigger if exists photoStamp;
CREATE TRIGGER photoStamp UPDATE OF family, folder, pname, uri, pdate, device, shareby, sharedate, tags,
 geox, geoy, exif, clientpath, mime, filesize, css, shareflag, sync
 ON h_photos
BEGIN
  UPDATE h_photos SET syncstamp = CURRENT_TIMESTAMP WHERE tabl = NEW.tabl and pid = NEW.pid;
END;

drop table if exists a_sharelog;
create table a_sharelog (
	org    varchar2(12) not null, -- family, fk-on-del
	tabl   varchar2(64) not null, -- doc's business name, e.g. 'h_photos'
	docId  varchar2(12) not null, -- fk-on-del
	synode varchar2(12) not null, -- fk-on-del, synode & device
	clientpath text     not null, -- clientpath
	dstpath text,
	expire date,
	oper   varchar2(12),
	optime datetime
);

select * from a_sharelog;
select * from a_synodes;

insert into a_sharelog  (synid, org, tabl, docId) select synid, org, tabl, '000000GP'
from a_synodes n where org = 'f/zsu';

drop table if exists syn_stamp;
create table syn_stamp
-- table-wise last updating stamps by remote nodes
-- 'D' is actually triggered a cleaning task; 'D' -> 'C';
-- for 'U', uri is not updated
(
	tabl    varchar2(64) not null, -- e.g. 'h_photos'
	synode  varchar2(12) not null, -- fk-on-del
	crud	char(1)      not null, -- 'R' is ignored; 'D' is actually triggered a cleaning task; 'D' -> 'C'; for 'U', only h_photos.sync & h_photos.clientpath is handled

	recount integer,
	-- not correct if multiple updated and not used as Semantics.DA can't access context for commitment results.
	-- as photos are uploaded file by file, this is probably only useful for debugging.

	syncstamp datetime default CURRENT_TIMESTAMP not null
);

drop table if exists syn_clean;
create table syn_clean (
	tabl       varchar2(64) not null, -- e.g. 'h_photos'
	synoder    varchar2(12) not null, -- fk-on-del, synode id for resource's PK
	clientpath text         not null, -- for h_photos.fullpath, or composed PK for resouce's id
	synodee    varchar2(12) not null, -- fk-on-del, synode id device to finish cleaning task
	flag       char(1)      not null, -- 'D' deleting, 'C' close (not exists),'R' rejected by device owner
	syncstamp  datetime     default CURRENT_TIMESTAMP not null
);

select * from a_sharelog;
select * from a_synodes;

insert into a_sharelog  (synid, org, tabl, docId) select synid, org, tabl, '000000GP'
from a_synodes n where org = 'f/zsu';

drop table if exists syn_stamp;
create table syn_stamp
-- table-wise last updating stamps by remote nodes
-- 'D' is actually triggered a cleaning task; 'D' -> 'C';
-- for 'U', uri is not updated
(
	tabl    varchar2(64) not null, -- e.g. 'h_photos'
	synodee varchar2(12) not null, -- fk-on-del, client id asked for sychronizing
	crud	char(1)      not null, -- 'R' is ignored; 'D' is actually triggered a cleaning task; 'D' -> 'C'; for 'U', only h_photos.sync & h_photos.clientpath is handled

	recount integer,
	-- not correct if multiple updated and not used as Semantics.DA can't access context for commitment results.
	-- as photos are uploaded file by file, this is probably only useful for debugging.

	syncstamp  datetime default CURRENT_TIMESTAMP not null,
	cleanstamp datetime default CURRENT_TIMESTAMP not null
);

drop table if exists syn_clean;
create table syn_clean (
	tabl        varchar2(64) not null, -- e.g. 'h_photos'
	synoder     varchar2(12) not null, -- fk-on-del, synode id for resource's PK
	clientpath  text         not null, -- for h_photos.fullpath, or composed PK for resouce's id
	synodee     varchar2(12) not null, -- fk-on-del, synode id device to finish cleaning task
	flag        char(1)      not null, -- 'D' deleting, 'C' close (not exists),'R' rejected by device owner
	cleanstamp  datetime     default CURRENT_TIMESTAMP not null
);
