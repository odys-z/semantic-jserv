package io.odysz.semantic.jserv.file;

import io.odysz.semantic.jprotocol.AnsonBody;
import io.odysz.semantic.jprotocol.AnsonMsg;

public class FileReq extends AnsonBody {
	String file;
	int len;
	String payload;

	protected FileReq(AnsonMsg<? extends AnsonBody> parent, String filename) {
		super(parent, null);
		file = filename;
	}

	public String file() { return file; }
	
//	public FileReq out(String filepath) throws FileNotFoundException {
//		outs = new FileOutputStream(filepath);
//		return this;
//	}
	
//	public FileReq localFile(String dir, String suffix) throws FileNotFoundException {
//		String ext = FilenameUtils.getExtension(file);
//		String filepath = FilenameUtils.removeExtension(file);
//		filepath = String.format("%s %s.%s", filepath, suffix, ext);
//		filepath = FilenameUtils.concat(dir, filepath);
//		outs = new FileOutputStream(filepath + suffix);
//		return this;
//	}

//	@Override
//	public void toJson(JsonWriter writer, JOpts opts) throws IOException {
//		writer.beginObject();
//		writer.name("file").value(conn);
//		writer.name("a").value(a);
//		writer.name("len").value(len);
//
//		writer.name("payload");
//		if (payload != null)
//			writer.value(payload);
//		else {
//			if (opts.noNull)
//				writer.value("");
//			else
//				writer.nullValue();
//		}
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
//				if ("file".equals(name))
//					file = reader.nextString();
//				else if ("len".equals(name))
//					len = reader.nextInt();
//				else if ("payload".equals(name))
//					payload = reader.nextString();
//				else
//					// ignore conn, ...
//					reader.nextString();
//
//				token = reader.peek();
//			}
//			reader.endObject();
//		}
//	}

}
