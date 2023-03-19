package io.odysz.semantic.tier.docs;

import static io.odysz.common.LangExt.isNull;

import java.io.IOException;
import java.sql.SQLException;

import io.odysz.anson.Anson;
import io.odysz.anson.AnsonField;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.ext.DocTableMeta;
import io.odysz.semantics.ISemantext;
import io.odysz.semantics.x.SemanticException;

/**
 * A sync entity, server side and jprotocol oriented data record,
 * used for record synchronizing in docsync.jserv. 
 * 
 * @author ody
 */
public class SyncEntity extends Anson {
	protected static String[] synpageCols;

	public String recId;
	public String recId() { return recId; }
	public SyncEntity recId(String did) {
		recId = did;
		return this;
	}

	public String clientpath;
	public String fullpath() { return clientpath; }

	/** Non-public: doc' device id is managed by session. */
	protected String synode;
	public String synode() { return synode; }
	public SyncEntity synode(String synode) {
		this.synode = synode;
		return this;
	}

	@AnsonField(shortenString=true)
	public String uri;
	public String uri() { return uri; }

	public String syncFlag;

	@AnsonField(ignoreTo=true)
	DocTableMeta entMeta;

	@AnsonField(ignoreTo=true, ignoreFrom=true)
	ISemantext semantxt;
	
	public SyncEntity() {}
	
	/**
	 * A helper used to make sure query fields are correct.
	 * @param meta
	 * @return cols for Select.cols()
	 */
	public static String[] nvCols(DocTableMeta meta) {
		return new String[] {
				meta.pk,
				meta.resname,
				meta.uri,
				meta.createDate,
				meta.shareDate,
				meta.shareby,
				meta.shareflag,
				meta.syncflag,
				meta.mime,
				meta.fullpath,
				meta.synoder,
				meta.folder,
				meta.size
		};
	}
	
	/**
	 * @param meta
	 * @return String [meta.pk, meta.shareDate, meta.shareflag, meta.syncflag]
	 */
	public static String[] synPageCols(DocTableMeta meta) {
		if (synpageCols == null)
			synpageCols = new String[] {
					meta.pk,
					meta.synoder,
					meta.fullpath,
					meta.shareby,
					meta.shareDate,
					meta.shareflag,
					meta.syncflag
			};
		return synpageCols;
	}

	public SyncEntity(AnResultset rs, DocTableMeta meta) throws SQLException {
		this.entMeta = meta;
		this.recId = rs.getString(meta.pk);
		this.uri = rs.getString(meta.uri);
		
		this.clientpath =  rs.getString(meta.fullpath);
		this.synode =  rs.getString(meta.synoder);
		
		this.syncFlag = rs.getString(meta.syncflag);
	}

	/**
	 * @param d
	 * @param fullpath
	 * @param meta
	 * @throws IOException checking local file failed
	 * @throws SemanticException device is null
	 */
	public SyncEntity(IFileDescriptor d, String fullpath, DocTableMeta meta) {
		this.synode = d.device();

		this.entMeta = meta;
		this.recId = d.recId();
		this.uri = d.uri();
	}

	public SyncEntity parseChain(BlockChain chain) {
		synode = chain.device;
		clientpath = chain.clientpath;
		return this;
	}

	/**
	 * Parse {@link PathsPage#clientPaths}.
	 * 
	 * @param flags
	 * @return this
	 */
	public SyncEntity parseFlags(String[] flags) {
		if (!isNull(flags)) {
			syncFlag = flags[0];
		}
		return this;
	}
}