package com.tencent.jinjingcao.wavetrack;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentLinkedQueue;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.RectF;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.Scroller;

/**
 * Dock for draw wave.
 * Created by jinjingcao on 2018/3/9.
 */

public class WaveScroller extends View {

    private static final String TAG = "WaveScroller";

    /**
     * fling time (ms.)
     */
    private static final int SCROLLING_VELOCITY_UNIT = 100;

    private static final float MIN_MOVE_DISTANCE = 20;

    /**
     * px per seconds (max 30 sec.) it will adjust after init view.
     *
     * @see #WaveScroller(Context, AttributeSet) #onPreDraw
     */
    protected int mPIX_PER_SECOND = 0;//720 / 30;

    /**
     * 20sample * 4096 / 2channel / 2bitDepth / 44100 * 1000ms ~~ 928.79818594104308390022675736961F {ms / wave_vertical_bar_rect}
     */
    public static final float TIME_PER_WAVE_VERTICAL_BAR = 928.79818594F;

    float mDensity = getContext().getResources().getDisplayMetrics().density;

    private final Paint mBorderPaint;
    private final Paint mTextPaint;
    private final Paint mWavePaint;
    private final Paint mWavePlayingPaint;
    private final Paint mWavePlayingBackPaint;

    float mWaveWidth = Math.round(5 * mDensity);
    final RectF rect = new RectF();

    private ArrayList<Wave> data = new ArrayList<>();

//    private static final Interpolator sInterpolator = new Interpolator() {
//        @Override
//        public float getInterpolation(float t) {
//            t -= 1.0f;
//            return t * t * t * t * t + 1.0f;
//        }
//    };


    private VelocityTracker mVelocityTracker;

    private final Scroller mScroll;

    static int sPAGE_MAX_COUNT = 40;
    static float sWAVE_MAX_HEIGHT;
    int mStartIndex = 0;
    int mMaxScrollX = -1;
    int mRealWidth;
    int mRealHeight;

    private float mLastX;
    float mCurrentLeft;
    float mLastAvailableLeft;
    boolean mIsDragging;

    private WaveScrollListener mListener;

    protected volatile boolean mInited;


    private static final int EVALUATOR_STEP = 5;
    private static final long TIMER_TRIGGER = 200;
    private volatile boolean isProgress;

    private TimerTask timerTask;
    private Timer timer;

    private volatile float mHighLightStartPos;
    private volatile float mHighLightProgressPos;
    private volatile float mHighLightEndPos;

    int mLeftPadding;
    int mRightPadding;
    boolean mNeedBorder;
    float mLastPageStart;
    static final long PAGE_MAX_TS = 30000;// max time per page.

    long mLastScrollEndTS;
    int mLastCurrX = -1;
    long mDuration;

    private long mLimitEndTime;
    private long mLimitStartTime;
    private Paint mBitPaint;

    private boolean mIgnoreCallBack;

    protected int mDragDirection;

    static final int Direction_UNKNOWN = 0;
    static final int Direction_RIGHT = 2;
    static final int Direction_LEFT = 1;

    public WaveScroller(Context context) {
        this(context, null);
    }

    public WaveScroller(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mTextPaint = new Paint();
        mTextPaint.setTextSize(24);
        mTextPaint.setColor(getResources().getColor(R.color.colorAccent));

        mWavePaint = new Paint();
        mWavePaint.setColor(getResources().getColor(R.color.colorWave));

//        Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.mask);
//        mShader = new BitmapShader(bitmap, TileMode.CLAMP, TileMode.CLAMP);

        mWavePlayingPaint = new Paint();
        mWavePlayingPaint.setColor(getResources().getColor(R.color.colorPlaying));

        mWavePlayingBackPaint = new Paint();
        mWavePlayingBackPaint.setColor(getResources().getColor(R.color.colorPlayingBack));

        mBorderPaint = new Paint();
        mBorderPaint.setColor(getResources().getColor(R.color.colorBorder));
        mBorderPaint.setStyle(Style.STROKE);
        mBorderPaint.setStrokeWidth(4 * mDensity);// 有一半在画布外

//        mWaveWidth = getRootView().getRootView().getWidth() / sPAGE_MAX_COUNT;

        getViewTreeObserver().addOnPreDrawListener(
                new ViewTreeObserver.OnPreDrawListener() {

                    @Override
                    public boolean onPreDraw() {

                        mRealWidth = WaveScroller.this.getWidth(); // 获取宽度
                        mRealHeight = WaveScroller.this.getHeight(); // 获取高度
                        mLeftPadding = WaveScroller.this.getPaddingLeft();
                        mRightPadding = WaveScroller.this.getPaddingRight();
                        if (mRealWidth > 0) {
                            WaveScroller.this.getViewTreeObserver().removeOnPreDrawListener(this);
                        } else {
                            Log.e(TAG, "width is 0 ...... need retry.");
                            return true;
                        }

//                        initSize();

                        return true;
                    }
                });

        mScroll = new Scroller(context/*, sInterpolator*/);
    }

