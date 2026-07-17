package com.example.uhf_bt.fragment;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import com.example.uhf_bt.MainActivity;
import com.example.uhf_bt.R;
import com.rscja.deviceapi.RFIDWithUHFBLE;
import com.rscja.deviceapi.ReaderBluetoothParameter;
import com.rscja.deviceapi.interfaces.ConnectionStatus;

public class BleParamFragment extends Fragment {
    private static final String TAG = "BleParamFragment";
    private MainActivity mContext;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private ReaderBluetoothParameter bluetoothParam;

    // UI Components
    private EditText etBluetoothName;
    private Spinner spinnerBaudrate;
    private EditText etPinCode;
    private EditText etBluetoothMac;
    private Spinner spinnerKeyboardType;
    private EditText etHidInterval;
    private Switch switchPairProperty;

    // 蓝牙服务相关参数控件
    private EditText etPostConnectionTime;
    private EditText etMinConnectionTime;
    private EditText etMaxConnectionTime;
    private EditText etMinSendTime;
    private Switch switchKeyboardService;
    private Switch switchSerialService;

    // Buttons
    private Button btnSetBluetoothName, btnGetBluetoothName;
    private Button btnSetBaudrate, btnGetBaudrate;
    private Button btnSetPinCode, btnGetPinCode;
    private Button btnSetBluetoothMac, btnGetBluetoothMac;
    private Button btnSetKeyboardType, btnGetKeyboardType;
    private Button btnSetHidInterval, btnGetHidInterval;
    private Button btnSetPairProperty, btnGetPairProperty;
    private Button btnRemoveBond, btnRestoreDefault;
    private Button btnGetBluetoothInfo;

    // 蓝牙服务相关参数按钮
    private Button btnSetPostConnectionTime, btnGetPostConnectionTime;
    private Button btnSetMinConnectionTime, btnGetMinConnectionTime;
    private Button btnSetMaxConnectionTime, btnGetMaxConnectionTime;
    private Button btnSetMinSendTime, btnGetMinSendTime;
    private Button btnSetServices, btnGetServices;

    // Info TextViews
    private TextView tvHardwareVersion, tvFirmwareVersion, tvSoftwareVersion;
    private TextView tvManufacturer;

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mContext = (MainActivity) getActivity();
        if (RFIDWithUHFBLE.getInstance() != null) {
            bluetoothParam = RFIDWithUHFBLE.getInstance().getReaderBluetoothParameter();
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (RFIDWithUHFBLE.getInstance().getConnectStatus() == ConnectionStatus.CONNECTED) {
            // get all parameters
            new Thread(() -> {
                getBluetoothName(false);
                getBaudrate(false);
                getPinCode(false);
                getBluetoothMac(false);
                getKeyboardType(false);
                getHidInterval(false);
                getPairProperty(false);
                getServices(false);
                getPostConnectionTime(false);
                getMinConnectionTime(false);
                getMaxConnectionTime(false);
                getMinSendTime(false);
                getBluetoothInfo(false);
            }).start();
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_ble_param, container, false);
        initViews(view);
        return view;
    }

