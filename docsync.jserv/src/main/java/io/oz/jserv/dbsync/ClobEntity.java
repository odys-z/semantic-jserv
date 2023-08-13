package io.oz.jserv.dbsync;

import static io.odysz.common.LangExt.isblank;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.odysz.anson.AnsonField;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.ext.DocTableMeta;
import io.odysz.semantic.syn.DBSynsactBuilder;
import io.odysz.semantic.syn.SynEntity;
import io.odysz.semantic.syn.SynodeMode;
import io.odysz.semantics.IUser;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.sql.Statement;
import io.odysz.transact.sql.Update;
import io.odysz.transact.sql.parts.condition.Funcall;
import io.odysz.transact.x.TransException;
import io.oz.jserv.docsync.SynState;
import io.oz.jserv.docsync.SyncFlag.SyncEvent;
import io.oz.jserv.docsync.SyncRobot;

/**
 * A db entity record with a c-lob, managing pushing of c-lobs' sequence.
 * 
 * (Pulling is response and handled in file stream way)
 * 
 * @author odys-z@github.com
 *
 */
public class ClobEntity {

	public enum syntype { push, pull }

	public interface OnChainOk {
		/**
		 * {@link DBWorker} use this as a chance of update user's data
		 * when block chain finished successfully.
		 *
		 * @param mainst
		 * @param ent the entity created with {@link OnChainStart}.
		 * @param meta
		 * @param robot
		 * @return either the original post statement or a new one.
		 */
		Update onEnd(Statement<?> mainst, SynEntity ent, DocTableMeta meta, IUser robot);
	}

	public interface OnChainStart {
		/**
		 * {@link DBWorker} use this as a chance of creating user's data object
		 * when block chain started.
		 *
		 * @param post
		 * @param req
		 * @param robot
		 * @param meta
		 * @return either the original post statement or a new one.
		 */
		SynEntity onStart(DBSyncReq req, IUser robot, DocTableMeta meta);
	}

	public static final boolean verbose = true;

	@AnsonField(ignoreTo=true, ignoreFrom=true)
	DATranscxt st;

	static HashMap<String, Clobs> blockChains;

	@AnsonField(ignoreTo=true)
	DocTableMeta meta;

	public ClobEntity (DocTableMeta meta, DATranscxt deflst) {
		this.meta = meta;
		this.st = deflst;
	}

	DBSyncResp startChain(DBSyncReq body, IUser usr, OnChainStart entresolve)
			throws IOException, TransException, SQLException, InterruptedException {

		String conn = Connects.uri2conn(body.uri());
		checkDuplicate(conn, usr.deviceId(), body.clientpath, usr);

//		syntype typ = body.a().equals(A.pushClobStart) ? syntype.push : syntype.pull;
		SynEntity e = entresolve.onStart(body, usr, meta)
				// .synTask(new SynTask(typ))
				.check(conn, (DBSynsactBuilder) st, body.entSubscribes());

		if (blockChains == null)
			blockChains = new HashMap<String, Clobs>(2);

		// TODO deleting temp dir is handled by SyncRobot.
		String tempDir = ((SyncRobot)usr).touchTempDir(conn, meta.tbl);

		Clobs chain = new Clobs(tempDir, body.clientpath)
				.device(usr.deviceId())
				.entity(e);

		String id = chainId(usr, body);

		if (blockChains.containsKey(id) && !body.resetChain)
			throw new SemanticException("Block chain already exists, restarting?");
		else if (body.resetChain)
			abortBlock(body, usr);

		blockChains.put(id, chain);
		return new DBSyncResp()
				.blockSeq(-1)
				.entity(chain.entity)
				;
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
				.whereEq(meta.org(), usr.orgId())
				.whereEq(meta.synoder, device)
				.whereEq(meta.fullpath, clientpath)
				.rs(st.instancontxt(conn, usr))
				.rs(0);
		rs.beforeFirst().next();

		if (rs.getInt("cnt") > 0)
			throw new SemanticException("Found existing file for device %s, client path: %s",
					device, clientpath);
	}

	DBSyncResp uploadClob(DBSyncReq body, IUser usr) throws IOException, TransException {
		String id = chainId(usr, body);
		if (!blockChains.containsKey(id))
			throw new SemanticException("Uploading blocks must accessed after starting chain is confirmed.");

		Clobs chain = blockChains.get(id);
		chain.appendBlock(body);

		return new DBSyncResp()
				.blockSeq(body.blockSeq())
				.entity(chain.entity);
	}

	/**
	 * @param body
	 * @param usr for synode requires, it should be type SyncRobot
	 * @param ok
	 * @param synmode 
	 * @return response
	 * @throws SQLException
	 * @throws IOException
	 * @throws InterruptedException
	 * @throws TransException
	 */
	DBSyncResp endChain(DBSyncReq body, IUser usr, SynodeMode synmode, OnChainOk ok)
			throws SQLException, IOException, InterruptedException, TransException {
		String id = chainId(usr, body);
		if (blockChains.containsKey(id)) {
			blockChains.get(id).closeChain();

			Clobs chain = blockChains.remove(id);
			String conn = Connects.uri2conn(body.uri());
			Statement<?> stmt = null;
			if (st.exists(conn, meta.tbl, id))
				stmt = updatent(meta, st, synmode, conn, chain, usr);
			else
				stmt = creatent(meta, st, synmode, chain, usr);

			ok.onEnd(stmt, chain.entity, meta, usr);

			return new DBSyncResp()
				.blockSeq(body.blockSeq())
				.entity(chain.entity);
		} else
			throw new SemanticException("Block chain to be end doesn't exist.");
	}

	// TODO static for moving to DocTablemeta?
	static protected Statement<?> creatent(DocTableMeta m, DATranscxt st, SynodeMode synmode,
			Clobs chain, IUser usr) throws TransException {

		return st.insert(m.tbl, usr)
				// here is where the finite state machine used
				.nv("", "");
	}

	// TODO static for moving to DocTablemeta?
	static Statement<?> updatent(DocTableMeta m, DATranscxt st, SynodeMode mode, String conn,
			Clobs chain, IUser usr) throws TransException {

		SynState from = loadState(m, st, mode, chain);

		SyncEvent e = SyncEvent.pushJnodend;
		
		return st.update(m.tbl, usr)
				// here is where the finite state machine used
				.nv(m.syncflag, from.to(e).toString())
				.whereEq(m.synoder, chain.device)
				.whereEq(m.fullpath, chain.clientpath);
	}

	static SynState loadState(DocTableMeta met, DATranscxt st, SynodeMode mod, Clobs chain) {
		return new SynState(null, null);
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

	/**TODO
	 * TODO move to DocsChain
 	public static String createFile(DATranscxt st, String conn, SynEntity ent,
			DocTableMeta meta, IUser usr, OnChainOk end)
			throws TransException, SQLException, IOException {
		Update post = DBSynode.onEncreate(ent, meta, usr);

		if (end != null)
			post = end.onEncreate(post, ent, meta, usr);

		return DocUtils.createFileB64(conn, ent, usr, meta, st, post);
	}
	*/
}
