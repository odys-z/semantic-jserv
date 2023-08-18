package io.oz.album.helpers;

import static io.odysz.common.LangExt.eq;
import static io.odysz.common.LangExt.filesize;
import static io.odysz.common.LangExt.gt;
import static io.odysz.common.LangExt.imagesize;
import static io.odysz.common.LangExt.isblank;
import static io.odysz.common.LangExt.lt;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.FileImageInputStream;
import javax.imageio.stream.ImageInputStream;

import org.apache.commons.io_odysz.FilenameUtils;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TIFF;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.SAXException;

import io.odysz.anson.JsonOpt;
import io.odysz.common.CheapMath;
import io.odysz.common.MimeTypes;
import io.odysz.common.Utils;
import io.odysz.semantics.x.SemanticException;
import io.oz.album.tier.Exifield;
import io.oz.album.tier.PhotoRec;

/**
 * Exif data format helper.
 * 
 * <h6>Credits to</h6>
 * Sean Leary, and the <a href='https://github.com/stleary/JSON-java'>JSON Reference Implementation for Java</a>.
 * 
 * <p/>
 * @since 0.6.50
 * @author Ody
 *
 */
public class Exif {
	static String geox0 = "0";
	static String geoy0 = "0";

	protected static String cfgFile = "tika.xml";
	static TikaConfig config;
	/**
	 * @param xml
	 * @return "(... xml)/tika.xml"
	 * @throws TikaException
	 * @throws IOException
	 * @throws SAXException
	 */
	public static String init(String xml) throws TikaException, IOException, SAXException {
		// TikaConfig config = new TikaConfig("/path/to/tika.xml");

		String absPath = FilenameUtils.concat(xml, cfgFile);
		Utils.logi("Loading tika configuration:\n%s", absPath);
		config = new TikaConfig(absPath);
		return absPath;
	}
	
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
			AutoDetectParser parser = new AutoDetectParser(config);
			Metadata metadata = new Metadata();

			photo.exif = new Exifield();
			parser.parse(stream, handler, metadata);
			for (String name: metadata.names()) {
				String val = metadata.get(name); 
				val = escape(val);
				// whitewash some faulty string
				// Huawei p30 take pics with 
				// name: ICC:Profile Description, val: "1 enUS(sRGB\0\0..." where length = 52
				photo.exif.add(name, val);

				try {
					if (eq("Content-Type", name) && isblank(photo.mime)) // can be error
						photo.mime = val; 
					else if (eq("Image Height", name)) {
						if (photo.widthHeight == null) photo.widthHeight = new int[2];
						photo.widthHeight[1] = imagesize(val);
					}
					else if (eq("Image Width", name)) {
						if (photo.widthHeight == null) photo.widthHeight = new int[2];
						photo.widthHeight[0] = imagesize(val);
					}
					else if (eq("File Size", name))
						photo.size = filesize(val);
					else if (eq("Rotation", name))
						photo.rotation = val;
				} catch (Exception e) {
					Utils.warn("Failed for parsing devide: %s, path: %s,\nname: %s, value: %s",
								photo.device(), photo.fullpath(),
								name, val);
				}
			}
	
			if (isblank(photo.createDate)) {
				Path file = Paths.get(filepath);
				FileTime fd = (FileTime) Files.getAttribute(file, "creationTime");
				photo.month(fd);
			}

			if (isblank(photo.widthHeight))
				photo.widthHeight = new int[]
					{metadata.getInt(TIFF.IMAGE_WIDTH), metadata.getInt(TIFF.IMAGE_LENGTH)}; // FIXME too brutal

			if ((eq("90", photo.rotation) || eq("270", photo.rotation)) && gt(photo.widthHeight[0], photo.widthHeight[1]))
				photo.wh = CheapMath.reduceFract(photo.widthHeight[1], photo.widthHeight[0]);
			else if ((eq("0", photo.rotation) || eq("180", photo.rotation)) && lt(photo.widthHeight[0], photo.widthHeight[1]))
				photo.wh = CheapMath.reduceFract(photo.widthHeight[1], photo.widthHeight[0]);
			else
				photo.wh = CheapMath.reduceFract(photo.widthHeight[0], photo.widthHeight[1]);
			
			// force audio
			if (MimeTypes.isAudio(photo.mime)) {
				photo.widthHeight = new int[] { 16, 9 };
				photo.wh = new int[] { 16, 9 };
				photo.rotation = "0";
			}
			// another way other than by Tika
			else if (MimeTypes.isImgVideo(photo.mime) && isblank(photo.widthHeight)) {
				try {
					// photo.rotation = "0";
					photo.widthHeight = Exif.parseWidthHeight(filepath);
				}
				catch (SemanticException e) {
					Utils.warn("Exif parse failed and can't parse width & height: %s", filepath);
					if (isblank(photo.widthHeight)) {
						photo.widthHeight = new int[] { 3, 4 };
						photo.wh = new int[] { 3, 4 };
					}
				}
			}

			if (isblank(photo.wh) && !isblank(photo.widthHeight))
				photo.wh = eq(photo.rotation, "90") || eq(photo.rotation, "270") 
					? CheapMath.reduceFract(photo.widthHeight[1], photo.widthHeight[0])
					: CheapMath.reduceFract(photo.widthHeight[0], photo.widthHeight[1]);

			photo.geox = metadata.get(TikaCoreProperties.LONGITUDE);
			if (photo.geox == null) photo.geox = geox0;

