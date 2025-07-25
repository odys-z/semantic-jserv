@REM @echo "Run this file from upper dir, e. g. run: winsrv/install-w.bat"
@echo "--- Installing Synode Windows Service ---"

@REM BEGIN Python modifying section, by commands.install_wsrv_byname(). Do not modify except debugging (Not using bat file arg for easy debugging)
@set jar_ver=0.7.4
@REM END Python modifying section

@REM @set serv_name="Synode.syn-%jar_ver%"
@set serv_name="%~2"
@echo %serv_name%

@set jar=jserv-album-%jar_ver%.jar
@set classname=SynotierJettyApp
@set full_classname=io.oz.syntier.serv.%classname%

if "%~1" == "uninstall" (
@echo linked with "cd winsrv && mklink uninstall-html-srv.bat ..\..\..\html-service\java\src\test\uninstall-html-srv.bat"?

@call winsrv\uninstall-html-srv.bat winsrv\portfolio-ia64.exe %serv_name%

) else (
@REM linked with "cd winsrv && mklink install-html-srv.bat ..\..\..\html-service\java\src\test\install-html-srv.bat"?
@echo copied "copy ..\jserv-album\target\%jar% bin" ?

@call winsrv\install-html-srv.bat winsrv\portfolio-ia64.exe bin\%jar% %serv_name% . %classname% %full_classname%  
sc query %serv_name%
)

@REM @echo "Tip for coverting log files' encoding (use VS Code Bash):"
@REM @echo "iconv -f GB2312 -t UTF-8 logs/commons-daemon.yyyy-mm-dd.log > commons-daemon-utf8.log"

