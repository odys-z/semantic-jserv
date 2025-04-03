set jar-ver=0.1.0

@echo "Run this file from upper dir, e. g. winsrv/install-html.bat"
set synodedir=%cd%
set classpath=%synodedir%\bin\html-web-%jar-ver%.jar
@echo %classpath%

winsrv\portfolio-srv.exe //IS//Portfolio-synode --Install=%cd%\winsrv\portfolio-srv.exe ^
--ServiceUser LocalSystem ^
--Description="Album-web" ^
--Jvm=auto ^
--StartPath=%cd% ^
--Classpath=%classpath% ^
--Startup=auto ^
--StartMode=jvm ^
--StartClass=io.oz.srv.HtmlServer ^
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
jar tf %synodedir%\bin\html-web-%jar-ver%.jar | findstr "HtmlServer"

winsrv\portfolio-srv.exe //ES//Portfolio-synode

