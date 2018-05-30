package com.tencent.jinjingcao.wavetrack;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;

import android.content.Context;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.tencent.jinjingcao.wavetrack.WaveScroller.WaveScrollListener;

/**
 * Track selector.
 * Created by jinjingcao on 2018/3/7.
 */

public class TrackSelectorWave extends RelativeLayout implements WaveScrollListener {

    private static final String TAG = "TrackSelector";

    public static final int SELECT_CHANGE_DELAY_MILLIS = 400;

    public static final int ACTION_RECOGNITION_OFFSET = 20;

//    public static final int PIX_TO_MILLI_SECOND = 100;//100px = 1s
//    public static final int PIX_TO_PCM_BYTE = 100;//100byte = 1s

    static final int MSG_START_PLAY = 1212;
    static final int MSG_DO_FETCH_THUMB = 1234;

    public static final long MIN_TIME_SELECTED = 10000;// min time
    public static final long MAX_TIME_SELECTED = 30000;// max time

    View mRoot;

    WaveScroller mWaveScroller;
    ViewGroup mSeekOption;
    ImageView mLeftArrow;
    ViewGroup mSeekSpan;
    TextView mSeekSpanText;
    ImageView mRightArrow;

    protected Handler mHandler = new TrackHandler(this);

    private float mLastX;
    private float mLastY;
    private float mDiffX;
    private float mLastLeft;
    private float mLastRight;
    private float mLastWidth;
    private boolean mIsDragRightArrow;
    private boolean mIsDragLeftArrow;

    private boolean mIsDragOption;

    private GestureDetector mDetector;

    private SelectorListener mSelectorListener;

    private float mPreLeft;
    private float mPreRight;
    private float mLastActionDownY;

    private long mLimitStartTime;
    private long mLimitEndTime;

    private long mDefaultOptionTime = MAX_TIME_SELECTED;
    protected float mMarginFixed = 0;

    public TrackSelectorWave(Context context) {
        this(context, null);
    }

    public TrackSelectorWave(Context context, AttributeSet attrs) {
        super(context, attrs);

//        setWillNotDraw(false);

//        TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.TrackSelector);

//        ta.recycle();

        mRoot = initRootView(context);
        addView(mRoot);

        initView();
        initEvent();
    }

    protected View initRootView(Context context) {
        return LayoutInflater.from(context).inflate(R.layout.track_selector_wave, this, false);
    }

    void initView() {
//        mHandler = getHandler();

        mWaveScroller = (WaveScroller) mRoot.findViewById(R.id.wave_scroller);
        mSeekOption = (ViewGroup) mRoot.findViewById(R.id.seek_option);
        mLeftArrow = (ImageView) mRoot.findViewById(R.id.left_arrow);
        mSeekSpan = (ViewGroup) mRoot.findViewById(R.id.seek_span);
        mSeekSpanText = (TextView) mRoot.findViewById(R.id.seek_span_text);
        mRightArrow = (ImageView) mRoot.findViewById(R.id.right_arrow);

//        mDetector = new GestureDetector(this.getContext(), mListener);

        getViewTreeObserver().addOnPreDrawListener(
                new ViewTreeObserver.OnPreDrawListener() {

                    @Override
                    public boolean onPreDraw() {
                        int w = mSeekOption.getWidth();
                        if (w == 0 || mWaveScroller.mRealWidth == 0 || mWaveScroller.mDuration == 0/* || mWaveScroller.getPixPerSecond() == 0*/) {
                            Log.e(TAG, "width is 0 ...... need retry.");
                            return true;
                        }
                        TrackSelectorWave.this.getViewTreeObserver().removeOnPreDrawListener(this);

//                        ViewGroup.LayoutParams lp = mSeekSpan.getLayoutParams();
////                        lp.width = (int) (mDefaultOptionTime / MAX_TIME_SELECTED * mWaveScroller.getWidth());
//                        lp.width = (int) Util.getPixByTs(mDefaultOptionTime, mWaveScroller.getPixPerSecond());
//                        mSeekSpan.setLayoutParams(lp);
//
//                        int center = (TrackSelectorWave.this.getWidth() - mWaveScroller.mLeftPadding - mWaveScroller.mRightPadding - lp.width) / 2;
//                        mSeekOption.setX(center);
//                        postInvalidate();
//
//                        postUpdateTimeSpan();

                        LinearLayout.LayoutParams arrowLp = (LinearLayout.LayoutParams) mLeftArrow.getLayoutParams();
                        mMarginFixed = arrowLp.rightMargin;

                        initAll();
//                        mWaveScroller.initItemData();
//                        mWaveScroller.initSize();
//                        initOption();
//                        mWaveScroller.initScroller();

                        return true;
                    }
                });
    }

