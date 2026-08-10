[![PyPI version](https://img.shields.io/pypi/v/semantier-generator.svg)](https://pypi.org/project/semantier-generator/)

# About Semantier Generator.py3

Generate c++ peer of semantic-*.

To run tests/ast/cp bash script, change $SRCDIR to [Anclient.cmake](https://github.com/odys-z/Anclient.cmake)/tests/ast:

```
    pip install semantier_generator
    cd tests/ast
    ./cp
    python -m semantier_gen settings/gen...josn, ast
```

# Constructor Semantics

- C++ 20 examples (semantier-generator 0.0.7) 

Example: Heartbeat.ast.json

```
  "dataAnclass": "io.odysz.semantic.jsession.HeartBeat",        class HeartBeat : public anson::AnsonBody {  
  "baseAnclass": "io.odysz.semantic.jprotocol.AnsonBody",
                                                                    HeartBeat() :
  ----------------------------------------------------------------------------------------------------------
  base": {"stype": "()",                                   =>           AnsonBody(Port::heartbeat),
          "args": ["AnsonBody", "Port::heartbeat"]},
  "args": [{"stype": "", "args": ["string", "clienturi"]}, =>           string clienturi, ... 
 
           {"stype": "ini",                                =>           string ssid, ... ssid(ssid)
            "args": ["string", "ssid", "ssid"]},
 
  "body": [{"stype": "=", "args": ["uri", "clienturi"]}]   =>           uri = clienturi;
  ----------------------------------------------------------------------------------------------------------
                                                                     {};
```

Example: docsreq.ast.json

For "()" semantics, user must implement function body of generated decleration, e. g. format() in the follow example.

```
  "body": {"stype": "()", "args": ["format", "p"]} },    =>   void format(const IFileDescriptor& p); # <---
                                                              # DocsReq() :
                                                              # UserReq() {
                                                              #  Type(_type_);
                                                                  format(p);   # <---
                                                              # }
```

Since 0.0.9, will force a C++ - Json bridging context pointer argument if the type is a JavaEnum or an AnsonMsg.
