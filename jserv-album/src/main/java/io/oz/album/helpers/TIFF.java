package io.oz.album.helpers;

/**
 * XMP Exif TIFF schema. This is a collection of
 * {@link String property definition} constants for the Exif TIFF
 * properties defined in the XMP standard.
 *
 * @see <a href="http://wwwimages.adobe.com/content/dam/Adobe/en/devnet/xmp/pdfs/cc-201306/XMPSpecificationPart2.pdf"
 * >XMP Specification, Part 2: Standard Schemas</a>
 * @since Apache Tika 0.8
 */
public interface TIFF {
    /**
     * The WGS84 Latitude of the Point
     */
    String LATITUDE = "geo:lat";

    /**
     * The WGS84 Longitude of the Point
     */
    String LONGITUDE = "geo:long";

    /**
     * The WGS84 Altitude of the Point
     */
    String ALTITUDE = "geo:alt";
    
    /**
     * "Number of bits per component in each channel."
     */
    String BITS_PER_SAMPLE = "tiff:BitsPerSample";

    /**
     * "Image height in pixels."
     */
    String IMAGE_LENGTH = "tiff:ImageLength";

    /**
     * "Image width in pixels."
     */
    String IMAGE_WIDTH = "tiff:ImageWidth";

    /**
     * "Number of components per pixel."
     */
    String SAMPLES_PER_PIXEL = "tiff:SamplesPerPixel";

    /**
     * Did the Flash fire when taking this image?
     */
    String FLASH_FIRED = "exif:Flash";

    /**
     * "Exposure time in seconds."
     */
    String EXPOSURE_TIME = "exif:ExposureTime";

    /**
     * "F-Number."
     * The f-number is the focal length divided by the "effective" aperture
     * diameter. It is a dimensionless number that is a measure of lens speed.
     */
    String F_NUMBER = "exif:FNumber";

    /**
     * "Focal length of the lens, in millimeters."
     */
    String FOCAL_LENGTH = "exif:FocalLength";

    /**
     * "ISO Speed and ISO Latitude of the input device as specified in ISO 12232"
     */
    String ISO_SPEED_RATINGS = "exif:IsoSpeedRatings";

    /**
     * "Manufacturer of the recording equipment."
     */
    String EQUIPMENT_MAKE = "tiff:Make";

    /**
     * "Model name or number of the recording equipment."
     */
    String EQUIPMENT_MODEL = "tiff:Model";

    /**
     * "Software or firmware used to generate the image."
     */
    String SOFTWARE = "tiff:Software";

    /**
     * "The Orientation of the image."
     * 1 = 0th row at top, 0th column at left
     * 2 = 0th row at top, 0th column at right
     * 3 = 0th row at bottom, 0th column at right
     * 4 = 0th row at bottom, 0th column at left
     * 5 = 0th row at left, 0th column at top
     * 6 = 0th row at right, 0th column at top
     * 7 = 0th row at right, 0th column at bottom
     * 8 = 0th row at left, 0th column at bottom
     */
    String[] ORIENTATION = new String[] {"1", "2", "3", "4", "5", "6", "7", "8"};

    /**
     * "Horizontal resolution in pixels per unit."
     */
    String RESOLUTION_HORIZONTAL = "tiff:XResolution";

    /**
     * "Vertical resolution in pixels per unit."
     */
    String RESOLUTION_VERTICAL = "tiff:YResolution";

    /**
     * "Units used for Horizontal and Vertical Resolutions."
     * One of "Inch" or "cm"
    String RESOLUTION_UNIT = "tiff:ResolutionUnit", "Inch", "cm";
     */

    /**
     * "Date and time when original image was generated"
     */
    String ORIGINAL_DATE = "exif:DateTimeOriginal";

    String EXIF_PAGE_COUNT = "exif:PageCount";
}