    void initSize() {
        if (data.size() <= 0 || mDuration <= 0) {
            Log.e(TAG, "initSize, illArguments.");
            return;
        }

        float waveCntPerSecond = data.size() / (float) (mDuration / 1000);
        mWaveWidth = mPIX_PER_SECOND / waveCntPerSecond;
        Log.w(TAG, "fix wave width." + mWaveWidth);

        sWAVE_MAX_HEIGHT = mRealHeight - 4 * mDensity;
        //                        mWaveWidth = mRealWidth / sPAGE_MAX_COUNT;
        sPAGE_MAX_COUNT = (int) ((mRealWidth - mLeftPadding - mRightPadding) / mWaveWidth);
        //                        PAGE_MAX_TS = (mRealWidth - mLeftPadding - mRightPadding) / TrackSelector.PIX_TO_MILLI_SECOND;

        //                        PAGE_MAX_TS = 30000;//Util.getTsByPix(mRealWidth - mLeftPadding - mRightPadding);

        Log.i(TAG, "sPAGE_MAX_COUNT:" + sPAGE_MAX_COUNT + ", mPIX_PER_SECOND:" + mPIX_PER_SECOND);
    }

    public void clear() {
        data.clear();
    }

    /**
     * 添加数据
     *
     * @param wave
     */
    public void add(Wave wave) {
        data.add(wave);
    }

    @Override
    protected void onDraw(Canvas canvas) {
//        Log.d(TAG, "onDraw");

        if (!mInited) return;

        if (mWaveWidth <= 0) {
            Log.e(TAG, "skip to. mWaveWidth error." + mWaveWidth);
            return;
        }

        if (mMaxScrollX > 0 && !isAvailed(mCurrentLeft)/*x >= mMaxScrollX || x < 0*/) {
            Log.e(TAG, "skip to. mMaxScrollX error." + "x:" + mCurrentLeft + ", max:" + mMaxScrollX);
            mCurrentLeft = mLastAvailableLeft;
        }

        doDraw(canvas);

//        super.onDraw(canvas);
    }

    protected void doDraw(Canvas canvas) {
        float startingLeft = mCurrentLeft;//xxxTmp >= 0 ? xxxTmp : mCurrentLeft;
        mStartIndex = (int) ((startingLeft + mLeftPadding) / mWaveWidth);
        mLastAvailableLeft = startingLeft;

        if (mNeedBorder) {
            canvas.drawRect(mLeftPadding, 0, mRealWidth - mRightPadding, mRealHeight, mBorderPaint);
        }

        ConcurrentLinkedQueue<Wave> careData = getCurrentPageData(mStartIndex);
        Iterator<Wave> it = careData.iterator();
        int index = 0;
        while (it.hasNext()) {
            // draw.
            Wave dt = it.next();
            float left = index * mWaveWidth + mLeftPadding;// keep left padding

            if (left > mRealWidth - mRightPadding) {// keep right padding
                break;
            }
            //            canvas.drawText(dt.volume + ";", left, 50, mTextPaint);
            //            Log.v(TAG, "height:" + dt.volume);

            float height = dt.percent * sWAVE_MAX_HEIGHT;

            float right = left + mWaveWidth / 5 * 3;// wave:space = 3:2
            float top = mRealHeight / 2 - height / 2;
            float bottom = mRealHeight / 2 + height / 2;
            float corner = mWaveWidth / 5 * 3 / 2;
            rect.set(left, top, right, bottom);
            if (left >= mHighLightStartPos - mWaveWidth && right <= mHighLightProgressPos + mWaveWidth) {
                canvas.drawRoundRect(rect, corner, corner, mWavePlayingPaint);
            } else if (left >= mHighLightProgressPos && right <= mHighLightEndPos) {
                canvas.drawRoundRect(rect, corner, corner, mWavePlayingBackPaint);
            } else {
                canvas.drawRoundRect(rect, corner, corner, mWavePaint);
            }
            index++;
        }
    }

