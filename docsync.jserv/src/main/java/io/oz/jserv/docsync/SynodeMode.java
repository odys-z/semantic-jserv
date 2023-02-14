package io.oz.jserv.docsync;

/**
 * jserv-node states
 */
public enum SynodeMode {
	/** jserv node mode: cloud hub, equivalent of {@link Docsyncer#cloudHub} */
	hub,
	/** jserv node mode: private main, equivalent of {@link Docsyncer#mainStorage} */
	main,
	/** jserv node mode: bridge , equivalent of {@link Docsyncer#privateStorage}*/
	bridge,
	/** jserv client device */
	device
}