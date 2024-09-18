-- delete from syn_node; 
INSERT INTO syn_node
(synid,  org,   mac,     nyq,  domain,   oper,   optime,       io_oz_synuid) VALUES
('X',   'URA', '#URA.X',  0,   'zsu',   'odyz',  '2024-04-01', 'X,X'),
('Y',   'URA', '#URA.Y',  0,   'zsu',   'odyz',  '2024-04-01', 'Y,Y'),
('Z',   'URA', '#URA.Z',  0,   'zsu',   'odyz',  '2024-04-01', 'Z,Z');

/*
{"type":"io.odysz.semantic.jprotocol.AnsonMsg","code":null,"opts":null,"port":"session","header":null,
"body":[{
  "type":"io.odysz.semantic.jsession.AnSessionReq","uid":"syrskyi",
  "parent":"io.odysz.semantic.jprotocol.AnsonMsg","a":"login","iv":"XBuVxOW6wOwdQPYE4oZiRA==","mds":null,
  "deviceId":"test-doclient/X-0","uri":"client-at-00",
  "token":"CG3JAeSkgSZ5LjP+2I4GTQ=="
}],"addr":null,"version":"1.1","seq":978}

SynDomanager sessions client
70 75 72
*/