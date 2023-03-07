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
  syncstamp DATETIME DEFAULT CURRENT_TIMESTAMP not NULL,

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

insert into h_photos2
(pid, family, folder, pname, uri, pdate, device, shareby, sharedate, tags, 
 geox, geoy, exif, oper, opertime, clientpath, mime, filesize, css, shareflag, sync) 
SELECT
 pid, family, folder, pname, uri, pdate, device, shareby, sharedate, tags, 
 geox, geoy, exif, oper, opertime, clientpath, mime, filesize, css, shareflag, sync 
from h_photos;

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

insert into a_sharelog  (synid, org, tabl, docId) select synid, org, tabl, '000000GP' 
from a_synodes n where org = 'f/zsu';

drop table if exists syn_stamp;
create table syn_stamp 
-- table-wise last updating stamps by remote nodes
-- 'D' is actually triggered a cleaning task; 'D' -> 'C'; 
-- for 'U', uri is not updated
(
	tabl    varchar2(64) NOT NULL, -- e.g. 'h_photos'
	synode  varchar2(12) NOT NULL, -- fk-on-del
	crud	char(1)      NOT NULL, -- 'R' is ignored; 'D' is actually triggered a cleaning task; 'D' -> 'C'; for 'U', only h_photos.sync & h_photos.clientpath is handled
	
	recount INTEGER,
	-- not correct if multiple updated and not used as Semantics.DA can't access context for commitment results.
	-- as photos are uploaded file by file, this is probably only useful for debugging.
	
	syncstamp DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL
);

drop table if exists syn_clean;
create table syn_clean (
	tabl       varchar2(64) NOT NULL, -- e.g. 'h_photos'
	synode     varchar2(12) NOT NULL, -- fk-on-del, synode id device to finish cleaning task
	clientpath text         NOT NULL, -- for h_photos.fullpath, or composed PK for other logic
	flag       char(1)      NOT NULL  -- 'D' deleting, 'C' close (not exists),'R' rejected by device owner
);

