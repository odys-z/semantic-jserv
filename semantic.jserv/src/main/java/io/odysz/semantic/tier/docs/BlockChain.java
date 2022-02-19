package io.odysz.semantic.tier.docs;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import org.apache.commons.io_odysz.FilenameUtils;

import io.odysz.anson.x.AnsonException;
import io.odysz.common.AESHelper;
import io.odysz.common.EnvPath;

/**
 * Blocks stream, not that block chain:)
 * @author ody
 *
 */
public class BlockChain {

	public final String ssId;
	public final String clientpath;
	final String chainId;
	public String id() { return chainId; }

	protected final String extroot;
	protected final String outputPath;
	protected final OutputStream ofs;
	
	protected final DocsReq waitings;

	/**Save file to $VALUME_HOME/ssid/tablDoc/clientname
	 * 
	 * @param rootpath configured root path, e.g. resolved by {@link io.odysz.semantic.ShExtFile}
	 * @param ssId
	 * @param clientpath
	 * @throws IOException
	 */
	public BlockChain(String rootpath, String ssId, String clientpath) throws IOException {
		this.ssId = ssId;
		this.clientpath = clientpath;

		clientpath = clientpath.replaceFirst("^/", "");
		clientpath = clientpath.replaceAll(":", "");
		extroot = rootpath;

		// String clientname = FilenameUtils.getName(clientpath);
		outputPath = EnvPath.decodeUri(extroot, ssId, clientpath);
		String parentpath = FilenameUtils.getFullPath(outputPath);
		try { new File(parentpath).mkdirs(); }
		catch (Exception ex) { ex.printStackTrace(); }

		chainId = FilenameUtils.concat(extroot, ssId, clientpath);

		File f = new File(outputPath);
		f.createNewFile();
		this.ofs = new FileOutputStream(f);

		waitings = new DocsReq().blockSeq(-1);

	}

	public BlockChain appendBlock(DocsReq blockReq) throws IOException, AnsonException {
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
			throw new AnsonException(0, "Handling block's sequence error.");

		while (waitings.nextBlock != null && waitings.blockSeq + 1 == waitings.nextBlock.blockSeq) {
			ofs.write(AESHelper.decode64(waitings.nextBlock.uri64));

			waitings.blockSeq = waitings.nextBlock.blockSeq;
			waitings.nextBlock = waitings.nextBlock.nextBlock;
		}
		return this;
	}

	public void closeChain() throws IOException, InterruptedException, AnsonException {
		if (waitings.nextBlock != null)
			Thread.sleep(1000);

		ofs.close();
		if (waitings.nextBlock != null)
			// some packages lost
			throw new AnsonException(1, "Some packages lost. path: %s", clientpath);
	}
}
