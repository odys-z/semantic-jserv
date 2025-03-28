portfolio.exe //IS//portfolio-srv --Install=%cd%\portfolio-srv.exe ^
--ServiceUser LocalSystem ^
--Description="Portfolio 0.7.0 id={synode}" ^
--Jvm=auto ^
--Classpath=%cd%/bin/jserv-album-0.7.0.jar ^
--Startup=auto ^
--StartMode=jvm ^
--StartClass=io.oz.syntier.serv.SynotierJettyApp ^
--StartMethod=main ^
--StopMode=java ^
--StopClass=io.oz.syntier.serv.SynotierJettyApp ^
--StopMethod=stopserv ^
--StopParams=stop ^
--LogPath=%cd%\logs ^
--StdOutput=auto ^
--StdError=auto

portfolio.exe //ES//portfolio-srv
