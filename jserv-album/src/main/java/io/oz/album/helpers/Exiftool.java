package io.oz.album.helpers;

import static io.odysz.common.LangExt.eq;
import static io.odysz.common.LangExt.isblank;
import static io.odysz.common.LangExt.isNull;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.io.output.NullOutputStream.NULL_OUTPUT_STREAM;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.commons.io.IOUtils;

import io.odysz.anson.JsonOpt;
import io.odysz.common.CheapMath;
import io.odysz.common.Configs;
import io.odysz.common.EnvPath;
import io.odysz.common.MimeTypes;
import io.odysz.common.Utils;
import io.odysz.transact.x.TransException;
import io.oz.album.peer.Exifield;
import io.oz.album.peer.PhotoRec;

public class Exiftool {
	public static boolean verbose = true;
	
	public static final String exiftool = "exiftool";
	
	static String geox0 = "0";
	static String geoy0 = "0";

	static String cmd;

	public static String init() throws InterruptedException, IOException, TimeoutException {
		cmd = EnvPath.replaceEnv(Configs.getCfg("exiftool"));
		Utils.logi("[Exiftool.init] command: %s", cmd);
		
		check();
		return cmd;
	}

	public static PhotoRec parseExif(PhotoRec photo, String filepath) throws IOException {
		try {
			photo.mime = isblank(photo.mime) ?
				Files.probeContentType(Paths.get(filepath)) : photo.mime;
		} catch (IOException e) { }

		File f = new File(filepath);
		photo.size = f.length();

		photo.exif = new Exifield();
		Metadata metadata = parse(filepath);
		ExiftoolSyntax.extract(photo, metadata);

		/*
		// {"type": "io.oz.album.helpers.Metadata", "props": {"Minor Version": ["0.0.0"], "Next Track ID": ["3"], "Modify Date": ["2024"], "Media Modify Date": ["2024"], "Current Time": ["0 s"], "Track Layer": ["0"], "File Type Extension": ["mp4"], "Media Duration": ["9.33 s"], "Graphics Mode": ["srcCopy"], "File Creation Date/Time": ["2025"], "Time Scale": ["1000"], "Track Header Version": ["0"], "X Resolution": ["72"], "Handler Description": ["SoundHandle"], "Color Profiles": ["nclx"], "Audio Bits Per Sample": ["16"], "Audio Channels": ["2"], "GPS Coordinates": ["30 deg 40' 12.00\" N, 104 deg 0' 11.16\" E"], "GPS Position": ["30 deg 40' 12.00\" N, 104 deg 0' 11.16\" E"], "Media Data Offset": ["810340"], "Poster Time": ["0 s"], "Media Header Version": ["0"], "Create Date": ["2024"], "ExifTool Version Number": ["13.21"], "Track ID": ["1"], "Movie Header Version": ["0"], "Preferred Rate": ["1"], "File Type": ["MP4"], "GPS Longitude": ["104 deg 0' 11.16\" E"], "Duration": ["9.33 s"], "Image Size": ["2400x1080"], "Compressor ID": ["avc1"], "File Access Date/Time": ["2025"], "Major Brand": ["MP4 v2 [ISO 14496-14]"], "Track Volume": ["0.00%"], "Audio Sample Rate": ["48000"], "File Modification Date/Time": ["2025"], "File Permissions": ["-rw-rw-rw-"], "Megapixels": ["2.6"], "Preferred Volume": ["100.00%"], "Track Duration": ["9.33 s"], "Selection Duration": ["0 s"], "Preview Duration": ["0 s"], "Track Modify Date": ["2024"], "Balance": ["0"], "Y Resolution": ["72"], "Handler Type": ["Audio Track"], "Warning": ["Unknown trailer with truncated 'dVer' data at offset 0x17d7486"], "GPS Latitude": ["30 deg 40' 12.00\" N"], "Android Version": ["10"], "Video Frame Rate": ["29.689"], "Compatible Brands": ["isom, mp42"], "Audio Format": ["mp4a"], "Op Color": ["0 0 0"], "File Size": ["25 MB"], "File Name": ["0105 VID_20241219_204417.mp4"], "Directory": ["C"], "Selection Time": ["0 s"], "Track Create Date": ["2024"], "Source Image Height": ["1080"], "Matrix Coefficients": ["BT.709"], "Media Time Scale": ["48000"], "Avg Bitrate": ["20.7 Mbps"], "Preview Time": ["0 s"], "Media Create Date": ["2024"], "Bit Depth": ["24"], "Image Height": ["1080"], "Pixel Aspect Ratio": ["65536"], "Rotation": ["90"], "Color Primaries": ["BT.709"], "Image Width": ["2400"], "Source Image Width": ["2400"], "MIME Type": ["video/mp4"], "Transfer Characteristics": ["BT.709"], "Video Full Range Flag": ["Limited"], "Media Data Size": ["24188706"], "Matrix Structure": ["1 0 0 0 1 0 0 0 1"]}}
		for (String name: metadata.names()) {
			ArrayList<String> vals = ((ArrayList<String>) metadata.get(name)); 
			String val = _0(vals);
			if (verbose) Utils.logi("%s :\t%s", name, val);

			val = Exif.escape(val);
			// white-wash some faulty string
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
				else if (eq("File Size", name) && photo.size == 0)
					photo.size = filesize(val);
				else if (eq("Orientation", name) || eq("Rotation", name))
					// photo.rotation = val;
					try {
						int orient = Integer.valueOf(val);
						photo.rotation = orient == 1 || orient == 2
								? 0   : orient == 3 || orient == 4
								? 180 : orient == 6 || orient == 5
								? 90  : orient == 8 || orient == 7
								? 270 : 0;
					} catch (Exception e) {
						photo.rotation = 0;
					}
				else if (eq("GPS Latitude", name) || eq("GPS Longitude", name))
					parseXY(photo, name, val);
			} catch (Exception e) {
				Utils.warn("Failed for parsing devide: %s, path: %s,\nname: %s, value: %s",
							photo.device(), photo.fullpath(),
							name, val);
			}
		}
		*/

		if (isblank(photo.createDate)) {
			Path file = Paths.get(filepath);
			FileTime fd = (FileTime) Files.getAttribute(file, "creationTime");
			photo.month(fd);
		}

//		if (isNull(photo.widthHeight) && metadata.getInt(TIFF.IMAGE_WIDTH) < 0 && metadata.getInt(TIFF.IMAGE_LENGTH) < 0) 
//			try {
//				if (verbose) Utils.logi(metadata.names());
//				photo.widthHeight = new int[]
//					// FIXME too brutal
//					{metadata.getInt(TIFF.IMAGE_WIDTH), metadata.getInt(TIFF.IMAGE_LENGTH)};
//			} catch (Exception e) { e.printStackTrace(); }

		// force audio
		if (MimeTypes.isAudio(photo.mime)) {
			photo.widthHeight = new int[] { 16, 9 };
			photo.wh = new int[] { 16, 9 };
			photo.rotation = 0;
		}
		// another way other than by Tika
//		else if (MimeTypes.isImgVideo(photo.mime) && isblank(photo.widthHeight)) {
//			try {
//				photo.widthHeight = Exif.parseWidthHeight(filepath);
//			}
//			catch (SemanticException e) {
//				if (verbose) Utils.warn("[Exif.verbose] Exif parse failed and can't parse width & height: %s", filepath);
//				if (isblank(photo.widthHeight)) {
//					photo.widthHeight = new int[] { 4, 3 };
//					photo.wh = new int[] { 4, 3 };
//				}
//			}
//		}

		if (isNull(photo.wh) && !isblank(photo.widthHeight))
//			photo.wh = photo.rotation == 90 || photo.rotation == 270
//				? CheapMath.reduceFract(photo.widthHeight[1], photo.widthHeight[0])
//				: CheapMath.reduceFract(photo.widthHeight[0], photo.widthHeight[1]);
			photo.wh = CheapMath.reduceFract(photo.widthHeight[0], photo.widthHeight[1]);

		return photo;
	}
	
