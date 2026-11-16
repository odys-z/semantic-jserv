'''
semantic-doc on a synode: the Sphinx of this installation, and html-service's status and 404 pages,
built with it. ISSUE sdoc-sphinx-venv (issues/sdoc-sphinx-venv.md)
'''
import os
import platform
import shutil
import subprocess
import sys
import tempfile
from datetime import datetime
from typing import Optional

from semanticshare.io.oz.jserv.docs.syn.singleton import AppSettings, web_dist, sdoc_sphinx

status_html, notfound_html = 'status.html', '404.html'
'''
The pages built in the web-dist root, html-service's status page and its 404 page (html-service.json "notfound").
'''


def sphinx_of_installer(current: Optional[str]) -> str:
    '''
    :param current: settings.json's sdocSphinx
    :return: the sphinx-build of the environment running this, e.g. <venv>/bin/sphinx-build; current if
    there is none (e.g. Windows, where Sphinx is packed), or the default, found in PATH
    '''
    exe = os.path.join(sys.prefix, 'Scripts' if os.name == 'nt' else 'bin',
                       'sphinx-build.exe' if os.name == 'nt' else 'sphinx-build')
    return exe if os.path.isfile(exe) else current or sdoc_sphinx


def template_dir(settings: AppSettings) -> Optional[str]:
    '''
    :return: <sdocTemplateRoot>/<sdocTemplateId>, or the first template found in sdocTemplateRoot (as Java
    AppSettings.sdocTemplateSource()); None if there is none
    '''
    root = settings.sdocTemplateRoot
    ok = lambda d: os.path.isfile(os.path.join(d, 'source', 'conf.py'))
    if settings.sdocTemplateId and ok(os.path.join(root, settings.sdocTemplateId)):
        return os.path.join(root, settings.sdocTemplateId)
    ids = sorted(d for d in os.listdir(root) if ok(os.path.join(root, d))) if os.path.isdir(root) else []
    return os.path.join(root, ids[0]) if ids else None


def build_pages(settings: AppSettings, synid: str, version: str) -> Optional[str]:
    '''
    Build the status and 404 pages into the web-dist root, with the doc template and settings.sdocSphinx.
    It's also the check that Sphinx, the theme and docutils are usable. The pages link the template's
    _static/, served by html-service at /sdoc-template/<id>/_static/.
    :return: None if built, else why not (pages already there are kept)
    '''
    tpl = template_dir(settings)
    if tpl is None:
        return f'no doc template in {settings.sdocTemplateRoot}'
    sphinx = settings.sdocSphinx or sdoc_sphinx
    try:
        ver = subprocess.run([sphinx, '--version'], capture_output=True, text=True, timeout=60).stdout.strip()
        info = (f':Portfolio: {version}\n:Synode: {synid}\n:OS: {platform.platform()}\n'
                f':Built: {datetime.now():%Y-%m-%d %H:%M}\n:Sphinx: {ver}\n:Template: {os.path.basename(tpl)}\n')
        with tempfile.TemporaryDirectory() as tmp:
            src, out = os.path.join(tmp, 'source'), os.path.join(tmp, 'html')
            shutil.copytree(os.path.join(tpl, 'source'), src)
            with open(os.path.join(src, 'index.rst'), 'w', encoding='utf-8') as f:
                f.write(f'Portfolio {version}\n{"=" * (10 + len(version))}\n\n'
                        f'This server publishes the documents of synode {synid}.\n\n{info}')
            with open(os.path.join(src, '404.rst'), 'w', encoding='utf-8') as f:
                f.write(':orphan:\n\nNot found\n=========\n\nThe page asked for is not here. A document may be '
                        f'unpublished, or its address mistyped.\n\n`Status of this server </{status_html}>`_\n\n{info}')
            r = subprocess.run([sphinx, '-b', 'html', '-q', src, out], capture_output=True, text=True, timeout=300)
            if r.returncode != 0:
                return f'{sphinx} failed (exit code {r.returncode}).\n{r.stdout}{r.stderr}'.strip()

            # the template's files, served by html-service, e.g. /sdoc-template/rtd-0.8/_static/
            rel = os.path.relpath(tpl, web_dist).replace(os.sep, '/')
            static = f'/{rel}/_static/' if not rel.startswith('..') else '/sdoc-template/rtd-0.8/_static/'
            for page, name in (('index.html', status_html), ('404.html', notfound_html)):
                with open(os.path.join(out, page), encoding='utf-8') as f:
                    html = f.read().replace('"_static/', f'"{static}').replace("'_static/", f"'{static}")
                with open(os.path.join(web_dist, name), 'w', encoding='utf-8') as f:
                    f.write(html)
        return None
    except (OSError, subprocess.SubprocessError) as e:
        return f'{sphinx}: {e}'
