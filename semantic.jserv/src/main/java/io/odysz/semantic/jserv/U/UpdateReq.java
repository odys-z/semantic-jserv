package io.odysz.semantic.jserv.U;

import static io.odysz.semantic.jprotocol.JProtocol.CRUD.*;

import java.io.IOException;
import java.util.ArrayList;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import io.odysz.common.LangExt;
import io.odysz.semantic.jprotocol.JBody;
import io.odysz.semantic.jprotocol.JHeader;
import io.odysz.semantic.jprotocol.JHelper;
import io.odysz.semantic.jprotocol.JMessage;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.sql.Query.Ix;

/**Update Request Body<br>
 * 
 * @author odys-z@github.com
 */
public class UpdateReq extends JBody {
	/**Main table */
	String mtabl;
	/**Main table alias*/
	// String mAlias; // not support select insert yet
	
	/**nvs: [nv-obj],
	 * nv-obj: {n: "roleName", v: "admin"}
	 *  */
	ArrayList<Object[]> nvs;
	
	/**inserting values, used for "I". 3d array [[[n, v], ...]] */
	protected ArrayList<ArrayList<?>> nvss;
	/**inserting columns, used for "I".*/
	private String[] cols;

	/**where: [cond-obj], see {@link #joins}for cond-obj.*/
	ArrayList<String[]> where;
	
	ArrayList<UpdateReq> postUpds;
	
	public JHeader header;

	public UpdateReq(JMessage<? extends JBody> parent, String conn) {
		super(parent, conn);
	}

	public UpdateReq(JMessage<? extends JBody> parent, String conn, String maintbl, String cmd) {
		super(parent, conn);
		mtabl = maintbl;
		a = cmd;
	}
	
	public UpdateReq nv(String n, String v) {
		if (nvs == null)
			nvs = new ArrayList<Object[]>();
		String[] nv = new String[2];
		nv[Ix.nvn] = n;
		nv[Ix.nvv] = v;
		nvs.add(nv);
		return this;	
	}

	/** get columns for sql insert into (COLS) 
	 * @return columns
	 */
	public String[] cols() {
		return cols;
	}

	/** get values in VALUE-CLAUSE for sql insert into (...) values VALUE-CLAUSE 
	 * @return [[[n, v], ...]]
	 */
	public ArrayList<ArrayList<?>> values() {
		return nvss;
	}
	
	public UpdateReq where(String oper, String lop, String rop) {
		if (where == null)
			where = new ArrayList<String[]>();

		String[] predicate = new String[Ix.predicateSize];
		predicate[Ix.predicateOper] = oper;
		predicate[Ix.predicateL] = lop;
		predicate[Ix.predicateR] = rop;

		where.add(predicate);
		return this;
	}

	public UpdateReq post(UpdateReq pst) {
		if (postUpds == null)
			postUpds = new ArrayList<UpdateReq>();
		postUpds.add(pst);
		return this;
	}
	
	@Override
	public void toJson(JsonWriter writer) throws IOException, SemanticException {
		writer.beginObject();
		// design notes: keep consists with QueryReq
		writer.name("conn").value(conn)
			.name("a").value(a)
//			.name("mAlias").value(mAlias)
			.name("mtabl").value(mtabl) ;

		if (where != null) {
			writer.name("where");
			JHelper.writeLst(writer, where);
		}
		if (nvs != null) {
			writer.name("nvs");
			JHelper.writeLst(writer, nvs);
		}
		if (postUpds != null) {
			writer.name("postUpds")
				.beginArray();
			for (UpdateReq post : postUpds)
				post.toJson(writer);
			writer.endArray();
		}

		// for insert only
		if (nvss != null) {
			writer.name("nvss");
			JHelper.writeLst(writer, nvss);
		}
		if (cols != null) {
			writer.name("cols");
			JHelper.writeStrings(writer, cols);
		}
		
		writer.endObject();
	}

	/**FIXME Call for Anson 
	 * @param writer
	 * @throws SemanticException
	 * @throws IOException
	 */
	protected void child2Json(JsonWriter writer) throws SemanticException, IOException { }

	@Override
	public void fromJson(JsonReader reader) throws IOException, SemanticException {
		JsonToken token = reader.peek();
		if (token == JsonToken.BEGIN_OBJECT) {
			reader.beginObject();
			token = reader.peek();
			while (token != JsonToken.END_OBJECT) {
				String name = reader.nextName();
				fromJsonName(name, reader);
				token = reader.peek();
			}
			reader.endObject();
		}
		else throw new SemanticException("Parse QueryReq failed. %s : %s", reader.getPath(), token.name());
	}

	@SuppressWarnings("unchecked")
	protected void fromJsonName(String name, JsonReader reader)
			throws SemanticException, IOException {
		if ("a".equals(name))
			a = JHelper.nextString(reader);
		else if ("conn".equals(name))
			conn = JHelper.nextString(reader);
		else if ("mtabl".equals(name))
			mtabl = JHelper.nextString(reader);
		else if ("nvs".equals(name))
			nvs = (ArrayList<Object[]>) JHelper.readLstStrs(reader);
		else if ("where".equals(name))
			where = (ArrayList<String[]>) JHelper.readLstStrs(reader);
		else if ("postUpds".equals(name)) {
			postUpds = new ArrayList<UpdateReq>();
			reader.beginArray();
			while (reader.peek() != JsonToken.END_ARRAY) {
				// FIXME Call for Anson 
				// FIXME Call for Anson 
				// This is a strong prof that we need print type information into json.
				// When we are creating the deserialized object,
				// how do we know should it be UpdateReq or InsertReq?
				// The distinguish flag (a) currently is not readed now.
				UpdateReq post = new UpdateReq(null, conn, mtabl, null);
				post.fromJson(reader);
				postUpds.add(post);
			}
			reader.endArray();
		}
		// FIXME Calling child function? not possible with Gson.
		// else readChild(name, reader);
		else if ("nvss".equals(name))
			nvss = JHelper.readLstLstStrs(reader);
		else if ("cols".equals(name))
			cols = JHelper.readStrs(reader);
	}

//	protected void readChild(String name, JsonReader reader) throws SemanticException, IOException {}

	/**Update request validating.
	 * The request must is an update with pk and setting values;
	 * or is an insert with some inserting value.
	 * @param flag Not used in v1.0
	 * @throws SemanticException 
	 */
	public void validate(int ... flag) throws SemanticException {
		if (!D.equals(a) && (nvs == null || nvs.size() <= 0) && (nvss == null || nvss.size() <= 0))
			throw new SemanticException("Updating/inserting denied for empty column values");
		if ((U.equals(a) || D.equals(a)) && where == null || where.isEmpty())
				throw new SemanticException("Updatin/deleting  denied for empty conditions");
		if (!R.equals(a) && mtabl == null || LangExt.isblank(mtabl))
				throw new SemanticException("Updating/inserting/deleting denied for empty main table");
	}

}
