package com.tencent.jinjingcao.wavetrack;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;

public class ThumbScroller extends WaveScroller {

    private static final String TAG = "ThumbScroller";
    private static final float THUMB_SIZE = 40;

    ArrayList<Thumb> data = new ArrayList<>();

    private Paint mBitPaint;
    private Paint mTextPaint;
    private Rect mRectSrc = new Rect();

    protected long mTimePerWave;

    private ThumbFetchListener fetchListener = new ThumbFetchListener() {
        @Override
        public void doFetch(long timeStamp, int index) {
            Log.d(TAG, "doFetch() called with: timeStamp = [" + timeStamp + "], index = [" + index + "]");
        }
    };

    public ThumbScroller(Context context) {
        super(context);
    }

    public ThumbScroller(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        mBitPaint = new Paint();
//        mBitPaint.setAntiAlias(true);
        mBitPaint.setDither(true);
        mBitPaint.setFilterBitmap(true);

        mTextPaint = new Paint();
        mTextPaint.setColor(Color.YELLOW);
        mTextPaint.setTextSize(50);
    }

    @Override
    void initSize() {
        if (data.size() <= 0 || mDuration <= 0) {
            Log.e(TAG, "initSize, illArguments.");
            return;
        }

        // pix per second.
//        mPIX_PER_SECOND = (int) ((mRealWidth - mLeftPadding - mRightPadding) * 1000 / Math.min(TrackSelectorWave.MAX_TIME_SELECTED, mDuration));

//        mWaveWidth = THUMB_SIZE * mDensity;//40 *
        Log.w(TAG, "fix wave width." + mWaveWidth);

        sWAVE_MAX_HEIGHT = mRealHeight - 4 * mDensity;
        sPAGE_MAX_COUNT = (int) Math.ceil((mRealWidth - mLeftPadding - mRightPadding) / mWaveWidth);

//        mRectSrc.right = mThumbWidth;//(int) mWaveWidth;
//        mRectSrc.bottom = mThumbHeight;//mRealHeight;

        Log.i(TAG, "sPAGE_MAX_COUNT:" + sPAGE_MAX_COUNT + ", mPIX_PER_SECOND:" + mPIX_PER_SECOND);
    }

    @Override
    public void initItemData() {
        if (mDuration <= 0) {
            Log.e(TAG, "initThumbData.");
            return;
        }
        mTimePerWave = Util.getTsByPix(mWaveWidth, getPixPerSecond());
        for (int i = 0; i < mDuration; i += mTimePerWave) {
            add(new Thumb(i));
        }
    }

    @Override
    public void initPixPerSec() {
        // 不足30秒的也铺满
        mPIX_PER_SECOND = (int) ((mRealWidth - mLeftPadding - mRightPadding) * 1000 / Math.min(TrackSelectorWave.MAX_TIME_SELECTED, mDuration));
        mWaveWidth = THUMB_SIZE * mDensity;//40 *
    }

