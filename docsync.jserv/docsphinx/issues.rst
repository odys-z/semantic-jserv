Issues
======

Injecting *jservs* at runtime
-----------------------------

When the application server is installed, it should know peer jservs
through dictionary.json, wich is version controlled in source.

The test uses a simple cheap way to setup this.

.. code-block:: java

    public static SynotierJettyApp main_(String vol_home,
        String[] args, PrintstreamProvider ... oe) {

        // @Option(name="-peer-jservs",
        // usage=only for test, e. g.
        // "X:http://127.0.0.1:8964/album-jserv Y:http://127.0.0.1/album-jserv")
        // public String CliArgs.jservs;
        CliArgs cli = new CliArgs();

        CmdLineParser parser = new CmdLineParser(cli);
        parser.parseArgument(args);

        if (!isblank(cli.installkey)) {
            YellowPages.load(FilenameUtils.concat(
                new File(".").getAbsolutePath(),
                webinf,
                EnvPath.replaceEnv(vol_home)));
            AppSettings.setupdb(cfg, webinf, vol_home, "config.xml", cli.installkey,
                    // inject jservs args to the peers' configuration
                    cli.jservs);
        }
    }
..

Where the *jservs* for peers are injected into SynodeConfig, and then updated into
table *syn_nodes*, planning future extension for providing *jservs* in a separate json. 

Only one syn-worker thread
--------------------------

Multiple synodes cannot work in one (test) running.

commit: 2079991c2cfda1a46ac532b94ebb836e41590377

See ExpSynodetier.syncIns().

Overhaul: sending exception to client
-------------------------------------

Re-design ServPort.err(MsgCode code, Exceptiion e);

How long will the syn-worker can work without clean buffered data
-----------------------------------------------------------------

The maximum distance between stamps in syn-change and synode's stamp is half of
Nyquence range, Long.MAX_VALUE, 2 ^ 63 -1 in Java. Each time the stamp will be
increased by 1 for syn-workers looping. The longest buffering is the difference
of the earliest buffered change logs and the latest stamp.

If each interval is one second, a year has 3.15 * 10 ^ 7 seconds, the longest time
can be correctly buffered is approx. 300 billion years.

.. _issue-extfile-uri-update:

Updating an ext-file record's content changes its syn-uid
---------------------------------------------------------

Raised by integrating semantic-doc's doc-trees into synodes, see
`architecture-doctree.md <../architecture-doctree.md>`_.

Updating uri of a record with *extFilev2* semantics is not supported.
DASemantics.ShExtFilev2.onUpdate() throws "Use delete & insert instead", and
ExessionPersist doesn't insert a syn_docref for a CRUD.U change of uri at peers.

So a record whose file content is replaced is deleted and inserted, getting a new
syn-uid. Anything referring to the record by its syn-uid, e.g. a doc-tree referring to
another doc-tree's node, *syn-uid : nid*, loses its target.

Decision for now (no change to the synchronizing engine):

* A committed doc-tree is never modified. A modification is a new edition, a new record.
  References point to an edition by its syn-uid, which is stable for the record's life.
* Whether the old edition is deleted is the user's choice. References to a deleted
  edition render as placeholders.
* No re-linking: referencing docs stay with the old edition (stale) until their owners
  edit the references, which makes new editions of their own.

Tested in semantic-DA, DBSyntableTest.test06Doctree(): doc-tree records in h_photos,
created, replaced by delete & insert, deleted.

Planned fix, for a later release (allow updating uri, keeping the syn-uid):

* semantic-DA ShExtFilev2.onUpdate(): write the new content (as onInsert), delete the
  old file on commit; store a doc-ref envelope as is when applied at a peer.
* semantic-DA ExessionPersist, CRUD.U: when uri is updated with an envelope, replace
  the syn_docref row.
* docsync SynssionPeer.resolveRef206Stream(): save a breakpoint only if uri still holds
  the same doc-ref (compare and set). Otherwise a downloading of version 1 overwrites a
  newly arrived envelope of version 2. Clean up the old file.
* Tests: semantic-DA with extFilev2; docsync, replacing while the peer is downloading.

Rejected: delete & insert keeping the old syn-uid, which breaks the exchange's
invariant that one uid is one entity's lifetime, as peers would get a D and a C of the
same uid.
