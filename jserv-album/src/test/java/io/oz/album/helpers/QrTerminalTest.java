package io.oz.album.helpers;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

class QrTerminalTest {

	@Test
	void testToString() throws WriterException {
		QrTerminal.print("https://odys-z.github.io", new QrProps(true));

		QRCodeWriter barcodeWriter = new QRCodeWriter();
		BitMatrix bitMatrix = 
		  barcodeWriter.encode("https://odys-z.github.io", BarcodeFormat.QR_CODE, 10, 10);
		
		QrTerminal.print(bitMatrix, false);
		assertEquals(
				  "█████████████████████████████████████\n"
				+ "█████████████████████████████████████\n"
				+ "█████████████████████████████████████\n"
				+ "██████ ▄▄▄▄▄ █▀ █▀▀█ ▀ █ ▄▄▄▄▄ ██████\n"
				+ "██████ █   █ █▀ ▄ █▀▀▄██ █   █ ██████\n"
				+ "██████ █▄▄▄█ █▀█ █▄▄█▀ █ █▄▄▄█ ██████\n"
				+ "██████▄▄▄▄▄▄▄█▄█▄█ █▄█ █▄▄▄▄▄▄▄██████\n"
				+ "██████▄   ▄▀▄▄  ▄█▄▄ ▄▀▄█ █▄█ ███████\n"
				+ "██████▀▄▄▀██▄▄▄ ▀ ▄█ ▄▀▄   ▄█▄ ██████\n"
				+ "██████ ▀▀██▄▄▄▄▄▀▄▀▄▄██ ▄▀▄▀▄ ▄██████\n"
				+ "██████ █▄▄▀▀▄▀ ▄ █▀▄█▄█▀▀  ▄█▄ ██████\n"
				+ "██████▄█████▄▄▀▄▀█▄ ██ ▄▄▄ ▀▄████████\n"
				+ "██████ ▄▄▄▄▄ █▄▀█ ▄▄▄█ █▄█ ▀▄▄ ██████\n"
				+ "██████ █   █ █  ▄▄  ▄▄▄  ▄ ▀ ▀▀██████\n"
				+ "██████ █▄▄▄█ █ █▀▀██▀▀ ▄▀▀▀ ▄█ ██████\n"
				+ "██████▄▄▄▄▄▄▄█▄▄█▄▄▄▄▄█▄█▄▄▄▄▄▄██████\n"
				+ "█████████████████████████████████████\n"
				+ "█████████████████████████████████████\n"
				+ "\n", QrTerminal.toString(bitMatrix, true));
	}

}
