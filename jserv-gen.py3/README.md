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
  "dataAnclass": "io.odysz.semantic.jsession.HeartBeat",   =>   class HeartBeat  
  "baseAnclass": "io.odysz.semantic.jprotocol.AnsonBody",  =>     : public anson::AnsonBody {
                                                                public:
                                                                    HeartBeat() :
  ----------------------------------------------------------------------------------------------------------
  base": {"stype": "()",                                   =>           AnsonBody(Port::heartbeat),
          "args": ["AnsonBody", "Port::heartbeat"]},
  "args": [{"stype": "", "args": ["string", "clienturi"]}, =>           string clienturi, ... 
 
           {"stype": "ini",                                =>           string ssid, ... ssid(ssid)
            "args": ["string", "ssid", "ssid"]},
  ----------------------------------------------------------------------------------------------------------
                                                                    {
  ----------------------------------------------------------------------------------------------------------
  "body": [{"stype": "=", "args": ["uri", "clienturi"]}]   =>           uri = clienturi;
  ----------------------------------------------------------------------------------------------------------
                                                                    }
                                                                };
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

Since 0.1.0, an indirect subclass of JavaEnum will be forced a class name initialization.

```
  { "type": "io.odysz.reflect.AnsonJavaEnumAst",
    "baseAnclass": "io.odysz.semantic.jprotocol.AnsonMsg.Port",
    "dataAnclass": "io.oz.anclient.ipcagent.WSPort",
    "ctorsemantics": [
      { "base": {"stype": "()", "args": ["base", "\"_sentinel_\""]}},
      ...
    ]
  }

  =>
  class WSPort : public anson::Port {
  public:
      inline static const std::string _type_ = "io.oz.anclient.ipcagent.WSPort";

      WSPort(const JsonOpt* ctx) : Port(ctx, "_sentinel_") {
          Anclass(_type_);
      }
      ...
  }
```
Since 0.0.9, will force a C++ - Json bridging context pointer argument if the type is a JavaEnum or an AnsonMsg.
