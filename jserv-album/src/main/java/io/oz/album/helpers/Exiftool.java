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

		if (isblank(photo.createDate)) {
			Path file = Paths.get(filepath);
			FileTime fd = (FileTime) Files.getAttribute(file, "creationTime");
			photo.month(fd);
		}

		// force audio
		if (MimeTypes.isAudio(photo.mime)) {
			photo.widthHeight = new int[] { 16, 9 };
			photo.wh = new int[] { 16, 9 };
			photo.rotation = 0;
		}

		if (isNull(photo.wh) && !isblank(photo.widthHeight))
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
