# Sphinx configuration of semantic-doc's html preview (and the synode's html build).
#
# The doc's files (index.rst, one <nid>.rst per section) are written beside this file
# by the previewer; this template only brings the configuration and static files.

project = 'semantic-doc'
author = ''
copyright = ''

extensions = []
exclude_patterns = ['_build']

html_theme = 'sphinx_rtd_theme'
html_static_path = ['static']
# styles of the editor's roles (red, bg-yellow, ...), shared with the editor
html_css_files = ['theme-roles.css']

html_show_sourcelink = False
html_copy_source = False
html_show_sphinx = False
html_theme_options = {
    'navigation_depth': 4,
    'collapse_navigation': False,
}
