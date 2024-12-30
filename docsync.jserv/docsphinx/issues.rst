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