    private void initViews(View view) {
        // EditTexts
        etBluetoothName = view.findViewById(R.id.et_bluetooth_name);
        etPinCode = view.findViewById(R.id.et_pin_code);
        etBluetoothMac = view.findViewById(R.id.et_bluetooth_mac);
        etHidInterval = view.findViewById(R.id.et_hid_interval);

        // Spinners
        spinnerBaudrate = view.findViewById(R.id.spinner_baudrate);
        spinnerKeyboardType = view.findViewById(R.id.spinner_keyboard_type);

        // Switch
        switchPairProperty = view.findViewById(R.id.switch_pair_property);

        // 蓝牙服务相关参数控件
        etPostConnectionTime = view.findViewById(R.id.et_post_connection_time);
        etMinConnectionTime = view.findViewById(R.id.et_min_connection_time);
        etMaxConnectionTime = view.findViewById(R.id.et_max_connection_time);
        etMinSendTime = view.findViewById(R.id.et_min_send_time);
        switchKeyboardService = view.findViewById(R.id.switch_keyboard_service);
        switchSerialService = view.findViewById(R.id.switch_serial_service);
        switchSerialService.setOnClickListener(v -> {
            if (!switchSerialService.isChecked()) {
                mContext.showToast(R.string.serial_service_warning_change_tip);
            }
            switchSerialService.setChecked(true);
        });

        // Buttons - Bluetooth Name
        btnSetBluetoothName = view.findViewById(R.id.btn_set_bluetooth_name);
        btnGetBluetoothName = view.findViewById(R.id.btn_get_bluetooth_name);
        btnSetBluetoothName.setOnClickListener(v -> setBluetoothName());
        btnGetBluetoothName.setOnClickListener(v -> getBluetoothName(true));

        // Buttons - Baudrate
        btnSetBaudrate = view.findViewById(R.id.btn_set_baudrate);
        btnGetBaudrate = view.findViewById(R.id.btn_get_baudrate);
        btnSetBaudrate.setOnClickListener(v -> setBaudrate());
        btnGetBaudrate.setOnClickListener(v -> getBaudrate(true));

        // Buttons - PIN Code
        btnSetPinCode = view.findViewById(R.id.btn_set_pin_code);
        btnGetPinCode = view.findViewById(R.id.btn_get_pin_code);
        btnSetPinCode.setOnClickListener(v -> setPinCode());
        btnGetPinCode.setOnClickListener(v -> getPinCode(true));

        // Buttons - Bluetooth MAC
        btnSetBluetoothMac = view.findViewById(R.id.btn_set_bluetooth_mac);
        btnGetBluetoothMac = view.findViewById(R.id.btn_get_bluetooth_mac);
        btnSetBluetoothMac.setOnClickListener(v -> setBluetoothMac());
        btnGetBluetoothMac.setOnClickListener(v -> getBluetoothMac(true));

        // Buttons - Keyboard Type
        btnSetKeyboardType = view.findViewById(R.id.btn_set_keyboard_type);
        btnGetKeyboardType = view.findViewById(R.id.btn_get_keyboard_type);
        btnSetKeyboardType.setOnClickListener(v -> setKeyboardType());
        btnGetKeyboardType.setOnClickListener(v -> getKeyboardType(true));

        // Buttons - HID Interval
        btnSetHidInterval = view.findViewById(R.id.btn_set_hid_interval);
        btnGetHidInterval = view.findViewById(R.id.btn_get_hid_interval);
        btnSetHidInterval.setOnClickListener(v -> setHidInterval());
        btnGetHidInterval.setOnClickListener(v -> getHidInterval(true));

        // Buttons - Pair Property
        btnSetPairProperty = view.findViewById(R.id.btn_set_pair_property);
        btnGetPairProperty = view.findViewById(R.id.btn_get_pair_property);
        btnSetPairProperty.setOnClickListener(v -> setPairProperty());
        btnGetPairProperty.setOnClickListener(v -> getPairProperty(true));

        // Other Buttons
        btnRemoveBond = view.findViewById(R.id.btn_remove_bond);
        btnRestoreDefault = view.findViewById(R.id.btn_restore_default);
        btnGetBluetoothInfo = view.findViewById(R.id.btn_get_bluetooth_info);
        btnRemoveBond.setOnClickListener(v -> removeBond());
        btnRestoreDefault.setOnClickListener(v -> restoreDefault());
        btnGetBluetoothInfo.setOnClickListener(v -> getBluetoothInfo(true));

        // 蓝牙服务相关参数按钮
        btnSetPostConnectionTime = view.findViewById(R.id.btn_set_post_connection_time);
        btnGetPostConnectionTime = view.findViewById(R.id.btn_get_post_connection_time);
        btnSetMinConnectionTime = view.findViewById(R.id.btn_set_min_connection_time);
        btnGetMinConnectionTime = view.findViewById(R.id.btn_get_min_connection_time);
        btnSetMaxConnectionTime = view.findViewById(R.id.btn_set_max_connection_time);
        btnGetMaxConnectionTime = view.findViewById(R.id.btn_get_max_connection_time);
        btnSetMinSendTime = view.findViewById(R.id.btn_set_min_send_time);
        btnGetMinSendTime = view.findViewById(R.id.btn_get_min_send_time);
        btnSetServices = view.findViewById(R.id.btn_set_services);
        btnGetServices = view.findViewById(R.id.btn_get_services);

        // 蓝牙服务相关参数按钮点击事件
        btnSetPostConnectionTime.setOnClickListener(v -> setPostConnectionTime());
        btnGetPostConnectionTime.setOnClickListener(v -> getPostConnectionTime(true));
        btnSetMinConnectionTime.setOnClickListener(v -> setMinConnectionTime());
        btnGetMinConnectionTime.setOnClickListener(v -> getMinConnectionTime(true));
        btnSetMaxConnectionTime.setOnClickListener(v -> setMaxConnectionTime());
        btnGetMaxConnectionTime.setOnClickListener(v -> getMaxConnectionTime(true));
        btnSetMinSendTime.setOnClickListener(v -> setMinSendTime());
        btnGetMinSendTime.setOnClickListener(v -> getMinSendTime(true));
        btnSetServices.setOnClickListener(v -> setServices());
        btnGetServices.setOnClickListener(v -> getServices(true));

        // Info TextViews
        tvHardwareVersion = view.findViewById(R.id.tv_hardware_version);
        tvFirmwareVersion = view.findViewById(R.id.tv_firmware_version);
        tvSoftwareVersion = view.findViewById(R.id.tv_software_version);
        tvManufacturer = view.findViewById(R.id.tv_manufacturer);
    }

