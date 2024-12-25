Closed Issues
=============

[closed] Album.jserv Denpendencies
----------------------------------

Thu Dec 12 12:41:54 2024 +0800

Refactor: move DocsException to semantic.jserv, in the package io...tier.docs, shared by anclient and docsync.

::

        ..> albumtier
        ^    |     |
        .    |     +-- anclient.java
        .    |     |    +--[Doclientier,  ...]
        .    |     |    +--[SessionClient ...]
        .    |     |    +--------------------------+
       test  |     |                               |
        ^    +-------- syndoclib (jserv-album-lib) |
        .    |     |    | (protocol: AlbumReq)     |
        .    |     |    +--------------------------+-- semantic.jserv
       jserv-alubm |                                     +-- [DocsException, ExpSyncDoc, ...]
          |        |
          |        |
          +----- docysync (SynssionPeer ...)

[closed] Design Synssion and SyncUser:
--------------------------------------

commit f7c7c8d6bbd3f383d5d4d295059dfabeeb9f861e

Date:   Tue Oct 29 19:41:16 2024 -0400

::

    √ 0. Docsync 0.2.0: config.xml/class-IUser for IUser object used at serverside,
        SynodeConfig by syntity.json's user id is used for Synssion client side, which
        is injected into sysconn while install;
        A SyncUser, admin, is used for domain wide in 0.2.0.
    √ 1. SynDomanager extends SyndomContext, and SyndomContext.load() is called by Syngleton
    √ 2. SynDomanager.loadomx() -> call SyndomContext.loadStampNv(), from which also initializing local robot.
    √ 3. ExpDoctier uses DocUser to represent session's IUser object
    √ 4. SynodeTier also uses a DocUser for initiate a synssion, which is not the same to the
        syn-context's local user, and will trigger unlockx() when logging out.
    √ 5. SyndomContext is responsible for synlock managing.
    √ 6. Simplify Syngleton.syndomanagers
