package com.example.uhf_bt.fragment;

import static com.example.uhf_bt.tool.FileUtils.readFile;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.uhf_bt.MainActivity;
import com.example.uhf_bt.R;
import com.example.uhf_bt.tool.SPUtils;
import com.example.uhf_bt.tool.StringUtils;
import com.rscja.deviceapi.RFIDWithUHFBLE;
import com.rscja.deviceapi.entity.FastInventoryEntity;
import com.rscja.deviceapi.entity.Gen2Entity;
import com.rscja.deviceapi.entity.InventoryModeEntity;

import java.io.File;

public class UHFSetFragment extends Fragment implements View.OnClickListener {
    private final static String TAG = "UHFSetFragment";
    MainActivity context;

    Button btnGetPower, btnSetPower, BtSetFre, BtGetFre, btnSetProtocol, btnGetProtocol, btnSetFreHop, btnbeepOpen,
            btnbeepClose;
    Button btnSetRFlink, btnGetRFlink, btnSetInventoryBank, btnGetInventoryBank, btnSetGen2, btnGetGen2;
    Button btnSetBuzzerVolume, btnGetBuzzerVolume, btnSetFastInventory, btnGetFastInventory, btnFactoryReset;
    Button btnSetBaudrate, btnGetBaudrate;

    Spinner spPower, SpinnerMode, spFreHop, splinkParams, spProtocol, spMemoryBank, spSessionID, spTarget, spParseWay,
            spFastInventory, spinnerBaudrate;
    CheckBox cbTagFocus, cbRssi, cbShowDuplicateTags, cbContinuousWave, cbAutoReconnect, cbFilterGibberish;

    TextView tvBuzzerVolume, tvFrequencyBand;
    SeekBar sbBuzzerVolume;

    LinearLayout llFreHop, llMemoryBankParam, llBaudrate, llBaudrateButtons;
    EditText etOffset, etLength;

    // 连续点击相关变量
    private int clickCount = 0;
    private long lastClickTime = 0;
    private static final int REQUIRED_CLICKS = 5;
    private static final long CLICK_INTERVAL_MS = 2000; // 2秒内

    private String[] arrayPower;
    private int[] arrayLinkValue;
    private int[] arrayMemoryBankValue;

