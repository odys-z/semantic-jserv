-- h_photos definition

CREATE TABLE "h_photos" if not exists (
  pid varchar(12) NOT NULL,

  family varchar2(12) NOT NULL,

  folder varchar(256) NOT NULL,

  -- renamed in 0.7.0
  docname varchar(256),
  -- pname varchar(256),
  
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

  -- removed in 0.7.0, from 0.6.5
  -- sync varchar2(4),

  oper varchar(12) not null,

  opertime datetime not null,  -- this is the timestamp

  -- required in 0.7.0, missing in 0.6.5
  syncstamp DATETIME DEFAULT CURRENT_TIMESTAMP not NULL,

  -- required in 0.7.0, missing in 0.6.5
  io_oz_synuid varchar2(25),

  PRIMARY KEY (pid)

  ----------------------------------
  -- same from 0.6.5 in 0.7.0
  -- pid varchar(12) NOT NULL,
  -- family varchar2(12) NOT NULL,
  -- folder varchar(256) NOT NULL,
  -- uri varchar(512) NOT NULL,   -- storage/userId/folder/recId-clientname
  -- pdate datetime,              -- picture taken time
  -- device varchar(12),          -- 'original device ID',
  -- clientpath TEXT DEFAULT '/' NOT NULL, -- shall we support 'moveTo' laterly?
  -- shareby varchar(12),         -- 'shared by / creator',
  -- sharedate datetime not null, -- 'shared date time',
  -- tags varchar(512) DEFAULT NULL ,
  -- geox double DEFAULT 0,
  -- geoy double DEFAULT 0,
  -- exif text default null,
  -- mime TEXT(64), 
  -- filesize INTEGER, 
  -- shareflag varchar2(12) default 'prv' not null, 
  -- css text,                    -- e.g. {"type":"io.oz.album.tier.PhotoCSS", "size":[3147,1461,1049,487]}
  -- oper varchar(12) not null,
  -- opertime datetime not null,  -- this is the timestamp
);