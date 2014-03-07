package com.skunkworks.util;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Map;
import java.util.Map.Entry;

public class HttpUtils {
	public static InputStream makeRawAPIRequest(String path,
			Map<String, String> post) throws IOException {
		StringBuilder content = new StringBuilder();
		if (post != null) {
			for (Entry<String, String> obj : post.entrySet()) {
				if (content.length() > 0) {
					content.append('&');
				}
				content.append(obj.getKey());
				content.append('=');
				content.append(URLEncoder.encode(obj.getValue(), "UTF-8"));
			}
		}

		URL url = new URL(path);
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		if (post != null) {
			conn.setRequestMethod("POST");
			conn.setDoOutput(true);
			conn.setDoInput(true);
			conn.setFixedLengthStreamingMode(content.toString().getBytes().length);
			conn.setRequestProperty("Content-Type",
					"application/x-www-form-urlencoded");
			DataOutputStream out = new DataOutputStream(conn.getOutputStream());

			out.writeBytes(content.toString());
			out.flush();
			out.close();
		}
		return conn.getInputStream();
	}

	public static String makeAPIRequest(String path, Map<String, String> post)
			throws IOException {
		InputStream in = makeRawAPIRequest(path, post);
		Reader bIn = new InputStreamReader(in);
		StringBuilder data = new StringBuilder();
		char[] buffer = new char[4096];
		while (true) {
			int read = bIn.read(buffer);
			if (read == -1) {
				break;
			}
			data.append(buffer, 0, read);
		}
		bIn.close();
		return data.toString();
	}
}
