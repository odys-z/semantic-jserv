package io.oz.album.helpers;

import java.util.Set;

import io.odysz.semantics.SemanticObject;

public class Metadata extends SemanticObject {

	public Set<String> names() {
		return props.keySet();
	}

//	public String name() {
//		return null;
//	}

	public String getLatitude() {
		return null;
	}

	public int getLongitude() {
		return (int) props.get(TIFF.);
	}
}
