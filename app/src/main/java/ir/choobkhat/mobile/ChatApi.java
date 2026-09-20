package ir.choobkhat.mobile;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** OpenAI-compatible /chat/completions transport; never log keys or message bodies. */
final class ChatApi {
    static String post(String baseUrl, String apiKey, String model, JSONArray messages) throws Exception {
        if (!baseUrl.startsWith("https://")) throw new IllegalArgumentException("آدرس باید HTTPS باشد.");
        URL url = new URL(baseUrl.replaceAll("/+$", "") + "/chat/completions");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST"); conn.setConnectTimeout(15000); conn.setReadTimeout(60000);
        conn.setDoOutput(true);
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        try {
            byte[] body = new JSONObject().put("model", model).put("messages", messages).toString().getBytes("UTF-8");
            try (OutputStream out = conn.getOutputStream()) { out.write(body); }
            int code = conn.getResponseCode();
            InputStream stream = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
            String response = read(stream);
            if (code < 200 || code >= 300) {
                String detail = "";
                try { detail = new JSONObject(response).optJSONObject("error").optString("message", ""); } catch (Exception ignored) { }
                throw new Exception("HTTP " + code + (detail.isEmpty() ? "" : ": " + detail));
            }
            String content = new JSONObject(response).getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content", "");
            if (content.isEmpty()) throw new Exception("پاسخ متنی از مدل دریافت نشد.");
            return content;
        } finally { conn.disconnect(); }
    }
    private static String read(InputStream in) throws Exception {
        if (in == null) return "";
        try (InputStream stream = in; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096]; int n;
            while ((n = stream.read(buffer)) != -1) {
                output.write(buffer, 0, n);
                if (output.size() > 1024 * 1024) throw new Exception("پاسخ بیش از حد بزرگ است.");
            }
            return output.toString("UTF-8");
        }
    }
}
