package io.oz.album.helpers;

import static io.odysz.common.LangExt._0;
import static io.odysz.common.Utils.logi;

import java.util.ArrayList;

import io.oz.album.peer.PhotoRec;

public class ExiftoolSyntax {
	static boolean verbose = false;

	static final String toolver = "Exiftool Version";
	static final String geox = "GPS Longitude";
	static final String width = "Image Width";
	static final String height = "Image Height";
	static final String orientation = "Orientation";
	static final String rotation = "Rotation";

    static void extract(PhotoRec photo, Metadata metadata) {
		for (String name: metadata.names()) {
			@SuppressWarnings("unchecked")
			ArrayList<String> vals = ((ArrayList<String>) metadata.get(name)); 
			String val = _0(vals);

			if (verbose) logi("%s :\t%s", name, val);

			val = Exiftool.escape(val);
			// white-wash some faulty string
			// Huawei p30 take pics with 
			// name: ICC:Profile Description, val: "1 enUS(sRGB\0\0..." where length = 52
			photo.exif.add(name, val);
		}

    	photo.geox = metadata.getString(geox);
    	photo.geoy = metadata.getString(geox);

 		try {
			String rot = metadata.has(orientation) ? metadata.getString(orientation) : metadata.getString(rotation);
			int orient = Integer.valueOf(rot.replaceAll("\\[|\\]", ""));
			photo.rotation = orient == 1 || orient == 2
					? 0   : orient == 3 || orient == 4
					? 180 : orient == 6 || orient == 5
					? 90  : orient == 8 || orient == 7
					? 270 : orient;
		} catch (Exception e) {
			photo.rotation = 0;
		}
   	
    	try {
    		String sw = metadata.getString(width);
			int w = Integer.valueOf(sw.replaceAll("\\[|\\]", ""));
			String sh = metadata.getString(height);
			int h = Integer.valueOf(sh.replaceAll("\\[|\\]", ""));
			

			if ((photo.rotation == 90 || photo.rotation == 270) && h < w)
				photo.widthHeight = new int[] {h, w};
			else photo.widthHeight = new int[] {w, h};
    	} catch (Exception e){ }
    	
	}
}