    // Click event handlers implemented as individual methods below

    private boolean checkConnection() {
        if (RFIDWithUHFBLE.getInstance().getConnectStatus() != ConnectionStatus.CONNECTED) {
            handler.post(() -> mContext.showToast(getString(R.string.no_connect)));
            return false;
        }
        return true;
    }

    private void setBluetoothName() {
        if (!checkConnection()) return;

        String name = etBluetoothName.getText().toString().trim();
        if (TextUtils.isEmpty(name)) {
            mContext.showToast(getString(R.string.ble_param_name_hint));
            return;
        }

        if (name.length() > 14) {
            mContext.showToast(getString(R.string.ble_param_name_too_long));
            return;
        }

        boolean result = bluetoothParam.setBluetoothName(name);
        mContext.showToast(result ? getString(R.string.ble_param_set_success) : getString(R.string.ble_param_set_failed));
        if (result) {
            mContext.updateConnectMessage(mContext.remoteBTName, name);
            mContext.saveConnectedDevice(mContext.remoteBTAdd, name);
        }
    }

    private void getBluetoothName(boolean showToast) {
        if (!checkConnection()) return;

        String name = bluetoothParam.getBluetoothName();
        handler.post(() -> {
            if (name != null) {
                etBluetoothName.setText(name);
            }
            if (showToast) {
                mContext.showToast(getString(name != null ? R.string.ble_param_get_success : R.string.ble_param_get_failed));
            }
        });
    }

    private void setBaudrate() {
        if (!checkConnection()) return;

        String baudrateStr = spinnerBaudrate.getSelectedItem().toString();
        int baudrate = Integer.parseInt(baudrateStr);
        boolean result = bluetoothParam.setBaudrate(baudrate);
        mContext.showToast(result ? getString(R.string.ble_param_set_success) : getString(R.string.ble_param_set_failed));
    }

    private void getBaudrate(boolean showToast) {
        if (!checkConnection()) return;

        int baudrate = bluetoothParam.getBaudrate();
        handler.post(() -> {
            // Find and select the corresponding item in spinner
            for (int i = 0; i < spinnerBaudrate.getCount(); i++) {
                if (spinnerBaudrate.getItemAtPosition(i).toString().equals(String.valueOf(baudrate))) {
                    spinnerBaudrate.setSelection(i);
                    break;
                }
            }
            if (showToast) {
                mContext.showToast(getString(R.string.ble_param_get_success) + ": " + baudrate);
            }
        });
    }

    private void setPinCode() {
        if (!checkConnection()) return;

        String pinCode = etPinCode.getText().toString().trim();
        if (TextUtils.isEmpty(pinCode) || pinCode.length() != 6) {
            mContext.showToast(getString(R.string.ble_param_pin_hint));
            return;
        }

        boolean result = bluetoothParam.setPinkey(pinCode);
        mContext.showToast(result ? getString(R.string.ble_param_set_success) : getString(R.string.ble_param_set_failed));
    }

    private void getPinCode(boolean showToast) {
        if (!checkConnection()) return;

        String pinCode = bluetoothParam.getPinkey();
        handler.post(() -> {
            if (pinCode != null) {
                etPinCode.setText(pinCode);
            }
            if (showToast) {
                mContext.showToast(getString(pinCode != null ? R.string.ble_param_get_success : R.string.ble_param_get_failed));
            }
        });
    }

    private void setBluetoothMac() {
        if (!checkConnection()) return;

        String mac = etBluetoothMac.getText().toString().trim();
        if (TextUtils.isEmpty(mac)) {
            mContext.showToast(getString(R.string.ble_param_mac_empty));
            return;
        }

        // 验证MAC地址格式：E2:7B:96:6A:4A:21
        if (!isValidMacAddress(mac)) {
            mContext.showToast(getString(R.string.ble_param_mac_invalid_format));
            return;
        }

        boolean result = bluetoothParam.setBluetoothMac(mac);
        mContext.showToast(result ? getString(R.string.ble_param_set_success) : getString(R.string.ble_param_set_failed));
    }

