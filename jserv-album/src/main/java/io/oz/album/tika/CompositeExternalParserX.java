// package io.oz.album.tika;

// import java.io.IOException;
// import java.util.List;

// import org.apache.tika.exception.TikaException;
// import org.apache.tika.mime.MediaTypeRegistry;
// import org.apache.tika.parser.CompositeParser;
// import org.apache.tika.parser.Parser;

// /**
//  * External Parser for exiftool in Windows (need fullpath to call it),
//  * with helper {@link ExternalParsersFactoryX}
//  * 
//  * @author Ody
//  *
//  */
// public class CompositeExternalParserX extends CompositeParser {
// 	private static final long serialVersionUID = 1L;

// 	public CompositeExternalParserX() throws IOException, TikaException {
//         this(new MediaTypeRegistry());
// 	}

//     @SuppressWarnings("unchecked")
// 	public CompositeExternalParserX(MediaTypeRegistry registry) throws IOException, TikaException {
//         super(registry, (List<Parser>) (List<? extends Parser>) ExternalParsersFactoryX.create());
//     }

// }
