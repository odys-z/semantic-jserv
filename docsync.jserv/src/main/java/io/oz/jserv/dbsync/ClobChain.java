package io.oz.jserv.dbsync;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.odysz.anson.AnsonField;
import io.odysz.common.EnvPath;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.DASemantics.ShExtFilev2;
import io.odysz.semantic.DASemantics.smtype;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.ext.DocTableMeta;
import io.odysz.semantic.tier.docs.SyncDoc;
import io.odysz.semantics.ISemantext;
import io.odysz.semantics.IUser;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.sql.Update;
import io.odysz.transact.sql.parts.condition.Funcall;
import io.odysz.transact.x.TransException;
import io.oz.jserv.docsync.SyncRobot;

import static io.odysz.common.LangExt.*;

/**
 * C-lob chians, managing
 * @author Alice
 *
 */
public class ClobChain {

	public interface OnChainOk {
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
		Update onDocreate(Update post, SyncDoc d, DocTableMeta meta, IUser robot);
	}

	public static final boolean verbose = true;

	@AnsonField(ignoreTo=true, ignoreFrom=true)
	DATranscxt st;

	static HashMap<String, Clobs> blockChains;

	@AnsonField(ignoreTo=true)
	DocTableMeta meta;

	public ClobChain (DocTableMeta meta, DATranscxt deflst) {
		this.meta = meta;
		this.st = deflst;
	}

	DBSyncResp startBlocks(DBSyncReq body, IUser usr, IDBEntityResolver entresolve)
			throws IOException, TransException, SQLException, InterruptedException {

		String conn = Connects.uri2conn(body.uri());
		checkDuplicate(conn, usr.deviceId(), body.clientpath, usr);

		if (blockChains == null)
			blockChains = new HashMap<String, Clobs>(2);

		// TODO deleting temp dir is handled by SyncRobot.
		String tempDir = ((SyncRobot)usr).touchTempDir(conn, meta.tbl);

		Clobs chain = new Clobs(tempDir, body.clientpath)
				.device(usr.deviceId())
				.entity(entresolve.toEntity(body));

		String id = chainId(usr, body);

		if (blockChains.containsKey(id) && !body.resetChain)
			throw new SemanticException("Block chain already exists, restarting?");
		else if (body.resetChain)
			abortBlock(body, usr);

		blockChains.put(id, chain);
		return new DBSyncResp()
				.blockSeq(-1)
				.start(chain);
	}

	void checkDuplication(DBSyncReq body, SyncRobot usr)
			throws SemanticException, TransException, SQLException {
		String conn = Connects.uri2conn(body.uri());
		checkDuplicate(conn, usr.deviceId(), body.clientpath, usr);
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
				.whereEq(meta.synoder, device)
				.whereEq(meta.fullpath, clientpath)
				.rs(st.instancontxt(conn, usr))
				.rs(0);
		rs.beforeFirst().next();

		if (rs.getInt("cnt") > 0)
			throw new SemanticException("Found existing file for device %s, client path: %s",
					device, clientpath);
	}

	DBSyncResp uploadBlock(DBSyncReq body, IUser usr) throws IOException, TransException {
		String id = chainId(usr, body);
		if (!blockChains.containsKey(id))
			throw new SemanticException("Uploading blocks must accessed after starting chain is confirmed.");

		Clobs chain = blockChains.get(id);
		chain.appendBlock(body);

		return new DBSyncResp()
				.blockSeq(body.blockSeq())
				/*
				.doc((SyncDoc) new SyncDoc()
					.clientname(chain.clientname)
					.cdate(body.createDate)
					.fullpath(chain.clientpath));
				*/
				;
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
	DBSyncResp endBlock(DBSyncReq body, IUser usr, OnChainOk ok)
			throws SQLException, IOException, InterruptedException, TransException {
		String id = chainId(usr, body);
		Clobs chain;
		if (blockChains.containsKey(id)) {
			blockChains.get(id).closeChain();
			chain = blockChains.remove(id);
		} else
			throw new SemanticException("Block chain to be end doesn't exist.");

		return new DBSyncResp()
				.blockSeq(body.blockSeq())
				// .entity(entityResolver.onEndChain(chain))
				;
	}

	DBSyncResp abortBlock(DBSyncReq body, IUser usr)
			throws SQLException, IOException, InterruptedException, TransException {
		String id = chainId(usr, body);
		DBSyncResp ack = new DBSyncResp();
		if (blockChains.containsKey(id)) {
			blockChains.get(id).abortChain();
			blockChains.remove(id);
			ack.blockSeqReply = body.blockSeq();
		} else
			ack.blockSeqReply = -1;

		return ack;
	}

	public static String chainId(IUser usr, DBSyncReq req) {
		return Stream
			.of(usr.orgId(), usr.uid(), req.synode, req.clientpath)
			.collect(Collectors.joining("."));
	}

	/** TODO move to DocsChain
 	public static String createFile(DATranscxt st, String conn, SynEntity ent,
			DocTableMeta meta, IUser usr, OnChainOk end)
			throws TransException, SQLException, IOException {
		Update post = DBSynode.onEncreate(ent, meta, usr);

		if (end != null)
			post = end.onEncreate(post, ent, meta, usr);

		return DocUtils.createFileB64(conn, ent, usr, meta, st, post);
	}
	*/

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
	static String resolvExtroot(DATranscxt defltst, String conn, String docId, IUser usr, DocTableMeta meta)
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