			photo.geoy = metadata.get(TikaCoreProperties.LATITUDE);
			if (photo.geoy == null) photo.geoy = geoy0;
		} catch (Exception ex) {
			ex.printStackTrace();
		}

		return photo;
	}

	/**
	 * Escape a Java string to a json string. 
	 * 
	 * <h6>Reference</h6><p>
	 * 1. Json validate characters by <a href='https://www.json.org/json-en.html'>json.org</a><br>
	 * <pre>
	 * character
	 * 	'0020' . '10FFFF' - '"' - '\'
	 * 	'\' escape 
	 * 
	 * escape
	 * 	'"'
	 * 	'\'
	 * 	'/'
	 * 	'b'
	 * 	'f'
	 * 	'n'
	 * 	'r'
	 * 	't'
	 * 	'u' hex hex hex hex
	 *
	 * hex
	 * 	digit
	 * 	'A' . 'F'
	 * 	'a' . 'f'
	 * </pre>
	 * 2. JSON-Java, a Java reference implementation, source at <a href='https://github.com/stleary/JSON-java'>github</a>.</p>
	 * The XML tag content is escaped by 
	 * <a href='https://github.com/stleary/JSON-java/blob/60662e2f8384d3449822a3a1179bfe8de67b55bb/src/main/java/org/json/XML.java#L149'>
	 * org.json.XML#escape()</a> from java string by checking character code point with #mustEscape(int), copied here as {@link #validChar(int)}.
	 * <pre>
     * [param] cp code point to test
     * [return] true if the code point is not valid for an XML
     * private static boolean mustEscape(int cp) {
     * 	// isISOControl is true when (cp >= 0 && cp <= 0x1F) || (cp >= 0x7F && cp <= 0x9F)
     * 	// all ISO control characters are out of range except tabs and new lines
     * 	return (Character.isISOControl(cp)
     * 		&& cp != 0x9
     * 		&& cp != 0xA
     * 		&& cp != 0xD
     * 		) || !(
     * 			// valid the range of acceptable characters that aren't control
     * 			(cp >= 0x20 && cp <= 0xD7FF)
     * 			|| (cp >= 0xE000 && cp <= 0xFFFD)
     * 			|| (cp >= 0x10000 && cp <= 0x10FFFF)) ;
     * 	}
	 * </pre>  
	 * where, the isIsonControl() is checking <pre>"
	 * Valid range from https://www.w3.org/TR/REC-xml/#charsets
	 * #x9 | #xA | #xD | [#x20-#xD7FF] | [#xE000-#xFFFD] | [#x10000-#x10FFFF]
	 * any Unicode character, excluding the surrogate blocks, FFFE, and FFFF."</pre>
	 * @param val
	 * @return The cut off string until the first invalid code point.
	 */
	public static String escape(String val, JsonOpt ...jopt) {
		StringBuilder sb = new StringBuilder(val.length());
		for (final int cp : codePointIterator(val)) {
			if (mustEscape(cp))
				break;
			else
				sb.appendCodePoint(cp);
		}
		return sb.toString();
	}

    /**
     * https://github.com/stleary/JSON-java/blob/60662e2f8384d3449822a3a1179bfe8de67b55bb/src/main/java/org/json/XML.java#L149
     * 
     * @param cp code point to test
     * @return true if the code point is not valid for an XML
     */
    public static boolean mustEscape(int cp) {
        /* Valid range from https://www.w3.org/TR/REC-xml/#charsets
         *
         * #x9 | #xA | #xD | [#x20-#xD7FF] | [#xE000-#xFFFD] | [#x10000-#x10FFFF]
         *
         * any Unicode character, excluding the surrogate blocks, FFFE, and FFFF.
         */
        // isISOControl is true when (cp >= 0 && cp <= 0x1F) || (cp >= 0x7F && cp <= 0x9F)
        // all ISO control characters are out of range except tabs and new lines
        return (Character.isISOControl(cp)
                && cp != 0x9
                && cp != 0xA
                && cp != 0xD
            ) || !(
                // valid the range of acceptable characters that aren't control
                (cp >= 0x20 && cp <= 0xD7FF)
                || (cp >= 0xE000 && cp <= 0xFFFD)
                || (cp >= 0x10000 && cp <= 0x10FFFF)
            )
        ;
    }
    
    /**
     * https://github.com/stleary/JSON-java/blob/60662e2f8384d3449822a3a1179bfe8de67b55bb/src/main/java/org/json/XML.java#L69
     * 
     * Creates an iterator for navigating Code Points in a string instead of
     * characters. Once Java7 support is dropped, this can be replaced with
     * <code>
     * string.codePoints()
     * </code>
     * which is available in Java8 and above.
     *
     * @see <a href=
     *      "http://stackoverflow.com/a/21791059/6030888">http://stackoverflow.com/a/21791059/6030888</a>
     */
    public static Iterable<Integer> codePointIterator(final String string) {
        return new Iterable<Integer>() {
            @Override
            public Iterator<Integer> iterator() {
                return new Iterator<Integer>() {
                    private int nextIndex = 0;
                    private int length = string.length();

                    @Override
                    public boolean hasNext() {
                        return this.nextIndex < this.length;
                    }

                    @Override
                    public Integer next() {
                        int result = string.codePointAt(this.nextIndex);
                        this.nextIndex += Character.charCount(result);
                        return result;
                    }

                    @Override
                    public void remove() {
                        throw new UnsupportedOperationException();
                    }
                };
            }
        };
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
