package io.odysz.semantic.ext;

import java.io.IOException;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import io.odysz.common.Utils;
import io.odysz.semantic.DA.DatasetCfg.TreeSemantics;
import io.odysz.semantic.jprotocol.JBody;
import io.odysz.semantic.jprotocol.JHelper;
import io.odysz.semantic.jprotocol.JMessage;
import io.odysz.semantic.jserv.R.QueryReq;

public class DatasetReq extends QueryReq {

	String sk;
	public String[] sqlArgs;
	public String rootId;
	/**String array of tree semantics from client */
	private String smtcss;
	/**{@link TreeSEmantics} of tree from {@link #smtcss} or set with {@link #treeSemtcs(TreeSemantics)} */
	private TreeSemantics stcs;

	public DatasetReq(JMessage<? extends JBody> parent, String conn) {
		super(parent, conn);
		a = "ds";
	}

	public String sk() { return sk; }

	public static DatasetReq formatReq(String conn, JMessage<DatasetReq> parent) {
		DatasetReq bdItem = new DatasetReq(parent, conn);
		return bdItem;
	}

	/**
	 * @return parsed semantics
	 */
	public TreeSemantics getTreeSemantics() {
		if (stcs != null)
			return stcs;
		else {
			if (smtcss == null)
				return null;
			else {
				stcs = new TreeSemantics(smtcss);
				return stcs;
			}
		}
	}

	public void treeSemtcs(TreeSemantics semtcs) {
		this.stcs = semtcs;
	}

	@Override
	public void toJson(JsonWriter writer) throws IOException {
		writer.beginObject();
		writer.name("a").value(a);
		writer.name("conn").value(conn);
		writer.name("sk").value(sk);
		writer.name("rootId").value(rootId);
		writer.name("page").value(page);
		writer.name("pgSize").value(pgsize);

		writer.name("smtcss").value(stcs == null ? "[" + smtcss + "]" : stcs.toJson());

		try {
			if (sqlArgs != null) {
				writer.name("joins");
				JHelper.writeStrings(writer, sqlArgs);
			}
		} catch (IOException e) {
			e.printStackTrace();	
		}
		writer.endObject();
	}

	@Override
	public void fromJson(JsonReader reader) throws IOException {
		JsonToken token = reader.peek();
		if (token == JsonToken.BEGIN_OBJECT) {
			reader.beginObject();
			while (token != JsonToken.END_OBJECT) {
				String name = reader.nextName();
				if ("a".equals(name))
					a = JHelper.nextString(reader);
				else if ("conn".equals(name))
					conn = JHelper.nextString(reader);
				else if ("page".equals(name))
					page = reader.nextInt();
				else if ("pgSize".equals(name))
					pgsize = reader.nextInt();
				else if ("sk".equals(name))
					sk = JHelper.nextString(reader);
				else if ("rootId".equals(name))
					rootId = JHelper.nextString(reader);
				else if ("smtcss".equals(name)) {
					JsonToken peek = reader.peek();
					if (peek == JsonToken.BEGIN_ARRAY) {
						reader.beginArray();
						String[] ss = JHelper.readStrs(reader);
						reader.endArray();
						stcs = new TreeSemantics(ss);
					}
					else if (peek == JsonToken.NULL)
						reader.nextNull();
					else {
						// What's this?
						Utils.warn("Not handled: %s - expecting array", reader.nextString());
					}
				}
				else if ("sqlArgs".equals(name))
					sqlArgs = JHelper.readStrs(reader);

				token = reader.peek();
			}
			reader.endObject();
		}
	}
}
