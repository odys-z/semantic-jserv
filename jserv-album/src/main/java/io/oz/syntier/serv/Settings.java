package io.oz.syntier.serv;

import java.awt.image.BufferedImage;
import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletResponse;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import io.odysz.anson.AnsonException;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jserv.ServPort;
import io.odysz.semantic.jserv.user.UserReq;
import io.odysz.semantics.x.SemanticException;
import io.oz.album.peer.AlbumPort;
import io.oz.syn.YellowPages;

@WebServlet(description = "Portfolio Synode Settings", urlPatterns = { "/settings.less" })
public class Settings extends ServPort<UserReq> {
	private static final long serialVersionUID = 1L;

	public Settings() throws Exception {
		super(AlbumPort.settings);
		YellowPages.load("");
		// genQrcode(YellowPages.synconfig().localhost);
	}

	@Override
	protected void onGet(AnsonMsg<UserReq> msg, HttpServletResponse resp)
			throws ServletException, IOException, AnsonException, SemanticException {
		// TODO Auto-generated method stub
		
	}

	@Override
	protected void onPost(AnsonMsg<UserReq> msg, HttpServletResponse resp)
			throws ServletException, IOException, AnsonException, SemanticException {
		// TODO Auto-generated method stub
		
	}

	public static BufferedImage genQrcode(String jserv) throws WriterException {
		QRCodeWriter barcodeWriter = new QRCodeWriter();
		BitMatrix bitMatrix = 
		  barcodeWriter.encode(jserv, BarcodeFormat.QR_CODE, 200, 200);

		return MatrixToImageWriter.toBufferedImage(bitMatrix);
	}
}
