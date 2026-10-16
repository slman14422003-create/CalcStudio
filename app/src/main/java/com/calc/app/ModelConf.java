package com.calc.app;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class ModelConf {

    public final String id;
    public final String label;
    public final String tier;
    public final String url;
    public final String fileName;
    public final String template;
    public final String role;
    public final String toolProtocol;
    public final int sizeMb;
    public final int ramMb;
    public final int maxOutputTokens;
    public final int threads;
    public final int topK;
    public final int maxToolCalls;
    public final int idleReleaseSec;
    public final double topP;
    public final double temperature;
    public final boolean tools;
    public final List<String> doList;
    public final List<String> dontList;

    private static List<ModelConf> cache;

    private ModelConf(JSONObject m, JSONObject d, boolean forceNoTools) {
        id = m.optString("id", "custom");
        label = m.optString("label", id);
        tier = m.optString("tier", "balanced");
        url = m.optString("url", "");
        fileName = url.isEmpty() ? m.optString("fileName", "") : fileNameFromUrl(url);
        template = m.optString("template", d.optString("template", "chatml"));
        role = m.optString("role", d.optString("role", ""));
        toolProtocol = m.optString("toolProtocol", d.optString("toolProtocol", ""));
        sizeMb = m.optInt("sizeMb", d.optInt("sizeMb", 0));
        ramMb = m.optInt("ramMb", d.optInt("ramMb", 0));
        maxOutputTokens = m.optInt("maxOutputTokens", d.optInt("maxOutputTokens", 320));
        threads = m.optInt("threads", d.optInt("threads", 0));
        topK = m.optInt("topK", d.optInt("topK", 10));
        maxToolCalls = m.optInt("maxToolCalls", d.optInt("maxToolCalls", 3));
        idleReleaseSec = m.optInt("idleReleaseSec", d.optInt("idleReleaseSec", 60));
        topP = m.optDouble("topP", d.optDouble("topP", 0.8));
        temperature = m.optDouble("temperature", d.optDouble("temperature", 0.1));
        tools = !forceNoTools && m.optBoolean("tools", d.optBoolean("tools", false));
        List<String> dos = toList(d.optJSONArray("do"));
        dos.addAll(toList(m.optJSONArray("do")));
        List<String> donts = toList(d.optJSONArray("dont"));
        donts.addAll(toList(m.optJSONArray("dont")));
        doList = Collections.unmodifiableList(dos);
        dontList = Collections.unmodifiableList(donts);
    }

    private static List<String> toList(JSONArray a) {
        List<String> out = new ArrayList<>();
        if (a == null) return out;
        for (int i = 0; i < a.length(); i++) {
            String s = a.optString(i, "");
            if (!s.isEmpty()) out.add(s);
        }
        return out;
    }

    public static String fileNameFromUrl(String raw) {
        String n = raw == null ? "" : raw;
        int q = n.indexOf('?');
        if (q >= 0) n = n.substring(0, q);
        int s = n.lastIndexOf('/');
        if (s >= 0) n = n.substring(s + 1);
        n = n.replaceAll("[^A-Za-z0-9._-]", "_");
        if (n.isEmpty()) n = "model";
        if (n.length() > 100) n = n.substring(n.length() - 100);
        if (!n.toLowerCase(Locale.US).endsWith(".litertlm")) n = n + ".litertlm";
        return n;
    }

    private static JSONObject loadRoot(Context ctx) {
        try (InputStream in = ctx.getAssets().open("models.json")) {
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            return new JSONObject(bos.toString("UTF-8"));
        } catch (Throwable t) {
            return new JSONObject();
        }
    }

    public static synchronized List<ModelConf> catalog(Context ctx) {
        if (cache != null) return cache;
        JSONObject root = loadRoot(ctx);
        JSONObject d = root.optJSONObject("defaults");
        if (d == null) d = new JSONObject();
        JSONArray arr = root.optJSONArray("models");
        List<ModelConf> out = new ArrayList<>();
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject m = arr.optJSONObject(i);
                if (m != null) out.add(new ModelConf(m, d, false));
            }
        }
        cache = Collections.unmodifiableList(out);
        return cache;
    }

    public static ModelConf forFile(Context ctx, String fileName, long fileBytes) {
        for (ModelConf c : catalog(ctx)) {
            if (c.fileName.equals(fileName)) return c;
        }
        JSONObject root = loadRoot(ctx);
        JSONObject d = root.optJSONObject("defaults");
        if (d == null) d = new JSONObject();
        try {
            String lower = fileName == null ? "" : fileName.toLowerCase(Locale.US);
            JSONObject m = new JSONObject();
            m.put("id", "custom");
            m.put("label", fileName == null ? "Custom" : fileName);
            m.put("fileName", fileName == null ? "" : fileName);
            m.put("template", lower.contains("qwen3") ? "qwen3" : lower.contains("phi") ? "phi" : "chatml");
            long mb = fileBytes / (1024L * 1024L);
            m.put("sizeMb", mb);
            m.put("ramMb", (long) (mb * 1.8));
            m.put("tools", mb >= 700);
            m.put("threads", mb >= 1500 ? 4 : mb >= 700 ? 3 : 2);
            m.put("idleReleaseSec", mb >= 1500 ? 90 : 45);
            return new ModelConf(m, d, false);
        } catch (Throwable t) {
            return fallback();
        }
    }

    public static ModelConf fallback() {
        try {
            JSONObject m = new JSONObject();
            m.put("id", "fallback");
            return new ModelConf(m, new JSONObject(), true);
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }

    public static ModelConf recommended(Context ctx) {
        long total = LocalLlm.totalMemoryMb(ctx);
        ModelConf best = null;
        for (ModelConf c : catalog(ctx)) {
            if (c.ramMb <= total * 0.32) best = c;
        }
        if (best == null && !catalog(ctx).isEmpty()) best = catalog(ctx).get(0);
        return best;
    }

    public String systemPrompt(boolean withTools) {
        StringBuilder sb = new StringBuilder();
        if (!role.isEmpty()) sb.append(role).append('\n');
        if (!doList.isEmpty()) {
            sb.append("Do:\n");
            for (String s : doList) sb.append("- ").append(s).append('\n');
        }
        if (!dontList.isEmpty()) {
            sb.append("Do not:\n");
            for (String s : dontList) sb.append("- ").append(s).append('\n');
        }
        if (withTools && tools && !toolProtocol.isEmpty()) sb.append(toolProtocol).append('\n');
        return sb.toString().trim();
    }
}
