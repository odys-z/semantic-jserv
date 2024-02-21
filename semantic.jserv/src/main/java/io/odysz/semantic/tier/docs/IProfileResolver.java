package io.odysz.semantic.tier.docs;

import static io.odysz.common.LangExt.isblank;
import static io.odysz.common.MimeTypes.image;
import static io.odysz.common.MimeTypes.video;

import java.text.ParseException;
import java.util.Date;

import io.odysz.common.DateFormat;
import io.odysz.semantic.jprotocol.AnsonBody;
import io.odysz.semantics.IUser;

/**
 * <p>Resolving profiles like saving folder etc., e.g.</p>
 * 
 * <p>The implementation should be configured as Config.xml/k="docsync.folder-resolver".</p>
 * 
 * @author odys-z@github.com
 *
 */
public interface IProfileResolver {
	/**
	 * <p>Generate saving folder of files at synodes.</p>
	 * 
	 * If the req.subFolder is not provided, parse "yyyy_MM" as folder from file's
	 * sharing date or creating date;<br>
	 * or handle sharer on ending of chain push.<br>
	 * 
	 * @param reqBody
	 * @param usr
	 * @return
	 */
	default String synodeFolder(AnsonBody reqBody, IUser usr) {
		DocsReq req = ((DocsReq)reqBody);
		if (!isblank(req.subfolder))
			return req.subfolder;
		try {
			if (isblank(req.mime) || image.is(req.mime) || video.is(req.mime))
				return DateFormat.formatYYmm(DateFormat.parse(req.shareDate));
			else
				return DateFormat.formatYYmm(DateFormat.parse(req.createDate));
		} catch (ParseException e) {
			return usr.deviceId() + "-" + DateFormat.formatYYmm(new Date());
		}	
	}

	/**
	 * <p>Modify request on chain started.</p>
	 * 
	 * <p>Null mime, videos and images are resolved to YYYY_MM of shareDate.
	 * Others including audios are resolved to YYYY_MM of create date.</p>
	 *  
	 * @param req
	 * @param usr
	 * @return req itself
	 */
	DocsReq onStartPush(DocsReq req, IUser usr);
}