    private ConcurrentLinkedQueue<Wave> getCurrentPageData(int index) {
        int pageMax = Math.min(data.size(), sPAGE_MAX_COUNT + 1);
        int lastPageIndex = data.size() - pageMax;

        if (index < 0) index = 0;
        if (index > lastPageIndex) index = lastPageIndex;

        mMaxScrollX = (int) (mWaveWidth * lastPageIndex);

        ConcurrentLinkedQueue<Wave> currentPageData = new ConcurrentLinkedQueue<>();
        List<Wave> subList = data.subList(index, index + pageMax);
        currentPageData.addAll(subList);

        return currentPageData;
    }

    /**
     * mCurrentLeft will modify after fling.
     *
     * @see #onTouchEvent(MotionEvent) case MotionEvent.ACTION_UP
     */
    @Override
    public void computeScroll() {
//        Log.d(TAG, "computeScroll() called" + mCurrentLeft);

        if (mScroll.computeScrollOffset()) {
            int tmp2 = mScroll.getCurrX();
            if (isAvailed(tmp2)) {
                mCurrentLeft = tmp2;
            } else {
                mScroll.forceFinished(true);
            }
            postInvalidate();
            mLastScrollEndTS = System.currentTimeMillis();
        } else {
            int tmp = mScroll.getCurrX();
            if (mLastCurrX != tmp) {
                if (mLastScrollEndTS > 0 && System.currentTimeMillis() - mLastScrollEndTS < 100) {
                    Log.w(TAG, "...........fling end......");
                    // fix position after a quick fling.
                    mCurrentLeft = tmp;
                    if (!mIgnoreCallBack) {
                        Log.e(TAG, "ignore current callBack()...");
                        callbackScroll();
                    }

                    mIgnoreCallBack = false;
                }
                mLastCurrX = tmp;
            }
        }
    }

    private void callbackScroll() {
        Log.d(TAG, "callbackScroll() called" + " ... " + mCurrentLeft);
        if (mListener != null && mLastPageStart != mCurrentLeft) {
            mLastPageStart = mCurrentLeft;
            float tmpOffSet = Util.getPixByTs(mLimitStartTime, getPixPerSecond());
            mListener.onScrollChanged(mCurrentLeft + tmpOffSet, mCurrentLeft + mRealWidth + mLimitStartTime + tmpOffSet);
        }
    }

    private void callbackScrolling() {
        Log.d(TAG, "callbackScrolling() called");
        if (mListener != null && mLastPageStart != mCurrentLeft) {
            mLastPageStart = mCurrentLeft;
            float tmpOffSet = Util.getPixByTs(mLimitStartTime, getPixPerSecond());
            mListener.onScrollChanging(mCurrentLeft + tmpOffSet, mCurrentLeft + mRealWidth + mLimitStartTime + tmpOffSet);
        }
    }

