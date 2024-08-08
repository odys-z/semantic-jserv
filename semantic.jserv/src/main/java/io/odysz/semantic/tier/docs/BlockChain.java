package io.odysz.semantic.tier.docs;

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
import io.odysz.transact.x.TransException;

/**
 * Blocks stream, not that block chain:)
 * 
 * <p>Block chain is not guarded with file read-write lock as it's not visible to others
 * until the database is updated with the path.</p>
 * 
 * @author ody
 */
public class BlockChain {

//	public final String saveFolder;
//	public final String clientpath;
//	public final String clientname;
//	public String cdate;

	public final String outputPath;
	protected final OutputStream ofs;
	
	protected final DocsReq waitings;

//	public String shareby;
//	public String shareDate;
//	public String shareflag;
//	public String device;

	public final String docTabl;

	public ExpSyncDoc doc;
	public BlockChain doc(ExpSyncDoc doc) {
		this.doc = doc;
		return this;
	}

	/**
	 * Port to DB-sync
	 * 
	 * @param tempDir
	 * @param saveFolder
	 * @param clientpath
	 * @throws IOException
	 */
//	public BlockChain(String docTabl, String tempDir, String saveFolder, String clientpath) throws IOException {
//
//		this.docTabl = docTabl;
//		this.saveFolder = saveFolder;
//		this.clientpath = clientpath;
//		clientname = FilenameUtils.getName(clientpath);
//		outputPath = EnvPath.decodeUri(tempDir, saveFolder, clientname);
//
//		String parentpath = FilenameUtils.getFullPath(outputPath);
//		new File(parentpath).mkdirs(); 
//
//		File f = new File(outputPath);
//		f.createNewFile();
//		this.ofs = new FileOutputStream(f);
//
//		waitings = new DocsReq().blockSeq(-1);
//	}

	/**
	 * @deprecated
	 * Create file output stream to $VALUME_HOME/userid/ssid/clientpath
	 * 
	 * @param tempDir
	 * @param clientpathRaw - client path that can match at client's environment (saving at server side replaced some special characters)
	 * @param createDate 
	 * @param targetFolder the file should finally saved to this sub folder (specified by client) 
	 * @throws IOException
	 * @throws TransException 
	public BlockChain(String docTabl, String tempDir, String devid,
			String clientpathRaw, String createDate, String targetFolder)
			throws IOException, TransException {

		if (LangExt.isblank(clientpathRaw))
			throw new TransException("Client path is neccessary to start a block chain transaction.");

		this.docTabl = docTabl;
		// this.cdate = createDate;
		// this.clientpath = clientpathRaw;
		// this.saveFolder = targetFolder;
		// this.device = devid;

		String clientpath = clientpathRaw.replaceFirst("^/", "");
		clientpath = clientpath.replaceAll(":", "");

		// clientname = FilenameUtils.getName(clientpath);
		outputPath = EnvPath.decodeUri(tempDir, clientpath);

		String parentpath = FilenameUtils.getFullPath(outputPath);
		new File(parentpath).mkdirs(); 

		File f = new File(outputPath);
		f.createNewFile();
		this.ofs = new FileOutputStream(f);

		waitings = new DocsReq().blockSeq(-1);
	}
	 */

	/**
	 * 
	 * @param docTabl
	 * @param tempDir
	 * @param devid
	 * @param doc
	 * @throws IOException
	 */
	public BlockChain(String docTabl, String tempDir, String devid, ExpSyncDoc doc) throws IOException {
		// doc.clientpath, body.doc.createDate, body.doc.folder()
		this.docTabl = docTabl;
		String clientpath = doc.clientpath.replaceFirst("^/", "");
		clientpath = clientpath.replaceAll(":", "");

		outputPath = EnvPath.decodeUri(tempDir, clientpath);

		String parentpath = FilenameUtils.getFullPath(outputPath);
		new File(parentpath).mkdirs(); 

		File f = new File(outputPath);
		f.createNewFile();
		this.ofs = new FileOutputStream(f);

		waitings = new DocsReq().blockSeq(-1);
		
		this.doc = doc;
	}

	public BlockChain appendBlock(DocsReq blockReq) throws IOException, TransException {
		DocsReq pre = waitings;
		DocsReq nxt = waitings.nextBlock;

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
			ofs.write(AESHelper.decode64(waitings.nextBlock.doc.uri64));

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
			throw new TransException("Aborting block chain. " + 
					"Blocks starting at block-seq = %s will be dropped. path: %s",
					waitings.nextBlock.blockSeq, doc.clientpath);
	}

	public String closeChain() throws IOException, TransException {
		if (waitings.nextBlock != null)
			try { Thread.sleep(1000);
			} catch (InterruptedException e1) { }

		ofs.close();

		if (waitings.nextBlock != null) {
			Path p = Paths.get(outputPath);
			try { Files.delete(p); }
			catch (IOException e) { e.printStackTrace(); }
			// some packages lost
			throw new TransException("Closing block chain. " +
					"Blocks starting at block-seq = %s will be dropped. path: %s",
					waitings.nextBlock.blockSeq, doc.clientpath);
		}

		return outputPath;
	}

//	public BlockChain share(String shareby, String shareDate, String shareflag) {
//		this.shareby = shareby;
//		this.shareDate = shareDate;
//		this.shareflag = shareflag;
//		return this;
//	}

//	public BlockChain device(String device) {
//		this.device = device;
//		return this;
//	}
}
