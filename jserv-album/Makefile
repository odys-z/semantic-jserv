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

# ln -s ../synode.py/dist/synode_py3-0.1.0-py3-none-any.whl portfolio-synode-0.7-py3-none-any.wh
PORTFOLIO_07 = portfolio-synode-0.7-py3-none-any.whl

# ln -s ../../Anclient/examples/example.js/album/web-dist web-dist
ALBUM_WEB    = web-dist
#WEBPACK_REPORT=$(ALBUM_WEB)/report.html
WEBPACK_REPORT=$(ALBUM_WEB)/report.html

default: jserv-sqlite

help:
	@cat Makefile

#
check:
	@echo "checking zip resources ..."

	@echo "$(wildcard exiftool-*.zip)"
ifeq ("$(wildcard exiftool-*.zip)","")
	${error "Exiftool zip doesn't exist."}
endif

	@echo "$(wildcard $(PORTFOLIO_07))"
ifeq ("$(wildcard $(PORTFOLIO_07))","")
	${error "Portfolio py3 package doesn't exist."}
endif

check-web:
	@echo "checking web-dist..."
	@if ! command -v stat >/dev/null 2>&1; then echo "[ERROR] Checking $(WEBPACK_REPORT) failed. stat command not found" && exit 1; fi
	stat -c '%w' pom.xml
	creation_time=$$(stat -c '%w' pom.xml)
	@echo $(creation_time)
	@if [ -z "$$creation_time" ]; then echo "[ERROR] Checking web-dist failed. No $(WEBPACK_REPORT) creation time available"; exit 1; fi

	@current_time=$$(date +%s)
	@one_hour_ago=$$((current_time - 3600))
	@creation_seconds=$$(date -d "$$creation_time" +%s 2>/dev/null)

	@if [ -z "$$creation_seconds" ]; then echo "Could not convert creation time of report.html"; exit 1; fi

	@if [ "$$creation_seconds" -le "$$one_hour_ago" ]; then \
		echo "WERNING: File $(WEBPACK_REPORT) was created 1 hour ago or earlier (creation time: $$creation_time)"; \
	else \
		echo "File $(WEBPACK_REPORT) was created at: $$creation_time"; \
	fi

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