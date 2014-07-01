package net.pms.remote;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.util.List;
import net.pms.Messages;
import net.pms.configuration.RendererConfiguration;
import net.pms.dlna.DLNAResource;
import net.pms.dlna.RootFolder;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RemoteBrowseHandler implements HttpHandler {
	private static final Logger LOGGER = LoggerFactory.getLogger(RemoteBrowseHandler.class);
	private final static String CRLF = "\r\n";
	private RemoteWeb parent;

	public RemoteBrowseHandler(RemoteWeb parent) {
		this.parent = parent;
	}

	private String getSearchStr(String query) {
		for(String p : query.split("&")) {
			String[] pair = p.split("=");
			if (pair[0].equalsIgnoreCase("str")) {
				if (pair.length > 1 && StringUtils.isNotEmpty(pair[1])) {
					return pair[1];
				}
			}
		}
		return null;
	}

	private String mkBrowsePage(String id, HttpExchange t) throws IOException {
		String user = RemoteUtil.userName(t);
		RootFolder root = parent.getRoot(user, true, t);
		String vars = t.getRequestURI().getQuery();
		String search = null;
		if (StringUtils.isNotEmpty(vars)) {
			search = getSearchStr(vars);
		}
		List<DLNAResource> res = root.getDLNAResources(id, true, 0, 0, root.getDefaultRenderer(), search);
		boolean upnpControl = RendererConfiguration.hasConnectedControlPlayers();

		// Media browser HTML
		StringBuilder sb          = new StringBuilder();
		StringBuilder foldersHtml = new StringBuilder();
		StringBuilder mediaHtml   = new StringBuilder();

		boolean showFolders = false;

		sb.append("<!DOCTYPE html>").append(CRLF);
			sb.append("<head>").append(CRLF);
				// this special (simple) script performs a reload
				// if we have been sent back here after a VVA
				sb.append("<script>if(typeof window.refresh!='undefined' && window.refresh){").append(CRLF);
				sb.append("window.refresh=false;window.location.reload();}</script>").append(CRLF);
				sb.append("<meta charset=\"utf-8\">").append(CRLF);
				sb.append("<link rel=\"stylesheet\" href=\"/files/reset.css\" type=\"text/css\" media=\"screen\">").append(CRLF);
				sb.append("<link rel=\"stylesheet\" href=\"/files/web.css\" type=\"text/css\" media=\"screen\">").append(CRLF);
				sb.append("<link rel=\"stylesheet\" href=\"/files/web-narrow.css\" type=\"text/css\" media=\"screen and (max-width: 1080px)\">").append(CRLF);
				sb.append("<link rel=\"stylesheet\" href=\"/files/web-wide.css\" type=\"text/css\" media=\"screen and (min-width: 1081px)\">").append(CRLF);
				sb.append("<link rel=\"icon\" href=\"/files/favicon.ico\" type=\"image/x-icon\">").append(CRLF);
				sb.append("<script src=\"/files/jquery.min.js\"></script>");
				sb.append("<script src=\"/files/jquery.ums.js\"></script>");
				sb.append("<script src=\"/bump/bump.js\"></script>");
				// simple prompt script for search folders
				sb.append("<script>function searchFun(url) {");
		        sb.append("var str=prompt(\"Enter search string:\");").append(CRLF);
				sb.append("if(str!=null){ window.location.replace(url+'?str='+str)}").append(CRLF);
				sb.append("return false;");
				sb.append("}</script>").append(CRLF);
				// script ends here
				sb.append("<title>Universal Media Server</title>").append(CRLF);
			sb.append("</head>").append(CRLF);
			sb.append("<body id=\"ContentPage\">").append(CRLF);
				sb.append("<div id=\"Container\">");
					sb.append("<div id=\"Menu\">");
						sb.append("<a href=\"/doc\" id=\"DocButton\" title=\"Documentation\"></a>");
						sb.append("<a href=\"/browse/0\" id=\"HomeButton\"></a>");
					sb.append("</div>");
					for (DLNAResource r : res) {
						String newId = r.getResourceId();
						String idForWeb = URLEncoder.encode(newId, "UTF-8");
						String thumb = "/thumb/" + idForWeb;
						String name = StringEscapeUtils.escapeHtml(r.resumeName());

						if (r.isFolder()) {
							// Do not display the transcode folder in the web interface
							if (!name.equals(Messages.getString("TranscodeVirtualFolder.0"))) {
								// The resource is a folder
								foldersHtml.append("<li>");
									if (r.getClass().getName().contains("SearchFolder")) {
										// search folder add a prompt
										// NOTE!!!
										// Yes doing getClass.getname is REALLY BAD, but this
										// is to make legacy plugins utilize this function as well
										String p = "/browse/" + idForWeb;
										foldersHtml.append("<a href=\"#\" onclick=\"searchFun('").append(p).append("');\" title=\"").append(name).append("\">");
									}
									else {
										foldersHtml.append("<a href=\"/browse/").append(idForWeb).append("\" title=\"").append(name).append("\">");
									}
										foldersHtml.append("<span>").append(name).append("</span>");
									foldersHtml.append("</a>").append(CRLF);
								foldersHtml.append("</li>").append(CRLF);
								showFolders = true;
							}
						} else {
							// The resource is a media file
							mediaHtml.append("<li>");
								mediaHtml.append("<a href=\"/play/").append(idForWeb).append("\" title=\"").append(name).append("\">");
									mediaHtml.append("<img class=\"thumb\" src=\"").append(thumb).append("\" alt=\"").append(name).append("\">");
									mediaHtml.append("<span>").append(name).append("</span>");
								mediaHtml.append("</a>").append(CRLF);
								if (upnpControl) {
									mediaHtml.append("<a href=\"javascript:bump.start('//")
										.append(parent.getAddress()).append("','/play/").append(idForWeb).append("','")
										.append(name).append("')\" title=\"").append("Play on another renderer")
										.append("\"><img src=\"/files/bump/bump16.png\" alt=\"bump\"></a>").append(CRLF);
								} else {
									mediaHtml.append("<a href=\"javascript:alert('").append("No upnp-controllable renderers suitable for receiving pushed media are available. Refresh this page if a new renderer may have recently connected.")
										.append("')\" title=\"").append("No other renderers available")
										.append("\"><img src=\"/files/bump/bump16.png\" style=\"opacity:0.3;cursor:default\" alt=\"bump\"></a>").append(CRLF);
								}
							mediaHtml.append("</li>").append(CRLF);
						}
					}
					String noFoldersCSS = "";
					if (!showFolders) {
						noFoldersCSS = " class=\"noFolders\"";
					}
					sb.append("<div id=\"FoldersContainer\"").append(noFoldersCSS).append("><div><ul id=\"Folders\">").append(foldersHtml).append("</ul></div></div>");
					if (mediaHtml.length() > 0) {
						sb.append("<ul id=\"Media\"").append(noFoldersCSS).append(">").append(mediaHtml).append("</ul>");
					}
				sb.append("</div>");
			sb.append("</body>");
		sb.append("</html>");

		return sb.toString();
	}

	private void writePage(String response, HttpExchange t) throws IOException {
		LOGGER.debug("Write page " + response);
		t.sendResponseHeaders(200, response.length());
		try (OutputStream os = t.getResponseBody()) {
			os.write(response.getBytes());
		}
	}

	@Override
	public void handle(HttpExchange t) throws IOException {
		LOGGER.debug("Got a browse request " + t.getRequestURI());
		if (RemoteUtil.deny(t)) {
			throw new IOException("Access denied");
		}
		String id = RemoteUtil.getId("browse/", t);
		LOGGER.debug("Found id " + id);
		String response = mkBrowsePage(id, t);
		writePage(response, t);
	}
}
