select * from h_photos hp ;

select * from syn_change sc ;
select * from syn_subscribe ss ;

select device, shareflag, shareby, sharedate, clientpath 
from h_photos t where device = 'test-doclient/Y-1' AND clientpath in ('src/test/res/anclient.java/Amelia Anisovych.mp4');

select * from oz_autoseq oa ;