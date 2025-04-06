@REM @echo "Run this file from upper dir, e. g. run: winsrv/install-w.bat"
@REM @echo "Installing jserv-album (Portfolio 0.7)"

@set jar-ver=0.7.1
@set serv_name="Synode.py3 (src) %jar-ver%"
@set jar=jserv-album-%jar-ver%.jar
@set classname=SynotierJettyApp
@set full_classname=io.oz.syntier.serv.%classname%

if "%~1" == "uninstall" (
@echo linked with "cd winsrv && mklink uninstall-html-srv.bat ..\..\..\html-service\java\src\test\uninstall-html-srv.bat"?
winsrv\uninstall-html-srv.bat winsrv\portfolio-ia64.exe %serv_name%
) else (
@echo linked with "cd winsrv && mklink install-html-srv.bat ..\..\..\html-service\java\src\test\install-html-srv.bat"?
copy ..\jserv-album\target\%jar% bin
winsrv\install-html-srv.bat winsrv\portfolio-ia64.exe bin\%jar% %serv_name% . %classname% %full_classname%  
)

@echo "Tip for coverting log files' encoding (use VS Code Bash):"
@echo "iconv -f GB2312 -t UTF-8 logs/commons-daemon.yyyy-mm-dd.log > commons-daemon-utf8.log"

@sc query %serv_name%
