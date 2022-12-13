package io.oz.jserv.sync;

/**
 * jserv-node states
 */
public enum SynodeMode {
	/** jserv node mode: cloud hub, equivalent of {@link Docsyncer#cloudHub} */
	hub,
	/** jserv node mode: private main, equivalent of {@link Docsyncer#mainStorage} */
	main,
	/** jserv node mode: private , equivalent of {@link Docsyncer#privateStorage}*/
	priv,
	/** jserv client device */
	device
}