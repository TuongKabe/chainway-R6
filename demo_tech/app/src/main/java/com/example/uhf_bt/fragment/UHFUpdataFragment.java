package com.example.uhf_bt.fragment;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.example.uhf_bt.MainActivity;
import com.example.uhf_bt.R;
import com.example.uhf_bt.filebrowser.FileManagerActivity;
import com.rscja.deviceapi.RFIDWithUHFBLE;
import com.rscja.deviceapi.interfaces.ConnectionStatus;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.HashMap;

import no.nordicsemi.android.dfu.DfuBaseService;
import no.nordicsemi.android.dfu.DfuServiceInitiator;
import no.nordicsemi.android.error.GattError;
import no.nordicsemi.android.nrftoolbox.dfu.DfuService;

public class UHFUpdataFragment extends Fragment implements View.OnClickListener {

    MainActivity mContext;
    TextView tvPath, tvMsg;
    Button btSelect;
    Button btnUpdata;
    String TAG = "DeviceAPI_UHFUpdata";
    RadioButton rbMainboard, rbUHFFirmware, rbBLE, rb_ex10;
    String version;

    private ProgressDialog progressDialog = null;
    private String mFilePath;
    private Uri mFileStreamUri;
    private ProgressBroadcastsReceiver mProgressBroadcastReceiver;

    private HashMap<String, String> beforeVerMap;
    private HashMap<String, String> latestVerMap;

    private static final int SELECT_FILE_REQ = 11;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_uhfupdata, container, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mContext = (MainActivity) getActivity();

        tvPath = (TextView) getView().findViewById(R.id.tvPath);
        tvMsg = (TextView) getView().findViewById(R.id.tvMsg);
        btSelect = (Button) getView().findViewById(R.id.btSelect);
        btnUpdata = (Button) getView().findViewById(R.id.btnUpdata);

        rbMainboard = (RadioButton) getView().findViewById(R.id.rbMainboard);
        rbUHFFirmware = (RadioButton) getView().findViewById(R.id.rbR2000);
        rb_ex10 = (RadioButton) getView().findViewById(R.id.rb_ex10);
        rbBLE = (RadioButton) getView().findViewById(R.id.rbBLE);
        rb_ex10.setVisibility(View.GONE);
        btSelect.setOnClickListener(this);
        btnUpdata.setOnClickListener(this);

        // 根据连接模式控制RadioButton的显示/隐藏
        updateRadioButtonVisibility();

