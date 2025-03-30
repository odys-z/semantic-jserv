set jar-ver=0.7.1

echo "Portfolio 0.7, Synode %1"
echo "Run this file from .. dir, e. g. winsrv/install.bat"
set synodedir=%cd%
set classpath=%synodedir%/bin/jserv-album-%jar-ver%.jar
echo %classpath%

cd winsrv
portfolio-srv.exe //IS//Portfolio-synode --Install=%cd%\portfolio-srv.exe ^
--ServiceUser LocalSystem ^
--Description="Portfolio 0.7, Synode %1" ^
--Jvm=auto ^
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

echo "Finding service main class:"
jar tf %synodedir%\bin\jserv-album-%jar-ver%.jar | findstr "SynotierJettyApp"

echo "For convert unreadable log files:"
echo "iconv -f GB2312 -t UTF-8 winsrv/logs/commons-daemon.2025-03-28.log > commons-daemon-utf8.log"

portfolio-srv.exe //ES//portfolio-srv

cd ..
