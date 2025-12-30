# Hunting the bug of shadow entity records

* deploy:

infor-11-hub: hub
infor-11-1 [installed (c)]
infor-11-2: peer

At infor-11-2, the file has been uploaded from clients and synchronized.
When reboot the Windows with the services, there is a gost records, with
uri of a doc-ref envelope.


* hub:

h_photos (PID 0101 0102 0103 0104 0105)

```
io_oz_synuid   
---------------
infor-11-1,0101
infor-11-1,0102
infor-11-1,0103
infor-11-1,0104
infor-11-2,0105


Name        |Value                                                                           
------------+-------------------------------------------------------
pid         |0105                                                                            
family      |inforise                                                                        
folder      |2025-11                                                                         
docname     |1763518794.png                                                                  
uri         |$VOLUME_HOME/inforise/admin/2025-11/0105 1763518794.png
pdate       |2025-11-19 10:19:54                                                             
device      |0001                                                                            
clientpath  |/storage/emulated/0/DCIM/Duolingo/1763518794.png                                
shareby     |admin                                                                           
sharedate   |2025-12-30                                                                      
tags        |                                                                                
geox        |null                                                                            
geoy        |null                                                                            
mime        |image/png                                                                       
filesize    |53642                                                                           
shareflag   |publish                                                                         
oper        |infor-11-hub                                                                    
opertime    |2025-12-30 03:28:58                                                             
syncstamp   |2025-12-30 03:27:57                                                             
io_oz_synuid|infor-11-2,0105                                                                 
```

syn_change

```
Name    |Value                                         
--------+----------------------------------------------
cid     |0007                                          
domain  |infor-11                                      
tabl    |h_photos                                      
crud    |I                                             
synoder |infor-11-2                                    
uids    |infor-11-2,0105                               
nyquence|2                                             
updcols |css,geox,mime,geoy,opertime,oper,filesize,exif
seq     |1                                             
```

syn_exchange_buf

```
peer      |changeId|pagex|
----------+--------+-----+
infor-11-2|0007    |    0|
```

log briefing:

```
====== infor-11-hub -> infor-11-2 ====== Challenge Page: ======
infor-11-hub
page-index: 1,	challenging size (all subscribers): 0
Syntities:

[docsyn]
update syn_sessions set chpage=1, answerx=1, expansx=1, mode=0, state=3 where peer = 'infor-11-2'
====== infor-11-hub <- infor-11-2 ====== Answering: ======
infor-11-hub
page-index: 1,	challenging size: 1
Syntities:

... ...  ... ...

====== infor-11-hub -> infor-11-2 ====== Challenge Page: ======
infor-11-hub
page-index: 0,	challenging size (all subscribers): 2
Syntities:

h_photos,	size: 1,
[docsyn]
update syn_sessions set chpage=0, answerx=0, expansx=0, mode=0, state=2 where peer = 'infor-11-2'
====== infor-11-hub <- infor-11-2 ====== Answering: ======
infor-11-hub
page-index: 0,	challenging size: 0
Syntities:

h_photos	1

... ...  ... ...

====== infor-11-hub -> infor-11-2 ====== Challenge Page: ======
infor-11-hub
page-index: 1,	challenging size (all subscribers): 0
Syntities:

[docsyn]
update syn_sessions set chpage=1, answerx=1, expansx=1, mode=0, state=3 where peer = 'infor-11-2'
====== infor-11-hub <- infor-11-2 ====== Answering: ======
infor-11-hub
page-index: 1,	challenging size: 1
Syntities:
```