    /**
     * 验证MAC地址格式
     * 格式：E2:7B:96:6A:4A:21 (6组十六进制数，用冒号分隔)
     */
    private boolean isValidMacAddress(String mac) {
        if (mac == null || mac.isEmpty()) {
            return false;
        }
        // 正则表达式：6组十六进制数，用冒号分隔
        String macPattern = "^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$";
        return mac.matches(macPattern);
    }

    private void getBluetoothMac(boolean showToast) {
        if (!checkConnection()) return;

        String mac = bluetoothParam.getBluetoothMAC();
        handler.post(() -> {
            if (mac != null) {
                etBluetoothMac.setText(mac);
            }
            if (showToast) {
                mContext.showToast(getString(mac != null ? R.string.ble_param_get_success : R.string.ble_param_get_failed));
            }
        });
    }

    private void setKeyboardType() {
        if (!checkConnection()) return;

        int keyboardType = spinnerKeyboardType.getSelectedItemPosition();
        boolean result = bluetoothParam.setKeyboard(keyboardType);
        mContext.showToast(result ? getString(R.string.ble_param_set_success) : getString(R.string.ble_param_set_failed));
    }

    private void getKeyboardType(boolean showToast) {
        if (!checkConnection()) return;

        int keyboardType = bluetoothParam.getKeyboard();
        handler.post(() -> {
            if (keyboardType >= 0 && keyboardType < spinnerKeyboardType.getCount()) {
                spinnerKeyboardType.setSelection(keyboardType);
            }
            if (showToast) {
                if (keyboardType >= 0 && keyboardType < spinnerKeyboardType.getCount()) {
                    mContext.showToast(getString(R.string.ble_param_get_success) + ": " + keyboardType);
                } else {
                    mContext.showToast(getString(R.string.ble_param_get_failed));
                }
            }
        });
    }

    private void setHidInterval() {
        if (!checkConnection()) return;

        String intervalStr = etHidInterval.getText().toString().trim();
        if (TextUtils.isEmpty(intervalStr)) {
            mContext.showToast(getString(R.string.ble_param_interval_hint));
            return;
        }

        short interval;
        try {
            interval = Short.parseShort(intervalStr);
        } catch (NumberFormatException e) {
            mContext.showToast(getString(R.string.ble_param_range_error, 0, 100));
            return;
        }

        // 验证范围：0-100毫秒，默认0
        if (interval < 0 || interval > 100) {
            mContext.showToast(getString(R.string.ble_param_range_error, 0, 100));
            return;
        }

        boolean result = bluetoothParam.setHidKeyboardCharInterval(interval);
        mContext.showToast(result ? getString(R.string.ble_param_set_success) : getString(R.string.ble_param_set_failed));
    }

    private void getHidInterval(boolean showToast) {
        if (!checkConnection()) return;

        short interval = bluetoothParam.getHidKeyboardCharInterval();
        handler.post(() -> {
            etHidInterval.setText(String.valueOf(interval));
            if (showToast) {
                mContext.showToast(getString(R.string.ble_param_get_success) + ": " + interval);
            }
        });
    }

    private void setPairProperty() {
        if (!checkConnection()) return;

        int pairStatus = switchPairProperty.isChecked() ? 1 : 0;
        boolean result = bluetoothParam.setPairWithBluetoothDevice(pairStatus);
        if (result) {
            mContext.showToast(getString(R.string.ble_param_set_success));
        } else {
            mContext.showToast(getString(R.string.ble_param_set_failed));
        }
        mContext.showToast(result ? getString(R.string.ble_param_set_success) : getString(R.string.ble_param_set_failed));
    }

    private void getPairProperty(boolean showToast) {
        if (!checkConnection()) return;

        int pairStatus = bluetoothParam.getPairWithBluetoothDevice();
        handler.post(() -> {
            switchPairProperty.setChecked(pairStatus == 1);
            if (showToast) {
                String statusText = pairStatus == 1 ? getString(R.string.ble_param_bound) : getString(R.string.ble_param_unbound);
                mContext.showToast(getString(R.string.ble_param_get_success) + ": " + statusText);
            }
        });
    }