    private final static int GET_FRE = 1;
    private final static int GET_POWER = 2;
    private final static int GET_PROTOCOL = 3;
    private final static int GET_CW = 4;
    private final Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case GET_FRE:
                    String mode = (String) msg.obj;
                    if (!mode.equals("")) {
                        String[] arrayMode = getResources().getStringArray(R.array.arrayMode);
                        for (int k = 0; k < arrayMode.length; k++) {
                            if (arrayMode[k].equals(mode)) {
                                SpinnerMode.setSelection(k);
                                if (msg.arg1 == 1)
                                    context.showToast(R.string.get_succ);
                                break;
                            }
                        }
                    } else {
                        if (msg.arg1 == 1) {
                            context.showToast(R.string.get_fail);
                        }
                    }
                    break;
                case GET_POWER:
                    int iPower = (int) msg.obj;
                    if (arrayPower != null && iPower > -1) {
                        for (int i = 0; i < arrayPower.length; i++) {
                            if (iPower == Integer.valueOf(arrayPower[i])) {
                                spPower.setSelection(i);
                                if (msg.arg1 == 1)
                                    context.showToast(R.string.get_succ);
                                break;
                            }
                        }
                    } else if (msg.arg1 == 1) {
                        context.showToast(R.string.get_fail);
                    }
                    break;
                case GET_PROTOCOL:
                    int pro = (int) msg.obj;
                    if (pro >= 0 && pro < spProtocol.getCount()) {
                        spProtocol.setSelection(pro);
                        if (msg.arg1 == 1)
                            context.showToast(R.string.get_succ);
                    } else {
                        if (msg.arg1 == 1)
                            context.showToast(R.string.get_fail);
                    }
                    break;
                case GET_CW:
                    int flag = (int) msg.obj;
                    if (flag == 1) {
                        cbContinuousWave.setChecked(true);
                        if (msg.arg1 == 1)
                            context.showToast(R.string.get_succ);
                    } else if (flag == 0) {
                        cbContinuousWave.setChecked(false);
                        if (msg.arg1 == 1)
                            context.showToast(R.string.get_succ);
                    } else {
                        if (msg.arg1 == 1)
                            context.showToast(R.string.get_fail);
                    }
                    break;
            }
        }
    };

    private ImageView ivParseWayTips;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_uhfset, container, false);
        init(view);
        return view;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        context = (MainActivity) getActivity();
        // 根据secretCodeFlag控制波特率区域的显示/隐藏
        if (llBaudrate != null && llBaudrateButtons != null && context != null) {
            int visible = context.secretCodeFlag ? View.VISIBLE : View.GONE;
            llBaudrate.setVisibility(visible);
            llBaudrateButtons.setVisibility(visible);
        }
        loadData();
    }

    private void init(View view) {
        spSessionID = view.findViewById(R.id.spSessionID);
        spTarget = view.findViewById(R.id.spTarget);
        btnGetGen2 = view.findViewById(R.id.btnGetGen2);
        btnSetGen2 = view.findViewById(R.id.btnSetGen2);
        btnGetGen2.setOnClickListener(this);
        btnSetGen2.setOnClickListener(this);
        llFreHop = view.findViewById(R.id.llFreHop);

        btnGetPower = (Button) view.findViewById(R.id.btnGetPower);
        btnSetPower = (Button) view.findViewById(R.id.btnSetPower);

        spPower = (Spinner) view.findViewById(R.id.spPower);
        arrayPower = getResources().getStringArray(R.array.arrayPower);
        // ArrayAdapter adapter = new ArrayAdapter(getContext(),
        // android.R.layout.simple_spinner_item, arrayPower);
        // spPower.setAdapter(adapter);

        SpinnerMode = (Spinner) view.findViewById(R.id.SpinnerMode);
        tvFrequencyBand = view.findViewById(R.id.tvFrequencyBand);
        BtSetFre = (Button) view.findViewById(R.id.BtSetFre);
        BtGetFre = (Button) view.findViewById(R.id.BtGetFre);

        spFreHop = (Spinner) view.findViewById(R.id.spFreHop);
        btnSetFreHop = (Button) view.findViewById(R.id.btnSetFreHop);

        btnbeepOpen = (Button) view.findViewById(R.id.btnbeepOpen);
        btnbeepClose = (Button) view.findViewById(R.id.btnbeepClose);
        cbTagFocus = (CheckBox) view.findViewById(R.id.cbTagFocus);
        cbRssi = (CheckBox) view.findViewById(R.id.cbRssi);
        cbShowDuplicateTags = (CheckBox) view.findViewById(R.id.cbShowDuplicateTags);
        splinkParams = view.findViewById(R.id.splinkParams);
        arrayLinkValue = getResources().getIntArray(R.array.arrayLinkValue);
        btnSetRFlink = (Button) view.findViewById(R.id.btnSetRFlink);
        btnGetRFlink = (Button) view.findViewById(R.id.btnGetRFlink);

        spMemoryBank = (Spinner) view.findViewById(R.id.spMemoryBank);
        arrayMemoryBankValue = getResources().getIntArray(R.array.arrayMemoryBankValue);
        llMemoryBankParam = view.findViewById(R.id.llMemoryBankParam);
        etOffset = view.findViewById(R.id.etOffset);
        etLength = view.findViewById(R.id.etLength);
        spMemoryBank.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                llMemoryBankParam.setVisibility(i == 2 || i == 3 ? View.VISIBLE : View.GONE);
                if (i == 2) {
                    etLength.setText("6");
                } else if (i == 3) {
                    etLength.setText("4");
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });
        llMemoryBankParam.setVisibility(
                spMemoryBank.getSelectedItemPosition() == 2 || spMemoryBank.getSelectedItemPosition() == 3
                        ? View.VISIBLE
                        : View.GONE);

        spFastInventory = view.findViewById(R.id.spFastInventory);
        btnSetFastInventory = view.findViewById(R.id.btnSetFastInventory);
        btnGetFastInventory = view.findViewById(R.id.btnGetFastInventory);

        btnSetInventoryBank = view.findViewById(R.id.btnSetInventoryBank);
        btnGetInventoryBank = view.findViewById(R.id.btnGetInventoryBank);
        btnSetInventoryBank.setOnClickListener(this);
        btnGetInventoryBank.setOnClickListener(this);

        btnGetFastInventory.setOnClickListener(this);
        btnSetFastInventory.setOnClickListener(this);
        btnSetRFlink.setOnClickListener(this);
        btnGetRFlink.setOnClickListener(this);
        cbTagFocus.setOnClickListener(this);

        btnSetFreHop.setOnClickListener(this);
        btnGetPower.setOnClickListener(this);
        btnSetPower.setOnClickListener(this);
        BtSetFre.setOnClickListener(this);
        BtGetFre.setOnClickListener(this);
        cbRssi.setOnClickListener(this);
        btnbeepOpen.setOnClickListener(this);
        btnbeepClose.setOnClickListener(this);
        cbShowDuplicateTags.setOnClickListener(this);
        spProtocol = (Spinner) view.findViewById(R.id.spProtocol);
        btnSetProtocol = (Button) view.findViewById(R.id.btnSetProtocol);
        btnSetProtocol.setOnClickListener(this);
        btnGetProtocol = (Button) view.findViewById(R.id.btnGetProtocol);
        btnGetProtocol.setOnClickListener(this);

        btnFactoryReset = (Button) view.findViewById(R.id.btnFactoryReset);
        btnFactoryReset.setOnClickListener(v -> factoryReset());

        // 波特率设置
        llBaudrate = view.findViewById(R.id.llBaudrate);
        llBaudrateButtons = view.findViewById(R.id.llBaudrateButtons);
        spinnerBaudrate = view.findViewById(R.id.spinnerBaudrate);
        btnSetBaudrate = view.findViewById(R.id.btnSetBaudrate);
        btnGetBaudrate = view.findViewById(R.id.btnGetBaudrate);

        btnSetBaudrate.setOnClickListener(this);
        btnGetBaudrate.setOnClickListener(this);

        cbContinuousWave = (CheckBox) view.findViewById(R.id.cbContinuousWave);
        cbContinuousWave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int flag = cbContinuousWave.isChecked() ? 1 : 0;
                setCW(flag, true);
            }
        });

        boolean reconnect = SPUtils.getInstance(getContext().getApplicationContext())
                .getSPBoolean(SPUtils.AUTO_RECONNECT, false);
        cbAutoReconnect = (CheckBox) view.findViewById(R.id.cbAutoReconnect);
        cbAutoReconnect.setChecked(reconnect);
        cbAutoReconnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SPUtils.getInstance(getContext().getApplicationContext()).setSPBoolean(SPUtils.AUTO_RECONNECT,
                        cbAutoReconnect.isChecked());
            }
        });

        tvBuzzerVolume = view.findViewById(R.id.tvBuzzerVolume);
        sbBuzzerVolume = view.findViewById(R.id.sbBuzzerVolume);
        btnSetBuzzerVolume = view.findViewById(R.id.btnSetBuzzerVolume);
        btnGetBuzzerVolume = view.findViewById(R.id.btnGetBuzzerVolume);
        sbBuzzerVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvBuzzerVolume.setText(progress + "%");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        btnSetBuzzerVolume.setOnClickListener(v -> setBuzzerVolume());
        btnGetBuzzerVolume.setOnClickListener(v -> getBuzzerVolume(true));

        cbFilterGibberish = view.findViewById(R.id.cbFilterGibberish);
        cbFilterGibberish.setOnCheckedChangeListener((buttonView, isChecked) -> {
            context.filterGibberish = isChecked;
        });

        spParseWay = view.findViewById(R.id.spParseWay);
        ivParseWayTips = view.findViewById(R.id.ivParseWayTips);
        spParseWay.setSelection(SPUtils.getInstance(getActivity()).getSPInt(SPUtils.PARSE_WAY, 0));
        spParseWay.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int position, long l) {
                if (context.parseWay != position) {
                    SPUtils.getInstance(getActivity()).setSPInt(SPUtils.PARSE_WAY, position);
                    context.parseWay = position;
                    context.selectEPC = "";
                }
                ivParseWayTips.setVisibility(position == MainActivity.PARSE_BY_ASCII ? View.VISIBLE : View.INVISIBLE);
                cbFilterGibberish.setVisibility(position == MainActivity.PARSE_BY_ASCII ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });

        ivParseWayTips.setOnClickListener(v -> {
            PopupWindow popupWindow = new PopupWindow(context);
            View popupView = LayoutInflater.from(context).inflate(R.layout.tooltip_layout, null);
            TextView tvTooltip = popupView.findViewById(R.id.tvTooltip);
            tvTooltip.setText(getString(R.string.msg_prase_way_tips));

            popupWindow.setContentView(popupView);
            popupWindow.setWidth(ViewGroup.LayoutParams.WRAP_CONTENT);
            popupWindow.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
            popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            popupWindow.setOutsideTouchable(true);
            popupWindow.showAsDropDown(ivParseWayTips);
        });

        // 为频率标题添加连续点击监听
        if (tvFrequencyBand != null) {
            tvFrequencyBand.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    handleFrequencyTitleClick();
                }
            });
        }

    }

    @Override
    public void onResume() {
        super.onResume();
        // 根据secretCodeFlag更新波特率区域的可见性
        if (llBaudrate != null && llBaudrateButtons != null) {
            int visible = context.secretCodeFlag ? View.VISIBLE : View.GONE;
            llBaudrate.setVisibility(visible);
            llBaudrateButtons.setVisibility(visible);
        }
        new Thread(() -> {
            getFre(false);
            getPower(false);
            getLinkParams(false);
            // getProtocol(false);
            getGen2(false);
            getMemoryBankMode(false);
            getBuzzerVolume(false);
            getFastInventory(false);
            context.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    cbShowDuplicateTags.setChecked(context.isShowDuplicateTags);
                    if (context.getConnectionMode() == MainActivity.ConnectionMode.WIFI) {
                        cbAutoReconnect.setVisibility(View.GONE);
                        cbRssi.setVisibility(View.GONE);
                    } else {
                        cbAutoReconnect.setVisibility(View.VISIBLE);
                        cbRssi.setVisibility(View.VISIBLE);
                        cbRssi.setChecked(context.uhf.isSupportRssi());
                    }
                }
            });
        }).start();
    }

    @Override
    public void onPause() {
        super.onPause();
        // 连续波一般只在认证使用，退出设置页面建议关掉
        if (cbContinuousWave.isChecked()) {
            setCW(0, false);
            cbContinuousWave.setChecked(false);
        }
    }

    @SuppressLint("NonConstantResourceId")
    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btnGetPower:
                getPower(true);
                break;
            case R.id.btnSetPower:
                setPower();
                break;
            case R.id.BtGetFre:
                getFre(true);
                break;
            case R.id.BtSetFre:
                setFre();
                break;
            case R.id.btnSetFreHop:
                setFre2();
                break;

            case R.id.btnSetProtocol:
                setProtocol();
                break;
            case R.id.btnGetProtocol:
                getProtocol(true);
                break;
            case R.id.btnbeepClose:
                if (context.uhf.setBeep(false)) {
                    context.showToast(R.string.setting_succ);
                } else {
                    context.showToast(R.string.setting_fail);
                }
                break;
            case R.id.btnbeepOpen:
                if (context.uhf.setBeep(true)) {
                    context.showToast(R.string.setting_succ);
                } else {
                    context.showToast(R.string.setting_fail);
                }
                break;
            case R.id.cbTagFocus:
                if (context.uhf.setTagFocus(cbTagFocus.isChecked())) {
                    context.showToast(R.string.setting_succ);
                } else {
                    context.showToast(R.string.setting_fail);
                }
                break;
            case R.id.btnSetRFlink:
                int index = splinkParams.getSelectedItemPosition();
                int link = arrayLinkValue[index];
                if (context.uhf.setRFLink(link)) {
                    context.showToast(R.string.setting_succ);
                } else {
                    context.showToast(R.string.setting_fail);
                }
                break;
            case R.id.btnGetRFlink:
                getLinkParams(true);
                break;
            case R.id.btnSetInventoryBank:
                setMemoryBankMode();
                break;
            case R.id.btnGetInventoryBank:
                getMemoryBankMode(true);
                break;
            case R.id.cbRssi:
                RFIDWithUHFBLE bleUhf1 = context.getUhfBLE();
                if (bleUhf1 != null) {
                    bleUhf1.setSupportRssi(cbRssi.isChecked());
                }
                break;
            case R.id.cbShowDuplicateTags:
                context.isShowDuplicateTags = cbShowDuplicateTags.isChecked();
                break;
            case R.id.btnGetGen2:
                getGen2(true);
                break;
            case R.id.btnSetGen2:
                setGen2();
                break;
            case R.id.btnGetFastInventory:
                getFastInventory(true);
                break;
            case R.id.btnSetFastInventory:
                setFastInventory();
                break;
            case R.id.btnSetBaudrate:
                setBaudrate();
                break;
            case R.id.btnGetBaudrate:
                getBaudrate(true);
                break;
        }
    }

    /**
     * 设置波特率
     */
    private void setBaudrate() {
        int selectedPosition = spinnerBaudrate.getSelectedItemPosition();
        int baudRateValue;
        // 第1个选项(115200)发送2，第2个选项(460800)发送3
        if (selectedPosition == 0) {
            baudRateValue = 2;
        } else if (selectedPosition == 1) {
            baudRateValue = 3;
        } else {
            context.showToast("无效的波特率选择");
            return;
        }
        boolean result = context.uhf.setBaudRate(baudRateValue);
        context.showToast(
                result ? getString(R.string.ble_param_set_success) : getString(R.string.ble_param_set_failed));
    }

    /**
     * 获取波特率
     */
    private void getBaudrate(boolean showToast) {
        int baudRate = context.uhf.getBaudRate();
        if (baudRate == -1) {
            context.showToast(getString(R.string.ble_param_get_failed));
            return;
        }
        if (baudRate == 2) {
            spinnerBaudrate.setSelection(0); // 115200
        } else if (baudRate == 3) {
            spinnerBaudrate.setSelection(1); // 460800
        }
        if (showToast) {
            context.showToast(getString(R.string.ble_param_get_success));
        }
    }

    private void sendMessage(int what, Object obj, int arg1) {
        Message msg = mHandler.obtainMessage(what, obj);
        msg.arg1 = arg1;
        mHandler.sendMessage(msg);
    }

    private void getPower(boolean showToast) {
        int iPower = context.uhf.getPower();
        Log.i(TAG, "getPower: " + iPower);
        // sendMessage(GET_POWER, iPower, showToast ? 1 : 0);
        mHandler.post(() -> {
            if (arrayPower != null && iPower > -1) {
                for (int i = 0; i < arrayPower.length; i++) {
                    if (iPower == Integer.valueOf(arrayPower[i])) {
                        spPower.setSelection(i);
                        if (showToast)
                            context.showToast(R.string.get_succ);
                        return;
                    }
                }
                if (showToast)
                    context.showToast("power=" + iPower);
                return;
            }
            if (showToast) {
                context.showToast(R.string.get_fail);
            }
        });
    }

    private void setPower() {
        // int iPower = Integer.parseInt(spPower.getSelectedItem().toString());
        int iPower = spPower.getSelectedItemPosition() + 1;
        if (context.uhf.setPower(iPower)) {
            context.showToast(R.string.setting_succ);
        } else {
            context.showToast(R.string.setting_fail);
        }
    }

    public void getLinkParams(boolean isToast) {
        int link = context.uhf.getRFLink();
        // sendMessage(GET_LINK_PARAMS, link, isToast ? 1 : 0);
        mHandler.post(() -> {
            if (link == -1) {
                if (isToast)
                    context.showToast(R.string.get_fail);
                return;
            }
            for (int i = 0; i < arrayLinkValue.length; i++) {
                if (link == arrayLinkValue[i]) {
                    splinkParams.setSelection(i);
                    if (isToast)
                        context.showToast(R.string.get_succ);
                    return;
                }
            }
            if (isToast)
                context.showToast("RFLink = " + link);
        });
    }

    public void getFre(boolean showToast) {
        int idx = context.uhf.getFrequencyMode();
        String mode = "";
        switch (idx) {
            case 0x01:
                mode = getString(R.string.china_standard1);
                break;
            case 0x02:
                mode = getString(R.string.china_standard2);
                break;
            case 0x04:
                mode = getString(R.string.europe_standard);
                break;
            case 0x08:
                mode = getString(R.string.united_states_standard);
                break;
            case 0x016:
                mode = getString(R.string.korea);
                break;
            case 0x032:
                mode = getString(R.string.japan);
                break;
            case 0x033:
                mode = getString(R.string.South_Africa_915_919MHz);
                break;
            case 0x034:
                mode = getString(R.string.TAIWAN);
                break;
            case 0x035:
                mode = getString(R.string.vietnam_918_923MHz);
                break;
            case 0x036:
                mode = getString(R.string.Peru_915_928MHz);
                break;
            case 0x037:
                mode = getString(R.string.Russia_860_867MHZ);
                break;
            case 0x080:
                mode = getString(R.string.Morocco);
                break;
            case 0x3B:
                mode = getString(R.string.Malaysia);
                break;
            case 0x3C:
                mode = getString(R.string.Brazil);
                break;
        }
        sendMessage(GET_FRE, mode, showToast ? 1 : 0);
    }

    private void setFre() {
        int f = 0;
        String mode = SpinnerMode.getSelectedItem().toString();
        if (getString(R.string.china_standard1).equals(mode)) {
            f = 0x01;
        } else if (getString(R.string.china_standard2).equals(mode)) {
            f = 0x02;
        } else if (getString(R.string.europe_standard).equals(mode)) {
            f = 0x04;
        } else if (getString(R.string.united_states_standard).equals(mode)) {
            f = 0x08;
        } else if (getString(R.string.korea).equals(mode)) {
            f = 0x16;
        } else if (getString(R.string.japan).equals(mode)) {
            f = 0x32;
        } else if (getString(R.string.South_Africa_915_919MHz).equals(mode)) {
            f = 0x33;
        } else if (getString(R.string.TAIWAN).equals(mode)) {
            f = 0x34;
        } else if (getString(R.string.vietnam_918_923MHz).equals(mode)) {
            f = 0x35;
        } else if (getString(R.string.Peru_915_928MHz).equals(mode)) {
            f = 0x36;
        } else if (getString(R.string.Russia_860_867MHZ).equals(mode)) {
            f = 0x37;
        } else if (getString(R.string.Morocco).equals(mode)) {
            f = 0x80;
        } else if (getString(R.string.Malaysia).equals(mode)) {
            f = 0x3B;
        } else if (getString(R.string.Brazil).equals(mode)) {
            f = 0x3C;
        }

        if (context.uhf.setFrequencyMode(f)) {
            context.showToast(R.string.uhf_msg_set_frequency_succ);
        } else {
            context.showToast(R.string.uhf_msg_set_frequency_fail);
        }
    }

    private void setFre2() {
        if (context.uhf.setFreHop(new Float(spFreHop.getSelectedItem().toString().trim()).floatValue())) {
            context.showToast(R.string.uhf_msg_set_frequency_succ);
        } else {
            context.showToast(R.string.uhf_msg_set_frequency_fail);
        }
    }

    /**
     * 设置协议
     *
     * @return
     */
    private boolean setProtocol() {
        if (context.uhf.setProtocol(spProtocol.getSelectedItemPosition())) {
            context.showToast(R.string.uhf_msg_set_protocol_succ);
            return true;
        } else {
            context.showToast(R.string.uhf_msg_set_protocol_fail);
        }
        return false;
    }

    /**
     * 获取协议
     *
     * @param showToast
     * @return
     */
    private void getProtocol(boolean showToast) {
        int pro = context.uhf.getProtocol();
        sendMessage(GET_PROTOCOL, pro, showToast ? 1 : 0);
    }

    /**
     * 设置连续波
     *
     * @param flag
     * @param showToast
     */
    private void setCW(int flag, boolean showToast) {
        boolean res = context.uhf.setCW(flag);
        if (showToast) {
            context.showToast(res ? getString(R.string.setting_succ) : getString(R.string.setting_fail));
        }
    }

    public void loadData() {
        String path = "sdcard/uhfData.txt";
        if (new File(path).exists()) {
            String data = readFile("sdcard/uhfData.txt");
            if (data != null && data.length() > 0) {
                llFreHop.setVisibility(View.VISIBLE);
                String[] strArr = data.split("\r\n");
                // ArrayAdapter adapter = ArrayAdapter.createFromResource(context,
                // R.array.arrayFreHop, android.R.layout.simple_spinner_item);
                ArrayAdapter<String> adapter = new ArrayAdapter<String>(context,
                        android.R.layout.simple_spinner_dropdown_item, strArr);
                spFreHop.setAdapter(adapter);
                adapter.notifyDataSetChanged();
            }
        }
        cbFilterGibberish.setVisibility(context.parseWay == MainActivity.PARSE_BY_ASCII ? View.VISIBLE : View.GONE);
        cbFilterGibberish.setChecked(context.filterGibberish);
    }

    private void getMemoryBankMode(boolean isClick) {
        InventoryModeEntity mode = context.uhf.getEPCAndTIDUserMode();
        if (mode == null) {
            if (isClick)
                context.showToast(R.string.get_fail);
            return;
        }
        mHandler.post(() -> {
            int bank = mode.getMode();
            boolean result = false;
            for (int i = 0; i < arrayMemoryBankValue.length; i++) {
                if (bank == arrayMemoryBankValue[i]) {
                    spMemoryBank.setSelection(i);
                    mHandler.postDelayed(() -> {
                        if (bank == InventoryModeEntity.MODE_EPC_TID_USER) {
                            etOffset.setText(String.valueOf(mode.getUserOffset()));
                            etLength.setText(String.valueOf(mode.getUserLength()));
                        } else if (bank == InventoryModeEntity.MODE_EPC_RESERVED) {
                            etOffset.setText(String.valueOf(mode.getReservedOffset()));
                            etLength.setText(String.valueOf(mode.getReservedLength()));
                        }
                    }, 50);
                    result = true;
                    break;
                }
            }
            if (isClick) {
                context.showToast(result ? getString(R.string.get_succ)
                        : getString(R.string.get_fail) + " mode=" + mode.getMode());
            }
        });
    }

    private void setMemoryBankMode() {
        if (StringUtils.toInt(etOffset.getText().toString().trim(), Integer.MIN_VALUE) == Integer.MIN_VALUE) {
            context.showToast(R.string.uhf_msg_offset_error);
            return;
        }
        if (StringUtils.toInt(etLength.getText().toString().trim(), Integer.MIN_VALUE) == Integer.MIN_VALUE) {
            context.showToast(R.string.uhf_msg_length_error);
            return;
        }
        int position = spMemoryBank.getSelectedItemPosition();
        boolean result = false;
        int offset = 0, length = 6;
        if (position == 0) {
            result = context.uhf.setEPCMode();
        } else if (position == 1) {
            result = context.uhf.setEPCAndTIDMode();
        } else if (position == 2) {
            offset = StringUtils.toInt(etOffset.getText().toString().trim(), 0);
            length = StringUtils.toInt(etLength.getText().toString().trim(), 6);
            result = context.uhf.setEPCAndTIDUserMode(offset, length);
        } else if (position == 3) {
            offset = StringUtils.toInt(etOffset.getText().toString().trim(), 0);
            length = StringUtils.toInt(etLength.getText().toString().trim(), 4);
            InventoryModeEntity entity = new InventoryModeEntity.Builder()
                    .setMode(InventoryModeEntity.MODE_EPC_RESERVED)
                    .setReservedOffset(offset)
                    .setReservedLength(length)
                    .build();
            result = context.uhf.setEPCAndTIDUserMode(entity);
        } else if (position == 4) { // TAG LED
            InventoryModeEntity entity = new InventoryModeEntity.Builder()
                    .setMode(InventoryModeEntity.MODE_LED_TAG)
                    .setReservedOffset(offset)
                    .setReservedLength(length)
                    .build();
            result = context.uhf.setEPCAndTIDUserMode(entity);
        }

        context.showToast(result ? R.string.setting_succ : R.string.setting_fail);
    }

    private boolean getGen2(final boolean isClick) {
        final Gen2Entity entity = context.uhf.getGen2();
        Log.e(TAG, "Get Gen2Entity: " + entity);
        if (entity != null) {
            context.runOnUiThread(() -> {
                int session = entity.getQuerySession();
                int target = entity.getQueryTarget();
                spSessionID.setSelection(session);
                spTarget.setSelection(target);
                if (isClick)
                    context.showToast(R.string.get_succ);
            });
            return true;
        }
        if (isClick)
            context.showToast(R.string.get_fail);
        return false;
    }

    private void setGen2() {
        int session = spSessionID.getSelectedItemPosition();
        int target = spTarget.getSelectedItemPosition();
        if (session < 0 || target < 0) {
            return;
        }
        Gen2Entity entity = context.uhf.getGen2();
        Log.i(TAG, "Set Gen2Entity: " + entity);
        if (entity != null) {
            entity.setQueryTarget(target);
            entity.setQuerySession(session);
            if (context.uhf.setGen2(entity)) {
                context.showToast(R.string.setting_succ);
            } else {
                context.showToast(R.string.setting_fail);
            }
        } else {
            context.showToast(R.string.setting_fail);
        }
    }

    private void getFastInventory(boolean isClick) {
        FastInventoryEntity entity = context.uhf.getFastInventoryMode();
        // Log.i(TAG, "getFastInventory: " + entity.getCr());
        context.runOnUiThread(() -> {
            if (entity == null || entity.getCr() < 0) {
                if (isClick)
                    context.showToast(R.string.get_fail);
                return;
            }
            Log.i(TAG, "getFastInventory: " + entity.getCr());
            if (entity.getCr() < spFastInventory.getCount()) {
                spFastInventory.setSelection(entity.getCr());
                if (isClick)
                    context.showToast(R.string.get_succ);
            } else {
                if (isClick)
                    context.showToast("Cr = " + entity.getCr());
            }
        });
    }

    private void setFastInventory() {
        FastInventoryEntity entity = new FastInventoryEntity(spFastInventory.getSelectedItemPosition());
        boolean flag = context.uhf.setFastInventoryMode(entity);
        context.showToast(flag ? R.string.setting_succ : R.string.setting_fail);
    }

    private void factoryReset() {
        if (context.uhf.factoryReset()) {
            context.showToast(R.string.reset_succ);
            new Thread(() -> {
                getFre(false);
                getPower(false);
                // getProtocol(false);
                getLinkParams(false);
                getGen2(false);
                getMemoryBankMode(false);
                getFastInventory(false);
                context.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        cbRssi.setChecked(context.uhf.isSupportRssi());
                        cbShowDuplicateTags.setChecked(context.isShowDuplicateTags);
                    }
                });
            }).start();
        } else {
            context.showToast(R.string.reset_fail);
        }
    }

    private void setBuzzerVolume() {
        int volume = sbBuzzerVolume.getProgress();
        RFIDWithUHFBLE bleUhf = context.getUhfBLE();
        if (bleUhf != null) {
            boolean flag = bleUhf.setVolume(volume);
            context.showToast(flag ? R.string.setting_succ : R.string.setting_fail);
        } else {
            context.showToast(R.string.msg_feature_bluetooth_only);
        }
    }

    private void getBuzzerVolume(boolean showToast) {
        RFIDWithUHFBLE bleUhf = context.getUhfBLE();
        if (bleUhf != null) {
            int volume = bleUhf.getVolume();
            Log.e(TAG, "getBuzzerVolume: " + volume);
            mHandler.post(() -> {
                if (volume >= 0) {
                    sbBuzzerVolume.setProgress(volume);
                    tvBuzzerVolume.setText(volume + "%");
                }
                if (showToast) {
                    context.showToast(volume >= 0 ? R.string.get_succ : R.string.get_fail);
                }
            });
        } else {
            if (showToast) {
                context.showToast(R.string.msg_feature_bluetooth_only);
            }
        }
    }

    /**
     * 处理频率标题的连续点击
     */
    private void handleFrequencyTitleClick() {
        long currentTime = System.currentTimeMillis();

        // 如果距离上次点击超过2秒，重置计数器
        if (currentTime - lastClickTime > CLICK_INTERVAL_MS) {
            clickCount = 1;
        } else {
            clickCount++;
        }

        lastClickTime = currentTime;

        // 如果达到5次点击，启用隐藏功能
        if (clickCount >= REQUIRED_CLICKS) {
            clickCount = 0; // 重置计数器
            if (context != null) {
                context.enableSecretFeatures();
                Toast.makeText(context, "ok", Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * 当隐藏功能被启用时调用，更新UI显示
     */
    public void onSecretFeatureEnabled() {
        if (llBaudrate != null && llBaudrateButtons != null) {
            llBaudrate.setVisibility(View.VISIBLE);
            llBaudrateButtons.setVisibility(View.VISIBLE);
        }
    }

}
