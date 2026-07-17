package com.example.uhf_bt.fragment;


import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import com.example.uhf_bt.MainActivity;
import com.example.uhf_bt.R;
import com.example.uhf_bt.tool.BarcodeUtil;
import com.example.uhf_bt.tool.Utils;
import com.rscja.deviceapi.entity.BarcodeResult;
import com.rscja.deviceapi.interfaces.ConnectionStatus;
import com.rscja.deviceapi.interfaces.KeyEventCallback;
import com.rscja.utility.StringUtility;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BarcodeFragment extends Fragment implements View.OnClickListener {
    private static final String TAG = BarcodeFragment.class.getSimpleName();
    MainActivity mContext;

    ScrollView scrBarcode;
    TextView tvData;
    Button btnScan, btClear;
    Spinner spingCodingFormat;
    CheckBox cbContinuous, cbBarcodeType;
    EditText etTime;
    EditText etId;
    EditText etValue;
    Button btnSet;
    Button btGet;


    ConnectStatus connectStatus = new ConnectStatus();
    Handler handler = new Handler(Looper.getMainLooper());
    ExecutorService executorService = Executors.newFixedThreadPool(10);

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_barcode, container, false);
        initView(view);
        return view;
    }

    public void initView(View view) {
        Log.i(TAG, "initView----------------");
        mContext = (MainActivity) getActivity();
        cbContinuous = (CheckBox) view.findViewById(R.id.cbContinuous);
        etTime = (EditText) view.findViewById(R.id.etTime);
        scrBarcode = (ScrollView) view.findViewById(R.id.scrBarcode);
        tvData = (TextView) view.findViewById(R.id.tvData);
        btnScan = (Button) view.findViewById(R.id.btnScan);
        btClear = (Button) view.findViewById(R.id.btClear);

        etId = (EditText) view.findViewById(R.id.etId);
        etValue = (EditText) view.findViewById(R.id.etValue);
        btnSet = (Button) view.findViewById(R.id.btnSet);
        btGet = (Button) view.findViewById(R.id.btGet);
        btnSet.setOnClickListener(this);
        btGet.setOnClickListener(this);


        cbBarcodeType = (CheckBox) view.findViewById(R.id.cbBarcodeType);
        btnScan.setOnClickListener(this);
        btClear.setOnClickListener(this);
        spingCodingFormat = (Spinner) view.findViewById(R.id.spingCodingFormat);
        handler.postDelayed(() -> setupKeyEventCallback(), 200);
        cbContinuous.setOnClickListener(this);
        cbBarcodeType.setOnClickListener(this);
        mContext.addConnectStatusNotice(connectStatus);


        handler.post(() -> {
            if (mContext.uhf.getConnectStatus() == ConnectionStatus.CONNECTED) {
                int isFlag = mContext.uhf.getBarcodeTypeInSSIID();
                if (isFlag == 1) {
                    cbBarcodeType.setChecked(true);
                } else if (isFlag == 0) {
                    cbBarcodeType.setChecked(false);
                }
            }
        });

    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        Log.i(TAG, "onDestroyView-");
        if (isRunning) {
            stopBarcode();
        }
        handler.removeCallbacksAndMessages(null);
        mContext.removeConnectStatusNotice(connectStatus);
        mContext.uhf.setKeyEventCallback(null);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btnScan:
                btnScanClick();
                break;
            case R.id.btClear:
                tvData.setText("");
                break;
            case R.id.cbContinuous:
                if (!cbContinuous.isChecked()) {
                    isRunning = false;
                }
                break;
            case R.id.cbBarcodeType:
                if (cbBarcodeType.isChecked()) {
                    boolean result = mContext.uhf.setBarcodeTypeInSSIID(true);
                    if (!result) {
                        cbBarcodeType.setChecked(false);
                    }
                } else {
                    boolean result = mContext.uhf.setBarcodeTypeInSSIID(false);
                    if (!result) {
                        cbBarcodeType.setChecked(true);
                    }
                }
                break;
            case R.id.btnSet:
                String hexIdData = etId.getText().toString();
                if (hexIdData != null) {
                    hexIdData = hexIdData.replace("0x", "").replace(" ", "");
                }
                if (!Utils.vailHexInput(hexIdData)) {
                    mContext.showToast(R.string.rfid_mgs_error_nohex);
                    return;
                }
                String hexValueData = etValue.getText().toString();
                hexValueData = hexValueData.replace("0x", "").replace(" ", "");
                if (!Utils.vailHexInput(hexValueData)) {
                    mContext.showToast(R.string.rfid_mgs_error_nohex);
                    return;
                }
                boolean result = mContext.uhf.setParameter(StringUtility.hexStringToBytes(hexIdData), StringUtility.hexString2Bytes(hexValueData));
                if (result) {
                    mContext.showToast(R.string.setting_succ);
                } else {
                    mContext.showToast(R.string.setting_fail);
                }
                break;
            case R.id.btGet:
                String hexData = etId.getText().toString();
                hexData = hexData.replace("0x", "").replace(" ", "");
                if (!Utils.vailHexInput(hexData)) {
                    mContext.showToast(R.string.rfid_mgs_error_nohex);
                    etValue.setText("");
                    return;
                }
                byte[] data = mContext.uhf.getParameter(StringUtility.hexString2Bytes(hexData));
                if (data == null) {
                    mContext.showToast(R.string.get_fail);
                    etValue.setText("");
                    return;
                }
                etValue.setText(StringUtility.bytes2HexString(data));
                mContext.showToast(R.string.get_succ);
                break;
        }
    }

    @SuppressLint("SetTextI18n")
    private void addBarcodeString(String barcode) {
        if (barcode == null) return;
        handler.post(() -> {
            if (tvData.getText().length() > 1000) {
                tvData.setText(barcode + "\r\n");
            } else {
                tvData.setText(tvData.getText() + barcode + "\r\n");
            }
            scroll2Bottom(scrBarcode, tvData);
        });
    }


    boolean isRunning = false;

    private void btnScanClick() {
        if (mContext.uhf.getConnectStatus() != ConnectionStatus.CONNECTED) {
            mContext.showToast(R.string.disconnect);
            return;
        }
        if (!isRunning) {
            isRunning = true;
            btnScan.setText(getString(cbContinuous.isChecked() ? R.string.btn_continous_scanning : R.string.btStop));
            executorService.submit(() -> scanBarcode());
        } else {
            stopBarcode();
        }
    }

    private void scanBarcode() {
        Log.i(TAG, "scanBarcode isRunning=" + isRunning);
        if (!isRunning) return;

        String data = null;
        byte[] temp = null;
        BarcodeResult barcodeResult = mContext.uhf.startScanBarcode();
        if (barcodeResult != null) {
            temp = barcodeResult.getBarcodeBytesData();
            Utils.playSound(1);
        }
        if (temp != null && temp.length > 0) {
            try {
                if (spingCodingFormat.getSelectedItemPosition() == 1) {
                    data = new String(temp, "utf8");
                } else if (spingCodingFormat.getSelectedItemPosition() == 2) {
                    data = new String(temp, "gb2312");
                } else {
                    data = new String(temp);
                }
            } catch (Exception ignored) {
            }
            if (barcodeResult.getBarcodeSSIID() > 0) {
                data = data + "  type=" + BarcodeUtil.getBarcodeType(barcodeResult.getBarcodeSSIID());
            } else if (barcodeResult.getBarcodeCodeID() != null) {
                data = data + "  type=" + BarcodeUtil.getBarcodeType(barcodeResult.getBarcodeCodeID());
            }
        } else {
            data = getString(R.string.msg_scan_fail);
        }
        addBarcodeString(data);

        if (cbContinuous.isChecked() && isRunning) {
            String timeStr = etTime.getText().toString().isEmpty() ? etTime.getHint().toString() : etTime.getText().toString();
            handler.postDelayed(() -> executorService.submit(this::scanBarcode), Integer.parseInt(timeStr));
        } else {
            handler.post(() -> {
                isRunning = false;
                btnScan.setText(getString(R.string.title_2Dscan));
            });
        }
    }


    private void stopBarcode() {
        Log.i(TAG, "stopBarcode");
        isRunning = false;
        handler.removeCallbacksAndMessages(null);
        boolean stopScan = mContext.uhf.stopScanBarcode();
        btnScan.setText(getString(R.string.title_2Dscan));
    }


    public static void scroll2Bottom(final ScrollView scroll, final View inner) {
        Handler handler = new Handler();
        handler.post(() -> {
            if (scroll == null || inner == null) {
                return;
            }
            // 内层高度超过外层
            int offset = inner.getMeasuredHeight() - scroll.getMeasuredHeight();
            if (offset < 0) {
                offset = 0;
            }
            scroll.scrollTo(0, offset);
        });

    }

    private void setupKeyEventCallback() {
        mContext.uhf.setKeyEventCallback(new KeyEventCallback() {
            @Override
            public void onKeyDown(int keycode) {
                Log.d(TAG, "onKeyDown keycode=" + keycode);
                if (mContext.uhf.getConnectStatus() != ConnectionStatus.CONNECTED) {
                    mContext.showToast(R.string.disconnect);
                    return;
                }
                btnScanClick();
            }

            @Override
            public void onKeyUp(int keycode) {
                Log.d(TAG, "onKeyUp keycode=" + keycode);
                if (mContext.uhf.getConnectStatus() != ConnectionStatus.CONNECTED) {
                    return;
                }
                if (keycode == 4) {
                    stopBarcode();
                }
            }
        });
    }

    class ConnectStatus implements MainActivity.IConnectStatus {
        @Override
        public void getStatus(ConnectionStatus connectionStatus) {
            if (connectionStatus == ConnectionStatus.CONNECTED) {
                handler.post(() -> {
                    int isFlag = mContext.uhf.getBarcodeTypeInSSIID();
                    Log.d(TAG, "isFlag=" + isFlag);
                    if (isFlag == 1) {
                        cbBarcodeType.setChecked(true);
                    } else if (isFlag == 0) {
                        cbBarcodeType.setChecked(false);
                    }
                    // 连接成功时重新设置按键回调，确保切换模式后按键功能正常
                    setupKeyEventCallback();
                });
            }
        }
    }
}