    boolean isAvailed(float currentX) {
        return currentX <= mMaxScrollX/* - mRightPadding*/ && currentX >= 0;// + mLeftPadding;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        Log.v(TAG, "onTouchEvent." + event.getAction() + " x:" + event.getX() + ", y:" + event.getY() + " --> " + mCurrentLeft);

        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(event);

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (!mScroll.isFinished()) {
                    mScroll.abortAnimation();
                }
                mLastX = event.getX();
                mIsDragging = true;

                return true;

            case MotionEvent.ACTION_MOVE:
                float moveDiff = event.getX() - mLastX;
                mCurrentLeft -= moveDiff;// 反向滚动
                mLastX = event.getX();
                if (moveDiff > MIN_MOVE_DISTANCE) {
                    mDragDirection = Direction_RIGHT;
                } else if (moveDiff < -MIN_MOVE_DISTANCE) {
                    mDragDirection = Direction_LEFT;
                } else {
                    mDuration = Direction_UNKNOWN;
                }
                clearHighlight();
                callbackScrolling();
                break;
            case MotionEvent.ACTION_UP:
                mCurrentLeft -= event.getX() - mLastX;// 反向滚动
                mLastX = event.getX();
                /*mScroll.startScroll(mScroll.getCurrX(), 0, (int) (mScroll.getCurrX() - mCurrentLeft), 0, 0);
                mIsDragging = false;*/

                if (mVelocityTracker != null) {
                    mVelocityTracker.computeCurrentVelocity(SCROLLING_VELOCITY_UNIT);
                    int xVelocity = -(int) mVelocityTracker.getXVelocity();// 反向滚动
                    int maxEndX = getMaxEndX();
                    int minStartX = 0;//mLeftPadding;// todo 头部被抹掉了2s
                    mScroll.forceFinished(true);
                    Log.e(TAG, "mCurrentLeft:" + mCurrentLeft + ", xVelocity:" + xVelocity);
                    mScroll.fling((int) mCurrentLeft, 0, xVelocity, 0,
                            minStartX, maxEndX, 0, 0);

                    mVelocityTracker.recycle();
                    mVelocityTracker = null;

                }
                callbackScroll();
                mIsDragging = false;
                break;
            case MotionEvent.ACTION_CANCEL:
                /*if (mVelocityTracker != null) {
                    mVelocityTracker.recycle();
                    mVelocityTracker = null;
                    mIsDragging = false;
                }*/
                callbackScroll();
                mIsDragging = false;
                break;
        }

        postInvalidate();
        Log.v(TAG, "onTouchEvent." + event.getAction() + " x:" + event.getX() + ", y:" + event.getY() + " ==> " + mCurrentLeft);

        return super.onTouchEvent(event);
    }

    private int getMaxPageNumber() {
        int maxPageCount = Math.min(data.size(), sPAGE_MAX_COUNT);
        return (int) Math.ceil(data.size() / (float) maxPageCount);
    }

    protected int getMaxEndX() {
        return Math.round(Math.min(Integer.MAX_VALUE, data.size() * mWaveWidth /*- mRightPadding*/));
    }

    public boolean isDragging() {
        return mIsDragging;
    }

    public ConcurrentLinkedQueue<Wave> getSectionData(float left, float right) {
        int leftIndex = (int) (left / mWaveWidth);
        int rightIndex = (int) (right / mWaveWidth);
        Wave[] dataArr = data.toArray(new Wave[]{});
        ConcurrentLinkedQueue<Wave> ret = new ConcurrentLinkedQueue<>();

        int len = Math.min(rightIndex, dataArr.length);
        for (int i = leftIndex; i < len; i++) {
            ret.add(dataArr[i]);
        }

        return ret;
    }


    public void initScroller() {
        if (mInited) {
            Log.e(TAG, "initScroller. already init.");
            return;
        }
        if (mDuration > 0 && getPixPerSecond() > 0) {
            mInited = true;
            postInvalidate();
        }
    }

    protected void startHighLight(float left, float end) {
        Log.w(TAG, "startHighLight. left:" + left + ", end:" + end);
        mHighLightEndPos = end;
        mHighLightStartPos = left;
        mHighLightProgressPos = left;
        if (isProgress) return;

        Log.i(TAG, "startHighLight......");
        timer = new Timer();
        timerTask = new TimerTask() {
            @Override
            public void run() {
                if (mHighLightProgressPos <= mHighLightEndPos) {
                    mHighLightProgressPos += getPlayStep(TIMER_TRIGGER);//EVALUATOR_STEP;
                } else {
                    mHighLightProgressPos = mHighLightStartPos;
//                    stopHighlight();
                }

                postInvalidate();
            }
        };

        isProgress = true;
        timer.schedule(timerTask, 0, TIMER_TRIGGER);
    }

    private float getPlayStep(long timerTrigger) {
        float ts = Util.getPixByTs(timerTrigger, getPixPerSecond());
//        Log.v(TAG, "getPlayStep ts:" + ts);
        return ts;
    }

    protected void clearHighlight() {
//        mHighLightEndPos = 0;
//        mHighLightStartPos = 0;
//        mHighLightProgressPos = 0;
        mHighLightProgressPos = mHighLightStartPos;

        stopHighlight();
    }

    private void stopHighlight() {
        isProgress = false;

        if (this.timerTask != null) {
            this.timerTask.cancel();
            this.timerTask = null;
        }

        if (this.timer != null) {
            this.timer.cancel();
            this.timer = null;
        }
    }

    public void setTrackListener(WaveScrollListener listener) {
        mListener = listener;
    }

