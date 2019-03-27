package io.odysz.jsample.cheap;

import java.io.IOException;
import java.util.ArrayList;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import io.odysz.semantic.jprotocol.JBody;
import io.odysz.semantic.jprotocol.JMessage;
import io.odysz.semantics.x.SemanticException;

public class CheapReq extends JBody {

	public String wftype;
	public String nodeDesc;
	public ArrayList<ArrayList<String[]>> childInserts;

	public CheapReq(JMessage<? extends JBody> parent, String conn) {
		super(parent, conn);
	}

	@Override
	public void toJson(JsonWriter writer) throws IOException {
		// TODO Auto-generated method stub

	}

	@Override
	public void fromJson(JsonReader reader) throws IOException, SemanticException {
		// TODO Auto-generated method stub

	}

	public CheapReq nodeDesc(String descpt) {
		return this;
	}

	/**Insert nv into the newly prepared row.
	 * @see {@link #newChildInstRow()}.
	 * @param n
	 * @param v
	 * @return
	 */
	public CheapReq childInsert(String n, String v) {
		childInserts.get(childInserts.size() - 1).add(new String[] {n, v});
		return this;
	}

	/**Prepare a new child table's row inserting.
	 * @return
	 */
	public CheapReq newChildInstRow() {
		if (childInserts == null)
			childInserts = new ArrayList<ArrayList<String[]>>();
		if (childInserts.size() == 0)
			childInserts.add(new ArrayList<String[]>());
		return this;
	}

}