    protected void initAll() {
        Log.d(TAG, "initAll() called");
        if (mWaveScroller.mInited || mWaveScroller.mRealWidth == 0) return;

        mWaveScroller.initPixPerSec();//mPIX_PER_SECOND
        mWaveScroller.initItemData();
        mWaveScroller.initSize();//
        initOption();//center
        mWaveScroller.initScroller();

        if (mSelectorListener != null) {
            mSelectorListener.onRender();
        }

        mWaveScroller.postInvalidate();
        postInvalidate();
    }

    public void forchRefresh() {
        mWaveScroller.scrollTo(0, 0);
    }

    protected void initOption() {
        ViewGroup.LayoutParams lp = mSeekSpan.getLayoutParams();
        long time = mDefaultOptionTime;
        if (mWaveScroller.mDuration > 0) {
            time = Math.min(mWaveScroller.mDuration, mDefaultOptionTime);
        }
        lp.width = (int) Util.getPixByTs(time, mWaveScroller.getPixPerSecond());
        mSeekSpan.setLayoutParams(lp);

        int center = (TrackSelectorWave.this.getWidth() - mWaveScroller.mLeftPadding - mWaveScroller.mRightPadding - lp.width) / 2;
        mSeekOption.setX(center);
        postInvalidate();

        TrackSelectorWave.this.postUpdateTimeSpan();
    }

    protected void postUpdateTimeSpan() {
        post(new Runnable() {
            @Override
            public void run() {
                TrackSelectorWave.this.fillSelectedTimeSpan();
            }
        });
    }

    void initEvent() {
        mWaveScroller.setTrackListener(this);
    }

