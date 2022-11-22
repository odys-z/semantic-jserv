package io.odysz.semantic.tier.docs;

/**
 * jserv-node states
 */
public enum SyncMode {
	/** jserv node mode: cloud hub, equivalent of {@link Docsyncer#cloudHub} */
	hub,
	/** jserv node mode: private main, equivalent of {@link Docsyncer#mainStorage} */
	main,
	/** jserv node mode: private , equivalent of {@link Docsyncer#privateStorage}*/
	priv
}