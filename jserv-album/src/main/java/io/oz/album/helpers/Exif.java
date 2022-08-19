package io.oz.album.helpers;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.FileImageInputStream;
import javax.imageio.stream.ImageInputStream;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.sax.BodyContentHandler;

import io.odysz.common.DateFormat;
import io.odysz.common.LangExt;
import io.odysz.common.Utils;
import io.oz.album.tier.Photo;

/**
 * Exif data format helper
 * 
 * @author Ody
 *
 */
public class Exif {
	static String geox0 = "0";
	static String geoy0 = "0";
	
	public static void geoxy0(int x, int y) {
		geox0 = String.valueOf(x);
		geoy0 = String.valueOf(y);
	}

	public static Photo parseExif(Photo photo, String filepath) {

		try {
			photo.mime = LangExt.isblank(photo.mime) ?
				Files.probeContentType(Paths.get(filepath)) : photo.mime;
		} catch (IOException e) { }

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
			if (d != null) {
				photo.createDate = DateFormat.formatime(d);
				photo.month(d);
			}
			else {
				Path file = Paths.get(filepath);
				FileTime fd = (FileTime) Files.getAttribute(file, "creationTime");
				photo.month(fd);
			}

			photo.geox = metadata.get(TikaCoreProperties.LONGITUDE);
			if (photo.geox == null) photo.geox = geox0;

			photo.geoy = metadata.get(TikaCoreProperties.LATITUDE);
			if (photo.geoy == null) photo.geoy = geoy0;
		} catch (Exception ex) { }

		return photo;
	}

	/**
	 * Gets image dimensions for given file.
	 * 
	 * Can't support ico and svg.
	 * 
	 * @see https://stackoverflow.com/a/12164026
	 * @param imgFile image file
	 * @return dimensions of image
	 * @throws IOException if the file is not a known image
	 */
	public static int[] parseWidthHeight(String pth) throws IOException {
	  File imgFile = new File(pth);
	  int pos = imgFile.getName().lastIndexOf(".");
	  if (pos == -1)
	    throw new IOException("No extension for file: " + imgFile.getAbsolutePath());
	  String suffix = imgFile.getName().substring(pos + 1);
	  Iterator<ImageReader> iter = ImageIO.getImageReadersBySuffix(suffix);
	  while(iter.hasNext()) {
	    ImageReader reader = iter.next();
	    try {
	      ImageInputStream stream = new FileImageInputStream(imgFile);
	      reader.setInput(stream);
	      int width = reader.getWidth(reader.getMinIndex());
	      int height = reader.getHeight(reader.getMinIndex());
	      return new int[] {width, height};
	    } catch (IOException e) {
	      Utils.warn("Error reading: " + imgFile.getAbsolutePath(), e);
	    } finally {
	      reader.dispose();
	    }
	  }

	  throw new IOException("Not a known image file: " + imgFile.getAbsolutePath());
	}

}
