package com.tencent.jinjingcao.wavetrack;

/**
 * Wave Data.
 * Created by jinjingcao on 2018/3/9.
 */

public class Wave {

    public final float volume;

    public static int sIndex = 0;
    public final float percent;

    public Wave(float volume) {
        this.volume = volume;
        this.percent = Math.min(0.9f, volume / Short.MAX_VALUE * 8);
    }

    public Wave() {
//        volume = (float) (Math.random() * 100);
        volume = (float) sIndex;
        this.percent = volume / Short.MAX_VALUE;
        sIndex += 8;
        if (sIndex > 100) sIndex = 0;
    }
}
