# About

The common protocol module between Albumtier.java & Jserv-album.

Dependency chart:

        .-> albumtier
        .    |     |
        .    |     +-> anclient.java
        .    |     |    +->[Doclientier,  ...]
        .    |     |    +->[SessionClient ...]
        .    |     |    +--------------------------+
        .    |     |                               |
        ^    +-------> syndoclib (jserv-album-lib) |
       test  |     |    | (protocol: AlbumReq)     |
        .    |     |    +--------------------------+-> semantic.jserv
    -- jserv-alubm |                                     +-> [DocsException, ExpSyncDoc, ...]
          |        |
          |        |
          +-> docysync (SynssionPeer ...)