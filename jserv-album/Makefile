# Make a zip file for distribution, as a temporary tool for
# cp whl, make 
#  
VERSION   = 0.7.0
SOURCEDIR = .
BUILDDIR  = bin
JAR_DIR   = target
ZIP       = jserv-album-bin.zip
VOLUME    = volume
SRC_WEB   = src/main/webapp/WEB-INF-$(VERSION)
EXIFTOOL_ZIP = exiftool-*.zip 
PORTFOLIO_07 = portfolio_synode-0.7.1-py3-none-any.whl

# ln -s ../../Anclient/examples/example.js/album/web-dist web-dist
ALBUM_WEB    = web-dist

default: jserv-sqlite

help:
	@cat Makefile

#
check:
	@echo "checke resources ..."

	@echo "$(wildcard exiftool-*.zip)"
ifeq ("$(wildcard exiftool-*.zip)","")
	${error "Exiftool zip doesn't exist."}
endif

	@echo "$(wildcard $(PORTFOLIO_07))"
ifeq ("$(wildcard $(PORTFOLIO_07))","")
	${error "Portfolio py3 package doesn't exist."}
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

	@jar -cMf jserv-album-$(VERSION).zip $(PORTFOLIO_07) $(BUILDDIR) $(VOLUME) WEB-INF $(ALBUM_WEB)
	
	@mv jserv-album-$(VERSION).zip $(BUILDDIR)
	@rm $(BUILDDIR)/jserv-album-*.jar
	@rm $(BUILDDIR)/$(EXIFTOOL_ZIP)
	@rm -rf WEB-INF
	@mvn dependency:tree | grep io.github.odys-z

clean:
	@rm -rf bin $(VOLUME) WEB-INF

.PHONY: help

default: jserv-sqlite