	static void parseXY(PhotoRec photo, String name, String val) {
    	if (eq("GPS Longitude", name)) // e. g. (name, val) == (GPS Latitude, 23 deg 27' 54.30" N), (GPS Longitude, 103 deg 24' 37.30" E)
    		photo.geox = val;
    	else if (eq("GPS Latitude", name)) 
    		photo.geoy = val;
	}

	public static void check() throws InterruptedException, IOException, TimeoutException {
        Process process = null;
        try {
            process = Runtime.getRuntime().exec(String.format("%s -ver", cmd));
            Thread stdErrSuckerThread = ignoreStream(process.getErrorStream(), false);
            Thread stdOutSuckerThread = ignoreStream(process.getInputStream(), false);
            stdErrSuckerThread.join();
            stdOutSuckerThread.join();
            //make the timeout parameterizable
            boolean finished = process.waitFor(60000, TimeUnit.MILLISECONDS);
            if (!finished) {
                throw new TimeoutException();
            }
            int result = process.exitValue();
            Utils.logi("[Exiftool.check]\n%s -ver\nexit: %s", cmd, result);

        } finally {
            if (process != null) {
                process.destroyForcibly();
            }
        }
    }
    
    public static Metadata parse(String path) throws IOException {
		String[] cmds = new String[] {cmd, path};
		Process process = null;
			process = Runtime.getRuntime().exec(cmds);

		if (process != null)
		try {
			process.getOutputStream().close();

			try (InputStream out = process.getInputStream();
				 InputStream err = process.getErrorStream()) {

				Metadata xhtml = new Metadata();
				extractMetadata(out, xhtml);
				ignoreStream(err, true);
				ignoreStream(out, true);
				return xhtml;
			} catch (Exception e) {}

		} finally {
			try { process.waitFor(); }
			catch (InterruptedException ignore) { }
		}
		else 
			Utils.warn("Checking exiftool failed: %s", cmds[0]);
		return null;
    }
    
    private static void extractMetadata(final InputStream stream, final Metadata metadata) {
        Thread t = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                	String[] kv = line.split(":");
                	if (kv != null && kv.length > 0)
                		metadata.add(kv[0].trim(), kv[1].trim());
                }
            } catch (IOException e) {
            } catch (TransException e) {
				e.printStackTrace();
			}
        });
        t.start();
        try {
            t.join();
        } catch (InterruptedException ignore) {
        }
    }
    
    /**
     * Starts a thread that reads and discards the contents of the
     * standard stream of the given process. Potential exceptions
     * are ignored, and the stream is closed once fully processed.
     *
     * @param stream       stream to sent to black hole (a k a null)
     * @param waitForDeath when {@code true} the caller thread will be
     *                     blocked till the death of new thread.
     * @return The thread that is created and started
     */
    private static Thread ignoreStream(final InputStream stream, boolean waitForDeath) {
        @SuppressWarnings("deprecation")
		Thread t = new Thread(() -> {
            try { IOUtils.copy(stream, NULL_OUTPUT_STREAM); }
            catch (IOException e) { }
            finally { IOUtils.closeQuietly(stream); }
        });

        t.start();
        if (waitForDeath) {
            try { t.join(); }
            catch (InterruptedException ignore) { }
        }
        return t;
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
}
