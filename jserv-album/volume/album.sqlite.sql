-- h stands for home

DROP table if exists h_photos;
CREATE TABLE h_photos (
  pid varchar(12) NOT NULL,
  folder varchar(256) NOT NULL,
  pname varchar(256),
  uri varchar(512) NOT NULL, -- storage/userId/folder/recId-clientname
  pdate datetime,
  device varchar(12), -- 'original device ID',
  shareby varchar(12), -- 'shared by / creator',
  cdate datetime not null, -- 'create date time',
  tags varchar(512) DEFAULT NULL ,
  geox double DEFAULT 0,
  geoy double DEFAULT 0,
  exif text default null,
  oper varchar(12) not null,
  opertime datetime not null,

  PRIMARY KEY (pid)
) ;

DROP table if exists h_collects ;
CREATE TABLE h_collects (
  cid varchar(12), -- NOT NULL,
  cname varchar(256), -- DEFAULT NULL,
  shareby varchar(12), -- comment 'shared by / creator',
  cdate datetime not null, -- comment 'create date time',
  tags varchar(512) DEFAULT NULL ,
  oper varchar(12) not null,
  opertime datetime not null,

  PRIMARY KEY (cid)
); -- subject collection

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
