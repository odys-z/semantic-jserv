/**
 * Credits to Auties000,
 * <a href='https://github.com/Auties00/QrToTerminal?tab=readme-ov-file#qr-code-to-terminal'>
 * Qr code to terminal</a>, * license unspecified, Mar 24, 2025 
 */
package io.oz.album.helpers;

import static io.odysz.common.LangExt.isNull;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.util.Objects;

/**
 * Utility class to print qr codes to the terminal
 */
public class QrTerminal {
    private static final String WHITE_WHITE = "█";
    private static final String BLACK_BLACK = " ";
    private static final String WHITE_BLACK = "▀";
    private static final String BLACK_WHITE = "▄";
    private static final int QUIET_ZONE = 2;
    public static final String BLACK = "\033[40m  \033[0m";
    public static final String WHITE = "\033[47m  \033[0m";

    /**
     * Prints a qr code to the terminal
     *
     * @param matrix The BitMatrix object that contains the QR code data.
     * @param small boolean - whether to print the QR code in a small format
     */
    public static void print(BitMatrix matrix, boolean small){
        System.out.println(toString(matrix, small));
    }
    
    public static void print(String content, QrProps...opt) throws WriterException {
    	int[] wh = isNull(opt) ? new int[] {10, 10} : opt[0].wh();
    	boolean small = isNull(opt) ? false : opt[0].small;

		QRCodeWriter barcodeWriter = new QRCodeWriter();
		BitMatrix bitMatrix = barcodeWriter.encode(content, BarcodeFormat.QR_CODE, wh[0], wh[1]);
		
		QrTerminal.print(bitMatrix, small);
    }

    /**
     * Transforms a qr code to a string
     *
     * @param matrix The BitMatrix object that contains the QR code data.
     * @param small boolean - whether to print the QR code in a small format
     * @return string
     */
    public static String toString(BitMatrix matrix, boolean small) {
        Objects.requireNonNull(matrix, "Missing argument: matrix");
        return small ? toSmallString(matrix) : toBigString(matrix);
    }

    private static String toSmallString(BitMatrix matrix) {
        var writer = new StringBuilder();

        var header = WHITE_WHITE.repeat(matrix.getWidth() + QUIET_ZONE * 2);
        writer.append((header + "\n").repeat(QUIET_ZONE / 2));

        for (var i = 0; i <= matrix.getWidth(); i += QUIET_ZONE) {
            writer.append(WHITE_WHITE.repeat(QUIET_ZONE));
            for (var j = 0; j <= matrix.getWidth(); j++) {
                var nextBlack = i + 1 < matrix.getWidth() && matrix.get(j, i + 1);
                var currentBlack = matrix.get(j, i);
                if (currentBlack && nextBlack) {
                    writer.append(BLACK_BLACK);
                } else if (currentBlack) {
                    writer.append(BLACK_WHITE);
                } else if (!nextBlack) {
                    writer.append(WHITE_WHITE);
                } else {
                    writer.append(WHITE_BLACK);
                }
            }

            writer.append(WHITE_WHITE.repeat(QUIET_ZONE - 1));
            writer.append("\n");
        }

        var trailing = WHITE_BLACK.repeat(matrix.getWidth() + QUIET_ZONE * 2);
        writer.append(trailing.repeat(QUIET_ZONE / 2 - 1));
        writer.append("\n");
        return writer.toString();
    }

    private static String toBigString(BitMatrix matrix) {
        return matrix.toString(BLACK, WHITE);
    }
}
