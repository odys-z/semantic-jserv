package io.odysz.jsample;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;

@WebServlet(description = "querying db via Semantic.DA", urlPatterns = { "/case.sample" })
public class SampleCase  extends HttpServlet {

}