    private void removeBond() {
        if (!checkConnection()) return;

        boolean result = bluetoothParam.removeBondedBluetoothDevice();
        if (result) {
            mContext.showToast(getString(R.string.ble_param_operation_success));
        } else {
            mContext.showToast(getString(R.string.ble_param_operation_failed));
        }
        mContext.showToast(result ? getString(R.string.ble_param_operation_success) : getString(R.string.ble_param_operation_failed));
    }

    private void restoreDefault() {
        if (!checkConnection()) return;

        boolean result = bluetoothParam.restoreDefaultBluetoothParams();
        if (result) {
            mContext.showToast(getString(R.string.ble_param_operation_success));
            clearAllFields();
        } else {
            mContext.showToast(getString(R.string.ble_param_operation_failed));
        }
        mContext.showToast(result ? getString(R.string.ble_param_operation_success) : getString(R.string.ble_param_operation_failed));
    }

    private void getBluetoothInfo(boolean showToast) {
        if (!checkConnection()) return;

        ReaderBluetoothParameter.BluetoothInfo info = bluetoothParam.getBluetoothInfo();
        handler.post(() -> {
            if (info != null) {
                tvHardwareVersion.setText(info.getHardwareVersion() != null ? info.getHardwareVersion() : "-");
                tvFirmwareVersion.setText(info.getFirmwareVersion() != null ? info.getFirmwareVersion() : "-");
                tvSoftwareVersion.setText(info.getSoftwareVersion() != null ? info.getSoftwareVersion() : "-");
                tvManufacturer.setText(info.getManufactor() != null ? info.getManufactor() : "-");
            }
            if (showToast) {
                mContext.showToast(getString(info != null ? R.string.ble_param_get_success : R.string.ble_param_get_failed));
            }
        });
    }

    // 蓝牙服务相关参数方法实现
    private void setPostConnectionTime() {
        if (!checkConnection()) return;

        String timeStr = etPostConnectionTime.getText().toString().trim();
        if (TextUtils.isEmpty(timeStr)) {
            mContext.showToast(getString(R.string.ble_param_time_hint));
            return;
        }

        int time;
        try {
            time = Integer.parseInt(timeStr);
        } catch (NumberFormatException e) {
            mContext.showToast(getString(R.string.ble_param_range_error, 0, 5000));
            return;
        }

        // 验证范围：0-5000毫秒，默认1000
        if (time < 0 || time > 5000) {
            mContext.showToast(getString(R.string.ble_param_range_error, 0, 5000));
            return;
        }

        boolean result = bluetoothParam.setPostBluetoothConnectionTime(time);
        mContext.showToast(result ? getString(R.string.ble_param_set_success) : getString(R.string.ble_param_set_failed));
    }

    private void getPostConnectionTime(boolean showToast) {
        if (!checkConnection()) return;

        int time = bluetoothParam.getPostBluetoothConnectionTime();
        handler.post(() -> {
            etPostConnectionTime.setText(String.valueOf(time));
            if (showToast) {
                mContext.showToast(getString(R.string.ble_param_get_success) + ": " + time);
            }
        });
    }

    private void setMinConnectionTime() {
        if (!checkConnection()) return;

        String timeStr = etMinConnectionTime.getText().toString().trim();
        if (TextUtils.isEmpty(timeStr)) {
            mContext.showToast(getString(R.string.ble_param_time_hint));
            return;
        }

        int time;
        try {
            time = Integer.parseInt(timeStr);
        } catch (NumberFormatException e) {
            mContext.showToast(getString(R.string.ble_param_range_error, 8, 20));
            return;
        }

        // 验证范围：8-20毫秒，默认8
        if (time < 8 || time > 20) {
            mContext.showToast(getString(R.string.ble_param_range_error, 8, 20));
            return;
        }

        boolean result = bluetoothParam.setMinimumConnectionTime(time);
        mContext.showToast(result ? getString(R.string.ble_param_set_success) : getString(R.string.ble_param_set_failed));
    }

    private void getMinConnectionTime(boolean showToast) {
        if (!checkConnection()) return;

        int time = bluetoothParam.getMinimumConnectionTime();
        handler.post(() -> {
            etMinConnectionTime.setText(String.valueOf(time));
            if (showToast) {
                mContext.showToast(getString(R.string.ble_param_get_success) + ": " + time);
            }
        });
    }

