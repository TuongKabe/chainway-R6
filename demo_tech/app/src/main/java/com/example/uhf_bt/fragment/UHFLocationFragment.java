package com.example.uhf_bt.fragment;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.example.uhf_bt.MainActivity;
import com.example.uhf_bt.R;
import com.example.uhf_bt.tool.StringUtils;
import com.example.uhf_bt.tool.UIHelper;
import com.example.uhf_bt.tool.Utils;
import com.example.uhf_bt.view.CircleSeekBar;
import com.example.uhf_bt.view.UhfLocationCanvasView;
import com.rscja.deviceapi.interfaces.ConnectionStatus;
import com.rscja.deviceapi.interfaces.IUHF;
import com.rscja.deviceapi.interfaces.IUHFLocationCallback;
import com.rscja.deviceapi.interfaces.KeyEventCallback;
import com.rscja.utility.StringUtility;

public class UHFLocationFragment extends Fragment {
    private final String TAG = "UHFLocationFragment";
    private MainActivity mContext;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private UhfLocationCanvasView llChart;
    private CircleSeekBar seekBarPower;
    private EditText etEPC;
    private Button btStart, btStop;

    private int progress = 5;

    // 进入页面时蜂鸣器开关状态
    private int beepFlag = -1;
    private ConnectStatus mConnectStatus = new ConnectStatus();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_uhflocation, container, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mContext = (MainActivity) getActivity();

        llChart = getView().findViewById(R.id.llChart);

        etEPC = getView().findViewById(R.id.etEPC);
        etEPC.setHint(mContext.parseWay == MainActivity.PARSE_BY_ASCII ? "ASCII" : "HEX");

        seekBarPower = getView().findViewById(R.id.seekBarPower);
        seekBarPower.setEnabled(false);
        seekBarPower.setProgress(5);
        seekBarPower.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress2, boolean fromUser) {
                Log.d(TAG, "  progress =" + progress2);
                progress = progress2;
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                Log.d(TAG, "  onStartTrackingTouch");
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                int p = 35 - progress;
                mContext.uhf.setDynamicDistance(p);
                Log.d(TAG, "  onStopTrackingTouch  p=" + p + "  progress=" + progress);
                // Toast.makeText(getContext(),"功率："+progress,Toast.LENGTH_SHORT).show();
            }
        });

        btStart = getView().findViewById(R.id.btStart);
        btStop = getView().findViewById(R.id.btStop);
        btStart.setOnClickListener(v -> startLocation());
        btStop.setOnClickListener(v -> stopLocation());

        getView().post(() -> {
            llChart.clean();
            if (mContext.selectEPC != null) {
                etEPC.setText(mContext.selectEPC);
            }
        });

        mContext.addConnectStatusNotice(mConnectStatus);
        handler.postDelayed(() -> setupKeyEventCallback(), 200);
    }

    private void setupKeyEventCallback() {
        mContext.uhf.setKeyEventCallback(new KeyEventCallback() {
            @SuppressLint("LongLogTag")
            @Override
            public void onKeyDown(int keycode) {
                Log.i(TAG, "keycode=" + keycode);
                if (!mContext.uhf.isSupportRssi()) {
                    UIHelper.ToastMessage(mContext, getResources().getString(R.string.uhf_not_support_rssi));
                    return;
                }
                if (mContext.uhf.getConnectStatus() == ConnectionStatus.CONNECTED) {
                    if (keycode == 3) {
                        mContext.isKeyDownUP = true;
                        startLocation();
                    } else {
                        if (!mContext.isKeyDownUP) {
                            if (keycode == 1) {
                                if (mContext.uhf.isInventorying()) {
                                    stopLocation();
                                } else {
                                    startLocation();
                                }
                            }
                        }
                    }
                }
            }

            @SuppressLint("LongLogTag")
            @Override
            public void onKeyUp(int keycode) {
                Log.d(TAG, "  keycode =" + keycode);
                if (keycode == 4) {
                    stopLocation();
                }
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        Log.i(TAG, "onDestroyView");
        stopLocation();
        mContext.removeConnectStatusNotice(mConnectStatus);
        mContext.uhf.setKeyEventCallback(null);
        Log.i(TAG, "onDestroyView end");
    }

    private void startLocation() {
        String epc = etEPC.getText().toString().trim();
        if (epc.isEmpty()) {
            mContext.showToast(R.string.uhf_msg_tag_must_not_null);
            return;
        }
        if (mContext.parseWay == MainActivity.PARSE_BY_ASCII) {
            epc = StringUtils.toAsciiHexString(epc);
        } else if (!StringUtility.isHexNumberRex(epc)) {
            mContext.showToast(R.string.rfid_mgs_error_nohex);
            return;
        }

        mContext.uhf.setBeep(false);
        SystemClock.sleep(200);

        boolean result = mContext.uhf.startLocation(mContext, epc, IUHF.Bank_EPC, 32, new IUHFLocationCallback() {
            @Override
            public void getLocationValue(int value, boolean valid) {
                Log.i(TAG, "getLocationValue value=" + value + " valid=" + valid);
                llChart.setData(value);
                if (valid) {
                    Utils.playSoundDelayed(value);
                }
            }

        });
        if (!result) {
            Toast.makeText(mContext, R.string.psam_msg_fail, Toast.LENGTH_SHORT).show();
            return;
        }
        btStart.setEnabled(false);
        etEPC.setEnabled(false);
        seekBarPower.setEnabled(true);
    }

    public void stopLocation() {
        Log.i(TAG, "stopLocation----------------" + mContext.uhf.isInventorying());
        if (!mContext.uhf.isInventorying())
            return;
        mContext.uhf.stopLocation();
        btStart.setEnabled(true);
        etEPC.setEnabled(true);
        seekBarPower.setEnabled(false);
        seekBarPower.setProgress(5);
    }

    @Override
    public void onResume() {
        super.onResume();
        beepFlag = mContext.uhf.getBeep();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (beepFlag != -1) {
            mContext.uhf.setBeep(beepFlag == 1);
        }
    }

    class ConnectStatus implements MainActivity.IConnectStatus {
        @Override
        public void getStatus(ConnectionStatus connectionStatus) {
            if (connectionStatus == ConnectionStatus.CONNECTED) {
                setupKeyEventCallback();
            }
        }
    }

}
