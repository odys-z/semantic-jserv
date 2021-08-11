
drop table indicators;

CREATE TABLE indicators (
    -- indicator configuration
	indId   VARCHAR(12) NOT NULL,
	indName VARCHAR(64) NOT NULL,
	parent  VARCHAR(12),
	sort    VARCHAR(4) NOT NULL,
	fullpath VARCHAR(256) NOT NULL,
	css     VARCHAR(256),  -- special display format, e.g. icon
	weight  FLOAT,         -- default weight. A poll should have question weight independently; children's weight should sum up to 1.0
	qtype   VARCHAR(4),    -- value type (single, multiple options, number, rank5, rank10, ...)
	remarks VARCHAR(512),
	extra   VARCHAR(128),
	CONSTRAINT ind_emotion_PK PRIMARY KEY (indId)
);

delete from indicators;

INSERT INTO indicators
(indId,   indName,     remarks,        css,                 weight, fullpath,         qtype, parent, sort)
VALUES
('i-1',   'Academic',  NULL,           '{icon: ''sys''}',     '.6', '1 i-1',            NULL, NULL,   1),
('i-1.1', 'GPA 1',     '/sys/domain',   '',                   '.2', '1 sys.1 domain',   's',  'i-1',  1),
('i-1.2', 'GPA 2',     '/sys/roles',    '',                   '.2', '1 sys.2 role',     's',  'i-1',  2),
('i-1.3', 'GPA 3',     '/sys/orgs',     '',                   '.2', '1 sys.3 org',      's',  'i-1',  3),
('i-1.4', 'GPA 4',     '/sys/users',    '',                   '.2', '1 sys.4 user',     's',  'i-1',  4),
('i-1.5', 'GPA 5',     '/n/indicators', '',                   '.2', '1 sys.5 inds',     's',  'i-1',  5),

('j-1',   'AP Scores', NULL,            '',                   '.4', '2 j-1',            NULL, NULL,   2),
('j-1.1', 'STEM',      '/n/dashboard',  '{icon: ''sms''}',    '.5', '2 j-1.1 j-1.1',    's',  'j-1',  1),
('j-1.2', 'Arts',      '/n/quizzes',    '{icon: ''send''}',   '.2', '2 j-1.2 j-1.2',    's',  'j-1',  2),
('j-1.3', 'Sociaty',   '/n/polls',      '{icon: ''paper''}',  '.1', '2 j-1.3 j-1.3',    's',  'j-1',  2),
('j-1.4', 'Business',  '/n/my-students','{icon: ''children''}','.2', '2 j-1.4 j-1.4',   's',  'j-1',  3),

('j-1.1.1','CS A',      'Computer Science A', '',            '.2', '2 j-1.1 j-1.1.1 j-1.1.1', 'n', 'j-1.1', 1),
('j-1.1.3','Physics BC','/c/status',     '{icon: ''sms''}',  '.3', '2 j-1.1 j-1.1.2 j-1.1.2', 'n', 'j-1.1', 2),
('j-1.1.4','Math',      '/c/myconn',     '{icon: ''send''}', '.2', '2 j-1.1 j-1.1.3 j-1.1.3', 'n', 'j-1.1', 3),
('j-1.1.5','CS B',      '/c/mypolls',    '{icon: ''sms''}',  '.2', '2 j-1.1 j-1.1.4 j-1.1.4', 'n', 'j-1.1', 4);

SELECT indId, indName, parent, sort, fullpath, css, weight, qtype, remarks, extra FROM indicators order by fullpath;


SELECT vid, val, dim1, dim2, dim3, dim4, dim5, dim6, dim7, dim8 FROM vector;
