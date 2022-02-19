package io.oz.album.tier;

import io.odysz.anson.Anson;

public class PhotoRecord extends Anson {
	public String recId;
	public String uri;
	public String token;

	public int syncFlag;
	public String clientId;
	public String clientpath;
}
