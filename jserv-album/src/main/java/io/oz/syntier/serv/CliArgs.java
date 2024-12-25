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
	
	@Option(name="-urlpath", usage="url root path to server, e. g. jserv-album (no first slash)")
	String urlpath;

	@Option(name="-peer-jservs", usage="only for test, e. g. \"X:http://127.0.0.1:8964/album-jserv Y:http://127.0.0.1/album-jserv\"")
	public String jservs;

	public CliArgs() {
		port = 8964;
	}
}
