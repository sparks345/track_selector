package com.tencent.jinjingcao.wavetrack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.tencent.jinjingcao.wavetrack.ThumbScroller.ThumbFetchListener;

public class TrackSelectorThumb extends TrackSelectorWave implements ThumbFetchListener {

    private static final String TAG = "TrackSelectorThumb";

    private static final long THUMB_MIN_TIME_SELECTED = 5000;

    private HandlerThread mFetchHandlerThread = new HandlerThread("TrackSelectorThumb.FetchBitMap-Local-" + System.currentTimeMillis());
    private final Handler mFetchHandler;

    private long mDefaultOptionTime = MAX_TIME_SELECTED;

    private ThumbFetchListener mFetchListener;

    private CopyOnWriteArrayList<Long> mCachedTimeStampList = new CopyOnWriteArrayList<Long>();
    private HashMap<Long, Boolean> xxxx = new HashMap<>();
    private int cachedSize = 0;

    public static final int MAX_CACHED_SIZE = 24;

    public TrackSelectorThumb(Context context) {
        this(context, null);
    }

    public TrackSelectorThumb(Context context, AttributeSet attrs) {
        super(context, attrs);

//        mRoot = initRootView(context);
//        addView(mRoot);
//
//        initScroller();
//        initEvent();

        mFetchHandlerThread.start();
        mFetchHandler = new Handler(mFetchHandlerThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case MSG_DO_FETCH_THUMB: {
                        long ts = (long) msg.obj;
                        TrackSelectorThumb.this.actionFetch(ts, -1);
                    }
                    break;
                }
            }
        };
    }

    @Override
    protected View initRootView(Context context) {
        return LayoutInflater.from(context).inflate(R.layout.track_selector_thumb, this, false);
    }

    @Override
    void initView() {
        mWaveScroller = (WaveScroller) mRoot.findViewById(R.id.wave_scroller);
        mSeekOption = (ViewGroup) mRoot.findViewById(R.id.seek_option);
        mLeftArrow = (ImageView) mRoot.findViewById(R.id.left_arrow);
        mSeekSpan = (ViewGroup) mRoot.findViewById(R.id.seek_span);
        mSeekSpanText = (TextView) mRoot.findViewById(R.id.seek_span_text);
        mRightArrow = (ImageView) mRoot.findViewById(R.id.right_arrow);


        getViewTreeObserver().addOnPreDrawListener(
                new ViewTreeObserver.OnPreDrawListener() {

                    @Override
                    public boolean onPreDraw() {
                        int w = mSeekOption.getWidth();
                        if (w == 0 || mWaveScroller.mRealWidth == 0 || mWaveScroller.mDuration == 0/* || mWaveScroller.getPixPerSecond() == 0*/) {
                            Log.e(TAG, "width is 0 ...... need retry.");
                            return true;
                        }
                        TrackSelectorThumb.this.getViewTreeObserver().removeOnPreDrawListener(this);

                        LinearLayout.LayoutParams arrowLp = (LinearLayout.LayoutParams) mLeftArrow.getLayoutParams();
                        mMarginFixed = arrowLp.rightMargin;

                        TrackSelectorThumb.this.initAll();

                        return true;
                    }
                });

    }

    @Override
    public long getMinTimeSelected() {
        return THUMB_MIN_TIME_SELECTED;
    }

    @Override
    public void initData(String mp4Path, long totalDuration) {
        Log.d(TAG, "initData() called with: mp4Path = [" + mp4Path + "], totalDuration = [" + totalDuration + "]");

        mWaveScroller.setDuration(totalDuration);//duration
//        mWaveScroller.initItemData();
//        mWaveScroller.initSize();//mPIX_PER_SECOND
//        initOption();//center
//        mWaveScroller.initScroller();
    }

    public void onFetchBack(long timeStamp, int index, Bitmap bitmap) {
        Log.d(TAG, "onFetchBack() called with: timeStamp = [" + timeStamp + "], index = [" + index + "], bitmap = [" + bitmap + "]");
        ((ThumbScroller) mWaveScroller).onFetchBack(timeStamp, index, bitmap);

        postInvalidate();
    }

    public void setThumbFetchListener(ThumbFetchListener listener) {
        ((ThumbScroller) mWaveScroller).setThumbFetchListener(this);
        this.mFetchListener = listener;
    }

    @Override
    public void doFetch(long timeStamp, int index) {
        Log.d(TAG, "doFetch() called with: timeStamp = [" + timeStamp + "], index = [" + index + "]");
        Message msg = mFetchHandler.obtainMessage(MSG_DO_FETCH_THUMB);
        msg.obj = timeStamp;
        mFetchHandler.sendMessage(msg);

        int idx = mCachedTimeStampList.indexOf(timeStamp);
        if (idx > -1) {
            Log.w(TAG, "doFetch.remove." + timeStamp);
            mCachedTimeStampList.remove(idx);
        }
        // move to queue end
        mCachedTimeStampList.add(timeStamp);

        // remove out of max cache size.
        while (mCachedTimeStampList.size() > MAX_CACHED_SIZE) {
            Long oldTimeStamp = mCachedTimeStampList.remove(0);
            ArrayList<Thumb> dt = ((ThumbScroller) mWaveScroller).data;
            for (Thumb dd : dt) {
                if (dd.timeStamp == oldTimeStamp) {
                    Log.w(TAG, "doFetch.recycle bitmap." + oldTimeStamp);
                    dd.bitmap.recycle();
                    dd.bitmap = null;
                    break;
                }
            }
        }
    }

    public void actionFetch(long ts, int i) {
        Log.d(TAG, "actionFetch() called with: ts = [" + ts + "], i = [" + i + "]");
        if (mFetchListener != null) {
            mFetchListener.doFetch(ts, i);
        }
    }

    public long getTimePerWave() {
        return ((ThumbScroller) mWaveScroller).mTimePerWave;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mFetchHandler.getLooper().quit();
        mFetchHandlerThread.quit();
        Log.i(TAG, "onDetachedFromWindow. Tidy threadHandler.");
    }

}
