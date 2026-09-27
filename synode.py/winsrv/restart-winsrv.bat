@set prunsrv=%1
@set srv_name=%2

@echo:
@echo ACTION NEEDED!
@echo:
@echo Please confirm permission (in the hidden dialog) to uninstall the service %srv_name%...

@%prunsrv% //ES//%srv_name%
