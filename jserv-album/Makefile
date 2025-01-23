# Make a zip file for distribution, as a temporary tool for
#  
VERSION   = 0.7.0
SOURCEDIR = .
BUILDDIR  = bin
JAR_DIR   = target
ZIP       = jserv-album-bin.zip
VOLUME    = volume

help:
	@cat Makefile

#

jserv-sqlite:
#   @mvn clean dependency:tree | @grep io.github.odys-z
	@rm -f $(ZIP)
	@rm -rf $(BUILDDIR)
	@mkdir  $(BUILDDIR)
	@mvn clean compile package -DskipTests
	@cp $(JAR_DIR)/jserv-album-*.jar $(BUILDDIR)

	@rm -rf $(VOLUME)
	@mkdir $(VOLUME)
	@touch $(VOLUME)/doc-jserv.db
	@touch $(VOLUME)/jserv-main.db
#	@cp -a volumes-0.7/volume-hub/. $(VOLUME)
	@zip -FSr jserv-album-$(VERSION).zip Makefile $(BUILDDIR)/jserv-album-*.jar install-settings $(VOLUME)
	@mv jserv-album-$(VERSION).zip $(BUILDDIR)

install:
	@echo "Usage: make install node=X volume=/var/local/volume"
	@echo "For Windows, run this in VS Code. (Needing the 'sed' command)"
	@echo building $(node)
	@echo configurations:
	@ls -l install-settings/$(node)

	@mkdir $(volume) 
	@cp install-settings/$(node)/dictionary.json $(volume)
	@cp install-settings/$(node)/syntity.json $(volume)

	@mkdir WEB-INF
	@cp install-settings/$(node)/settings.json WEB-INF/settings.json
	@sed -i 's/"volume"   : ".*"/"volume"   : "$(volume)"/' WEB-INF/settings.json

.PHONY: help