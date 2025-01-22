# Make a zip file for distribution, as a temporary tool for
#  
SOURCEDIR = .
BUILDDIR  = target/_build
ZIP       = jserv-album-bin.zip
VOLUME    = volume

help:
	@cat Makefile

#

jserv:
#   @mvn clean dependency:tree | @grep io.github.odys-z
	@rm -f $(ZIP)
	@rm -rf $(BUILDDIR)
	@mkdir  $(BUILDDIR)
	@mvn clean compile package

	@rm -rf $(VOLUME)
	@mkdir $(VOLUME)
	@cp -a volumes-0.7/volume-hub/. $(VOLUME)
	@zip -r jserv-alubm-hub.zip target/jserv-album-*.jar $(VOLUME)

	@rm -rf $(VOLUME)
	@mkdir $(VOLUME)
	@cp -a volumes-0.7/volume-prv/. $(VOLUME)
	@zip -r jserv-alubm-prv.zip target/jserv-album-*.jar $(VOLUME)

.PHONY: help