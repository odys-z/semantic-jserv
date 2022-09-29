package io.oz.jserv.sync;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.util.HashMap;

import io.odysz.anson.AnsonField;
import io.odysz.common.EnvPath;
import io.odysz.common.Utils;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.DASemantics.ShExtFile;
import io.odysz.semantic.DASemantics.smtype;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.ext.DocTableMeta;
import io.odysz.semantic.tier.docs.BlockChain;
import io.odysz.semantic.tier.docs.DocUtils;
import io.odysz.semantic.tier.docs.DocsReq;
import io.odysz.semantic.tier.docs.DocsResp;
import io.odysz.semantic.tier.docs.SyncDoc;
import io.odysz.semantics.ISemantext;
import io.odysz.semantics.IUser;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.sql.Update;
import io.odysz.transact.sql.parts.condition.Funcall;
import io.odysz.transact.x.TransException;

public class Dochain {

	public interface IOnChainOk {
		/**
		 * {@link Docsyncer} use this as a chance of update user's data
		 * when block chain finished successfully.
		 * 
		 * @param post
		 * @param d
		 * @param meta
		 * @param robot
		 * @return either the original post statement or a new one.
		 */
		Update onDocreate(Update post, SyncDoc d, DocTableMeta meta, SyncRobot robot);
	}

	public static final boolean verbose = true;

	DATranscxt st;

	static HashMap<String, BlockChain> blockChains;

	@AnsonField(ignoreTo=true)
	DocTableMeta meta;
	
	public Dochain (DocTableMeta meta, DATranscxt deflst) {
		this.meta = meta;
		this.st = deflst;
	}

	DocsResp startBlocks(DocsReq body, IUser usr) throws IOException, TransException, SQLException {
		String conn = Connects.uri2conn(body.uri());
		checkDuplicate(conn, usr.deviceId(), body.clientpath, usr);

		if (blockChains == null)
			blockChains = new HashMap<String, BlockChain>(2);

		// in jserv 1.4.3 and album 0.5.2, deleting temp dir is handled by PhotoRobot. 
		String tempDir = ((SyncRobot)usr).touchTempDir(conn, meta.tbl);

		BlockChain chain = new BlockChain(tempDir, body.clientpath, body.createDate, body.subFolder)
				.share(body.shareby, body.shareDate, body.shareflag);

		String id = body.device() + " " + chain.clientpath;

		if (blockChains.containsKey(id))
			throw new SemanticException("Why started again?");

		blockChains.put(id, chain);
		return new DocsResp()
				.blockSeq(-1)
				.clientname(chain.clientname)
				.fullpath(chain.clientpath)
				.cdate(body.createDate);
	}

	void checkDuplication(DocsReq body, SyncRobot usr)
			throws SemanticException, TransException, SQLException {
		String conn = Connects.uri2conn(body.uri());
		checkDuplicate(conn, usr.deviceId(), body.clientpath, usr);
	}