        registerProgressListener();
        init();
        mContext.addConnectStatusNotice(iConnectStatus);
    }

    private MainActivity.IConnectStatus iConnectStatus = new MainActivity.IConnectStatus() {
        @Override
        public void getStatus(ConnectionStatus connectionStatus) {
            Log.e(TAG, "reconnected>connectionStatus=" + connectionStatus);
            if (connectionStatus == ConnectionStatus.CONNECTED) {
                sleep(1000);
                RFIDWithUHFBLE bleUhf = mContext.getUhfBLE();
                if (bleUhf != null) {
                    latestVerMap = bleUhf.getBluetoothVersion();
                }
                showBTVersion();
            } else if (connectionStatus == ConnectionStatus.DISCONNECTED) {

            }
        }
    };

    /**
     * 根据连接模式更新RadioButton的可见性
     * WiFi模式：只显示射频模块（rbUHFFirmware），隐藏主板（rbMainboard）和蓝牙（rbBLE）
     * 蓝牙模式：显示主板、射频模块和蓝牙三个选项
     */
    private void updateRadioButtonVisibility() {
        if (mContext == null || rbUHFFirmware == null || rbMainboard == null || rbBLE == null) {
            return;
        }
        MainActivity.ConnectionMode mode = mContext.getConnectionMode();
        if (mode == MainActivity.ConnectionMode.WIFI) {
            // WiFi模式：只显示射频模块
            rbUHFFirmware.setVisibility(View.VISIBLE);
            rbMainboard.setVisibility(View.GONE);
            rbBLE.setVisibility(View.GONE);
            // 如果当前选中的是被隐藏的选项，则自动选择射频模块
            if (rbMainboard.isChecked() || rbBLE.isChecked()) {
                rbUHFFirmware.setChecked(true);
            }
        } else {
            // 蓝牙模式：显示三个选项
            rbUHFFirmware.setVisibility(View.VISIBLE);
            rbMainboard.setVisibility(View.VISIBLE);
            rbBLE.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btSelect:
                Intent intent = new Intent(mContext, FileManagerActivity.class);
                startActivity(intent);
                break;
            case R.id.btnUpdata:
                update();
                break;

        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // 每次Fragment显示时更新RadioButton可见性，确保切换模式后正确显示
        if (getView() != null) {
            updateRadioButtonVisibility();
        }
    }

    @Override
    public void onDestroyView() {
        mContext.unregisterReceiver(pathReceiver);
        unregisterProgressListener();
        mContext.removeConnectStatusNotice(iConnectStatus);
        super.onDestroyView();
    }

    public void update() {
        if (!(mContext.uhf.getConnectStatus() == ConnectionStatus.CONNECTED)) {
            Toast.makeText(mContext, "Connection disconnected!", Toast.LENGTH_SHORT).show();
            return;
        }
        String filePath = tvPath.getText().toString();
        if (TextUtils.isEmpty(filePath)) {
            Toast.makeText(mContext, R.string.up_msg_sel_file, Toast.LENGTH_SHORT).show();
            return;
        }

        if (rbUHFFirmware.isChecked() || rbMainboard.isChecked() || rb_ex10.isChecked()) {
            if (filePath.toLowerCase().lastIndexOf(".bin") < 0) {
                Toast.makeText(mContext, "The file format is wrong!", Toast.LENGTH_SHORT).show();
                return;
            }

            tvMsg.setText("");
            int flag = 0;
            if (rbMainboard.isChecked()) {
                flag = 0;
                version = mContext.uhf.getSTM32Version();// 获取版本号
            } else if (rbUHFFirmware.isChecked()) {
                flag = 1;
                version = mContext.uhf.getVersion();// 获取版本号
            } else if (rb_ex10.isChecked()) {
                flag = 3;
                version = mContext.uhf.getEx10SDKFirmware();// 获取版本号
            }

            tvMsg.setText("version:" + version);
            Log.d(TAG, "version=" + version);
            new UpdateTask(filePath, flag).execute();
        } else if (rbBLE.isChecked()) {
            if (filePath.toLowerCase().lastIndexOf(".zip") < 0) {
                Toast.makeText(mContext, "The file format is wrong!", Toast.LENGTH_SHORT).show();
                return;
            }
            updateBLE(mContext, mFilePath, mFileStreamUri, mContext.mDevice);
        }
    }

    class UpdateTask extends AsyncTask<String, Integer, Boolean> {

        String path = "";
        int flag;

        public UpdateTask(String path, int flag) {
            this.path = path;
            this.flag = flag;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            // mypDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            // mypDialog.setMessage("init...");
            String msg = "";
            if (rbUHFFirmware.isChecked()) {
                msg = getString(R.string.prepare_update_uhf);
            } else if (rbMainboard.isChecked()) {
                msg = getString(R.string.prepare_update_mainboard);
            } else if (rbBLE.isChecked()) {
                msg = getString(R.string.prepare_update_ble);
            } else if (rb_ex10.isChecked()) {
                msg = getString(R.string.prepare_update_ex10);
            }
            progressDialog = new ProgressDialog(mContext);
            progressDialog.setMessage(msg);
            progressDialog.setCancelable(false);
            progressDialog.setCanceledOnTouchOutside(false);
            progressDialog.show();
        }

        @Override
        protected Boolean doInBackground(String... strings) {
            boolean result = false;
            File uFile = new File(path);
            if (!uFile.exists()) {
                return false;
            }
            long uFileSize = uFile.length();
            int packageCount = (int) (uFileSize / 64);
            RandomAccessFile raf = null;
            try {
                raf = new RandomAccessFile(path, "r");
            } catch (FileNotFoundException e) {
            }
            if (raf == null) {
                return false;
            }
            Log.d(TAG, "uhfJump2Boot flag=" + flag);

            if (!mContext.uhf.uhfJump2Boot(flag)) {
                Log.d(TAG, "uhfJump2Boot fail");
                return false;
            }

            sleep(2000);
            Log.d(TAG, "UHF uhfStartUpdate");
            if (!mContext.uhf.uhfStartUpdate()) {
                Log.d(TAG, "uhfStartUpdate fail");
                return false;
            }
            int pakeSize = 64;
            byte[] currData = new byte[(int) uFileSize];
            for (int k = 0; k < packageCount; k++) {
                int index = k * pakeSize;
                try {
                    int rsize = raf.read(currData, index, pakeSize);
                    // Log.d(TAG, "总包数量="+uFileSize+" beginPack=" +index + " endPack=" +
                    // (index+pakeSize-1) +" rsize="+rsize);
                } catch (IOException e) {
                    stop();
                    return false;
                }
                byte[] data = Arrays.copyOfRange(currData, index, index + pakeSize);
                // Log.d(TAG,"data="+ StringUtility.bytes2HexString(data,data.length));
                if (mContext.uhf.uhfUpdating(data)) {
                    result = true;
                    publishProgress(index + pakeSize, (int) uFileSize);
                } else {
                    Log.d(TAG, "uhfUpdating fail");
                    stop();
                    return false;
                }

            }
            if (uFileSize % pakeSize != 0) {
                int index = packageCount * pakeSize;
                int len = (int) (uFileSize % pakeSize);
                try {
                    int rsize = raf.read(currData, index, len);
                    Log.d(TAG, "beginPack=" + index + " countPack=" + len + " rsize=" + rsize);
                } catch (IOException e) {
                    stop();
                    return false;
                }
                if (mContext.uhf.uhfUpdating(Arrays.copyOfRange(currData, index, index + len))) {
                    result = true;
                    publishProgress((int) uFileSize, (int) uFileSize);
                } else {
                    Log.d(TAG, "uhfUpdating fail");
                    stop();
                    return false;
                }
            }
            stop();
            return result;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);
            progressDialog
                    .setMessage((values[0] * 100 / values[1]) + "% " + mContext.getString(R.string.app_msg_Upgrade));
            tvMsg.setText("version:" + version);
        }

        @Override
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);
            if (!result) {
                Toast.makeText(mContext, R.string.uhf_msg_upgrade_fail, Toast.LENGTH_SHORT).show();
                tvMsg.setText(R.string.uhf_msg_upgrade_fail);
                tvMsg.setTextColor(Color.RED);
            } else {
                Toast.makeText(mContext, R.string.uhf_msg_upgrade_succ, Toast.LENGTH_SHORT).show();
                tvMsg.setText(R.string.uhf_msg_upgrade_succ);
                tvMsg.setTextColor(Color.GREEN);
            }
            String version = "";
            if (flag == 0) {
                version = mContext.uhf.getSTM32Version();
            } else if (flag == 1) {
                version = mContext.uhf.getVersion();
            } else if (flag == 3) {
                version = mContext.uhf.getEx10SDKFirmware();
            }

            tvMsg.setText(tvMsg.getText() + " version=" + version);
            progressDialog.dismiss();
        }

        private void stop() {
            Log.d(TAG, "UHF uhfStopUpdate");
            if (!mContext.uhf.uhfStopUpdate())
                Log.d(TAG, "uhfStopUpdate fail");
            sleep(2000);
        }
    }

    private void sleep(int time) {
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    // -----------------------------------------------------------------------------
    PathReceiver pathReceiver = new PathReceiver();

    public void init() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(FileManagerActivity.Path_ACTION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            mContext.registerReceiver(pathReceiver, intentFilter, Context.RECEIVER_EXPORTED);
        } else {
            mContext.registerReceiver(pathReceiver, intentFilter);
        }
    }

    private void reconnect() {
        mContext.connectBluetooth(mContext.mDevice.getAddress());
    }

    public class PathReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            mFilePath = intent.getStringExtra(FileManagerActivity.Path_Key);
            mFileStreamUri = Uri.fromFile(new File(mFilePath));
            tvPath.setText(mFilePath);
        }
    }

    private class ProgressBroadcastsReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            final String address = intent.getStringExtra(DfuBaseService.EXTRA_DEVICE_ADDRESS);
            final String action = intent.getAction();
            if (action == null)
                return;

            switch (action) {
                case DfuBaseService.BROADCAST_PROGRESS:
                    final int progress = intent.getIntExtra(DfuBaseService.EXTRA_DATA, 0);
                    final float speed = intent.getFloatExtra(DfuBaseService.EXTRA_SPEED_B_PER_MS, 0.0f);
                    final float avgSpeed = intent.getFloatExtra(DfuBaseService.EXTRA_AVG_SPEED_B_PER_MS, 0.0f);
                    final int currentPart = intent.getIntExtra(DfuBaseService.EXTRA_PART_CURRENT, 0);
                    final int partsTotal = intent.getIntExtra(DfuBaseService.EXTRA_PARTS_TOTAL, 0);

                    switch (progress) {
                        case DfuBaseService.PROGRESS_CONNECTING:
                            setMsg("Connecting…");
                            break;
                        case DfuBaseService.PROGRESS_STARTING:
                            setMsg("Starting DFU…");
                            break;
                        case DfuBaseService.PROGRESS_ENABLING_DFU_MODE:
                            setMsg("Starting bootloader…");
                            break;
                        case DfuBaseService.PROGRESS_VALIDATING:
                            setMsg("Validating…");
                            break;
                        case DfuBaseService.PROGRESS_DISCONNECTING:
                            setMsg("Disconnecting…");
                            break;
                        case DfuBaseService.PROGRESS_COMPLETED:
                            setMsg("Done");
                            Toast.makeText(mContext, "success", Toast.LENGTH_SHORT).show();
                            hideDialog();
                            reconnect();
                            break;
                        case DfuBaseService.PROGRESS_ABORTED:
                            setMsg("Uploading of the application has been canceled.");
                            Toast.makeText(mContext, "canceled", Toast.LENGTH_SHORT).show();
                            hideDialog();
                            reconnect();
                            break;
                        default:
                            setProgress(progress);
                            break;
                    }
                    break;
                case DfuBaseService.BROADCAST_ERROR:
                    final int error = intent.getIntExtra(DfuBaseService.EXTRA_DATA, 0);
                    final int errorType = intent.getIntExtra(DfuBaseService.EXTRA_ERROR_TYPE, 0);
                    switch (errorType) {
                        case DfuBaseService.ERROR_TYPE_COMMUNICATION_STATE:
                            setMsg(GattError.parseConnectionError(error));
                            Toast.makeText(mContext, "fail", Toast.LENGTH_SHORT).show();
                            hideDialog();
                            Log.e(TAG, String.format("error=%d,type=%d,msg=%s", error, errorType,
                                    GattError.parseConnectionError(error)));
                            // reconnect();
                            break;
                        case DfuBaseService.ERROR_TYPE_DFU_REMOTE:
                            setMsg(GattError.parseConnectionError(error));
                            Toast.makeText(mContext, "fail", Toast.LENGTH_SHORT).show();
                            hideDialog();
                            Log.e(TAG, String.format("error=%d,type=%d,msg=%s", error, errorType,
                                    GattError.parseConnectionError(error)));
                            // reconnect();
                            break;
                        default:
                            setMsg(GattError.parse(error));
                            Toast.makeText(mContext, "fail", Toast.LENGTH_SHORT).show();
                            Log.e(TAG,
                                    String.format("error=%d,type=%d,msg=%s", error, errorType, GattError.parse(error)));
                            hideDialog();
                            // reconnect();
                            break;
                    }
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case SELECT_FILE_REQ:
                if (data != null) {
                    final Uri uri = data.getData();
                    if (uri != null && uri.getScheme().equals("content")) {
                        mFileStreamUri = uri;
                        mFilePath = getRealPathFromURI(mContext, mFileStreamUri);
                        tvPath.setText(mFilePath);
                    }
                }
                break;
        }
    }

    public void registerProgressListener() {
        if (mProgressBroadcastReceiver == null) {
            mProgressBroadcastReceiver = new ProgressBroadcastsReceiver();
            final IntentFilter filter = new IntentFilter();
            filter.addAction(DfuBaseService.BROADCAST_PROGRESS);
            filter.addAction(DfuBaseService.BROADCAST_ERROR);
            LocalBroadcastManager.getInstance(mContext).registerReceiver(mProgressBroadcastReceiver, filter);
        }
    }

    public void unregisterProgressListener() {
        if (mProgressBroadcastReceiver != null) {
            LocalBroadcastManager.getInstance(mContext).unregisterReceiver(mProgressBroadcastReceiver);
            mProgressBroadcastReceiver = null;
        }
    }

    // ---------------------蓝牙固件升级
    // being--------------------------------------------------------
    @SuppressLint("MissingPermission")
    public void updateBLE(Context context, String mFilePath, Uri mFileStreamUri, BluetoothDevice mSelectedDevice) {

        Log.e(TAG, "mFileStreamUri=" + mFileStreamUri);
        Log.e(TAG, "mFilePath=" + mFilePath);
        Log.e(TAG, "mSelectedDevice=" + mSelectedDevice);
        tvMsg.setText("");
        RFIDWithUHFBLE bleUhf = mContext.getUhfBLE();
        if (bleUhf != null) {
            beforeVerMap = bleUhf.getBluetoothVersion();
        }
        Log.e(TAG, "beforeVerMap=" + beforeVerMap);
        // todo mContext.uhf.setConnectionStatusCallback(null);
        if (TextUtils.isEmpty(mFilePath) || mFileStreamUri == null) {
            Toast.makeText(mContext, getString(R.string.choose_update_file), Toast.LENGTH_LONG).show();
            return;
        }
        if (mSelectedDevice == null) {
            Toast.makeText(mContext, getString(R.string.choose_ble_device), Toast.LENGTH_LONG).show();
            return;
        }
        final DfuServiceInitiator starter = new DfuServiceInitiator(mSelectedDevice.getAddress())
                .setDeviceName(mSelectedDevice.getName())
                .setKeepBond(false)
                .setForceDfu(false)
                .setForeground(false)
                .setPacketsReceiptNotificationsEnabled(false)
                .setPacketsReceiptNotificationsValue(12)
                .setDisableNotification(true)
                .setUnsafeExperimentalButtonlessServiceInSecureDfuEnabled(true);
        starter.setZip(mFileStreamUri, mFilePath);
        starter.start(context, DfuService.class);
        showDialog();
    }

    private void showDialog() {
        if (progressDialog == null) {
            progressDialog = new ProgressDialog(mContext);
        }
        progressDialog.setMessage(getString(R.string.prepare_update_ble));
        progressDialog.setCancelable(false);
        progressDialog.setCanceledOnTouchOutside(false);
        progressDialog.show();
    }

    private void hideDialog() {
        if (progressDialog != null)
            progressDialog.dismiss();
        progressDialog = null;
    }

    private void setMsg(String msg) {
        if (progressDialog != null)
            progressDialog.setMessage(msg);
    }

    private void setProgress(int pro) {
        if (progressDialog != null)
            progressDialog.setMessage(pro + "%");
    }

    @SuppressLint("SetTextI18n")
    private void showBTVersion() {
        Log.e(TAG, "beforeVerMap=" + beforeVerMap + ", lastestVerMap=" + latestVerMap);
        if (beforeVerMap != null && latestVerMap != null) {
            tvMsg.setText(getString(R.string.before_firmware_version) + beforeVerMap.get(RFIDWithUHFBLE.VERSION_BT_FIRMWARE)
                    + "\n" + getString(R.string.before_hardware_version) + beforeVerMap.get(RFIDWithUHFBLE.VERSION_BT_HARDWARE)
                    + "\n" + getString(R.string.before_software_version) + beforeVerMap.get(RFIDWithUHFBLE.VERSION_BT_SOFTWARE)
                    + "\n" + getString(R.string.after_firmware_version) + latestVerMap.get(RFIDWithUHFBLE.VERSION_BT_FIRMWARE)
                    + "\n" + getString(R.string.after_hardware_version) + latestVerMap.get(RFIDWithUHFBLE.VERSION_BT_HARDWARE)
                    + "\n" + getString(R.string.after_software_version) + latestVerMap.get(RFIDWithUHFBLE.VERSION_BT_SOFTWARE));

            // 修复当升级蓝牙固件后继续升级其他模块时，断开连接重新连接后，tvMsg显示蓝牙固件的信息。
            beforeVerMap = null;
        }
    }

    public String getRealPathFromURI(Context context, Uri contentURI) {
        String result;
        Cursor cursor = context.getContentResolver().query(contentURI,
                new String[]{MediaStore.Images.ImageColumns.DATA},//
                null, null, null);
        if (cursor == null)
            result = contentURI.getPath();
        else {
            cursor.moveToFirst();
            int index = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA);
            result = cursor.getString(index);
            cursor.close();
        }
        return result;
    }
}
