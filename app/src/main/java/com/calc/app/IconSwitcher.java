package com.calc.app;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

public final class IconSwitcher {

    public static final class Variant {
        public final String alias;
        public final int nameRes;
        public final int iconRes;

        Variant(String alias, int nameRes, int iconRes) {
            this.alias = alias;
            this.nameRes = nameRes;
            this.iconRes = iconRes;
        }
    }

    public static final Variant[] ALL = {
            new Variant("IconBlue", R.string.icon_blue, R.mipmap.ic_launcher_blue),
            new Variant("IconViolet", R.string.icon_violet, R.mipmap.ic_launcher_violet),
            new Variant("IconGreen", R.string.icon_green, R.mipmap.ic_launcher_green),
            new Variant("IconOrange", R.string.icon_orange, R.mipmap.ic_launcher_orange),
            new Variant("IconRed", R.string.icon_red, R.mipmap.ic_launcher_red),
            new Variant("IconPink", R.string.icon_pink, R.mipmap.ic_launcher_pink),
            new Variant("IconGraphite", R.string.icon_graphite, R.mipmap.ic_launcher_graphite),
            new Variant("IconMidnight", R.string.icon_midnight, R.mipmap.ic_launcher),
            new Variant("IconGold", R.string.icon_gold, R.mipmap.ic_launcher_gold)
    };

    public static final int DEFAULT_INDEX = 7;

    private IconSwitcher() {}

    private static ComponentName component(Context c, int index) {
        return new ComponentName(c, "com.calc.app." + ALL[index].alias);
    }

    public static int current(Context c) {
        PackageManager pm = c.getPackageManager();
        for (int i = 0; i < ALL.length; i++) {
            if (pm.getComponentEnabledSetting(component(c, i)) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                return i;
            }
        }
        return DEFAULT_INDEX;
    }

    public static void apply(Context c, int index) {
        if (index < 0 || index >= ALL.length) return;
        PackageManager pm = c.getPackageManager();
        pm.setComponentEnabledSetting(component(c, index),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
        for (int i = 0; i < ALL.length; i++) {
            if (i == index) continue;
            pm.setComponentEnabledSetting(component(c, i),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
        }
    }
}
