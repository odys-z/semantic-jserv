package io.oz.jserv.docsync;
//package io.odysz.semantic.tier.docs;
//
//import java.io.IOException;
//import java.nio.file.Files;
//import java.nio.file.Paths;
//import java.nio.file.attribute.FileTime;
//
//import io.odysz.anson.Anson;
//import io.odysz.common.DateFormat;
//
///**
// * <p>Query results of synchronizing data.</p>
// * 
// * <b>Note:</b>
// * This is only used for client, e.g. Android, querying information of file synchronizing.
// * For file synchronizing task description, see DocsyncResp
// * 
// * <p>To query a file's synchronizing state, without client DB, the way to match
// * a file at server side is match device name and client path. So no db record Id
// * can be used here. </p>
// * @author ody
// *
// */
//public class SyncRec extends Anson implements IFileDescriptor {
//
//	String docId;
//	String device;
//	String clientpath;
//
//	String mime;
//	String uri;
//	String cdate;
//	String filename;
//
//	public SyncRec() { }
//
//	public SyncRec(IFileDescriptor p) {
//		this.clientpath = p.fullpath();
//		this.filename = p.clientname();
//		this.cdate = p.cdate();
//	}
//
//	@Override
//	public String fullpath() {
//		return clientpath;
//	}
//
//	@Override
//	public IFileDescriptor fullpath(String clientpath) throws IOException {
//		this.clientpath = clientpath;
//		cdate = DateFormat.formatime((FileTime) Files.getAttribute(Paths.get(clientpath), "creationTime"));
//		return this;
//	}
//
//	@Override
//	public String clientname() {
//		return filename;
//	}
//
//	@Override
//	public String cdate() { return cdate; }
//
//	@Override
//	public String recId() {
//		return docId;
//	}
//
//	@Override
//	public String mime() {
//		return mime;
//	}
//
//	@Override
//	public String device() {
//		return device;
//	}
//
//	@Override
//	public String uri() {
//		return uri;
//	}
//}
