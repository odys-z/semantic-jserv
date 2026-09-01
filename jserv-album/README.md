# About

  The Portfolio 0.6.5 and above synode source. 

## Pack & Deploy with Python 3.9.1

  This project is planned to build with Python 3.9.1 in a long term.

  See [Semantics.py3](https://pypi.org/search/?q=semantics.py3) for install Python 3.9.1 alone side other versions. 

  ```
     /opt/python3.9.1/bin/python3.9 -m venv ~/myenv-391
     source ~/myenv-391/bin/activate
     install semantics.py3 anson.py3

    # To build the distribution package
    invoke make --deploy="a tasks configure file modified from tasks.github.json"
  ```
