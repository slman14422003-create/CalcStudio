package com.calc.app;

import java.util.LinkedHashMap;
import java.util.Map;

public final class Converter {

    public static final String LENGTH = "length";
    public static final String WEIGHT = "weight";
    public static final String TEMP = "temp";
    public static final String AREA = "area";
    public static final String SPEED = "speed";
    public static final String DATA = "data";
    public static final String TIME = "time";

    public static final String[] CATEGORIES = { LENGTH, WEIGHT, TEMP, AREA, SPEED, DATA, TIME };

    public static Map<String, Double> units(String category) {
        Map<String, Double> m = new LinkedHashMap<>();
        switch (category) {
            case LENGTH:
                m.put("mm", 0.001); m.put("cm", 0.01); m.put("m", 1.0); m.put("km", 1000.0);
                m.put("in", 0.0254); m.put("ft", 0.3048); m.put("yd", 0.9144); m.put("mi", 1609.344);
                break;
            case WEIGHT:
                m.put("mg", 0.000001); m.put("g", 0.001); m.put("kg", 1.0); m.put("ton", 1000.0);
                m.put("oz", 0.0283495); m.put("lb", 0.453592);
                break;
            case AREA:
                m.put("m²", 1.0); m.put("km²", 1000000.0); m.put("ha", 10000.0);
                m.put("ft²", 0.092903); m.put("ac", 4046.86);
                break;
            case SPEED:
                m.put("m/s", 1.0); m.put("km/h", 0.277778); m.put("mph", 0.44704); m.put("knot", 0.514444);
                break;
            case DATA:
                m.put("bit", 0.125); m.put("byte", 1.0); m.put("KB", 1024.0); m.put("MB", 1048576.0);
                m.put("GB", 1073741824.0); m.put("TB", 1099511627776.0);
                break;
            case TIME:
                m.put("ms", 0.001); m.put("s", 1.0); m.put("min", 60.0); m.put("h", 3600.0);
                m.put("day", 86400.0); m.put("week", 604800.0);
                break;
            case TEMP:
                m.put("°C", 0.0); m.put("°F", 0.0); m.put("K", 0.0);
                break;
        }
        return m;
    }

    public static double convert(String category, String from, String to, double value) {
        if (category.equals(TEMP)) return convertTemp(from, to, value);
        Map<String, Double> units = units(category);
        double base = value * units.get(from);
        return base / units.get(to);
    }

    private static double convertTemp(String from, String to, double v) {
        double celsius;
        switch (from) {
            case "°F": celsius = (v - 32) * 5.0 / 9.0; break;
            case "K": celsius = v - 273.15; break;
            default: celsius = v;
        }
        switch (to) {
            case "°F": return celsius * 9.0 / 5.0 + 32;
            case "K": return celsius + 273.15;
            default: return celsius;
        }
    }
}
