@REM @echo "Run this file from upper dir, e. g. run: winsrv/install-html-w.bat"

@set jar-ver=0.1.5
@set serv_name="Synode.web-%jar-ver%"
@set classname=HtmlServer
@set full_classname=io.oz.srv.HtmlServer

if "%~1" == "uninstall" (
@echo linked with "cd winsrv && mklink uninstall-html-srv.bat ..\..\..\html-service\java\src\test\uninstall-html-srv.bat"?
@call winsrv\uninstall-html-srv.bat winsrv\portfolio-ia64.exe %serv_name%
) else (
@echo linked with "cd winsrv && mklink install-html-srv.bat ..\..\..\html-service\java\src\test\install-html-srv.bat"?
@echo copied "copy ..\..\html-service\java\target\html-web-%jarv-ver%.jar bin" ?
@call winsrv\install-html-srv.bat winsrv\portfolio-ia64.exe bin\html-web-%jar-ver%.jar %serv_name% . %classname% %full_classname%  
sc query %serv_name%
)

@REM @echo "Tip for coverting log files' encoding (use VS Code Bash):"
@REM @echo "iconv -f GB2312 -t UTF-8 logs/commons-daemon.yyyy-mm-dd.log > commons-daemon-utf8.log"
