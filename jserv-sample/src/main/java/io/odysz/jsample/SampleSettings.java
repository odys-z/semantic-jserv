package io.odysz.jsample;

import static io.odysz.common.LangExt.f;
import static io.odysz.common.Utils.logi;

import java.io.FileNotFoundException;
import java.io.IOException;

import io.odysz.anson.Anson;
import io.odysz.common.FilenameUtils;

/**
 * @since 1.5.3
 */
public class SampleSettings extends Anson {

	public static SampleSettings load(String webinf, String settings_json)
			throws FileNotFoundException, IOException {
		return (SampleSettings) Anson.fromPath(FilenameUtils.concat(webinf, settings_json));
	}

	public String vol_name;
	public String conn;
	public String port;

	public static SampleSettings check(String webinf, String settings_json, boolean verbose)
			throws FileNotFoundException, IOException {

		logi("Loading settings: %s", settings_json);
		SampleSettings settings = SampleSettings.load(webinf, settings_json);
		logi("%s:\n%s\n%s", settings_json, settings.vol_name, settings.toString());

		logi("%s : %s", settings.vol_name, System.getProperty(settings.vol_name));
		return settings;
	}

	String jserv() {
		return f("http://localhost:%s", port);
	}

}