	private void checkDuplicate(String conn, String device, String clientpath, IUser usr)
			throws SemanticException, TransException, SQLException {
		AnResultset rs = (AnResultset) st
				.select(meta.tbl, "p")
				.col(Funcall.count(meta.pk), "cnt")
				.whereEq(meta.device, device)
				.whereEq(meta.fullpath, clientpath)
				.rs(st.instancontxt(conn, usr))
				.rs(0);
		rs.beforeFirst().next();

		if (rs.getInt("cnt") > 0)
			throw new SemanticException("Found existing file for device %s, client path: %s",
					device, clientpath);
	}

	
	DocsResp uploadBlock(DocsReq body, IUser usr) throws IOException, TransException {
		// String id = body.chainId();
		// String id = chainId(usr, body.clientpath);

		String id = body.device() + " " + body.clientpath;
		if (!blockChains.containsKey(id))
			throw new SemanticException("Uploading blocks must accessed after starting chain is confirmed.");

		BlockChain chain = blockChains.get(id);
		chain.appendBlock(body);

		return new DocsResp()
				.blockSeq(body.blockSeq())
				.clientname(chain.clientname)
				.fullpath(chain.clientpath)
				.cdate(body.createDate);
	}

	
	DocsResp endBlock(DocsReq body, SyncRobot usr)
			throws SQLException, IOException, InterruptedException, TransException {
		// String id = chainId(usr, body.clientpath);

		String id = body.device() + " " + body.clientpath;
		BlockChain chain;
		if (blockChains.containsKey(id)) {
			blockChains.get(id).closeChain();
			chain = blockChains.remove(id);
		} else
			throw new SemanticException("Block chain to be end doesn't exist.");

		// insert photo (empty uri)
		String conn = Connects.uri2conn(body.uri());
		// DocTableMeta meta = new DocTableMeta(conn);
		SyncDoc photo = new SyncDoc();

		photo.createDate = chain.cdate;
		// Exif.parseExif(photo, chain.outputPath);

		photo.clientpath = chain.clientpath;
		photo.device = usr.device();
		photo.pname = chain.clientname;
		photo.folder(chain.saveFolder);
		photo.shareby = chain.shareby;
		photo.sharedate = chain.shareDate;
		photo.shareflag = chain.shareflag;

		photo.uri = null;
		String pid = createFile(st, conn, photo, meta, usr, 
				(Update post, SyncDoc f, DocTableMeta meta, SyncRobot robot) -> {
					return null;
				});

		// move file
		String targetPath = resolvExtroot(st, conn, pid, usr, meta);
		if (verbose)
			Utils.logi("   %s\n-> %s", chain.outputPath, targetPath);
		Files.move(Paths.get(chain.outputPath), Paths.get(targetPath), StandardCopyOption.REPLACE_EXISTING);

		return new DocsResp()
				.recId(pid)
				.blockSeq(body.blockSeq())
				.clientname(chain.clientname)
				.fullpath(chain.clientpath)
				.cdate(body.createDate);
	}
	
	DocsResp abortBlock(DocsReq body, IUser usr)
			throws SQLException, IOException, InterruptedException, TransException {
		// String id = body.chainId();
		// String id = chainId(usr, body.clientpath);
		String id = body.device() + " " + body.clientpath;
		DocsResp ack = new DocsResp();
		if (blockChains.containsKey(id)) {
			blockChains.get(id).abortChain();
			blockChains.remove(id);
			ack.blockSeqReply = body.blockSeq();
		} else
			ack.blockSeqReply = -1;

		return ack;
	}

	
//	private String chainId(IUser usr, String clientpathRaw) {
//		return usr.sessionId() + " " + clientpathRaw;
//	}


	public static String createFile(DATranscxt st, String conn, SyncDoc photo,
			DocTableMeta meta, SyncRobot usr, IOnChainOk end)
			throws TransException, SQLException, IOException {
		
		// DocTableMeta meta = new DocTableMeta(conn);

		Update post = Docsyncer.onDocreate(photo, meta, usr);
		if (end != null)
			post = end.onDocreate(post, photo, meta, usr);

		String pid = DocUtils.createFile(conn, photo, usr, meta, st, post);

		return pid;
	}


	static String resolvExtroot(DATranscxt defltst, String conn, String docId, IUser usr, DocTableMeta meta) throws TransException, SQLException {
		ISemantext stx = defltst.instancontxt(conn, usr);
		AnResultset rs = (AnResultset) defltst
				.select(meta.tbl)
				.col("uri").col("folder")
				.whereEq("pid", docId).rs(stx)
				.rs(0);

		if (!rs.next())
			throw new SemanticException("Can't find file for id: %s (permission of %s)", docId, usr.uid());

		String extroot = ((ShExtFile) DATranscxt.getHandler(conn, meta.tbl, smtype.extFile)).getFileRoot();
		return EnvPath.decodeUri(extroot, rs.getString("uri"));
	}
	
}
