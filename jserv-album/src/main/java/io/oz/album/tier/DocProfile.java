package io.oz.album.tier;

import java.text.ParseException;
import java.util.Date;

import io.odysz.common.DateFormat;
import io.odysz.semantic.tier.docs.DocsReq;
import io.odysz.semantic.tier.docs.IFolderResolver;
import io.odysz.semantics.IUser;
import io.oz.jserv.sync.SynodeMode;

import static io.odysz.common.LangExt.isblank;
import static io.odysz.common.MimeTypes.*;
import static io.oz.jserv.sync.SynodeMode.*;

/**
 * Generate saving folder of files at synodes.
 * 
 * Null mime, videos and images are resolved to YYYY_MM of shareDate.
 * Others including audios are resolved to YYYY_MM of create date.
 *  
 * This class should be configured as Config.xml/k="docsync.folder-resolver".
 * 
 * @author odys-z@github.com
 *
 */
public class DocProfile implements IFolderResolver {

	private SynodeMode mode;

	public DocProfile(SynodeMode mode) {
		this.mode = mode;
	}
	
	@Override
	public String resolve(DocsReq req, IUser usr) {
		String cname = req.subFolder;
		if (this.mode == device)
			return cname;
		else
			try {
				if (isblank(req.mime) || image.is(req.mime) || video.is(req.mime))
					return DateFormat.formatYYmm(DateFormat.parse(req.shareDate));
				else
					return DateFormat.formatYYmm(DateFormat.parse(req.createDate));
			} catch (ParseException e) {
				return usr.deviceId() + "-" + DateFormat.formatYYmm(new Date());
			}
	}
}
