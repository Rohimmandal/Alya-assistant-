package com.alya.assistant;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

final class GeminiClient {
    interface ConnectionListener {
        void onConnection(HttpURLConnection connection);
    }

    static String generate(String key, String model, String language, List<JSONObject> messages, List<String> memories, ConnectionListener listener) throws Exception {
        if (key.isEmpty()) throw new IllegalStateException("Add your Gemini API key in Settings to start a conversation.");
        String modelId = model.matches("[A-Za-z0-9._-]+") ? model : "gemini-2.5-flash";
        URL url = java.net.URI.create("https://generativelanguage.googleapis.com/v1beta/models/" + modelId + ":generateContent").toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        listener.onConnection(connection);
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setRequestProperty("x-goog-api-key", key);
        connection.setDoOutput(true);

        JSONArray contents = new JSONArray();
        int start = Math.max(0, messages.size() - 20);
        for (int index = start; index < messages.size(); index++) contents.put(messages.get(index));
        JSONObject payload = new JSONObject();
        StringBuilder instructions = new StringBuilder("You are Alya Assistant, a virtual assistant, not a human. Respond naturally in ")
            .append(language.equals("Automatic") ? "the user's language" : language).append(", unless asked to switch.");
        if (!memories.isEmpty()) instructions.append(" The user explicitly saved these memories for personalization: ").append(String.join("; ", memories)).append('.');
        payload.put("system_instruction", new JSONObject().put("parts", new JSONArray().put(new JSONObject().put("text", instructions.toString()))));
        payload.put("contents", contents);
        payload.put("generationConfig", new JSONObject().put("maxOutputTokens", 2048));

        int status;
        String response;
        try {
            try (OutputStream output = connection.getOutputStream()) {
                output.write(payload.toString().getBytes(StandardCharsets.UTF_8));
            }
            status = connection.getResponseCode();
            InputStream stream = status < 400 ? connection.getInputStream() : connection.getErrorStream();
            response = readFully(stream);
        } finally {
            connection.disconnect();
            listener.onConnection(null);
        }
        if (status == 400 || status == 401 || status == 403) throw new IllegalStateException("Gemini rejected this API key or request. Check Settings.");
        if (status == 429) throw new IllegalStateException("Gemini rate limit or quota reached. Try again later.");
        if (status >= 500) throw new IllegalStateException("Gemini is temporarily unavailable. Try again later.");
        if (status < 200 || status >= 300) throw new IllegalStateException("Gemini request failed (HTTP " + status + ").");
        JSONArray candidates = new JSONObject(response).optJSONArray("candidates");
        if (candidates == null || candidates.length() == 0) throw new IllegalStateException("Gemini returned no text. Please rephrase your message.");
        JSONObject content = candidates.getJSONObject(0).optJSONObject("content");
        JSONArray parts = content == null ? null : content.optJSONArray("parts");
        StringBuilder answer = new StringBuilder();
        if (parts != null) for (int index = 0; index < parts.length(); index++) answer.append(parts.getJSONObject(index).optString("text", ""));
        if (answer.toString().trim().isEmpty()) throw new IllegalStateException("Gemini returned no text. Please rephrase your message.");
        return answer.toString().trim();
    }

    private static String readFully(InputStream stream) throws Exception {
        if (stream == null) return "";
        try (InputStream input = stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }
}