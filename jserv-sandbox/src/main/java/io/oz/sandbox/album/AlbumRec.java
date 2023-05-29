package io.oz.sandbox.album;

import java.util.ArrayList;

import io.odysz.anson.Anson;

public class AlbumRec extends Anson {

	String album;

	/** Collects' ids */
	ArrayList<PhotoCollect> collects;

	/** Collects' default length (first page size) */
	int collectSize;

	/** Photos ids, but what's for? */
	ArrayList<String> collect;

}
