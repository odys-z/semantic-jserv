from setuptools import setup, find_packages

# import py2exe
# pip install wheel
# python setup.py bdist_wheel sdist

setup(
    name='synode.py3',
    version='0.1.0',
    description='Portfolio Synode Stand Alone Service',
    author='Ody Z',
    zip_safe=False,
    author_email='odys.zhou@gmail.com',
    keywords='Documents Files Relational Database Synchronization',
    # py_modules=["src"],
    # package_dir={"": "src"},  # Tell setuptools that packages are under 'src'

    packages=['src'] + [f'src.{pkg}' for pkg in find_packages(where='src')],  # Include src and its subpackages
    package_dir={'src': 'src'},

    package_data={
        "synodepy3": ["form.ui"],  # Include the form.ui file in the package
    },

    entry_points={'console_scripts': ['synode-clean = src.synodepy3.cli:clean', 'synode-start-web = src.synodepy3.cli:startweb']},

    # package_data={'Portfolio.bin': ['../resources/portfolio-srv.exe']},
    # data_files=[('bin', ['bin/jserv-album-0.7.0.jar', '../resources/portfolio-srv.exe'])],
    include_package_data=True,
    
    install_requires=['pyside6', 'qrcode', 'anson.py3>=0.0.7', 'psutil', 'pillow>=8.0.0']
    # classifiers=["Programming Language :: Python :: 3"]
)