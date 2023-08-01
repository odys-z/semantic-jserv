package io.oz.album.tier;

import java.util.HashMap;

import io.odysz.anson.Anson;

public class Exifield extends Anson {
	HashMap<String, String> exif;

	public Exifield add(String name, String v) {
		if (exif == null)
			exif = new HashMap<String, String>();
		exif.put(name, v);
		return this;
	}
}