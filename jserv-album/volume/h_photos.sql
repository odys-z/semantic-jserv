delete from oz_autoseq;

INSERT INTO oz_autoseq (sid,seq,remarks) VALUES
	 ('a_logs.logId',0,'log'),
	 ('a_users.userId',64,'user'),
	 ('a_roles.roleId',4096,'role'),
	 ('a_orgs.orgId',262144,'orgniation'),
	 ('a_attaches.attId',0,'attachement'),
	 ('h_albums.aid',0,'album/home'),
	 ('h_collects.cid',0,'album/home'),
	 ('h_photos.pid',0,'album/home');

select * from oz_autoseq;

delete from h_photos;

INSERT INTO h_photos
(pid,uri,pname,pdate,cdate,tags,oper,opertime) VALUES
	 ('test-00000','omni/ody/2019_08/DSC_0005.JPG','DSC_0005.JPG','2019-08-24','2021-08-24','#Qing Hai Lake','ody','2022-01-13'),
	 ('test-00001','omni/ody/2019_08/DSC_0124.JPG','DSC_0124.JPG','2019-08-24','2021-08-24','#Qing Hai Lake','ody','2022-01-13'),
	 ('test-00002','omni/ody/2021_08/IMG_20210826.jgp','IMG_20210826.jgp','2019-08-24 15:44:30','2021-08-26','#Lotus Lake','ody','2022-01-13'),
	 ('test-00003','omni/ody/2021_10/IMG_20211005.jgp','IMG_20211005.jgp','2019-10-05 11:19:18','2021-10-05','#Song Gong Fort','ody','2022-01-13'),
	 ('test-00004','omni/ody/2021_12/DSG_0753.JPG','DSG_0753.JPG','2021-12-05','2021-12-05','#Garze','ody','2022-01-13'),
	 ('test-00005','omni/ody/2021_12/DSG_0827.JPG','DSG_0827.JPG','2021-12-05','2021-12-05','#Garze','ody','2022-01-13'),
	 ('test-00006','omni/ody/2021_12/DSG_0880.JPG','DSG_0880.JPG','2021-12-31','2021-12-31','#Toronto','ody','2022-01-13');
	 
select * from h_photos ph ;

<<<<<<< HEAD
delete from h_album;


=======
delete * from h_collects;

insert into h_collects ( cid, cname, shareby, cdate, tags ) values
('c-001', 'Liar & Fool', 'ody','2022-01-13', '#family' ),
('c-002', 'Toronto', 'ody','2022-01-13', '#travel' ),
('c-003', 'Garze', 'ody','2022-01-13', '#travel' );

select * from h_collects;

delete * from h_albums;

insert into h_albums ( aid, aname, shareby, cdate, tags )
values ('a-001', 'Living a life', 'ody', '2022-01-13', '#life' );

select * from h_albums;

delete * from h_album_coll;
insert into h_album_coll (aid, cid) values
('a-001', 'c001'), ('a-001', 'c002'), ('a-001', 'c003');

delete * from h_coll_phot;
insert into h_coll_phot (cid, pid) values
('c-001', 'test-00000'),
('a-001', 'test-00002'),
('c-001', 'test-00004'),
('c-001', 'test-00005');
>>>>>>> 6280c7125480e520316da97fd734faa9e1fbf541
