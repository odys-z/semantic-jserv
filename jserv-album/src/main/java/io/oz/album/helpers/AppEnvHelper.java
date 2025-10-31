package io.oz.album.helpers;

import static io.odysz.common.Utils.logi;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import io.oz.album.peer.PhotoRec;

import static io.odysz.common.LangExt.len;

/**
 * @deprecated Only for resolving the issue result from JDK auto-update on Ubuntu.
 * See <a href='https://odys-z.github.io/products/portfolio/synode/trouble.html#trouble-by-ubuntu-auto-update'>
 * the troubleshooting</a>. 
 */
public class AppEnvHelper {

	public static void main(String [] args) throws IOException, InterruptedException, TimeoutException {
		logi("[AppEnvHelper] Change POM for switch to App main().");
		logi("[AppEnvHelper]", System.getProperty("java.version"));
		logi("[AppEnvHelper]", System.getProperty("java.home"));
		if (len(args) > 0) {
			Exiftool.cmd = "exiftool";
			Exiftool.check();

			logi(args[0]);


			PhotoRec p = new PhotoRec();
			Exiftool.parseExif(p, args[0]);
			logi(p.toBlock());
		}
		
	}
}
