# About

  The Portfolio 0.8.0 and above synode source. 

## Pack & Deploy 

  Rquires Python 3.12. With venv setup, build from source,
  
  ```
    pip install invoke, build, anson.py3, semantics.py3, anclient.py3, jre-mirror, deprecated, Pyside=6.8.2.1, qrcode

    # 0.8.0 POSIX
    invoke make --deploy=tasks.[your-confg].json

    # 0.8.0 Windows 
    invoke deploy --deploy=tasks.[your-confg].json
  ```

  Package Content

  ```
    -------------------- WINDOWS ---------------------------
    - portfolio-desktop-windows-0.8.0.zip
      - desktop [album-gui.exe, ws-agent.jar, settings]
        - jar17/*

    - portfolio-synode-windows-0.8.0.zip
      - synode-windows-0.8.0.whl [setup-cli.ex/pip, setup-gui.ex/pip, uninstall-cli.exe/pip, win-srv, album-synode.jar, web-album.jar, res/apk]
        - jre17.zip
      - desktop [album-gui.exe, ws-agent.jar, settings]

    --------------------- POSIX * --------------------------
    - portfolio-synode-posix-0.8.0.tar.gz
      - [Debug Requires] synode-windows-0.8.0.whl [setup-gui(pip), setup-cli(pip), uninstall-cli(pip), album-synode.jar, web-album.jar, res/apk]
      * [TODO]  desktop [album-gui, ws-agent.jar, settings]
  ```

### Python Version

  This project (tasks.py) itself is planned to build with Python 3.9.1 in a long term,
  while Pyside6 in synode.py requires Python 3.12.

  See [Semantics.py3](https://pypi.org/search/?q=semantics.py3) for install Python 3.9.1 alone side other versions. 

  ```
     /opt/python3.9.1/bin/python3.9 -m venv ~/myenv-391
     source ~/myenv-391/bin/activate
     install semantics.py3 anson.py3

    # To build the distribution package
    invoke make --deploy="a tasks configure file modified from tasks.github.json"
  ```

  See [synode.py](../synode.py/) for Pyside6 and Python 3.12 requirements.

### Sensitive files ignored for git

  ```
    example.android/local.properties           # see local.github.properties
    example.android/.../values/products.xml    # TODO products.github.json
    example.android/.../values-zh/products.xml # TODO products.github.json
    example.js/album/.../host.json             # TODO products.github.json
    html-service/java/test-dist/host.json      # see host.github.json
  ```