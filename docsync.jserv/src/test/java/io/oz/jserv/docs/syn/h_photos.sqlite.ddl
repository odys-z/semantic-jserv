-- drop table if exists h_photos;
CREATE TABLE if not exists h_photos (
  pid        varchar(12)   NOT NULL,
  family     varchar2(12)  NOT NULL,
  folder     varchar2(256) NOT NULL,
  docname    varchar2(256),
  uri        varchar2(512) NOT NULL,    -- storage/userId/folder/recId-clientname
  pdate      datetime,                  -- picture taken time

  device     varchar(12)   NOT NULL,    -- 'original device ID',
  clientpath text DEFAULT '' NOT NULL,  -- shall we support 'moveTo' laterly?

  shareby    varchar(12),               -- 'shared by / creator',
  sharedate  datetime     NOT NULL,     -- 'shared date time',
  exif       text,
  mime       varchar2(64),
  filesize   integer,
  shareflag  varchar2(12) default 'prv' NOT NULL,
  -- crud       char(1),
  oper       varchar(12)  NOT NULL,
  opertime   datetime     NOT NULL,
  io_oz_synuid varchar2(25),

  PRIMARY KEY (pid)
);
