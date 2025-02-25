# Make a zip file for distribution, as a temporary tool for
#  
VERSION   = 0.7.0
SOURCEDIR = .
BUILDDIR  = bin
JAR_DIR   = target
ZIP       = jserv-album-bin.zip
VOLUME    = volume
SRC_WEB   = src/main/webapp/WEB-INF-$(VERSION)
EXIFTOOL_ZIP = exiftool-*.zip 

default: jserv-sqlite

help:
	@cat Makefile

#
check:
	echo "checke resources ..."

	echo "$(wildcard exiftool-*.zip)"
ifeq ("$(wildcard exiftool-*.zip)","")
	${error "Exiftool zip does not exist."}
endif

jserv-sqlite: check
	@rm -f $(ZIP)
	@rm -rf $(BUILDDIR)
	@mkdir  $(BUILDDIR)
	@mvn clean compile package -DskipTests
	@cp $(JAR_DIR)/jserv-album-*.jar $(BUILDDIR)
	@cp $(EXIFTOOL_ZIP) $(BUILDDIR)

	@rm -rf $(VOLUME)
	@mkdir $(VOLUME)
	@touch $(VOLUME)/doc-jserv.db
	@touch $(VOLUME)/jserv-main.db

	@rm -rf WEB-INF
	@cp -r $(SRC_WEB) WEB-INF

#	mv install-settings/install.sh .
#	mv install-settings/exiftool.exe .
#	chmod +x install.sh
#	mv install-settings/set-jdk-exiftool.sh .
#	chmod +x set-jdk-exiftool.sh

#	@jar -cMf jserv-album-$(VERSION).zip \
	Makefile $(BUILDDIR)/jserv-album-*.jar $(VOLUME) WEB-INF
	@jar -cMf jserv-album-$(VERSION).zip $(BUILDDIR) $(VOLUME) WEB-INF
	
#	install.sh install-settings \
#	set-jdk-exiftool.sh

#	mv set-jdk-exiftool.sh install-settings/
#	mv exiftool.exe install-settings/
#	mv install.sh install-settings/

	@mv jserv-album-$(VERSION).zip $(BUILDDIR)
	@rm $(BUILDDIR)/jserv-album-*.jar
	@rm $(BUILDDIR)/$(EXIFTOOL_ZIP)
	@rm -rf WEB-INF
	@mvn dependency:tree | grep io.github.odys-z

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
#	@rm -rf bin install-settings $(VOLUME) WEB-INF
	@rm -rf bin $(VOLUME) WEB-INF

.PHONY: help

default: jserv-sqlite