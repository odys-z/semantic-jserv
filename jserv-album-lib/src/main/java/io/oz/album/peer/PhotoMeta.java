package io.oz.album.peer;

import io.odysz.semantic.meta.ExpDocTableMeta;
import io.odysz.transact.x.TransException;

/**
 * @author ody
 *
 */
public class PhotoMeta extends ExpDocTableMeta {

	public final String tags;
	public final String exif;
	public final String family;
	public final String geox;
	public final String geoy;
	public final String css;

	public PhotoMeta(String conn) throws TransException {
		super("h_photos", "pid", "device", conn);
		
		tags   = "tags";
		exif   = "exif";
		family = "family";
		
		geox = "geox";
		geoy = "geoy";
		css = "css";

		// [2026-08-15] v0.5.20
		// This old way is deprecated. ddlSqlite is hard coded
		// Disable this initialization as this field shouldn't be used, except tests.
		// ddlSqlite = loadSqlite(PhotoMeta.class, "h_photos.sqlite.ddl");

		ddlSqlite = "CREATE TABLE " + tbl + " (\n"
		 		+ "  " + pk + " varchar(12) NOT NULL,\n"
		 		+ "  " + family + " varchar2(12) NOT NULL,\n"
		 		+ "  " + folder + " varchar(256) NOT NULL,\n"
		 		+ "  " + resname + " varchar(256),\n"
		 		+ "	 " + uri + " varchar(512) NOT NULL,   -- storage/userId/folder/recId-clientname\n"
		 		+ "  " + createDate + " datetime,              -- picture taken time\n"
		 		+ "  " + device + " varchar(12),          -- 'original device ID',\n"
		 		+ "  " + fullpath + " TEXT DEFAULT '/' NOT NULL, -- original fullpath\n"
		 		+ "  " + shareby + " varchar(12),         -- 'shared by / creator',\n"
		 		+ "  " + shareDate + " datetime not null, -- 'shared date time',\n"
		 		+ "  " + tags + " varchar(512) DEFAULT NULL ,\n"
		 		+ "  " + geox + " double DEFAULT 0,\n"
		 		+ "  " + geoy + " double DEFAULT 0,\n"
		 		+ "  " + exif + " text default null,\n"
		 		+ "  " + mime + " TEXT(64), \n"
		 		+ "  " + size + " INTEGER, \n"
		 		+ "  " + css + " text,                    -- e.g. {\"type\":\"io.oz.album.tier.PhotoCSS\", \"size\":[3147,1461,1049,487]}\n"
		 		+ "  " + shareflag + " varchar2(12) default 'prv' not null, \n"
		 		+ "  oper varchar(12) not null,\n"
		 		+ "  opertime datetime not null,  -- this is the timestamp\n"
		 		+ "  syncstamp DATETIME DEFAULT CURRENT_TIMESTAMP not NULL,\n"
		 		+ "  " + io_oz_synuid + " varchar2(25),\n"
		 		+ "  PRIMARY KEY (" + pk + ")\n"
		 		+ ");";
	}
}
