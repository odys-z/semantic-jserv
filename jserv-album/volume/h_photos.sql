delete from h_photos;

INSERT INTO h_photos
(pid,pname,pdate,cdate,tags,oper,opertime) VALUES
	 ('test-00000','abc1.jpg','2021-12-31','2021-12-31','#Toronto','admin','2022-01-13'),
	 ('test-00001','abc2.jpg','2021-12-31','2021-12-31','#Toronto','admin','2022-01-13'),
	 ('test-00003','abc3.jpg','2021-12-31','2021-12-31','#Toronto','admin','2022-01-13'),
	 ('test-00004','file.jpg','2021-12-31','2021-12-31','#Toronto','admin','2022-01-13');
	 
select * from h_photos hp ;