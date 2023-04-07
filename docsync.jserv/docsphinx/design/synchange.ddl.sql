drop table if exists syn_nyqeunce;
create table syn_nyqeunce (
	tabl     varchar2(64) not null, -- e.g. 'h_photos'
	synode   varchar2(12) not null,
	nyquence integer -- radix64? 
);

drop table if exists syn_change;
create table syn_change (
	tabl        varchar2(64) not null, -- e.g. 'h_photos'
	recId       varchar2(12) not null, -- entity record Id
	clientpath  text         not null, -- for h_photos.fullpath, or composed PK for resouce's id, not null?
	clientpath2 text,                  -- support max 3 fields of composed PK, TODO any better patterns?
	nyquence    integer,               -- radix64? 
	cud         char(1)       not null -- I/U/D
);

drop table if exists syn_subscribe;
create table syn_subscribe (
	tabl        varchar2(64) not null, -- e.g. 'h_photos'
	recId       varchar2(12) not null, -- entity record Id
	clientpath  text         not null, -- for h_photos.fullpath, or composed PK for resouce's id, not null?
	clientpath2 text,                  -- support max 3 fields of composed PK, TODO any better patterns?
	synodee     varchar2(12) not null, -- subscriber, fk-on-del, synode id device to finish cleaning task
	del         integer      not null  -- deletion acknowledgement: R/E
); 