    private void setMaxConnectionTime() {
        if (!checkConnection()) return;

        String timeStr = etMaxConnectionTime.getText().toString().trim();
        if (TextUtils.isEmpty(timeStr)) {
            mContext.showToast(getString(R.string.ble_param_time_hint));
            return;
        }

        int time;
        try {
            time = Integer.parseInt(timeStr);
        } catch (NumberFormatException e) {
            mContext.showToast(getString(R.string.ble_param_range_error, 24, 40));
            return;
        }

        // 验证范围：24-40毫秒，默认24
        if (time < 24 || time > 40) {
            mContext.showToast(getString(R.string.ble_param_range_error, 24, 40));
            return;
        }

        boolean result = bluetoothParam.setMaximumConnectionTime(time);
        mContext.showToast(result ? getString(R.string.ble_param_set_success) : getString(R.string.ble_param_set_failed));
    }

    private void getMaxConnectionTime(boolean showToast) {
        if (!checkConnection()) return;

        int time = bluetoothParam.getMaximumConnectionTime();
        handler.post(() -> {
            etMaxConnectionTime.setText(String.valueOf(time));
            if (showToast) {
                mContext.showToast(getString(R.string.ble_param_get_success) + ": " + time);
            }
        });
    }

    private void setMinSendTime() {
        if (!checkConnection()) return;

        String timeStr = etMinSendTime.getText().toString().trim();
        if (TextUtils.isEmpty(timeStr)) {
            mContext.showToast(getString(R.string.ble_param_time_hint));
            return;
        }

        int time;
        try {
            time = Integer.parseInt(timeStr);
        } catch (NumberFormatException e) {
            mContext.showToast(getString(R.string.ble_param_range_error, 0, 255));
            return;
        }

        // 验证范围：0-255毫秒，默认10
        if (time < 0 || time > 255) {
            mContext.showToast(getString(R.string.ble_param_range_error, 0, 255));
            return;
        }

        boolean result = bluetoothParam.setMinimumSendTime(time);
        mContext.showToast(result ? getString(R.string.ble_param_set_success) : getString(R.string.ble_param_set_failed));
    }

    private void getMinSendTime(boolean showToast) {
        if (!checkConnection()) return;

        int time = bluetoothParam.getMinimumSendTime();
        handler.post(() -> {
            etMinSendTime.setText(String.valueOf(time));
            if (showToast) {
                mContext.showToast(getString(R.string.ble_param_get_success) + ": " + time);
            }
        });
    }

    private void setServices() {
        if (!checkConnection()) return;

        ReaderBluetoothParameter.BluetoothServices services = new ReaderBluetoothParameter.BluetoothServices();
        services.setSupportKeyboardService(switchKeyboardService.isChecked());
        services.setSupportSerialPortService(switchSerialService.isChecked());

        boolean result = bluetoothParam.setSupportedServices(services);
        mContext.showToast(result ? getString(R.string.ble_param_set_success) : getString(R.string.ble_param_set_failed));
    }

    private void getServices(boolean showToast) {
        if (!checkConnection()) return;

        ReaderBluetoothParameter.BluetoothServices services = bluetoothParam.getSupportedServices();
        handler.post(() -> {
            if (services != null) {
                switchKeyboardService.setChecked(services.isSupportKeyboardService());
                switchSerialService.setChecked(services.isSupportSerialPortService());
            }
            if (showToast) {
                if (services != null) {
                    String status = "Keyboard: " + (services.isSupportKeyboardService() ? getString(R.string.ble_param_supported) : getString(R.string.ble_param_not_supported)) +
                            ", Serial: " + (services.isSupportSerialPortService() ? getString(R.string.ble_param_supported) : getString(R.string.ble_param_not_supported));
                    mContext.showToast(getString(R.string.ble_param_get_success) + "\n" + status);
                } else {
                    mContext.showToast(getString(R.string.ble_param_get_failed));
                }
            }
        });
    }

    private void clearAllFields() {
        etBluetoothName.setText("");
        etPinCode.setText("");
        etBluetoothMac.setText("");
        etHidInterval.setText("");
        spinnerBaudrate.setSelection(0);
        spinnerKeyboardType.setSelection(0);
        switchPairProperty.setChecked(false);

        // 清除蓝牙服务相关参数字段
        etPostConnectionTime.setText("");
        etMinConnectionTime.setText("");
        etMaxConnectionTime.setText("");
        etMinSendTime.setText("");
        switchKeyboardService.setChecked(false);
        switchSerialService.setChecked(false);

        tvHardwareVersion.setText("-");
        tvFirmwareVersion.setText("-");
        tvSoftwareVersion.setText("-");
        tvManufacturer.setText("-");
    }

}