package io.oz.album.helpers;

import java.util.ArrayList;

import io.odysz.semantics.SemanticObject;

public class Metadata extends SemanticObject {

	public ArrayList<String> names() {
		return new ArrayList<String>(props.keySet());
	}

//	public String name() {
//		return null;
//	}

	public String getLatitude() {
		return String.valueOf(props.get(TIFF.LATITUDE));
	}

	public String getLongitude() {
		return String.valueOf(props.get(TIFF.LONGITUDE));
	}
}
