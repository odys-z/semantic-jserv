@echo "Don't link in Windows! This will make winsrv failed to link WEB-INF. Use hard copy."
@REM This is only useful for testing. The resouces is packaged into zip by jserv-album/tasks.py.
@REM see jserv-album/tasks.py, resources

@REM mklink exiftool.zip ..\..\jserv-album\exiftool-13.21_64.zip
@REM @cp ..\..\jserv-album\exiftool-13.21_64.zip exiftool.zip

@REM mklink jserv-album-0.7.1.jar ..\..\jserv-album\target\jserv-album-0.7.1.jar
@copy ..\..\jserv-album\target\jserv-album-0.7.1.jar jserv-album-0.7.1.jar

@REM mklink html-web-0.1.0.jar ..\..\..\html-service\java\target\html-web-0.1.1.jar
@copy ..\..\..\html-service\java\target\html-web-0.1.1.jar .