package io.oz.syntier.serv;

import org.kohsuke.args4j.Option;

public class CliArgs {

	@Option(name="-port", usage="Listening Port")
	int port;

	@Option(name="-ip", usage="Host's IP address")
	String ip;

	@Option(name="-install-key", usage="Install root key")
	String installkey;
	
	@Option(name="-key", usage="root key used while initalize db")
	String rootkey;
}
