drop table if exists syn_seq;
create table syn_seq
-- deprecated
-- table-wise last updating stamps by remote nodes
(
	tabl    varchar2(64) not null, -- e.g. 'h_photos'
	synodee varchar2(12) not null, -- subscriber, fk-on-del, client id asked for sychronizing
	recount integer,
	-- not correct if multiple updated and not used as Semantics.DA can't access context for commitment results.
	-- as photos are uploaded file by file, this is probably only useful for debugging.
	crud	char(1)
	-- only last one is correct, and is not semantically used
	-- 'R' is ignored; 'D' is actually triggered a cleaning task; 'D' -> 'C'; for 'U', only h_photos.sync & h_photos.clientpath is handled

	-- synyquist   integer not null,
	-- cleanyquist integer not null
);

drop table if exists syn_change;
create table syn_change (
	tabl        varchar2(64) not null, -- e.g. 'h_photos'
	recId       varchar2(12) not null, -- entity record Id
	-- synodee     varchar2(12) not null, -- subscriber, fk-on-del, synode id device to finish cleaning task
	synoder     varchar2(12) not null, -- publisher, fk-on-del, synode id for resource's PK
	clientpath  text         not null, -- for h_photos.fullpath, or composed PK for resouce's id, not null?
	clientpath2 text,                  -- support max 3 fields of composed PK, TODO any better patterns?
	-- synyquist   integer      not null  -- last Nyquist sequence number of synodee
	crud        char(1)      not null  -- I/U/D
	-- flag        char(1)      not null  -- 'D' deleting, 'C' close (not exists),'R'/'E' rejected (then erased) by device owner
);

drop table if exists syn_subscribe;
create table syn_subscribe (
	tabl        varchar2(64) not null, -- e.g. 'h_photos'
	recId       varchar2(12) not null, -- entity record Id
	clientpath  text         not null, -- for h_photos.fullpath, or composed PK for resouce's id, not null?
	clientpath2 text,                  -- support max 3 fields of composed PK, TODO any better patterns?
	synodee     varchar2(12) not null  -- subscriber, fk-on-del, synode id device to finish cleaning task
	synyquist   integer      not null  -- last Nyquist sequence number of synodee
);

drop table if exists syn_clean;
create table syn_clean (
	tabl        varchar2(64) not null, -- e.g. 'h_photos'
	synoder     varchar2(12) not null, -- publisher, fk-on-del, synode id for resource's PK
	clientpath  text         not null, -- for h_photos.fullpath, or composed PK for resouce's id
	clientpath2 text,                  -- support max 3 fields of composed PK, TODO any better patterns?
	synodee     varchar2(12) not null, -- subscriber, fk-on-del, synode id device to finish cleaning task
	flag        char(1)      not null, -- 'D' deleting, 'C' close (not exists),'R' rejected by device owner
	cleanyquist integer      not null  -- last Nyquist sequence number of synodee
);
