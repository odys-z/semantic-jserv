package io.oz.jserv.dbsync;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.io_odysz.FilenameUtils;

import io.odysz.common.AESHelper;
import io.odysz.common.EnvPath;
import io.odysz.common.LangExt;
import io.odysz.semantic.SynEntity;
import io.odysz.transact.x.TransException;

/**
 * Blocks stream, not that block chain:)
 *
 * <p>Block chain is not guarded with file read-write lock as it's not visible to others
 * until the database is updated with the path.</p>
 *
 * @author ody
 */
public class Clobs {

	public final String clientpath;
	public final String clientname;

	/** Local file for writing clob */
	public final String outputPath;
	/** Local file stream for writing clob */
	protected final OutputStream ofs;

	protected final DBSyncReq waitings;

	String device;

	SynEntity entity;

	/**Create file output stream to $VALUME_HOME/userid/ssid/clientpath
	 *
	 * @param tempDir
	 * @param clientpathRaw - client path that can match at client's environment (saving at server side replaced some special characters)
	 * @throws IOException
	 * @throws TransException
	 */
	public Clobs(String tempDir, String clientpathRaw) throws IOException, TransException {

		if (LangExt.isblank(clientpathRaw))
			throw new TransException("Client path is neccessary to start a block chain transaction.");
		this.clientpath = clientpathRaw;

		String clientpath = clientpathRaw.replaceFirst("^/", "");
		clientpath = clientpath.replaceAll(":", "");

		clientname = FilenameUtils.getName(clientpath);
		outputPath = EnvPath.decodeUri(tempDir, clientpath);

		String parentpath = FilenameUtils.getFullPath(outputPath);
		new File(parentpath).mkdirs();

		File f = new File(outputPath);
		f.createNewFile();
		this.ofs = new FileOutputStream(f);

		waitings = new DBSyncReq(null, "", "m.tbl").blockSeq(-1);
	}

	public Clobs appendBlock(DBSyncReq blockReq) throws IOException, TransException {
		DBSyncReq pre = waitings;
		DBSyncReq nxt = waitings.nextBlock;

		while (nxt != null && nxt.blockSeq < blockReq.blockSeq) {
				pre = nxt;
				nxt = nxt.nextBlock;
		}
		pre.nextBlock = blockReq;
		blockReq.nextBlock = nxt;

		// assertNotNull(ofs); makes out going stream in trouble?
		if (ofs == null) throw new IOException("Output stream broken!");
		if (waitings.nextBlock != null && waitings.blockSeq >= waitings.nextBlock.blockSeq)
			throw new TransException("Handling block's sequence error.");

		while (waitings.nextBlock != null && waitings.blockSeq + 1 == waitings.nextBlock.blockSeq) {
			ofs.write(AESHelper.decode64(waitings.nextBlock.uri64));

			waitings.blockSeq = waitings.nextBlock.blockSeq;
			waitings.nextBlock = waitings.nextBlock.nextBlock;
		}
		return this;
	}

	public void abortChain() throws IOException, InterruptedException, TransException {
		if (waitings.nextBlock != null)
			Thread.sleep(1000);

		ofs.close();

		try { Files.delete(Paths.get(outputPath)); }
		catch (IOException e) { e.printStackTrace(); }

		if (waitings.nextBlock != null)
			// some packages lost
			throw new TransException("Some packages lost. path: %s", clientpath);
	}

	public String closeChain() throws IOException, InterruptedException, TransException {
		if (waitings.nextBlock != null)
			Thread.sleep(1000);

		ofs.close();

		if (waitings.nextBlock != null) {
			Path p = Paths.get(outputPath);
			try { Files.delete(p); }
			catch (IOException e) { e.printStackTrace(); }

			// some packages lost
			throw new TransException("Some packages lost. path: %s", clientpath);
		}

		return outputPath;
	}

	public Clobs device(String device) {
		this.device = device;
		return this;
	}

	public Clobs entity(SynEntity entity) {
		this.entity = entity;
		return this;
	}

	public SynEntity parseEntity() {
		SynEntity e = new SynEntity();
		return e;
	}
}