//    public void setPadding(int leftPadding, int rightPadding) {
//        mLeftPadding = leftPadding;
//        mRightPadding = rightPadding;
//    }

    public void drawBorder(boolean b) {
        mNeedBorder = b;
        postInvalidate();
    }

    public void navigateToTime(long startTs, long endTs) {
        long pageStartTs = getPageStartTime(startTs, endTs);
        int pageStartPix = (int) Util.getPixByTs(pageStartTs - mLimitStartTime, getPixPerSecond());
//        pageStartPix += mLeftPadding;
        mIgnoreCallBack = true;
//        mScroll.startScroll(mScroll.getCurrX(), 0, pageStartPix - mScroll.getCurrX(), 0, 0);
        mScroll.setFinalX(pageStartPix - mScroll.getCurrX());
        postInvalidate();
    }

    public long getPageStartTime(long startTs, long endTs) {
        long ts = endTs - startTs;
        if (ts > PAGE_MAX_TS) {
            endTs = startTs + PAGE_MAX_TS;
        }

        // make option center.
        long middleTs = (endTs - startTs) / 2 + startTs;
        long pageStartTs = middleTs - PAGE_MAX_TS / 2;

        // last page count
        int lastPageCount = data.size() % sPAGE_MAX_COUNT;
        long lastPageStartTs = Util.getTsByByteSize(data.size() - lastPageCount);

        if (pageStartTs <= 0) {
            pageStartTs = 0;
        } else if (pageStartTs >= lastPageStartTs) {
            // todo
//            pageStartTs = pageStartTs >= data.size() * TrackSelector.PIX_TO_PCM_BYTE;
            pageStartTs = lastPageStartTs;
        }

        return pageStartTs;
    }

    public float getPageStartTime() {
        Log.d(TAG, "getPageStartTime() called." + mScroll.getCurrX() + "..." + mCurrentLeft);
//        return Util.getTsByPix(mScroll.getCurrX()/* - mLeftPadding*/, getPixPerSecond());
        return Util.getTsFloatByPix(mCurrentLeft/* - mLeftPadding*/, getPixPerSecond()) + mLimitStartTime;
    }

    public int getPixPerSecond() {
        return mPIX_PER_SECOND;
    }

    public void setDuration(long ts) {
        mDuration = ts;
    }

    public void setLimitTime(long limitStart, long limitEnd) {
        Log.d(TAG, "setLimitTime() called with: limitStart = [" + limitStart + "], limitEnd = [" + limitEnd + "]");
        mLimitStartTime = limitStart;
        mLimitEndTime = limitEnd;
        int f = (int) Math.floor(limitStart / mWaveWidth / getPixPerSecond());
        int t = (int) Math.ceil(limitEnd / mWaveWidth / getPixPerSecond());
        if (f >= 0 && t <= data.size() - 1) {
            List<Wave> tmp = data.subList(f, t);
            data = new ArrayList<>();
            data.addAll(tmp);
        } else {
            Log.e(TAG, "setLimitTime: f:" + f + ", t:" + t);
        }
    }

    public void initItemData() {

    }

    public void initPixPerSec() {
        mPIX_PER_SECOND = (int) ((mRealWidth - mLeftPadding - mRightPadding) / 30);// 固定铺满整行30s
    }

    public interface WaveScrollListener {

        void onScrollChanged(float pageStart, float pageEnd);

        void onScrollChanging(float pageStart, float pageEnd);
    }
}
