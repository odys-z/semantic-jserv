-- h_photos definition

CREATE TABLE "h_photos" (
  pid varchar(12) NOT NULL,
  family varchar2(12) NOT NULL,
  folder varchar(256) NOT NULL,
  docname varchar(256),
  uri varchar(512) NOT NULL,   -- storage/userId/folder/recId-clientname
  pdate datetime,              -- picture taken time
  
  device varchar(12),          -- 'original device ID',
  clientpath TEXT DEFAULT '/' NOT NULL, -- original fullpath

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

  oper varchar(12) not null,
  opertime datetime not null,  -- this is the timestamp
  io_oz_synuid varchar2(25),

  PRIMARY KEY (pid)
);