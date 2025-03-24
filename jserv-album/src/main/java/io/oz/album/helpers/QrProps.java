package io.oz.album.helpers;

public class QrProps {

	boolean small;
	int[] wh;

	public QrProps(boolean small) {
		this.small = small;
	}

	public int[] wh() {
		return this.wh == null ? new int[] {10, 10} : this.wh;
	}

}
