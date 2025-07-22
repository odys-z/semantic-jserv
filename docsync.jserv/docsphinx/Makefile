# Minimal makefile for Sphinx documentation
#

# You can set these variables from the command line, and also
# from the environment for the first two.
SPHINXOPTS    ?=
SPHINXBUILD   ?= sphinx-build
SOURCEDIR     = .
BUILDDIR      = _build

PAGEDIR       = ../../docs/docsync

# Put it first so that "make" without argument is like "make help".
help:
	@$(SPHINXBUILD) -M help "$(SOURCEDIR)" "$(BUILDDIR)" $(SPHINXOPTS) $(O)

# reference
# https://www.docslikecode.com/articles/github-pages-python-sphinx/
# https://github.com/sphinx-doc/sphinx/issues/3382#issuecomment-470772316
ghpage:
	@rm -rf $(PAGEDIR)
	@rm -r $(BUILDDIR)
	@mkdir $(BUILDDIR)
	@make html
	@cp -a $(BUILDDIR)/html/. $(PAGEDIR)
	@touch $(PAGEDIR)/.nojekyll

.PHONY: help Makefile

# Catch-all target: route all unknown targets to Sphinx using the new
# "make mode" option.  $(O) is meant as a shortcut for $(SPHINXOPTS).
%: Makefile
	@$(SPHINXBUILD) -M $@ "$(SOURCEDIR)" "$(BUILDDIR)" $(SPHINXOPTS) $(O)
