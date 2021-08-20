SELECT indId, indName, parent, sort, fullpath, css, weight, qtype, remarks, extra
FROM ind_emotion;

drop table ind_emotion;
CREATE TABLE ind_emotion (
    -- indicator configuration
	indId      VARCHAR(12),
	indName    VARCHAR(64),
	parent     VARCHAR(12),
	sort       VARCHAR(4),      -- tree sibling sort
	fullpath   VARCHAR(256),
	css        VARCHAR(256),    -- special display format, e.g. icon
	weight     FLOAT,           -- default weight. A poll should have question weight independently
	qtype      VARCHAR(4),      -- question type (single, multiple answer, ...)
	remarks    VARCHAR(512),    -- used as quiz question
	qsort      int DEFAULT 0,   -- sort in a quiz
	expectings VARCHAR(512),    -- expected answers
	descrpt    VARCHAR(256),    -- a short description
	extra      VARCHAR(128),
	CONSTRAINT ind_emotion_PK PRIMARY KEY (indId)
);

drop table polls;
CREATE TABLE polls (
    -- poll main table (child of quizzes)
	pid varchar2(12),        -- poll id
	quizId varchar2(12),     -- quiz id, fk -> quizzes.qid
	userId varchar2(12),     -- [optional] sys / regisetered user
	userInfo varchar2(1000), -- temp user info, json?
	extra varchar2(1000),
	CONSTRAINT polls_PK PRIMARY KEY (pid)
);

CREATE TABLE polldetails (
	-- poll details (child of polls)
	pssId varchar2(12),       -- poll detail record Id
	pollId varchar2(12),      -- poll Id (fk)
	-- quizId varchar2(12),      -- quiz Id (fk)
	questId varchar2(12),     -- question Id (fk)
	results varchar2(1000),   -- answer
	CONSTRAINT polldetails_PK PRIMARY KEY (pssid)
);

drop table quizzes;
CREATE TABLE quizzes (
	-- quizzes records (master)
    qid        varchar(12) PRIMARY KEY,
    title      varcher(512),
    oper       varchar(12) NOT NULL,
    optime     NUMERIC NOT NULL,
    tags       varchar(200),
    quizinfo   varchar(2000),
    qowner     varchar(12),
    dcreate    NUMERIC,
    subject    varchar(12),
    flag       varchar(2),     -- e.g. is this a template
    pubTime    NUMERIC,        -- publish datetime
    extra      varchar(1000)   -- e.g. times been copied
);

drop table questions;
CREATE TABLE questions (
	-- quizzes details (child of quizzes)
	-- some questions are copied from indicators, some are not
    qid        varchar2(12) PRIMARY KEY,
    quizId     varchar2(12) NOT NULL, 
    indId      varchar2(12),           -- fk -> indicators.indId, nullable
    question   varchar2(2000),
    answers    varchar2(500) NOT NULL, -- a question doesn't have structured answers / options
    qtype      varchar2(12) NOT NULL,
    answer     varchar2(12) NOT NULL,
    qorder     NUMERIC,
    shortDesc  varchar2(200),
    hints      varchar2(1000),
    extra      varchar2(1000)
);


