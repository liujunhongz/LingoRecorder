package com.liulishuo.engzo.lingorecorder.demo.activity;

import android.annotation.TargetApi;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.liulishuo.engzo.lingorecorder.LingoRecorder;
import com.liulishuo.engzo.lingorecorder.demo.AndroidAacProcessor;
import com.liulishuo.engzo.lingorecorder.demo.R;
import com.liulishuo.engzo.lingorecorder.demo.Utils;
import com.liulishuo.engzo.lingorecorder.processor.AudioProcessor;

import java.util.Map;

/**
 * demonstrate flac codec
 */

public class AacDemonstrateActivity extends RecordActivity {

    private static final String AAC = "androidAac";

    private Button btnRecord;
    private EditText etOutput;
    private TextView tvDuration;
    private TextView tvSize;

    private AndroidAacProcessor flacProcessor;
    private String outputFile;

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_aac_demonstrate);

        tvDuration = (TextView) findViewById(R.id.record_duration);
        tvSize = (TextView) findViewById(R.id.record_size);
        tvDuration.setText(getString(R.string.record_duration, ""));
        tvSize.setText(getString(R.string.record_size, ""));

        etOutput = (EditText) findViewById(R.id.et_output_file);
        View unSupportView = findViewById(R.id.tv_unsuport_view);
        View demonstrateView = findViewById(R.id.view_demonstrate);

        final boolean supportFlac = checkSupportAac();
        if (!supportFlac) {
            unSupportView.setVisibility(View.VISIBLE);
            demonstrateView.setVisibility(View.GONE);
            return;
        }
        unSupportView.setVisibility(View.GONE);
        demonstrateView.setVisibility(View.VISIBLE);

        flacProcessor = new AndroidAacProcessor();
        lingoRecorder.put(AAC, flacProcessor);

        btnRecord = (Button) findViewById(R.id.btn_record);
        btnRecord.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!checkRecordPermission()) return;
                if (!lingoRecorder.isAvailable()) return;
                if (lingoRecorder.isRecording()) {
                    lingoRecorder.stop();
                } else {
                    outputFile = etOutput.getText().toString();
                    flacProcessor.setFilePath(outputFile);
                    lingoRecorder.start();
                    btnRecord.setText(R.string.stop_record);
                }
            }
        });
    }

    private boolean checkSupportAac() {
        return Utils.checkSupportMediaCodec("audio/mp4a-latm") != null;
    }

    @Override
    protected void onRecordError(Throwable throwable) {
        super.onRecordError(throwable);
        btnRecord.setText(R.string.start_record);
    }

    @Override
    protected void onProcessError(Throwable throwable) {
        super.onProcessError(throwable);
        btnRecord.setText(R.string.start_record);
    }

    @Override
    protected void onProcessStop(Map<String, AudioProcessor> map) {
        btnRecord.setText(R.string.start_record);
        tvSize.setText(getString(R.string.record_size, Utils.formatFileSize(outputFile)));
    }

    @Override
    protected void onRecordStop(LingoRecorder.OnRecordStopListener.Result result) {
        tvDuration.setText(getString(R.string.record_duration,
                Utils.getDurationString(result.getDurationInMills())));
    }

    @Override
    protected void onPermissionGranted() {
        btnRecord.performClick();
    }
}
