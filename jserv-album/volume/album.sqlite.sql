select * from h_photos hp ;

select device, shareflag, shareby, sharedate, clientpath 
from h_photos t where device = 'test-doclient/Y-1' AND clientpath in ('src/test/res/anclient.java/Amelia Anisovych.mp4');
