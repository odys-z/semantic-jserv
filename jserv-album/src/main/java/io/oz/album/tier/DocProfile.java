package io.oz.album.tier;

import java.text.ParseException;
import java.util.Date;

import io.odysz.common.DateFormat;
import io.odysz.common.LangExt;
import io.odysz.semantic.jprotocol.AnsonBody;
import io.odysz.semantic.tier.docs.DocsReq;
import io.odysz.semantic.tier.docs.IProfileResolver;
import io.odysz.semantic.tier.docs.ShareFlag;
import io.odysz.semantics.IUser;
import io.oz.syn.SynodeMode;

import static io.odysz.common.LangExt.isblank;
import static io.odysz.common.MimeTypes.*;
import static io.oz.syn.SynodeMode.*;

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
public class DocProfile implements IProfileResolver {

	private SynodeMode mode;

	public DocProfile(SynodeMode mode) {
		this.mode = mode;
	}
	
	@Override
	public String synodeFolder(AnsonBody reqBody, IUser usr) {
		DocsReq req = ((DocsReq)reqBody);
		String cname = req.doc.folder();
		if (this.mode == nonsyn)
			return cname;
		else
			try {
				if (isblank(req.doc.mime) || image.is(req.doc.mime) || video.is(req.doc.mime))
					return DateFormat.formatYYmm(DateFormat.parse(req.doc.sharedate));
				else
					return DateFormat.formatYYmm(DateFormat.parse(req.doc.createDate));
			} catch (ParseException e) {
				return usr.deviceId() + "-" + DateFormat.formatYYmm(new Date());
			}
	}

	/**
	 * <ol>
	 * <li>ignore the request's sharer if it is come from a device (keep it for synode)</li>
	 * </ol>
	 * 
	 * @see IProfileResolver#onStartPush(DocsReq, IUser)
	 */
	@Override
	public DocsReq onStartPush(DocsReq req, IUser usr) {
		if (isDevice(req.uri()))
			req.doc.shareby(usr.uid());
		if (isblank(req.doc.shareflag))
			req.doc.shareflag = ShareFlag.publish.name();
		return req;
	}

	protected boolean isDevice(String uri) {
		return LangExt.endWith(uri, ".and", ".js", ".ts", ".c#", ".c++", ".py");
	}
}
