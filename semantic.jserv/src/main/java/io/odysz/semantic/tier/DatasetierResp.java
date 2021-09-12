package io.odysz.semantic.tier;

import java.util.Set;

import io.odysz.semantic.jprotocol.AnsonResp;

public class DatasetierResp extends AnsonResp {

	Set<String> sks;

	public DatasetierResp sks(Set<String> sks) {
		this.sks = sks;
		return this;
	}

}
