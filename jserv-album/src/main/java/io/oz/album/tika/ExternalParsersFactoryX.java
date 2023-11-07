package io.oz.album.tika;

import static io.odysz.common.LangExt.eq;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.apache.commons.io_odysz.FilenameUtils;
import org.apache.tika.exception.TikaException;
import org.apache.tika.parser.external.ExternalParser;
import org.apache.tika.parser.external.ExternalParsersFactory;

import io.odysz.common.Configs;
import io.odysz.common.Configs.keys;
import io.odysz.common.Utils;

public class ExternalParsersFactoryX extends ExternalParsersFactory {
	static String wkpath = null;
	
	public static void workDir(String workpath) {
		wkpath = workpath;
	}

    public static List<ExternalParser> create() throws IOException, TikaException {
    	if (eq("windows", Configs.getCfg(keys.fileSys))) {
    		if (wkpath == null)
    			throw new IOException("For windows, the Tika patch requires knowing work path (call workDir(p)) first to work.");
    			
			String filepath = FilenameUtils.concat(
					wkpath, Configs.getCfg(keys.tika_ex_parser_win));

			filepath = new File(filepath).getAbsolutePath();
			Utils.logi(filepath);

			return create(new File(filepath).toURI().toURL());
    	}
    	else return ExternalParsersFactory.create();
    }
}
