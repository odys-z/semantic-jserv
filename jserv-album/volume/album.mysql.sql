GRANT all ON `album-cloud`.* TO 'odysz'@'%';

DROP table if exists c_photos ;
CREATE TABLE c_photos (
  pid varchar(12) NOT NULL,
  pname varchar(256) COLLATE utf8mb4_general_ci DEFAULT NULL,
  pdate datetime DEFAULT NULL,
  device varchar(12) comment 'original device ID',
  shareby varchar(12) comment 'shared by / creator',
  cdate datetime not null comment 'create date time',
  tags varchar(512) DEFAULT NULL ,
  geox double DEFAULT 0,
  geoy double DEFAULT 0,
  exif text default null,
  oper varchar(12) not null,
  opertime datetime not null,

  PRIMARY KEY (`pid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='photo records';

DROP table if exists c_collects ;
CREATE TABLE c_collects (
  cid varchar(12) COLLATE utf8mb4_bin NOT NULL,
  cname varchar(256) COLLATE utf8mb4_general_ci DEFAULT NULL,
  shareby varchar(12) comment 'shared by / creator',
  cdate datetime not null comment 'create date time',
  tags varchar(512) DEFAULT NULL ,
  oper varchar(12) not null,
  opertime datetime not null,

  PRIMARY KEY (cid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='photo collection'

DROP table if exists c_coll_phot ;
CREATE TABLE c_coll_phot (
  cid varchar(12) COLLATE utf8mb4_bin NOT NULL,
  pid varchar(12) COLLATE utf8mb4_bin NOT NULL,
  CONSTRAINT c_coll_phot_FK1 FOREIGN KEY (cid) REFERENCES c_collects (cid),
  CONSTRAINT c_coll_phot_FK2 FOREIGN KEY (pid) REFERENCES c_photos (pid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='photo-collection relationship'

DROP table if exists c_albums ;
CREATE TABLE c_albums (
  aid varchar(12) COLLATE utf8mb4_bin NOT NULL,
  aname varchar(256) COLLATE utf8mb4_general_ci DEFAULT NULL,
  shareby varchar(12) comment 'shared by / creator',
  cdate datetime not null comment 'create date time',
  tags varchar(512) DEFAULT NULL ,
  oper varchar(12) not null,
  opertime datetime not null,

  PRIMARY KEY (aid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='collection group, the album'

DROP table if exists c_album_coll ;
CREATE TABLE c_album_coll (
  aid varchar(12) COLLATE utf8mb4_bin NOT NULL,
  cid varchar(12) COLLATE utf8mb4_bin NOT NULL,
  CONSTRAINT c_album_coll_FK1 FOREIGN KEY (aid) REFERENCES c_albums (aid),
  CONSTRAINT c_album_coll_FK2 FOREIGN KEY (cid) REFERENCES c_collects (cid)
) ENGINE=InnoDB
DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin
COMMENT='collection-album relationship'
