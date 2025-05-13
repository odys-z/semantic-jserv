### What to: configure version number by setup.py, override by tasks.py.
#
# invoke make: export envs and write to __version__.py & *.bat -> run inst_<srv>_byname(py-var: srvname)
# py -m build: use default vers, write to __version__.py & *.bat -> ..

### How to
# invoke build
#
# Don't directly build with:
# pip install wheel
# python -m build

from setuptools import setup, find_packages
from pathlib import Path
from src.synodepy3.__version__ import synode_ver

this_directory = Path(__file__).parent
long_description = (this_directory / "README.md").read_text()

setup(
    name='synode.py3',
    version=synode_ver,
    description='Portfolio Synode Stand Alone Service',
    author='Ody Z',
    zip_safe=False,
    author_email='odys.zhou@gmail.com',
    keywords='Documents Files Relational Database Synchronization',
    long_description=long_description,
    long_description_content_type='text/markdown',

    packages=['src'] + [f'src.{pkg}' for pkg in find_packages(where='src')],  # Include src and its subpackages
    package_dir={'src': 'src'},

    package_data={
        "synodepy3": ["form.ui"],  # Include the form.ui file in the package
    },

    entry_points={'console_scripts': ['synode-uninstall-srv = src.synodepy3.cli:uninst_srv', 'synode-clean = src.synodepy3.cli:clean', 'synode-start-web = src.synodepy3.cli:startweb']},

    # package_data={'Portfolio.bin': ['../resources/portfolio-srv.exe']},
    # data_files=[('bin', ['bin/jserv-album-0.7.0.jar', '../resources/portfolio-srv.exe'])],
    include_package_data=True,
    
    install_requires=['pyside6', 'qrcode', 'anson.py3>=0.1.6', 'anclient.py3>=0.1.0', 'psutil', 'pillow>=8.0.0', 'invoke>=2.2.0']
    # classifiers=["Programming Language :: Python :: 3"]
)