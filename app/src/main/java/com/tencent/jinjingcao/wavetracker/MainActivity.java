package com.tencent.jinjingcao.wavetracker;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.tencent.jinjingcao.wavetrack.TrackSelectorThumb;
import com.tencent.jinjingcao.wavetrack.TrackSelectorWave;
import com.tencent.jinjingcao.wavetrack.TrackSelectorWave.SelectorListener;

public class MainActivity extends AppCompatActivity implements SelectorListener {

    // Storage Permissions
    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static final String TAG = "MainActivity";
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    private TrackSelectorWave mTrackerSelector;
    private TrackSelectorThumb mTrackerSelectorThumb;

    private TextView mTextView;
    private TextView mTextView2;
    private EditText mTxtSeekFrom;
    private EditText mTxtSeekTo;
    private Button mBtnSeekGo;
    private EditText mTxtSelectStart;
    private EditText mTxtSelectEnd;

    private SelectorListener mListener = new SelectorListener() {
        @Override
        public void onSelectChanged(long start, long end) {
            Log.d(TAG, "onSelectChanged() called with: start = [" + start + "], end = [" + end + "]");
        }

        @Override
        public void onSelectChanging(long start, long end) {
            Log.d(TAG, "onSelectChanging() called with: start = [" + start + "], end = [" + end + "]");
        }

        @Override
        public void onRender() {
            Log.d(TAG, "onRender() called");
        }
    };

    private void assignViews() {
        mTrackerSelector = (TrackSelectorWave) findViewById(R.id.tracker_selector);
        mTrackerSelectorThumb = (TrackSelectorThumb) findViewById(R.id.tracker_selector_thumb);
        mTextView = (TextView) findViewById(R.id.textView);
        mTextView2 = (TextView) findViewById(R.id.textView2);
        mTxtSeekFrom = (EditText) findViewById(R.id.txt_seek_from);
        mTxtSeekTo = (EditText) findViewById(R.id.txt_seek_to);
        mBtnSeekGo = (Button) findViewById(R.id.btn_seek_go);
        mTxtSelectStart = (EditText) findViewById(R.id.txt_select_start);
        mTxtSelectEnd = (EditText) findViewById(R.id.txt_select_end);

        mBtnSeekGo.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                String startStr = mTxtSeekFrom.getText().toString();
                String endStr = mTxtSeekTo.getText().toString();

                mTrackerSelector.setSelectSpan(Long.parseLong(startStr), Long.parseLong(endStr));
            }
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        verifyStoragePermissions(this);
        setContentView(R.layout.main_activity);

        assignViews();

//        mTrackerSelector = findViewById(R.id.tracker_selector);
        mTrackerSelector.setSelectorListener(this);

        final String pcmPath = "/mnt/sdcard/Music/mic.pcm";

        new Thread(new Runnable() {
            @Override
            public void run() {
                mTrackerSelector.initData(pcmPath, 4000000);

                mTrackerSelector.post(new Runnable() {
                    @Override
                    public void run() {
                        mTrackerSelector.syncSelectedAndStartHighLight(false);
                    }
                });

//                mTrackerSelector.setLimitTime(10000, 50000);
            }
        }).start();


        //////////////////////////////
//        mTrackerSelectorThumb.postDelayed(new Runnable() {
//            @Override
//            public void run() {
//                mTrackerSelectorThumb.setSelectorListener(mListener);
//                mTrackerSelectorThumb.initData(pcmPath, 40000);
//            }
//        }, 3000);

    }

    public static void verifyStoragePermissions(Activity activity) {
        // Check if we have write permission
        int permission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);

        if (permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                    activity,
                    PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE
            );
        }
    }


    @Override
    public void onSelectChanged(long start, long end) {
        Log.d(TAG, "onSelectChanged() called with: start = [" + start + "], end = [" + end + "]");
        mTxtSelectStart.setText(String.valueOf(start));
        mTxtSelectEnd.setText(String.valueOf(end));
    }

    @Override
    public void onSelectChanging(long start, long end) {
        Log.d(TAG, "onSelectChanging() called with: start = [" + start + "], end = [" + end + "]");
        mTxtSelectStart.setText(String.valueOf(start));
        mTxtSelectEnd.setText(String.valueOf(end));
    }

    @Override
    public void onRender() {

    }
}
