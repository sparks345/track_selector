package com.tencent.jinjingcao.wavetrack;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.support.annotation.Nullable;

public class Thumb {
    public final long timeStamp;
    public final long tmp;
    protected Bitmap bitmap;

    public Thumb(long timeStamp) {
        this.timeStamp = timeStamp;
        this.tmp = (long) (Math.random() * 120);
    }

    @Nullable
    public Bitmap getThumb() {
        return this.bitmap;
    }

    public void setBitmap(Bitmap bitmap) {
        this.bitmap = bitmap;
    }
}
