delete from a_functions;

INSERT INTO a_functions
(funcId,      funcName,         url,            css,                  flags, fullpath,        parentId,sibling)
VALUES
('sys',       'Acadynamo',      NULL,           '{icon: ''sys''}',      '1', '1 sys',            NULL, 1),
('sys-domain','Domain Settings','/sys/domain',   '',                    '1', '1 sys.1 domain',   'sys',1),
('sys-role',  'Role Manage',    '/sys/roles',    '',                    '1', '1 sys.2 role',     'sys',2),
('sys-org',   'Orgnizations',   '/sys/orgs',     '',                    '1', '1 sys.3 org',      'sys',3),
('sys-uesr',  'Uesr Manage',    '/sys/users',    '',                    '1', '1 sys.4 user',     'sys',4),
('sys-inds',  'Emotion Indicators','/n/indicators','',                  '1', '1 sys.5 inds',     'sys',5),

('n01',       'North Pole',     NULL,            '',                    '1', '2 n01',            NULL, 2),
('n-dash',    'North Dashboard','/n/dashboard',  '{icon: ''sms''}',     '1', '2 n01.1 n-dash',   'n01',1),
('n-quizzes', 'Polls Overview', '/n/quizzes',    '{icon: ''send''}',    '1', '2 n01.2 n-polls',  'n01',2),
('n-polls',   'Polls Overview', '/n/polls',      '{icon: ''paper''}',   '1', '2 n01.2 n-polls',  'n01',2),
('n-mystud',  'My Students',    '/n/my-students','{icon: ''children''}','1', '2 n01.3 n-mystud', 'n01',3),

('c01',       'Center Me',      NULL,            '',                    '1', '3 c01',             NULL, 3),
('c-status',  'My Status',      '/c/status',     '{icon: ''sms''}',     '1', '3 c01.1 c-status',  'c01',1),
('c-myconn',  'My Connection',  '/c/myconn',     '{icon: ''send''}',    '1', '3 c01.2 c-status',  'c01',2),
('c-mypolls', 'My Polls',       '/c/mypolls',    '{icon: ''sms''}',     '1', '3 c01.3 c-mypolls', 'c01',3);

SELECT funcId, parentId, funcName, url, sibling sort, fullpath, css, flags
  			FROM a_functions f order by f.fullpath