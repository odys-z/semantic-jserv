package io.oz.sandbox.album;

import java.util.List;

import io.odysz.semantic.tier.docs.DocsResp;

public class AlbumResp extends DocsResp {

	List<?> docforest;

	public AlbumResp forest(List<?> forest) {
		this.docforest = forest;
		return this;
	}

}
