package io.oz.jserv.dbsync;

import java.io.IOException;
import java.sql.SQLException;

import io.odysz.anson.AnsonField;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.ext.DocTableMeta;
import io.odysz.semantic.meta.SyntityMeta;
import io.odysz.semantic.syn.SynEntity;
import io.odysz.semantic.tier.docs.IFileDescriptor;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.sql.Insert;

/**
 * A synchronizable entity managed by the package, also a server side
 * and jprotocol oriented data record, used for record synchronizing
 * in docsync.jserv. 
 * 
 * @author Ody
 */
public class SynExtDoc extends SynEntity {

	@AnsonField(shortenString=true)
	public String uri;
	public String uri() { return uri; }

	public String syncFlag;

	/**
	 * A helper used to make sure query fields of Ext-entity are correct.
	 * @param meta
	 * @return cols for Select.cols()
	 */
	public static String[] nvCols(DocTableMeta meta) {
		return new String[] {
				meta.pk,
				meta.clientname,
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

	public SynExtDoc(AnResultset rs, DocTableMeta meta) throws SQLException {
		super(rs, meta);
		// this.entMeta = meta;
		// this.recId = rs.getString(meta.pk);
		this.uri = rs.getString(meta.uri);
		
		// this.clientpath =  rs.getString(meta.fullpath);
		// this.synode =  rs.getString(meta.synoder);
		
		// this.syncFlag = rs.getString(meta.syncflag);
	}

	/**
	 * @param d
	 * @param fullpath
	 * @param meta
	 * @throws IOException checking local file failed
	 * @throws SemanticException device is null
	 */
	public SynExtDoc(IFileDescriptor d, String fullpath, DocTableMeta meta) {
		super(meta);
		// this.synode = d.device();

		// this.entMeta = meta;
		this.recId = d.recId();
		this.uri = d.uri64();
	}

	@Override
	public Insert insertEntity(SyntityMeta m, Insert ins) {
		// TODO Auto-generated method stub
		return null;
	}
}