    @Override
    protected void doDraw(Canvas canvas) {
        float startingLeft = mCurrentLeft;//xxxTmp >= 0 ? xxxTmp : mCurrentLeft;
        mStartIndex = (int) ((startingLeft/* + mLeftPadding*/) / mWaveWidth);
        mLastAvailableLeft = startingLeft;
        int offset = (int) ((startingLeft/* + mLeftPadding*/) % mWaveWidth);// offset for image, like case bitmap draw a half.

//        Log.e(TAG, "MMMM:" + mCurrentLeft + ", offset: " + offset + ", mStartIndex:" + mStartIndex);

        ConcurrentLinkedQueue<Thumb> careData = getCurrentPageData(mStartIndex);
        Iterator<Thumb> it = careData.iterator();
        int index = 0;
        while (it.hasNext()) {
            // draw.
            Thumb dt = it.next();
            float left = index * mWaveWidth + mLeftPadding - offset;// - 10 * mDensity;// keep left padding

            if (left > mRealWidth - mRightPadding) {// keep right padding
                break;
            }
            //            canvas.drawText(dt.volume + ";", left, 50, mTextPaint);
            //            Log.v(TAG, "height:" + dt.volume);

            float right = left + mWaveWidth;
            float top = 0;
//            float bottom = (float) mRealHeight - dt.tmp;//forDebug
            float bottom = (float) mRealHeight;

            // bitmap
            Bitmap tmp = getBitmap(dt);

            // src
            if (index == 0) {
                mRectSrc.left = (int) ((offset / mWaveWidth) * tmp.getWidth());
                left = Math.max(mLeftPadding, left);
            } else {
                mRectSrc.left = 0;
            }

            if (index >= careData.size() - 2) {// first one may have viewable, the last one is size - 2.
//                float lastPPP = mRealWidth - mLeftPadding - left;
                right = Math.min(mRealWidth - mRightPadding, right);
                float lastPPP = right - left;
                mRectSrc.right = (int) ((1 - ((mWaveWidth - lastPPP) / mWaveWidth)) * tmp.getWidth());// todo, need verify.
            } else {
                mRectSrc.right = tmp.getWidth();
            }
            mRectSrc.bottom = tmp.getHeight();

            // dest
//            left = Math.max(mLeftPadding, left);
//            right = Math.min(mRealWidth - mRightPadding, right);
            rect.set(left, top, right, bottom);
            canvas.drawBitmap(tmp, mRectSrc, rect, mBitPaint);
//            canvas.drawText(index+";", left, 50, mTextPaint);// for debug

            index++;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {

        if (!mInited) return;

        if (mWaveWidth <= 0) {
            Log.e(TAG, "skip to. mWaveWidth error." + mWaveWidth);
            return;
        }

        if (mMaxScrollX >= 0 && !isAvailed(mCurrentLeft)/*x >= mMaxScrollX || x < 0*/) {
            Log.e(TAG, "skip to. mMaxScrollX error." + "x:" + mCurrentLeft + ", max:" + mMaxScrollX);
            mCurrentLeft = mLastAvailableLeft;
        }

        doDraw(canvas);

//        super.onDraw(canvas);
    }

    private Bitmap getBitmap(Thumb thumb) {
        Bitmap tmp = thumb.getThumb();
        if (tmp == null) {
            fetchListener.doFetch(thumb.timeStamp, -1);
            return ((BitmapDrawable) getResources().getDrawable(R.drawable.thumb_default)).getBitmap();
//            return ((BitmapDrawable) getResources().getDrawable(R.drawable.test)).getBitmap();// for debug
        }
        return tmp;
    }

    private ConcurrentLinkedQueue<Thumb> getCurrentPageData(int index) {
        int pageMax = Math.min(data.size(), sPAGE_MAX_COUNT + 1);// extra 1 for draw half bitmap of left and right.
        int lastPageIndex = data.size() - pageMax;

        if (index < 0) index = 0;
        if (index > lastPageIndex) index = lastPageIndex;

        mMaxScrollX = (int) (mWaveWidth * lastPageIndex);

        ConcurrentLinkedQueue<Thumb> currentPageData = new ConcurrentLinkedQueue<>();
        List<Thumb> subList = data.subList(index, index + pageMax);
        currentPageData.addAll(subList);

        return currentPageData;
    }

    @Override
    protected int getMaxEndX() {
        return Math.round(Math.min(Integer.MAX_VALUE, data.size() * mWaveWidth + mLeftPadding + mRightPadding));
    }

    @Override
    public int getPixPerSecond() {
        return mPIX_PER_SECOND;
    }

    @Override
    public void setDuration(long ts) {
        super.setDuration(ts);
    }

    public void add(Thumb thumb) {
        data.add(thumb);
    }

    @WorkerThread
    public void onFetchBack(long timeStamp, int index, Bitmap bitmap) {
        Log.d(TAG, "onFetchBack() called with: timeStamp = [" + timeStamp + "], index = [" + index + "], bitmap = [" + bitmap + "]");

        if (mTimePerWave <= 0) {
            Log.e(TAG, "onFetchBack, mTimePerWave need init.");
            return;
        }

        // find the nearest bitmap
        for (Thumb dt : data) {
            if (timeStamp >= dt.timeStamp && timeStamp < dt.timeStamp + mTimePerWave) {
                dt.setBitmap(bitmap);
                Log.i(TAG, "onFetchBack, setBitmap data." + dt.timeStamp);
                postInvalidate();
                break;
            }
        }
    }

    public void setThumbFetchListener(ThumbFetchListener listener) {
        this.fetchListener = listener;
    }

    public interface ThumbFetchListener {
        void doFetch(long timeStamp, int index);
    }
}
