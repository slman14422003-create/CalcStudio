package com.calc.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class OnlineLlm {

    public static final String[][] PRESETS = {
            {"groq", "Groq", "https://api.groq.com/openai/v1/chat/completions", "llama-3.1-8b-instant", "https://console.groq.com/keys"},
            {"gemini", "Gemini", "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions", "gemini-2.5-flash", "https://aistudio.google.com/apikey"},
            {"openrouter", "OpenRouter", "https://openrouter.ai/api/v1/chat/completions", "meta-llama/llama-3.3-70b-instruct:free", "https://openrouter.ai/keys"},
            {"custom", "Custom", "", "", ""}
    };

    private volatile HttpURLConnection conn;

    public static String[] preset(String id) {
        for (String[] p : PRESETS) {
            if (p[0].equals(id)) return p;
        }
        return PRESETS[PRESETS.length - 1];
    }

    public void cancel() {
        final HttpURLConnection c = conn;
        if (c == null) return;
        new Thread(c::disconnect).start();
    }

    public String chat(String url, String key, String model, String system, String user, int maxTokens) throws IOException {
        byte[] bytes;
        try {
            JSONArray msgs = new JSONArray();
            msgs.put(new JSONObject().put("role", "system").put("content", system));
            msgs.put(new JSONObject().put("role", "user").put("content", user));
            JSONObject body = new JSONObject();
            body.put("model", model);
            body.put("messages", msgs);
            body.put("temperature", 0.1);
            body.put("max_tokens", maxTokens);
            bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        } catch (org.json.JSONException e) {
            throw new IOException(e.getMessage());
        }
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        conn = c;
        try {
            c.setRequestMethod("POST");
            c.setConnectTimeout(15000);
            c.setReadTimeout(60000);
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            c.setRequestProperty("Accept", "application/json");
            if (key != null && !key.isEmpty()) c.setRequestProperty("Authorization", "Bearer " + key);
            c.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream out = c.getOutputStream()) {
                out.write(bytes);
            }
            int code = c.getResponseCode();
            String text = read(code / 100 == 2 ? c.getInputStream() : c.getErrorStream());
            if (code / 100 != 2) throw new IOException(errorMessage(code, text));
            return parse(text);
        } finally {
            c.disconnect();
            if (conn == c) conn = null;
        }
    }

    private static String read(InputStream in) throws IOException {
        if (in == null) return "";
        try (InputStream s = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = s.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString("UTF-8");
        }
    }

    private static String parse(String text) throws IOException {
        try {
            JSONObject o = new JSONObject(text);
            JSONObject msg = o.getJSONArray("choices").getJSONObject(0).getJSONObject("message");
            if (msg.isNull("content")) return "";
            return msg.getString("content");
        } catch (org.json.JSONException e) {
            throw new IOException("Bad response");
        }
    }

    private static String errorMessage(int code, String text) {
        String detail = null;
        try {
            String t = text.trim();
            JSONObject o = t.startsWith("[") ? new JSONArray(t).getJSONObject(0) : new JSONObject(t);
            Object err = o.opt("error");
            if (err instanceof JSONObject) detail = ((JSONObject) err).optString("message", null);
            else if (err != null) detail = String.valueOf(err);
        } catch (Throwable ignored) {
        }
        if (detail == null || detail.isEmpty()) return "HTTP " + code;
        if (detail.length() > 160) detail = detail.substring(0, 160);
        return "HTTP " + code + ": " + detail;
    }
}
