"""
About
=====

The root package of ody's.

Troubleshooting
===============

This package name will make troubles when debugging in PyCharm with virtual env.

The error
---------

    Fatal Python error: init_sys_streams: can't initialize sys standard streams
    Python runtime state: core initialized
    AttributeError: module 'io' has no attribute 'open'

    Current thread 0x00005f58 (most recent call first):
    <no Python frame>

    Process finished with exit code 1

The Reason
----------

This package, project/src/io/__init__.py, is shadowing the system's built-in io package, io.py.

This error won't happen if debugging with system's default Python interpreter.

Grok tells the difference:

The sys.path of default interpreter:

    The system interpreter’s sys.path typically includes:

    . The current working directory (if set).
    . Directories from the PYTHONPATH environment variable (if set).
    . Standard library paths (e.g., C:\Python313\lib).
    . Site-packages for installed packages (e.g., C:\Python313\lib\site-packages).

The sys.path of venv:

    . The current working directory (often synodepy3 or synodepy3/src in PyCharm).
    . The project directory or its subdirectories (e.g., synodepy3/src), especially if PyCharm’s debug configuration adds content/source roots to PYTHONPATH.
    . The virtual environment’s standard library (e.g., synodepy3/venv39/lib/python3.9).
    . The virtual environment’s site-packages (e.g., synodepy3/venv39/lib/python3.9/site-packages).

    PyCharm’s debug configuration for venv39 may add synodepy3/src to sys.path
    (e.g., via Add content roots to PYTHONPATH), which isn’t the case for the
    system interpreter.

Fix
---

The package name is impossible to be changed. To debug in Pycham,
in debug configuration, uncheck

    Add content roots to PYTHONPATH

"""