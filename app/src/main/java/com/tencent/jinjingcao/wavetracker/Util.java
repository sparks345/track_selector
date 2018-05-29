package com.tencent.jinjingcao.wavetracker;

import android.app.Activity;

/**
 * Created by jinjingcao on 2018/3/12.
 */

public class Util {

    private static int sScreenWidth;
    private static int sScreenHeight;

    public static int getScreenWidth(Activity context) {
        return context.getWindowManager().getDefaultDisplay().getWidth();
    }
}