    /**
     * test code
     *
     * @param filePath path
     * @deprecated
     */
    public void initData(String filePath, long ts) {
        File testPCM = new File(filePath);
        if (!testPCM.exists()) {
            Log.e(TAG, "initData error.");
            return;
        }
        FileInputStream stream = null;
        try {
            stream = new FileInputStream(testPCM);
            int totalLen = stream.available();
            int bufferLen = 8 * 1024;//Math.min(1024, totalLen);
            byte[] buffer = new byte[bufferLen];
//            int start = 0;
            stream.skip(512 * 1024 * 8); // you can simple here.
            int cnt = stream.read(buffer, 0, bufferLen);
            int readIndex = cnt;
            while (cnt > 0) {
                if (cnt % 2 != 0) {
                    Log.e(TAG, "invalid pcm cnt." + cnt);
                    break;
                }

                for (int i = 0; i < cnt; i += 2) {
                    byte high = buffer[i + 1];
                    byte low = buffer[i];
                    short volume = (short) ((high << 8) | (low & 0xff));
//                    Log.v(TAG, "v:" + volume);
                    volume = (short) ((volume < 0) ? -volume : volume);
                    mWaveScroller.add(new Wave(volume));
                }

                // todo debug...
                stream.skip(512 * 1024 * 8); // you can simple here.
                cnt = stream.read(buffer, 0, bufferLen);
                readIndex += cnt;
            }

            Log.i(TAG, "len:" + totalLen + ", readIndex:" + readIndex);

            mWaveScroller.setDuration(ts);//duration
//            initAll();


//            post(new Runnable() {
//                @Override
//                public void run() {
//                    syncSelectedAndStartHighLight(false);
//                }
//            });

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * init data.
     *
     * @param bytes data
     */
    public void initData(byte[] bytes, long ts) {
        int cnt = bytes.length;
        if (cnt > 0) {
            if (cnt % 2 != 0) {
                Log.e(TAG, "invalid pcm cnt." + cnt);
                return;
            }

            for (int i = 0; i < cnt; i += 2) {
                byte high = bytes[i + 1];
                byte low = bytes[i];
                short volume = (short) ((high << 8) | (low & 0xff));
//                    Log.v(TAG, "v:" + volume);
                volume = (short) ((volume < 0) ? -volume : volume);
                mWaveScroller.add(new Wave(volume));
            }

            Log.i(TAG, "len:" + cnt);

            mWaveScroller.setDuration(ts);//duration
//            mWaveScroller.initItemData();
//            mWaveScroller.initSize();
//            initOption();
//            mWaveScroller.initScroller();

        }
    }


    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        float touchX = ev.getX();
//        if (!mWaveScroller.isDragging() && touchX > mSeekOption.getX() && touchX < mSeekOption.getX() + mSeekOption.getWidth()) {
        if (!mWaveScroller.isDragging() && touchX > mSeekOption.getX() && touchX < mSeekOption.getX() + mSeekOption.getWidth()) {
            if (touchX > mSeekOption.getX() + mSeekOption.getRight() - mRightArrow.getWidth()) {
                return true;
            } else if (touchX < mSeekOption.getX() + mLeftArrow.getWidth() + mMarginFixed) {
                return true;
            }
            return super.onInterceptTouchEvent(ev);
        } else {
            return super.onInterceptTouchEvent(ev);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        Log.i(TAG, "onTouchEvent." + event.getAction());

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:

                // start point.
                mLastX = event.getX();
                mLastY = event.getY();

                // 拖动左右箭头
                float centerPosX = 0;

                if (mLastX > mSeekOption.getX() + mSeekOption.getRight() - mRightArrow.getWidth()) {
                    mIsDragRightArrow = true;
                    mLastWidth = mSeekSpan.getWidth();

                    mWaveScroller.drawBorder(true);
                    mWaveScroller.clearHighlight();
                } else if (mLastX < mSeekOption.getX() + mLeftArrow.getWidth() + mMarginFixed) {
                    mIsDragLeftArrow = true;
                    mLastLeft = mSeekOption.getX();
                    mLastWidth = mSeekSpan.getWidth();

                    mWaveScroller.drawBorder(true);
                    mWaveScroller.clearHighlight();
                } else {
//                    return mWaveScroller.dispatchTouchEvent(event);
                }

                centerPosX = mSeekOption.getX() + mSeekOption.getWidth() / 2;
                mDiffX = mLastX - centerPosX;

                return true;

            case MotionEvent.ACTION_MOVE:
                float nLastX = event.getX();
                float nLastY = event.getY();
                float xPos = 0;

                // option
//                if (/*nLastX > nLastY && */(Math.abs(nLastX - mLastX) > ACTION_RECOGNITION_OFFSET || true)) {
                if (mIsDragRightArrow) {
                    ViewGroup.LayoutParams lp = mSeekSpan.getLayoutParams();
                    float offsetX = nLastX - mLastX;
                    float rPos = mLastLeft + offsetX;
                    if (mLastWidth + mSeekOption.getX() + mLeftArrow.getWidth() + mMarginFixed + mRightArrow.getWidth() + offsetX <= getWidth()
                            && mLastWidth + offsetX >= getMinSelectWidth(getMinTimeSelected())) {
                        int tmpRight = ((int) (mLastWidth + offsetX));
                        float preLeft = mSeekOption.getX() + mLeftArrow.getWidth() + mMarginFixed;
                        float preRight = preLeft + mSeekSpanText.getWidth();
                        if (mLimitEndTime <= 0 || (Util.getTsByPix((long) preRight, mWaveScroller.getPixPerSecond()) + mWaveScroller.getPageStartTime() <= mLimitEndTime)) {
                            lp.width = tmpRight;
                            mSeekSpan.setLayoutParams(lp);
                            mWaveScroller.clearHighlight();
                            syncSelectedAndStartHighLight(true);
                        }
                    }
                } else if (mIsDragLeftArrow) {
                    float offsetX = nLastX - mLastX;
                    xPos = (float) Math.ceil(mLastLeft + offsetX);// avoid space between array and option text.
                    if (xPos > 0 && (mLastWidth - offsetX) >= getMinSelectWidth(getMinTimeSelected())) {
                        float preLeft = mSeekOption.getX() + mLeftArrow.getWidth() + mMarginFixed;
                        if (mLimitStartTime <= 0 || (Util.getTsByPix((long) preLeft, mWaveScroller.getPixPerSecond()) + mWaveScroller.getPageStartTime() >= mLimitStartTime)) {
                            mSeekOption.setX(xPos);

                            ViewGroup.LayoutParams lp = mSeekSpan.getLayoutParams();
                            lp.width = ((int) (mLastWidth - offsetX));
                            mSeekSpan.setLayoutParams(lp);
                            mWaveScroller.clearHighlight();
                            syncSelectedAndStartHighLight(true);
                        }
                    }
                } else {
                    // action. -- disable drag option.
                    // xPos = nLastX - mSeekOption.getWidth() / 2 - mDiffX;
                    // if (isXPosAvailable(xPos)) {
                    //    mSeekOption.setX(xPos);
                    //}
//                        mWaveScroller.dispatchTouchEvent(event);

//                    syncSelectedAndStartHighLight(true);
                }


                mIsDragOption = true;
                return true;
//                }

//                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mIsDragRightArrow = false;
                mIsDragLeftArrow = false;
                mIsDragOption = false;

                syncSelectedAndStartHighLight(false);
                mWaveScroller.drawBorder(false);
                callbackSelected(mPreLeft, mPreRight);

                break;
        }

//        mDetector.onTouchEvent(event);

        return super.onTouchEvent(event);
    }

    private float getMinSelectWidth(long minTimeSelected) {
        return Util.getPixByTs(minTimeSelected, mWaveScroller.getPixPerSecond());
    }

    protected void fillSelectedTimeSpan() {
        float preLeft = mSeekOption.getX() + mLeftArrow.getWidth() + mMarginFixed;
        float preRight = preLeft + mSeekSpanText.getWidth();
        mSeekSpanText.setText(String.format(getContext().getString(R.string.seek_span_text), getWaveTS(preRight - preLeft)));
    }

    private void postOnSelectChanged(final float preLeft, final float preRight, boolean immdeartely) {
        mHandler.removeMessages(MSG_START_PLAY);

        Message msg = mHandler.obtainMessage(MSG_START_PLAY);
        msg.arg1 = (int) preLeft;
        msg.arg2 = (int) preRight;

        mHandler.sendMessageDelayed(msg, immdeartely ? 0 : SELECT_CHANGE_DELAY_MILLIS);

    }


    public long getWaveTS(float pos) {
//        return Math.round(pos / PIX_TO_MILLI_SECOND);
        return Util.getTsByPix(pos / 1000.0F, mWaveScroller.getPixPerSecond());
    }


    private boolean isXPosAvailable(float xPos) {
        return xPos >= 0 && xPos <= getWidth() - mSeekOption.getWidth();
    }

    public void setSelectSpan(long startTs, long endTs) {
        Log.d(TAG, "setSelectSpan() called with: startTs = [" + startTs + "], endTs = [" + endTs + "]");
        // navigate to correct page.
        mWaveScroller.navigateToTime(startTs, endTs);
        // do select.
        long pageStartTime = mWaveScroller.getPageStartTime(startTs, endTs);
        long scrollByTs = startTs - pageStartTime;
        long scrollByPix = Math.round(Util.getPixByTs(scrollByTs, mWaveScroller.getPixPerSecond()));
//        scrollByPix += mWaveScroller.getPaddingLeft();
        mSeekOption.setX(scrollByPix);

        ViewGroup.LayoutParams lp = mSeekSpan.getLayoutParams();
        lp.width = Math.round(Util.getPixByTs(endTs - startTs, mWaveScroller.getPixPerSecond()));
        mSeekSpan.setLayoutParams(lp);

//        mPreLeft = mSeekOption.getX() + mLeftArrow.getWidth() + mMarginFixed;
//        mPreRight = mPreLeft + mSeekSpanText.getWidth();
//        postOnSelectChanged(mPreLeft, mPreRight);

        postDelayed(new Runnable() {
            @Override
            public void run() {
//                syncSelectedAndStartHighLight();
                fillSelectedTimeSpan();
                mWaveScroller.clearHighlight();
                mPreLeft = mSeekOption.getX() + mLeftArrow.getWidth() + mMarginFixed;
                mPreRight = mPreLeft + mSeekSpanText.getWidth();
                mWaveScroller.startHighLight(mPreLeft, mPreRight);
            }
        }, 400);

        Log.i(TAG, "scrollPix:" + scrollByPix + ", mPreLeft:" + mPreLeft);
    }

    public void setSelectorListener(SelectorListener listener) {
        mSelectorListener = listener;
    }

    /**
     * call back of scroller
     *
     * @param pageStart startPos
     * @param pageEnd   endPos
     */
    @Override
    public void onScrollChanged(float pageStart, float pageEnd) {
        Log.v(TAG, "onScrollChanged() called with: pageStart = [" + pageStart + "], pageEnd = [" + pageEnd + "]");

        float preLeft = /*pageStart +*/ mSeekOption.getX() + mLeftArrow.getWidth() + mMarginFixed;
        float preRight = preLeft + mSeekSpanText.getWidth();
        callbackSelected(preLeft, preRight);

        syncSelectedAndStartHighLight(false);
    }

    @Override
    public void onScrollChanging(float pageStart, float pageEnd) {
        Log.i(TAG, "onScrollChanging() called with: pageStart = [" + pageStart + "], pageEnd = [" + pageEnd + "]");

        float preLeft = /*pageStart +*/ mSeekOption.getX() + mLeftArrow.getWidth() + mMarginFixed;
        float preRight = preLeft + mSeekSpanText.getWidth();
        callbackSelecting(preLeft, preRight);

        syncSelectedAndStartHighLight(false);
    }

    public void syncSelectedAndStartHighLight(boolean immdeartely) {
        Log.d(TAG, "syncSelectedAndStartHighLight() called with: immdeartely = [" + immdeartely + "]");
        mPreLeft = mSeekOption.getX() + mLeftArrow.getWidth() + mMarginFixed;
        mPreRight = mPreLeft + mSeekSpanText.getWidth();

        postOnSelectChanged(mPreLeft, mPreRight, immdeartely);

        fillSelectedTimeSpan();
    }

    public void getPos(Rect rect) {
        if (rect == null) {
            throw new IllegalArgumentException("rect is null.");
        }

        mSeekOption.getGlobalVisibleRect(rect);
    }

    private void callbackSelecting(float preLeft, float preRight) {
        Log.d(TAG, "callbackSelecting() called with: preLeft = [" + preLeft + "], preRight = [" + preRight + "]");
        if (mSelectorListener != null) {
            long pageStart = Math.round(mWaveScroller.getPageStartTime());
            int offset = mWaveScroller.getPaddingLeft();
            mSelectorListener.onSelectChanging(
                    pageStart + Util.getTsByPix(preLeft - offset, mWaveScroller.getPixPerSecond()),
                    pageStart + Util.getTsByPix(preRight - offset, mWaveScroller.getPixPerSecond())
            );
            /*mSelectorListener.onSelectChanging(
                    getTimeStart(),
                    getTimeEnd()
            );*/
        }
    }

    private void callbackSelected(float preLeft, float preRight) {
        Log.d(TAG, "callbackSelected() called with: preLeft = [" + preLeft + "], preRight = [" + preRight + "]");
        if (mSelectorListener != null) {
            long pageStart = Math.round(mWaveScroller.getPageStartTime());
            int offset = mWaveScroller.getPaddingLeft();
            mSelectorListener.onSelectChanged(
                    pageStart + Util.getTsByPix(preLeft - offset, mWaveScroller.getPixPerSecond()),
                    pageStart + Util.getTsByPix(preRight - offset, mWaveScroller.getPixPerSecond())
            );
//            mSelectorListener.onSelectChanged(pageStart + getWaveTS(preLeft - offset), pageStart + getWaveTS(preRight - offset));
/*            mSelectorListener.onSelectChanged(
                    getTimeStart(),
                    getTimeEnd()
            );*/
//            mSelectorListener.onSelectChanged();
        }
    }

    Rect tmpRect = new Rect();

    /**
     * transmit touch event to waveScroller
     *
     * @param event evt
     * @return Y/N
     */
    public boolean transmitTouchEvent(MotionEvent event) {
        //        return super.dispatchTouchEvent(event);
        mWaveScroller.getLocalVisibleRect(tmpRect);
        int y = tmpRect.centerY();
        int x = tmpRect.centerX();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mLastActionDownY = y;//event.getY();
                break;
        }

        x += (event.getY() - mLastActionDownY);
        MotionEvent evt = MotionEvent.obtain(event.getDownTime(), event.getEventTime(), event.getAction(), x, y, 0);
        return mWaveScroller.dispatchTouchEvent(evt);
    }

