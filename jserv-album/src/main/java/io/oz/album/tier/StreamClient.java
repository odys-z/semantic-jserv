package io.oz.album.tier;

public class StreamClient<T extends FileRecord> {

	public String download(T rec, String filepath) {
		return filepath;
	}

	public String upload(T rec, String localPath) {
		return localPath;
	}

}
