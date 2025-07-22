package io.oz.album.peer;

import java.util.HashMap;

import io.odysz.transact.sql.parts.AnDbField;

public class Exifield extends AnDbField {
	HashMap<String, String> exif;

	public Exifield add(String name, String v) {
		if (exif == null)
			exif = new HashMap<String, String>();
		exif.put(name, v);
		return this;
	}
}