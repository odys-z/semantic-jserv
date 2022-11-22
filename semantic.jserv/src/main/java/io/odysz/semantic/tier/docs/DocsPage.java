package io.odysz.semantic.tier.docs;

import java.util.ArrayList;

import io.odysz.anson.Anson;

/**
 * Task page to update synchronizing information.
 * 
 * @author odys-z@github.com
 *
 */
public class DocsPage extends Anson {
	public String device;
    public int taskNo = 0;
    public int start;
    public int end;
    
    public SyncFlag flag;
    
    public DocsPage() {}

    public DocsPage(int begin, int afterLast) {
        start = begin;
        end = afterLast;
    }

    public DocsPage nextPage(int size) {
        start = end;
        end += size;
        return this;
    }

	protected ArrayList<String> pathsPage;
	public ArrayList<String> paths() { return pathsPage; }
}
