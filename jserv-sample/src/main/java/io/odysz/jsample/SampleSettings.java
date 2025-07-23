package io.odysz.jsample;

import static io.odysz.common.Utils.logi;

/**
 * @since 1.5.3
 */
public class SampleSettings {

	public static SampleSettings load(String webinf, String settings_json) {
		// TODO Auto-generated method stub
		return null;
	}

	public String vol_name;
	public String conn;

	public static SampleSettings check(String webinf, String settings_json, boolean verbose) {

		logi("Loading settings: %s", settings_json);
		SampleSettings settings = SampleSettings.load(webinf, settings_json);
		logi("%s:\n%s\n%s", settings_json, settings.vol_name, settings.toString());

		logi("%s : %s", settings.vol_name, System.getProperty(settings.vol_name));
		return null;
	}

	public String jserv() {
		return null;
	}

}