    public long getTimeStart() {
        long pageStart = Math.round(mWaveScroller.getPageStartTime());
        int offset = mWaveScroller.getPaddingLeft();

        float preLeft = mSeekOption.getX() + mLeftArrow.getWidth() + mMarginFixed;

        return pageStart + Util.getTsByPix(preLeft - offset, mWaveScroller.getPixPerSecond());
    }

    public long getTimeEnd() {
        long pageStart = Math.round(mWaveScroller.getPageStartTime());
        int offset = mWaveScroller.getPaddingLeft();

        float preLeft = mSeekOption.getX() + mLeftArrow.getWidth() + mMarginFixed;
        float preRight = preLeft + mSeekSpanText.getWidth();

        return pageStart + Util.getTsByPix(preRight - offset, mWaveScroller.getPixPerSecond());
    }

    public void setLimitTime(long limitStart, long limitEnd) {
        Log.d(TAG, "setLimitTime() called with: limitStart = [" + limitStart + "], limitEnd = [" + limitEnd + "]");
        mLimitStartTime = limitStart;
        mLimitEndTime = limitEnd;

        mWaveScroller.setLimitTime(limitStart, limitEnd);
    }

    public void setDefaultOptionTime(int timeSpan) {
        Log.d(TAG, "setDefaultOptionTime() called with: timeSpan = [" + timeSpan + "]");
        mDefaultOptionTime = timeSpan;
    }

