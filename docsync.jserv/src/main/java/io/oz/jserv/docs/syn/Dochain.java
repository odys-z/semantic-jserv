package io.oz.jserv.docs.syn;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.odysz.anson.AnsonField;
import io.odysz.common.EnvPath;
import io.odysz.common.Utils;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.DASemantics.ShExtFilev2;
import io.odysz.semantic.DASemantics.smtype;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.meta.ExpDocTableMeta;
import io.odysz.semantic.syn.DBSyntableBuilder;
import io.odysz.semantic.syn.SyncRobot;
import io.odysz.semantic.tier.docs.BlockChain;
import io.odysz.semantic.tier.docs.DocUtils;
import io.odysz.semantic.tier.docs.DocsReq;
import io.odysz.semantic.tier.docs.DocsResp;
import io.odysz.semantic.tier.docs.ExpSyncDoc;
import io.odysz.semantic.tier.docs.IProfileResolver;
import io.odysz.semantics.ISemantext;
import io.odysz.semantics.IUser;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.sql.Update;
import io.odysz.transact.sql.parts.condition.Funcall;
import io.odysz.transact.x.TransException;

import static io.odysz.common.LangExt.*;

public class Dochain {

	public interface OnChainOk {
		/**
		 * {@link Syntier} use this as a chance of update user's data
		 * when block chain finished successfully.
		 * 
		 * @param post
		 * @param d
		 * @param meta
		 * @param robot
		 * @return either the original post statement or a new one.
		 */
		Update onDocreate(Update post, ExpSyncDoc d, ExpDocTableMeta meta, IUser robot);
	}

	public static final boolean verbose = true;

	@AnsonField(ignoreTo=true, ignoreFrom=true)
	DATranscxt st;

	static HashMap<String, BlockChain> blockChains;

	@AnsonField(ignoreTo=true)
	ExpDocTableMeta meta;
	
	public Dochain (ExpDocTableMeta meta, DATranscxt deflst) {
		this.meta = meta;
		this.st = deflst;
	}

	DocsResp startBlocks(DocsReq body, IUser usr, IProfileResolver profiles)
			throws IOException, TransException, SQLException, InterruptedException {

		String conn = Connects.uri2conn(body.uri());
		checkDuplicate(conn, usr.deviceId(), body.doc.clientpath, usr);

		if (blockChains == null)
			blockChains = new HashMap<String, BlockChain>(2);

		// in jserv 1.4.3 and album 0.5.2, deleting temp dir is handled by SyncRobot. 
		String tempDir = ((SyncRobot)usr).touchTempDir(conn, meta.tbl);

		String saveFolder = profiles.synodeFolder(body, usr);
		if (isblank(saveFolder, "/", "\\\\", ":", "\\."))
			throw new SemanticException("Can not resolve saving folder for doc %s, user %s, with resolver %s",
					body.doc.clientpath, usr.uid(), profiles.getClass().getName());
		
//		BlockChain chain = new BlockChain(body.docTabl, tempDir, body.device().id,
//					body.doc.clientpath, body.doc.createDate, saveFolder)
//				.device(usr.deviceId())
//				.share(body.doc.shareby, body.doc.sharedate, body.doc.shareflag);
		BlockChain chain = new BlockChain(body.docTabl, tempDir, body.device().id, body.doc);

		String id = chainId(usr, body);

		if (blockChains.containsKey(id) && !body.reset)
			throw new SemanticException("Block chain already exists, restarting?");
		else if (body.reset)
			abortBlock(body, usr);

		blockChains.put(id, chain);
		return new DocsResp()
				.blockSeq(-1)
				/*
				.doc((ExpSyncDoc) new ExpSyncDoc()
					.clientname(chain.clientname)
					.cdate(body.doc.createDate)
					.fullpath(chain.clientpath));
				*/
				.doc(chain.doc.uri64(null));
	}

	void checkDuplication(DocsReq body, SyncRobot usr)
			throws SemanticException, TransException, SQLException {
		String conn = Connects.uri2conn(body.uri());
		checkDuplicate(conn, usr.deviceId(), body.doc.clientpath, usr);
	}

	void checkDuplicate(String conn, String device, String clientpath, IUser usr)
			throws SemanticException, TransException, SQLException {
		if (isblank(usr.orgId()))
			throw new SemanticException("Can't delete doc without user's org id. device %s, client path: %s",
					device, clientpath);

		AnResultset rs = (AnResultset) st
				.select(meta.tbl, "p")
				.col(Funcall.count(meta.pk), "cnt")
				.whereEq(meta.org, usr.orgId())
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
		String id = chainId(usr, body);
		if (!blockChains.containsKey(id))
			throw new SemanticException("Uploading blocks must accessed after starting chain is confirmed.");

		BlockChain chain = blockChains.get(id);
		chain.appendBlock(body);

		return new DocsResp()
				.blockSeq(body.blockSeq())
				.doc((ExpSyncDoc) new ExpSyncDoc()
					.clientname(chain.doc.clientname())
					.cdate(body.doc.createDate)
					.fullpath(chain.doc.clientpath));
	}

