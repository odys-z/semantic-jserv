# Make a zip file for distribution, as a temporary tool for
#  
VERSION   = 0.7.0
SOURCEDIR = .
BUILDDIR  = bin
JAR_DIR   = target
ZIP       = jserv-album-bin.zip
VOLUME    = volume
SRC_WEB   = src/main/webapp/WEB-INF-$(VERSION)

default: jserv-sqlite

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

	@rm -rf WEB-INF
	@cp -r $(SRC_WEB) WEB-INF
#	@mkdir $(VOLUME)/$(node)
#	@cp -a volumes-0.7/volume-hub/. $(VOLUME)/$(node)
	@zip -FSr jserv-album-$(VERSION).zip \
	Makefile $(BUILDDIR)/jserv-album-*.jar install-settings $(VOLUME) WEB-INF install.sh
	@mv jserv-album-$(VERSION).zip $(BUILDDIR)
	@rm $(BUILDDIR)/jserv-album-*.jar
	@rm -rf WEB-INF

# install:
# 	@echo "Usage: make install node=X volume=/var/local/volume"
# 	@echo "For Windows, run this in VS Code. (Needing the 'sed' command)"
# 	@echo building $(node)
# 	@echo configurations:
# 	@ls -l install-settings/$(node)

# 	@mkdir $(volume) 
# 	@cp install-settings/$(node)/dictionary.json $(volume)
# 	@cp install-settings/$(node)/syntity.json $(volume)

# 	@cp install-settings/$(node)/settings.json WEB-INF/settings.json
# #	@sed -i 's/"volume"\s*:\s*".*"/"volume"   : "$(volume)"/' WEB-INF/settings.json
# 	set v_path = $(shell realpath $(volume))
# 	@echo $v_path
# 	@sed -i "s/\"volume\"\s*:\s*\".*\"/\"volume\"   : \"$v_path\"/" WEB-INF/settings.json

clean:
	@rm -rf bin install-settings $(VOLUME) WEB-INF
	@echo "You need also remove volume path manually."

.PHONY: help

default: jserv-sqlite