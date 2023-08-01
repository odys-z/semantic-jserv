package io.oz.album.helpers;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.Date;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.FileImageInputStream;
import javax.imageio.stream.ImageInputStream;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TIFF;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.sax.BodyContentHandler;

import io.odysz.common.CheapMath;
import io.odysz.common.DateFormat;
import io.odysz.common.Utils;
import io.odysz.semantics.x.SemanticException;
import io.oz.album.tier.Exifield;
import io.oz.album.tier.PhotoRec;

import static io.odysz.common.LangExt.eq;
import static io.odysz.common.LangExt.isblank;
import static io.odysz.common.LangExt.split;

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

	public static PhotoRec parseExif(PhotoRec photo, String filepath) {

		try {
			photo.mime = isblank(photo.mime) ?
				Files.probeContentType(Paths.get(filepath)) : photo.mime;
		} catch (IOException e) { }

		File f = new File(filepath);
		photo.size = f.length();
		try (FileInputStream stream = new FileInputStream(f)) {
			BodyContentHandler handler = new BodyContentHandler();
			AutoDetectParser parser = new AutoDetectParser();
			Metadata metadata = new Metadata();

			// photo.exif = new ArrayList<String>();
			photo.exif = new Exifield();
			parser.parse(stream, handler, metadata);
			for (String name: metadata.names()) {
				String val = metadata.get(name);
				// photo.exif.add(name + ":" + (exif == null ? "null" : exif.trim().replace("\n", "\\n")));
				photo.exif.add(name, (val == null ? null : val.trim().replace("\n", "\\n")));
				
				try {
					if (eq("Content-Type", name))
						photo.mime = val; 
					else if (eq("Image Height", name)) {
						if (photo.widthHeight == null) photo.widthHeight = new int[2];
						photo.widthHeight[1] = Integer.valueOf(metadata.get(name));
					}
					else if (eq("Image Width", name)) {
						if (photo.widthHeight == null) photo.widthHeight = new int[2];
						photo.widthHeight[0] = Integer.valueOf(metadata.get(name));
					}
					else if (eq("File Size", name))
						photo.size = Long.valueOf(split(metadata.get(name), " ")[0]); // 170442 bytes
				} catch (Exception e) {
					Utils.warn("Failed for parsing %s : %s,\n%s : %s",
							photo.device(), photo.fullpath(), name, metadata.get(name));
				}
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

			photo.widthHeight = new int[]
				{metadata.getInt(TIFF.IMAGE_WIDTH), metadata.getInt(TIFF.IMAGE_LENGTH)};

			photo.wh = CheapMath.reduceFract(photo.widthHeight[0], photo.widthHeight[1]);

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
	 * Can't support webp, ico and svg.
	 * 
	 * @deprecated limited image types can be supported.
	 * 
	 * @see https://stackoverflow.com/a/12164026
	 * @param imgFile image file
	 * @return dimensions of image
	 * @throws IOException if the file is not understood or missing
	 * @throws SemanticException parsing width etc. failed
	 */
	public static int[] parseWidthHeight(String pth) throws IOException, SemanticException {
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
			} finally { reader.dispose(); }
		}

		throw new SemanticException("Not a known image file: " + imgFile.getAbsolutePath());
	}

}
