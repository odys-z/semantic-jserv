CREATE TABLE a_orgs (
	orgId    varchar2(12) NOT NULL,
	orgName  varchar2(50),
	orgType  varchar2(40) , -- a reference to a_domain.domainId (parent = 'a_orgs')
	parent   varchar2(12),
	sort     int DEFAULT 0,
	fullpath varchar2(200),
	market   varchar2(64),
	webroot  TEXT,
	album0   varchar2(16),

	PRIMARY KEY (orgId)
);