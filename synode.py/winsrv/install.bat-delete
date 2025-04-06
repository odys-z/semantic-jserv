set jar-ver=0.7.1

@echo "Portfolio 0.7, Synode %1"
@echo "Run this file from w dir, e. g. winsrv/install.bat"
set synodedir=%cd%
set classpath=%synodedir%\bin\jserv-album-%jar-ver%.jar
@echo %classpath%

@REM cd winsrv
@REM https://commons.apache.org/proper/commons-daemon/procrun.html

winsrv\portfolio-srv.exe //IS//Portfolio-synode --Install=%cd%\winsrv\portfolio-srv.exe ^
--ServiceUser LocalSystem ^
--Description="Portfolio 0.7, Synode %1" ^
--Jvm=auto ^
--StartPath=%cd% ^
--Classpath=%classpath% ^
--Startup=auto ^
--StartMode=jvm ^
--StartClass=io.oz.syntier.serv.SynotierJettyApp ^
--StartMethod=jvmStart ^
--JvmOptions=-Dfile.encoding=UTF-8;-Dstdout.encoding=UTF-8;-Dstderr.encoding=UTF-8 ^
--StopMode=java ^
--StopClass=io.oz.syntier.serv.SynotierJettyApp ^
--StopMethod=jvmStop ^
--StopParams=stop ^
--LogPath=%cd%\logs ^
--StdOutput=auto ^
--StdError=auto

@echo "Finding service main class:"
jar tf %synodedir%\bin\jserv-album-%jar-ver%.jar | findstr "SynotierJettyApp"

@echo "For convert unreadable log files:"
@echo "iconv -f GB2312 -t UTF-8 winsrv/logs/commons-daemon.yyyy-mm-dd.log > commons-daemon-utf8.log"

winsrv\portfolio-srv.exe //ES//Portfolio-synode

