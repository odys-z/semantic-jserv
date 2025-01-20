package io.oz.album.helpers;

import static io.odysz.common.LangExt._0;

import java.util.ArrayList;

import io.odysz.semantics.SemanticObject;

public class Metadata extends SemanticObject {

	public ArrayList<String> names() {
		return new ArrayList<String>(props.keySet());
	}

	public String getLatitude() {
		return String.valueOf(props.get(TIFF.LATITUDE));
	}

	public String getLongitude() {
		return String.valueOf(props.get(TIFF.LONGITUDE));
	}

	public <T> T meta(String name) {
		@SuppressWarnings("unchecked")
		ArrayList<T> vals = (ArrayList<T>) get(name); 
		return _0(vals);
	}
}
