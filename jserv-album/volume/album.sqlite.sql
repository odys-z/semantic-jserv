-- h stands for home

DROP table if exists h_photos;
CREATE TABLE h_photos (
  pid varchar(12) NOT NULL,
  folder varchar(256) NOT NULL,
  pname varchar(256),
  uri varchar(512) NOT NULL, -- storage/userId/folder/recId-clientname
  pdate datetime,     -- picture taken time
  device varchar(12), -- 'original device ID',
  shareby varchar(12), -- 'shared by / creator',
  sharedate datetime not null, -- 'shared date time',
  tags varchar(512) DEFAULT NULL ,
  geox double DEFAULT 0,
  geoy double DEFAULT 0,
  exif text default null,
  oper varchar(12) not null,
  opertime datetime not null,
  clientpath TEXT DEFAULT '/' NOT NULL,
  mime TEXT(64),       -- e.g. image/png;base64,
  css text,            -- ui styles, v0.5.5: {type: NA, size: [width, height, w, h]}, where w/h is reduction of width/height

  PRIMARY KEY (pid)
);


DROP table if exists h_collects ;
CREATE TABLE h_collects (
  cid varchar(12) NOT NULL,     -- NOT NULL,
  cname varchar(256),           -- DEFAULT NULL,
  shareby varchar(12) NOT NULL, -- comment 'shared by / creator',
  yyyy_mm varchar(8) NOT NULL,  -- subfolder
  cdate datetime not null,      -- comment 'create date time',
  tags varchar(512) DEFAULT NULL ,
  oper varchar(12) not null,
  opertime datetime not null,

  PRIMARY KEY (cid)
); -- subject collection, every photo has a default collection, uid/yyyy_mm

DROP table if exists h_coll_phot ;
CREATE TABLE h_coll_phot (
  cid varchar(12) NOT NULL,
  pid varchar(12) NOT NULL,
  CONSTRAINT h_coll_phot_FK1 FOREIGN KEY (cid) REFERENCES h_collects (cid),
  CONSTRAINT h_coll_phot_FK2 FOREIGN KEY (pid) REFERENCES h_photos (pid)
);

DROP table if exists h_albums ;
CREATE TABLE h_albums (
  aid varchar(12) NOT NULL,
  storage varchar(256) NOT NULL,
  aname varchar(256) DEFAULT NULL,
  shareby varchar(12), -- comment 'shared by / creator',
  cdate datetime not null, -- comment 'create date time',
  tags varchar(512) DEFAULT NULL ,
  oper varchar(12) not null,
  opertime datetime not null,

  PRIMARY KEY (aid)
);

DROP table if exists h_album_coll ;
CREATE TABLE h_album_coll (
  aid varchar(12) NOT NULL,
  cid varchar(12) NOT NULL,
  CONSTRAINT h_album_coll_FK1 FOREIGN KEY (aid) REFERENCES h_albums (aid),
  CONSTRAINT h_album_coll_FK2 FOREIGN KEY (cid) REFERENCES h_collects (cid)
); -- collection-album relationship

SELECT name FROM sqlite_schema WHERE type ='table';



drop table if exists doc_devices;
CREATE TABLE doc_devices (
  synode0 varchar(12)  NOT NULL, -- initial node a device is registered
  device  varchar(12)  NOT NULL, -- ak, generated when registering, but is used together with synode-0 for file identity.
  devname varchar(256) NOT NULL, -- set by user, warn on duplicate, use old device id if user confirmed, otherwise generate a new one.
  mac     varchar(512),          -- an anciliary identity for recognize a device if there are supporting ways to automatically find out a device mac
  org     varchar(12)  NOT NULL, -- fk-del, usually won't happen
  owner   varchar(12),           -- or current user, not permenatly bound
  cdate   datetime,
  PRIMARY KEY (synode0, device)
); -- registered device names. Name is set by user, prompt if he's device names are duplicated

insert into oz_autoseq (sid, seq, remarks) values ('doc_devices.device', 0, '');
