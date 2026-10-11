package com.exteragram.messenger.ai.network;

import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.FileLog;

import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import tw.nekomimi.nekogram.utils.HttpClient;

public final class LangSearchClient {

    private static final String API_URL = "https://api.langsearch.com/v1/web-search";
    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");

    private static final OkHttpClient client = HttpClient.INSTANCE.getLlmInstance().newBuilder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .callTimeout(25, TimeUnit.SECONDS)
            .build();

    private LangSearchClient() {}

    public static String search(String query, String apiKey) {
        if (TextUtils.isEmpty(apiKey) || TextUtils.isEmpty(apiKey.trim())) {
            return "LangSearch error: API key is not configured.";
        }
        if (TextUtils.isEmpty(query) || TextUtils.isEmpty(query.trim())) {
            return "Search query cannot be empty.";
        }

        try {
            JSONObject bodyJson = new JSONObject();
            bodyJson.put("query", query.trim());
            bodyJson.put("count", 5);

            RequestBody requestBody = RequestBody.create(bodyJson.toString(), JSON_MEDIA_TYPE);
            Request request = new Request.Builder()
                    .url(API_URL)
                    .header("Authorization", "Bearer " + apiKey.trim())
                    .header("Content-Type", "application/json")
                    .post(requestBody)
                    .build();

            Call call = client.newCall(request);
            try (Response response = call.execute()) {
                ResponseBody respBody = response.body();
                String raw = respBody != null ? respBody.string() : "";
                int httpCode = response.code();

                if (httpCode == 401 || httpCode == 403) {
                    return "LangSearch error: Invalid or unauthorized API key (HTTP " + httpCode + "). Please verify your key in AI Settings.";
                }
                if (httpCode == 429) {
                    return "LangSearch error: Rate limit reached (HTTP 429). Please try again later.";
                }
                if (!response.isSuccessful()) {
                    String msg = "HTTP " + httpCode;
                    try {
                        JSONObject errJson = new JSONObject(raw);
                        if (errJson.has("message")) {
                            msg += ": " + errJson.getString("message");
                        }
                    } catch (Exception ignore) {}
                    return "LangSearch request failed: " + msg;
                }

                JSONObject json = new JSONObject(raw);
                JSONObject data = json.optJSONObject("data");
                if (data == null) {
                    return "No search results returned for query: \"" + query + "\"";
                }

                JSONObject webPages = data.optJSONObject("webPages");
                if (webPages == null) {
                    return "No web pages found for query: \"" + query + "\"";
                }

                JSONArray value = webPages.optJSONArray("value");
                if (value == null || value.length() == 0) {
                    return "No matching search results found for query: \"" + query + "\"";
                }

                StringBuilder sb = new StringBuilder();
                sb.append("Web search results for: \"").append(query).append("\"\n\n");
                int limit = Math.min(value.length(), 5);
                for (int i = 0; i < limit; i++) {
                    JSONObject item = value.optJSONObject(i);
                    if (item == null) continue;
                    String title = item.optString("name", item.optString("title", "Untitled"));
                    String url = item.optString("url", item.optString("link", ""));
                    String snippet = item.optString("snippet", item.optString("text", item.optString("description", "")));
                    String date = item.optString("datePublished", "");

                    sb.append(i + 1).append(". ").append(title).append("\n");
                    if (!TextUtils.isEmpty(url)) {
                        sb.append("   Source: ").append(url).append("\n");
                    }
                    if (!TextUtils.isEmpty(date)) {
                        sb.append("   Date: ").append(date).append("\n");
                    }
                    if (!TextUtils.isEmpty(snippet)) {
                        sb.append("   Summary: ").append(snippet.trim()).append("\n");
                    }
                    sb.append("\n");
                }
                return sb.toString().trim();
            }
        } catch (Exception e) {
            FileLog.e("LangSearch error: " + e.getMessage(), e);
            return "Failed to perform web search: " + (e.getMessage() != null ? e.getMessage() : e.toString());
        }
    }
}