    public long getMinTimeSelected() {
        return MIN_TIME_SELECTED;
    }

    public interface SelectorListener {

        void onSelectChanged(long start, long end);

        void onSelectChanging(long start, long end);

        void onRender();
    }

    private static class TrackHandler extends Handler {

        private final WeakReference<TrackSelectorWave> mTrackSelector;

        public TrackHandler(TrackSelectorWave trackSelector) {
            mTrackSelector = new WeakReference<TrackSelectorWave>(trackSelector);
        }

        @Override
        public void handleMessage(Message msg) {
            Log.d(TAG, "handleMessage() called with: msg = [" + msg + "]");
            switch (msg.what) {
                case MSG_START_PLAY: {
                    int preLeft = msg.arg1;
                    int preRight = msg.arg2;

                    TrackSelectorWave selector = mTrackSelector.get();
                    if (selector != null) {
                        // play music.

                        // invoke start highlight.
                        selector.mWaveScroller.startHighLight(preLeft, preRight);
//                        if (selector.mSelectorListener != null) {
//                        selector.callbackSelected(preLeft, preRight);
                        selector.callbackSelecting(preLeft, preRight);
//                            long pageStart = Math.round(selector.mWaveScroller.getPageStartTime() / 1000.0F);
//                            int offset = selector.mWaveScroller.getPaddingLeft();
//                            selector.mSelectorListener.onSelectChanging(pageStart + selector.getWaveTS(preLeft - offset), pageStart + selector.getWaveTS(preRight - offset));
//                        }
                    }
                }
                break;

/*                case MSG_DO_FETCH_THUMB: {
                    long ts = (long) msg.obj;
                    TrackSelectorWave selector = mTrackSelector.get();
                    if (selector != null) {
                        ((TrackSelectorThumb) selector).actionFetch(ts, -1);
                    }
                }
                break;*/
            }
        }
    }

}
