# V 1.6.x

Fixes:

- Docsync.jserv tests cannot run in batch mode.

Tasks:

- JservUrl with jprotocol instance for service tier. Not static.

- Remove client func-uri to conn-id mapping

- Refactor Req.header

    act = [uri, port-name, ask, extra-memo]?
    replace all sysuri & synuri with AnclientSettings at client?

- Deprecate XML configures?

- Accept generated protocol layer? 

# Version 1.5.x

Experimetental for relational database synchronization.

# Version 1.4.x

Stable latest version for applications.

# Version 1.1.0

- Add Antson.java dependency, remove dependency on
[Gson](https://mvnrepository.com/artifact/com.google.code.gson/gson)
