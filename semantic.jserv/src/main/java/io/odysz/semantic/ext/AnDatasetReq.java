package io.odysz.semantic.ext;

import io.odysz.semantic.DA.DatasetCfgV11.TreeSemantics;
import io.odysz.semantic.jprotocol.AnsonBody;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jserv.R.AnQueryReq;

public class AnDatasetReq extends AnQueryReq {

	String sk;
	public String[] sqlArgs;
	public String rootId;
	/**String array of tree semantics from client */
	protected String smtcss;
	/**{@link TreeSemantics} of tree from {@link #smtcss} or set with {@link #treeSemtcs} ({@link TreeSemantics}) */
	protected TreeSemantics stcs;

	String root;
	public String root() { return root; }

	public AnDatasetReq() { super(null, null);}

	public AnDatasetReq root(String rootId) {
		this.root = rootId;
		return this;
	}

	public AnDatasetReq(AnsonMsg<? extends AnsonBody> parent, String conn) {
		super(parent, conn);
		a = "ds";
	}

	public String sk() { return sk; }

	public static AnDatasetReq formatReq(String conn, AnsonMsg<AnDatasetReq> parent, String sk) {
		AnDatasetReq bdItem = new AnDatasetReq(parent, conn);
		bdItem.sk = sk;
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

	public AnDatasetReq treeSemtcs(TreeSemantics semtcs) {
		this.stcs = semtcs;
		return this;
	}

//	@Override
//	public void toJson(JsonWriter writer, JOpts opts) throws IOException {
//		writer.beginObject();
//		writer.name("a").value(a);
//		writer.name("conn").value(conn);
//		writer.name("sk").value(sk);
//		writer.name("rootId").value(rootId);
//		writer.name("page").value(page);
//		writer.name("pgSize").value(pgsize);
//
//		// sk's semantics overriding smtcss?
//		if (sk != null) {
//			writer.name("smtcss");
//			writer.beginArray();
//			// writer.value(stcs == null ? smtcss : LangExt.toString(stcs.treeSmtcs()));
//			if (stcs != null)
//				JHelper.writeStrss(writer, stcs.treeSmtcs(), opts);
//			writer.endArray();
//		}
//
//		try {
//			if (sqlArgs != null) {
//				writer.name("joins");
//				JHelper.writeStrings(writer, sqlArgs, opts);
//			}
//		} catch (IOException e) {
//			e.printStackTrace();	
//		}
//		writer.endObject();
//	}
//
//	@Override
//	public void fromJson(JsonReader reader) throws IOException, SemanticException {
//		JsonToken token = reader.peek();
//		if (token == JsonToken.BEGIN_OBJECT) {
//			reader.beginObject();
//			token = reader.peek();
//			while (token != JsonToken.END_OBJECT) {
//				String name = reader.nextName();
//				if ("a".equals(name))
//					a = JHelper.nextString(reader);
//				else if ("conn".equals(name))
//					conn = JHelper.nextString(reader);
//				else if ("page".equals(name))
//					page = reader.nextInt();
//				else if ("pgSize".equals(name))
//					pgsize = reader.nextInt();
//				else if ("rootId".equals(name))
//					rootId = JHelper.nextString(reader);
//				else if ("sk".equals(name)) {
//					// only one of sk and smtcss should appear
//					sk = JHelper.nextString(reader);
//					if (stcs == null) // stcs can be load according to smtcss
//						stcs = DatasetCfg.getTreeSemtcs(sk);
//				}
//				else if ("smtcss".equals(name)) {
//					// only one of sk and smtcss should appear
//					JsonToken peek = reader.peek();
//					if (peek == JsonToken.BEGIN_ARRAY) {
////						reader.beginArray();
//						String[][] ss = JHelper.readStrss(reader);
////						reader.endArray();
//						if (ss != null && stcs == null) // stcs can be load according to sk
//							stcs = new TreeSemantics(ss);
//					}
//					else if (peek == JsonToken.NULL)
//						reader.nextNull();
//					else {
//						// What's this?
//						Utils.warn("Not handled: %s - %s - expecting array", name, reader.nextString());
//					}
//				}
//				else if ("sqlArgs".equals(name))
//					sqlArgs = JHelper.readStrs(reader);
//				else fromJsonName(name, reader);
//
//				token = reader.peek();
//			}
//			reader.endObject();
//		}
//		else throw new SemanticException("Parse DatasetReq failed. %s : %s", reader.getPath(), token.name());
//	}
}
