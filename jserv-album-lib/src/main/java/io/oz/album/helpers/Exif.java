package io.oz.album.helpers;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Date;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.sax.BodyContentHandler;

import io.odysz.common.DateFormat;
import io.oz.album.tier.Photo;

public class Exif {
	static String geox0 = "0";
	static String geoy0 = "0";
	
	public static void geoxy0(int x, int y) {
		geox0 = String.valueOf(x);
		geoy0 = String.valueOf(y);
	}

	public static Photo parseExif(Photo photo, String filepath) {
		try (FileInputStream stream = new FileInputStream(new File(filepath))) {
			BodyContentHandler handler = new BodyContentHandler();
			AutoDetectParser parser = new AutoDetectParser();
			Metadata metadata = new Metadata();

			photo.exif = new ArrayList<String>();
			parser.parse(stream, handler, metadata);
			for (String name: metadata.names()) {
				String exif = metadata.get(name);
				photo.exif.add(name + ":" +
							(exif == null ? "null" : exif.trim().replace("\n", "\\n")));
			}

			Date d = metadata.getDate(TikaCoreProperties.CREATED);
			photo.createDate = DateFormat.formatime(d);
			photo.month(d);

			photo.geox = metadata.get(TikaCoreProperties.LANGUAGE);
			if (photo.geox == null) photo.geox = geox0;

			photo.geoy = metadata.get(TikaCoreProperties.LATITUDE);
			if (photo.geoy == null) photo.geoy = geoy0;
		} catch (Exception ex) { }

		return photo;
	}

}