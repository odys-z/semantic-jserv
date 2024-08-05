package io.oz.jserv.docs.common_lib;

import java.io.IOException;
import java.io.InputStream;

import io.odysz.semantic.tier.docs.ExpSyncDoc;

/**
 * <p>A file accessor used by AlbumTier etc., for accessing files without visiting traditional file system.</p>
 *
 * This is a special bridge (interface) that semantiers can access file through Android content providers or so.
 */
public interface IFileProvider {
    /**
     * Default implementation example:
     * <pre>return Files.size(Paths.get(f.fullpath());</pre>
     * @return size
     */
    long meta(ExpSyncDoc f) throws IOException;

    /**
     * <p>Open file input stream.</p>
     * Example for normal file system implementation:<pre>
     * new FileInputStream(new File(path));</pre>
     * 
     * @return readable stream
     */
    InputStream open(ExpSyncDoc f) throws IOException;

    /**
     * <p>Resolve the initial folder (with Policies).</p>
     * Currently, the save folder policy is simple last modified date for documents and creating date
     * for medias if API version later than Build.VERSION_CODES.O, otherwise use last modified.
     * (Audio file in Andriod is named with date string).
     *
     * @since 0.2.1 (Albumtier)
     * @return initial folder to save the file at server side
     */
    String saveFolder();
}
