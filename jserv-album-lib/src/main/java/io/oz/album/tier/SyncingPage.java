package io.oz.album.tier;

import io.odysz.anson.Anson;

public class SyncingPage extends Anson {
	public String device;
    public int taskNo = 0;
    public int start;
    public int end;
    
    public SyncingPage() {}

    public SyncingPage(int begin, int afterLast) {
        start = begin;
        end = afterLast;
    }

    public SyncingPage nextPage(int size) {
        start = end;
        end += size;
        return this;
    }
}
