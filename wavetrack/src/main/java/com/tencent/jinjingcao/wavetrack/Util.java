package com.tencent.jinjingcao.wavetrack;

/**
 * util
 * Created by jinjingcao on 2018/3/21.
 */

public class Util {
    /**
     * 默认声道数
     */
    private static final int DEFAULT_CHANNELS = 2;

    /**
     * 默认位深
     */
    private static final int DEFAULT_BIT_DEPTH = 2;

    /**
     * 默认采样率
     */
    private static final int DEFAULT_SAMPLE_RATE = 44100;

    //    public static final int mPIX_PER_SECOND = 100;//1s = 100px
    public static final int SECOND_TO_PIC_BYTE = 100;//1s = 100byte


    public static float getPixByTs(long ts, int SECOND_TO_PIX) {
        return (ts / 1000.0f) * SECOND_TO_PIX;
    }

    public static long getTsByPix(float px, int SECOND_TO_PIX) {
        return (long) Math.round((px / SECOND_TO_PIX) * 1000);
    }

    public static float getTsFloatByPix(float px, int SECOND_TO_PIX) {
        return (px / SECOND_TO_PIX) * 1000;
    }

    public static float getByteSizeByTs(long bs) {
        return 9998888;
    }

    public static long getTsByByteSize(float px) {
        return 9999999;
    }

    public static int byteSizeToTimeMillis(int byteSize) {
        return byteSizeToTimeMillis(byteSize, DEFAULT_SAMPLE_RATE, DEFAULT_CHANNELS, DEFAULT_BIT_DEPTH);
    }

    private static int byteSizeToTimeMillis(int byteSize, int sampleRate, int channels, int bitDepth) {
        // 每个声道字节数
        double byteSizePerChannel = ((double) byteSize) / channels;
        // 每个声道采样数
        double samplePerChannel = byteSizePerChannel / bitDepth;
        // 时长，单位秒
        double duration = samplePerChannel / sampleRate;
        // 时长，单位毫秒
        double d = (duration * 1000);
        return (int) d;
    }
}
