package com.calc.app;

import java.util.LinkedHashMap;
import java.util.Map;

public final class Converter {

    public static final String LENGTH = "length";
    public static final String WEIGHT = "weight";
    public static final String TEMP = "temp";
    public static final String AREA = "area";
    public static final String VOLUME = "volume";
    public static final String SPEED = "speed";
    public static final String TIME = "time";
    public static final String DATA = "data";
    public static final String ENERGY = "energy";
    public static final String PRESSURE = "pressure";
    public static final String POWER = "power";
    public static final String ANGLE = "angle";

    public static final String[] CATEGORIES = {
            LENGTH, WEIGHT, TEMP, AREA, VOLUME, SPEED, TIME, DATA, ENERGY, PRESSURE, POWER, ANGLE
    };

    public static Map<String, Double> units(String category) {
        Map<String, Double> m = new LinkedHashMap<>();
        switch (category) {
            case LENGTH:
                m.put("mm", 0.001); m.put("cm", 0.01); m.put("m", 1.0); m.put("km", 1000.0);
                m.put("\u00b5m", 0.000001); m.put("in", 0.0254); m.put("ft", 0.3048); m.put("yd", 0.9144);
                m.put("mi", 1609.344); m.put("nmi", 1852.0);
                break;
            case WEIGHT:
                m.put("mg", 0.000001); m.put("g", 0.001); m.put("kg", 1.0); m.put("ton", 1000.0);
                m.put("oz", 0.028349523125); m.put("lb", 0.45359237); m.put("st", 6.35029318);
                break;
            case AREA:
                m.put("cm\u00b2", 0.0001); m.put("m\u00b2", 1.0); m.put("km\u00b2", 1000000.0); m.put("ha", 10000.0);
                m.put("in\u00b2", 0.00064516); m.put("ft\u00b2", 0.09290304); m.put("yd\u00b2", 0.83612736);
                m.put("ac", 4046.8564224); m.put("mi\u00b2", 2589988.110336);
                break;
            case VOLUME:
                m.put("mL", 0.001); m.put("L", 1.0); m.put("m\u00b3", 1000.0);
                m.put("tsp", 0.00492892159375); m.put("tbsp", 0.01478676478125); m.put("fl oz", 0.0295735295625);
                m.put("cup", 0.2365882365); m.put("pt", 0.473176473); m.put("qt", 0.946352946); m.put("gal", 3.785411784);
                break;
            case SPEED:
                m.put("m/s", 1.0); m.put("km/h", 1.0 / 3.6); m.put("mph", 0.44704); m.put("knot", 1852.0 / 3600.0);
                m.put("ft/s", 0.3048);
                break;
            case DATA:
                m.put("bit", 0.125); m.put("byte", 1.0); m.put("KB", 1024.0); m.put("MB", 1048576.0);
                m.put("GB", 1073741824.0); m.put("TB", 1099511627776.0); m.put("PB", 1125899906842624.0);
                break;
            case TIME:
                m.put("ms", 0.001); m.put("s", 1.0); m.put("min", 60.0); m.put("h", 3600.0);
                m.put("day", 86400.0); m.put("week", 604800.0); m.put("month", 2629800.0); m.put("year", 31557600.0);
                break;
            case ENERGY:
                m.put("J", 1.0); m.put("kJ", 1000.0); m.put("cal", 4.184); m.put("kcal", 4184.0);
                m.put("Wh", 3600.0); m.put("kWh", 3600000.0); m.put("BTU", 1055.05585262);
                break;
            case PRESSURE:
                m.put("Pa", 1.0); m.put("kPa", 1000.0); m.put("bar", 100000.0); m.put("atm", 101325.0);
                m.put("psi", 6894.757293168); m.put("mmHg", 133.322387415);
                break;
            case POWER:
                m.put("W", 1.0); m.put("kW", 1000.0); m.put("MW", 1000000.0); m.put("hp", 745.699871582);
                m.put("BTU/h", 0.29307107017);
                break;
            case ANGLE:
                m.put("deg", 1.0); m.put("rad", 180.0 / Math.PI); m.put("grad", 0.9); m.put("turn", 360.0);
                break;
            case TEMP:
                m.put("\u00b0C", 0.0); m.put("\u00b0F", 0.0); m.put("K", 0.0);
                break;
            default:
                break;
        }
        return m;
    }

    public static double convert(String category, String from, String to, double value) {
        if (category.equals(TEMP)) return convertTemp(from, to, value);
        Map<String, Double> units = units(category);
        Double f = units.get(from);
        Double t = units.get(to);
        if (f == null || t == null) return value;
        return value * f / t;
    }

    private static double convertTemp(String from, String to, double v) {
        double celsius;
        switch (from) {
            case "\u00b0F": celsius = (v - 32) * 5.0 / 9.0; break;
            case "K": celsius = v - 273.15; break;
            default: celsius = v;
        }
        switch (to) {
            case "\u00b0F": return celsius * 9.0 / 5.0 + 32;
            case "K": return celsius + 273.15;
            default: return celsius;
        }
    }
}