	/**
	 * @param body
	 * @param usr for synode requires, it should be type SyncRobot
	 * @param ok
	 * @return response
	 * @throws SQLException
	 * @throws IOException
	 * @throws InterruptedException
	 * @throws TransException
	 */
	DocsResp endBlock(DocsReq body, IUser usr, OnChainOk ok)
			throws SQLException, IOException, InterruptedException, TransException {
		String id = chainId(usr, body);
		BlockChain chain;
		if (blockChains.containsKey(id)) {
			blockChains.get(id).closeChain();
			chain = blockChains.remove(id);
		} else
			throw new SemanticException("Block chain to be end doesn't exist.");

		// insert photo (empty uri)
		String conn = Connects.uri2conn(body.uri());
		// ExpSyncDoc photo = new ExpSyncDoc().createByChain(chain);
		ExpSyncDoc photo = chain.doc;
		photo.uri64 = null; // suppress semantics ExtFile, and support me (query befor move?).
		
		String pid = createFile(st, conn, photo, meta, usr, ok);

		// move file
		String targetPath = resolvExtroot(st, conn, pid, usr, meta);
		if (verbose)
			Utils.logi("Dochain#endBlock(): %s\n-> %s", chain.outputPath, targetPath);
		Files.move(Paths.get(chain.outputPath), Paths.get(targetPath), StandardCopyOption.REPLACE_EXISTING);

		return new DocsResp()
				.blockSeq(body.blockSeq())
				.doc(photo.recId(pid));
	}

	DocsResp abortBlock(DocsReq body, IUser usr)
			throws SQLException, IOException, InterruptedException, TransException {
		String id = chainId(usr, body);
		DocsResp ack = new DocsResp();
		if (blockChains.containsKey(id)) {
			blockChains.get(id).abortChain();
			blockChains.remove(id);
			ack.blockSeqReply = body.blockSeq();
		} else
			ack.blockSeqReply = -1;

		return ack;
	}

	public static String chainId(IUser usr, DocsReq req) {
		return Stream
			.of(usr.orgId(), usr.uid(), req.device().id, req.doc.clientpath)
			.collect(Collectors.joining("."));
	}

	/**
	 * Create doc record with local file.
	 * 
	 * @param st
	 * @param conn
	 * @param photo
	 * @param meta
	 * @param usr
	 * @param end
	 * @return doc id, e.g. pid
	 * @throws TransException
	 * @throws SQLException
	 * @throws IOException
	 */
	public static String createFile(DATranscxt st, String conn, ExpSyncDoc photo,
			ExpDocTableMeta meta, IUser usr, OnChainOk end)
			throws TransException, SQLException, IOException {
		Update post = null; // Docsyncer.onDocreate(photo, meta, usr);

		if (end != null)
			post = end.onDocreate(post, photo, meta, usr);

		return DocUtils.createFileBy64((DBSyntableBuilder)st, conn, photo, usr, meta, post);
	}

//	public static String createFile(DATranscxt st, String conn, ExpSyncDoc photo,
//			DocTableMeta meta, IUser usr, OnChainOk end)
//			throws TransException, SQLException, IOException {
//		Update post = null; // Docsyncer.onDocreate(photo, meta, usr);
//
//		if (end != null)
//			post = end.onDocreate(post, photo, meta, usr);
//
//		return DocUtils.createFileB64(st, conn, photo, usr, meta, post);
//	}


	/**
	 * Resolve file root with samantics handler of {@link smtype#extFilev2}.
	 * 
	 * @param defltst
	 * @param conn
	 * @param docId
	 * @param usr
	 * @param meta
	 * @return root resolved by {@link smtype#extFilev2}
	 * @throws TransException
	 * @throws SQLException
	 */
	static String resolvExtroot(DATranscxt defltst, String conn, String docId, IUser usr, ExpDocTableMeta meta)
			throws TransException, SQLException {
		ISemantext stx = defltst.instancontxt(conn, usr);
		AnResultset rs = (AnResultset) defltst
				.select(meta.tbl)
				.col("uri").col("folder")
				.whereEq("pid", docId).rs(stx)
				.rs(0);

		if (!rs.next())
			throw new SemanticException("Can't find file for id: %s (permission of %s)", docId, usr.uid());

		String extroot = ((ShExtFilev2) DATranscxt.getHandler(conn, meta.tbl, smtype.extFilev2)).getFileRoot();
		return EnvPath.decodeUri(extroot, rs.getString("uri"));
	}
}
