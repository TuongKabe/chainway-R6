package com.example.uhf_bt.fragment;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.example.uhf_bt.AppContext;
import com.example.uhf_bt.MainActivity;
import com.example.uhf_bt.R;
import com.example.uhf_bt.tool.CheckUtils;
import com.example.uhf_bt.tool.MyTextWatcher;
import com.example.uhf_bt.tool.NumberTool;
import com.example.uhf_bt.tool.SPUtils;
import com.example.uhf_bt.tool.StringUtils;
import com.example.uhf_bt.tool.Utils;
import com.rscja.deviceapi.RFIDWithUHFBLE;
import com.rscja.deviceapi.entity.InventoryParameter;
import com.rscja.deviceapi.entity.UHFTAGInfo;
import com.rscja.deviceapi.interfaces.ConnectionStatus;
import com.rscja.deviceapi.interfaces.IUHF;
import com.rscja.deviceapi.interfaces.IUHFInventoryCallback;
import com.rscja.deviceapi.interfaces.KeyEventCallback;
import com.rscja.utility.StringUtility;

import java.util.ArrayList;
import java.util.List;

public class UHFReadTagFragment extends Fragment {

    private String TAG = "UHFReadTagFragment";
    private MainActivity mContext;

    int lastIndex = -1;
    private ListView LvTags;
    private Button btnInventory, btnSingleInventory, btStop, btClear;
    private TextView tv_count, tv_total, tv_time, tv_rate;
    EditText etTime;
    private CheckBox cbPhase;

    private boolean isExit = false;
    private long total = 0;
    private double useTime = 0;
    private double rate = 0; // 速率 (tags/s)
    private MyAdapter adapter;

    private List<UHFTAGInfo> tagList = new ArrayList<>();
    public static String KEY_TAG = "KEY_TAG";

    private ConnectStatus mConnectStatus = new ConnectStatus();
    int maxRunTime = 999999;

    // --------------------------------------获取
    // 解析数据-------------------------------------------------
    final int FLAG_START = 0;// 开始
    final int FLAG_STOP = 1;// 停止
    final int FLAG_UPDATE_TIME = 2; // 更新时间
    final int FLAG_UHFINFO = 3;
    final int FLAG_SUCCESS = 10;// 成功
    final int FLAG_FAIL = 11;// 失败
    final int FLAG_TIME_OVER = 12;//
    final int FLAG_UPDATE_RATE = 13; // 更新实时速率
    private long mStrTime;
    private long lastRateTime = 0; // 上次计算速率的时间戳
    private long lastRateTotal = 0; // 上次计算速率时的total值

    Handler handler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case FLAG_TIME_OVER:
                    Log.i(TAG, "FLAG_TIME_OVER =" + (System.currentTimeMillis() - mStrTime));
                    useTime = NumberTool.getPointDouble(1, (System.currentTimeMillis() - mStrTime) / 1000.0F);
                    tv_time.setText(useTime + "s");
                    stop();
                    break;
                case FLAG_STOP:
                    if (msg.arg1 == FLAG_SUCCESS) {
                        // 停止成功
                        btClear.setEnabled(true);
                        btStop.setEnabled(false);
                        btnInventory.setEnabled(true);
                        btnSingleInventory.setEnabled(true);
                    } else {
                        // 停止失败
                        Utils.playSound(2);
                        Toast.makeText(mContext, R.string.uhf_msg_inventory_stop_fail, Toast.LENGTH_SHORT).show();
                    }
                    break;
                case FLAG_START:
                    if (msg.arg1 == FLAG_SUCCESS) {
                        // 开始读取标签成功
                        // btClear.setEnabled(false);
                        btStop.setEnabled(true);
                        btnInventory.setEnabled(false);
                        btnSingleInventory.setEnabled(false);
                    } else {
                        // 开始读取标签失败
                        Utils.playSound(2);
                    }
                    break;
                case FLAG_UPDATE_TIME:
                    if (mContext.isScanning) {
                        useTime = NumberTool.getPointDouble(1, (System.currentTimeMillis() - mStrTime) / 1000.0F);
                        tv_time.setText(useTime + "s");
                        handler.sendEmptyMessageDelayed(FLAG_UPDATE_TIME, 10);
                    } else {
                        handler.removeMessages(FLAG_UPDATE_TIME);
                    }
                    break;
                case FLAG_UPDATE_RATE:
                    if (mContext.isScanning && mContext.secretCodeFlag) {
                        long currentTime = System.currentTimeMillis();
                        if (lastRateTime > 0) {
                            long deltaTotal = total - lastRateTotal;
                            double deltaTime = (currentTime - lastRateTime) / 1000.0;
                            if (deltaTime > 0) {
                                rate = NumberTool.getPointDouble(1, deltaTotal / deltaTime);
                                tv_rate.setText(rate + " tags/s");
                            }
                        }
                        lastRateTime = currentTime;
                        lastRateTotal = total;
                        handler.sendEmptyMessageDelayed(FLAG_UPDATE_RATE, 1000);
                    } else {
                        handler.removeMessages(FLAG_UPDATE_RATE);
                    }
                    break;
                case FLAG_UHFINFO:
                    UHFTAGInfo info = (UHFTAGInfo) msg.obj;
                    addTagToList(info);
                    // Utils.playSound(1);
                    break;
            }
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_uhfread_tag, container, false);
        initFilter(view);
        return view;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        Log.i(TAG, "UHFReadTagFragment.onActivityCreated");
        super.onActivityCreated(savedInstanceState);
        lastIndex = -1;
        mContext = (MainActivity) getActivity();
        init();
        if (TextUtils.isEmpty(mContext.selectEPC)) {
            selectIndex = -1;
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        stop();
    }

    @Override
    public void onDestroyView() {
        Log.i(TAG, "UHFReadTagFragment.onDestroyView");
        super.onDestroyView();
        isExit = true;
        mContext.uhf.setInventoryCallback(null);
        mContext.uhf.setKeyEventCallback(null);
        mContext.removeConnectStatusNotice(mConnectStatus);
    }

    private void init() {
        isExit = false;
        mContext.addConnectStatusNotice(mConnectStatus);
        LvTags = (ListView) getView().findViewById(R.id.LvTags);
        btnSingleInventory = (Button) getView().findViewById(R.id.btnSingleInventory);
        btnInventory = (Button) getView().findViewById(R.id.btnInventory);
        btStop = (Button) getView().findViewById(R.id.btStop);
        btClear = (Button) getView().findViewById(R.id.btClear);
        tv_count = (TextView) getView().findViewById(R.id.tv_count);
        tv_total = (TextView) getView().findViewById(R.id.tv_total);
        tv_time = (TextView) getView().findViewById(R.id.tv_time);
        tv_rate = (TextView) getView().findViewById(R.id.tv_rate);
        etTime = (EditText) getView().findViewById(R.id.etTime);
        cbPhase = getView().findViewById(R.id.cbPhase);

        // 根据secretCodeFlag控制速率显示的显示/隐藏
        if (tv_rate != null) {
            tv_rate.setVisibility(mContext.secretCodeFlag ? View.VISIBLE : View.GONE);
        }

        btnSingleInventory.setOnClickListener(v -> singleInventory());
        btnInventory.setOnClickListener(v -> startInventory());
        btStop.setOnClickListener(v -> stop());
        btClear.setOnClickListener(v -> clearData());
        tv_count.setText(String.valueOf(tagList.size()));
        tv_total.setText(String.valueOf(total));
        tv_time.setText(useTime + "s");
        tv_rate.setText("0 tags/s");

        mContext.tagList = tagList;

        adapter = new MyAdapter(mContext);
        LvTags.setAdapter(adapter);
        setupKeyEventCallback();
        LvTags.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                selectIndex = position;
                adapter.notifyDataSetInvalidated();
                mContext.selectEPC = tagList.get(position).getEPC();
            }
        });
        LvTags.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> adapterView, View view, int position, long l) {
                ClipboardManager clipboard = (ClipboardManager) view.getContext()
                        .getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("label", mContext.tagList.get(position).getExtraData(KEY_TAG));
                clipboard.setPrimaryClip(clip);
                Toast.makeText(view.getContext(), R.string.msg_copy_clipboard, Toast.LENGTH_SHORT).show();
                return false;
            }
        });
        // clearData();
    }

    private CheckBox cbFilter;
    private ViewGroup layout_filter;
    private Button btnSetFilter;

    private void initFilter(View view) {
        layout_filter = (ViewGroup) view.findViewById(R.id.layout_filter);
        layout_filter.setVisibility(View.GONE);
        cbFilter = (CheckBox) view.findViewById(R.id.cbFilter);
        cbFilter.setOnCheckedChangeListener(
                (buttonView, isChecked) -> layout_filter.setVisibility(isChecked ? View.VISIBLE : View.GONE));

        final EditText etLen = (EditText) view.findViewById(R.id.etLen);
        final EditText etPtr = (EditText) view.findViewById(R.id.etPtr);
        final EditText etData = (EditText) view.findViewById(R.id.etData);
        final RadioGroup rgBank = (RadioGroup) view.findViewById(R.id.rgBank);
        final RadioButton rbEPC = (RadioButton) view.findViewById(R.id.rbEPC);
        final RadioButton rbTID = (RadioButton) view.findViewById(R.id.rbTID);
        final RadioButton rbUser = (RadioButton) view.findViewById(R.id.rbUser);

        int parseWay = SPUtils.getInstance(AppContext.getInstance()).getSPInt(SPUtils.PARSE_WAY, 0);
        if (parseWay == MainActivity.PARSE_BY_ASCII) {
            etData.setHint("ASCII");
        } else {
            etData.setHint("HEX");
        }

        etData.addTextChangedListener(new MyTextWatcher() {
            @Override
            public void afterTextChanged(Editable editable) {
                if (mContext.parseWay == MainActivity.PARSE_BY_ASCII && !rbTID.isChecked()) {
                    etLen.setText(String.valueOf(etData.getText().toString().trim().length() * 8));
                } else {
                    etLen.setText(String.valueOf(etData.getText().toString().trim().length() * 4));
                }
            }
        });
        rgBank.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbEPC) {
                etPtr.setText("32");
            } else {
                etPtr.setText("0");
            }
            if (checkedId == R.id.rbTID || mContext.parseWay != MainActivity.PARSE_BY_ASCII) {
                etData.setHint("HEX");
                etLen.setText(String.valueOf(etData.getText().toString().trim().length() * 4));
            } else {
                etData.setHint("ASCII");
                etLen.setText(String.valueOf(etData.getText().toString().trim().length() * 8));
            }
        });

        btnSetFilter = (Button) view.findViewById(R.id.btSet);
        btnSetFilter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (etLen.getText().toString().isEmpty()) {
                    mContext.showToast(getString(R.string.uhf_msg_len_not_null));
                    return;
                }
                if (etPtr.getText().toString().isEmpty()) {
                    mContext.showToast(getString(R.string.uhf_msg_addr_not_null));
                    return;
                }
                int filterBank = RFIDWithUHFBLE.Bank_EPC;
                if (rbEPC.isChecked()) {
                    filterBank = RFIDWithUHFBLE.Bank_EPC;
                } else if (rbTID.isChecked()) {
                    filterBank = RFIDWithUHFBLE.Bank_TID;
                } else if (rbUser.isChecked()) {
                    filterBank = RFIDWithUHFBLE.Bank_USER;
                }
                int ptr = Utils.toInt(etPtr.getText().toString(), 0);
                int len = Utils.toInt(etLen.getText().toString(), 0);
                String data = etData.getText().toString().trim();
                if (mContext.parseWay == MainActivity.PARSE_BY_ASCII && filterBank != IUHF.Bank_TID) { // TID 不转换 ASCII
                    data = StringUtils.toAsciiHexString(data);
                } else {
                    data = data.replace(" ", "");
                }
                if (len > 0 && !TextUtils.isEmpty(data)) {
                    if (!StringUtility.isHexNumberRex(data)) {
                        mContext.showToast(getString(R.string.uhf_msg_filter_data_nohex));
                        return;
                    }
                    int dataLen = data.length();
                    if (len <= dataLen * 4) {
                        if (dataLen % 2 != 0)
                            data += "0";
                    } else {
                        mContext.showToast(R.string.uhf_msg_set_filter_fail2);
                        return;
                    }
                    if (mContext.uhf.setFilter(filterBank, ptr, len, data)) {
                        mContext.showToast(R.string.uhf_msg_set_filter_succ);
                    } else {
                        mContext.showToast(R.string.uhf_msg_set_filter_fail);
                    }
                } else {
                    // 禁用过滤
                    if (mContext.uhf.setFilter(RFIDWithUHFBLE.Bank_EPC, 32, 0, "")) {
                        mContext.showToast(R.string.msg_disable_succ);
                    } else {
                        mContext.showToast(R.string.msg_disable_fail);
                    }
                }
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        // 根据secretCodeFlag更新速率显示的可见性
        if (tv_rate != null) {
            tv_rate.setVisibility(mContext.secretCodeFlag ? View.VISIBLE : View.GONE);
        }

        if (mContext.uhf.getConnectStatus() == ConnectionStatus.CONNECTED) {
            btnInventory.setEnabled(true);
            btnSingleInventory.setEnabled(true);
            cbFilter.setEnabled(true);
        } else {
            btnInventory.setEnabled(false);
            btnSingleInventory.setEnabled(false);
            cbFilter.setChecked(false);
            cbFilter.setEnabled(false);
        }
    }

    private void clearData() {
        total = 0;
        tv_count.setText("0");
        tv_total.setText("0");
        tv_time.setText("0s");
        tv_rate.setText("0 tags/s");
        rate = 0;
        // 重置实时速率统计变量
        lastRateTime = 0;
        lastRateTotal = 0;
        tagList.clear();
        mContext.selectEPC = null;
        selectIndex = -1;
        adapter.notifyDataSetChanged();
    }

    /**
     * 停止识别
     */
    private void stop() {
        Log.i(TAG, "stop mContext.isScanning=false");
        handler.removeMessages(FLAG_TIME_OVER);
        handler.removeMessages(FLAG_UPDATE_RATE);
        if (mContext.isScanning) {
            stopInventory();
        }
        // 重置实时速率统计变量
        lastRateTime = 0;
        lastRateTotal = 0;
        mContext.isScanning = false;
    }

    private void stopInventory() {
        // Log.i(TAG, "stopInventory() 2");
        ConnectionStatus connectionStatus = mContext.uhf.getConnectStatus();
        if (connectionStatus != ConnectionStatus.CONNECTED) {
            return;
        }
        boolean result = false;
        result = mContext.uhf.stopInventory();
        Message msg = handler.obtainMessage(FLAG_STOP);
        if (!result && connectionStatus == ConnectionStatus.CONNECTED) {
            msg.arg1 = FLAG_FAIL;
        } else {
            msg.arg1 = FLAG_SUCCESS;
        }
        handler.sendMessage(msg);
    }

    private void singleInventory() {
        mStrTime = System.currentTimeMillis();
        UHFTAGInfo info = mContext.uhf.inventorySingleTag();
        if (info != null) {
            Message msg = handler.obtainMessage(FLAG_UHFINFO);
            msg.obj = info;
            handler.sendMessage(msg);
        }
        handler.sendEmptyMessage(FLAG_UPDATE_TIME);
    }

    public void startInventory() {
        if (mContext.isScanning) {
            return;
        }
        selectIndex = -1;

        // --
        String time = etTime.getText().toString();
        if (time.length() > 0 && time.startsWith(".")) {
            etTime.setText("");
            time = "";
        }
        if (!time.isEmpty()) {
            maxRunTime = (int) (Float.parseFloat(time) * 1000);
            clearData();
        } else {
            maxRunTime = Integer.parseInt(etTime.getHint().toString()) * 1000;
        }
        // --
        mContext.uhf.setInventoryCallback(new IUHFInventoryCallback() {
            @Override
            public void callback(UHFTAGInfo uhftagInfo) {
                // handler.sendMessage(handler.obtainMessage(FLAG_UHFINFO, uhftagInfo));
                handler.post(() -> addTagToList(uhftagInfo));
            }
        });
        mContext.isScanning = true;
        // cbFilter.setChecked(true);
        Message msg = handler.obtainMessage(FLAG_START);

        InventoryParameter inventoryParameter = new InventoryParameter();
        inventoryParameter.setResultData(new InventoryParameter.ResultData().setNeedPhase(cbPhase.isChecked()));
        if (mContext.uhf.startInventoryTag(inventoryParameter)) {
            mStrTime = System.currentTimeMillis();
            msg.arg1 = FLAG_SUCCESS;
            handler.sendEmptyMessage(FLAG_UPDATE_TIME);
            handler.removeMessages(FLAG_TIME_OVER);
            handler.sendEmptyMessageDelayed(FLAG_TIME_OVER, maxRunTime);
            // 如果secretCodeFlag为true，启动实时速率更新
            if (mContext.secretCodeFlag) {
                lastRateTime = System.currentTimeMillis();
                lastRateTotal = total;
                handler.removeMessages(FLAG_UPDATE_RATE);
                handler.sendEmptyMessageDelayed(FLAG_UPDATE_RATE, 500);
            }
        } else {
            msg.arg1 = FLAG_FAIL;
            mContext.isScanning = false;
        }
        handler.sendMessage(msg);
    }

    private void addTagToList(UHFTAGInfo uhftagInfo) {
        // Log.i(TAG, "addTagToList: " + uhftagInfo.getEPC() + " " + uhftagInfo.getTid()
        // + " " + uhftagInfo.getUser() + " " + uhftagInfo.getReserved());
        if (uhftagInfo.getReserved() == null)
            uhftagInfo.setReserved("");
        if (uhftagInfo.getEPC() == null)
            uhftagInfo.setEPC("");
        if (uhftagInfo.getTid() == null)
            uhftagInfo.setTid("");
        if (uhftagInfo.getUser() == null)
            uhftagInfo.setUser("");

        if (mContext.parseWay == MainActivity.PARSE_BY_ASCII) {
            uhftagInfo.setEPC(StringUtils.fromAsciiHexString(uhftagInfo.getEPC(), mContext.filterGibberish));
            uhftagInfo.setUser(StringUtils.fromAsciiHexString(uhftagInfo.getUser(), mContext.filterGibberish));
        } else if (mContext.parseWay == MainActivity.PARSE_BY_DECIMAL) {
            uhftagInfo.setEPC(StringUtils.byteArrayTolongString(uhftagInfo.getEpcBytes()));
            uhftagInfo.setUser(StringUtils.byteArrayTolongString(uhftagInfo.getUserBytes()));
        }

        if (uhftagInfo.getEPC().isEmpty()) {
            return;
        }

        boolean[] exists = new boolean[1];
        int index = CheckUtils.getInsertIndex(tagList, uhftagInfo, exists);
        if (exists[0]) {
            tagList.get(index).setRssi(uhftagInfo.getRssi());
            tagList.get(index).setPhase(uhftagInfo.getPhase());
            if (mContext.isShowDuplicateTags) {
                tagList.get(index).setCount(tagList.get(index).getCount() + 1);
                tv_total.setText(String.valueOf(++total));
            }
        } else {
            uhftagInfo.setExtraData(KEY_TAG, generateTagString(uhftagInfo));
            tagList.add(index, uhftagInfo);
            tv_count.setText(String.valueOf(tagList.size()));
            tv_total.setText(String.valueOf(++total));
        }
        adapter.notifyDataSetChanged();
    }

    private String generateTagString(UHFTAGInfo uhftagInfo) {
        Log.i(TAG, "reserved=" + uhftagInfo.getReserved() + ", epc=" + uhftagInfo.getEPC() + ", tid="
                + uhftagInfo.getTid() + ", user=" + uhftagInfo.getUser());
        String data = "";
        if (uhftagInfo.getReserved() != null && !uhftagInfo.getReserved().isEmpty()) {
            data += "RESERVED:" + uhftagInfo.getReserved();
            data += "\nEPC:" + uhftagInfo.getEPC();
        } else {
            data += TextUtils.isEmpty(uhftagInfo.getTid()) ? uhftagInfo.getEPC() : "EPC:" + uhftagInfo.getEPC();
        }
        if (!TextUtils.isEmpty(uhftagInfo.getTid()) && !uhftagInfo.getTid().equals("0000000000000000")
                && !uhftagInfo.getTid().equals("000000000000000000000000")) {
            data += "\nTID:" + uhftagInfo.getTid();
        }
        if (uhftagInfo.getUser() != null && uhftagInfo.getUser().length() > 0) {
            data += "\nUSER:" + uhftagInfo.getUser();
        }
        return data;
    }

    /**
     * 设置按键事件回调
     */
    private void setupKeyEventCallback() {
        handler.postDelayed(() -> {
            if (!isExit && mContext.uhf != null) {
                mContext.uhf.setKeyEventCallback(new KeyEventCallback() {
                    @Override
                    public void onKeyDown(int keycode) {
                        Log.d(TAG, "  keycode =" + keycode + "   ,isExit=" + isExit);
                        if (!isExit && mContext.uhf.getConnectStatus() == ConnectionStatus.CONNECTED) {
                            if (keycode == 3) {
                                mContext.isKeyDownUP = true;
                                startInventory();
                            } else {
                                if (!mContext.isKeyDownUP) {
                                    if (keycode == 1) {
                                        if (mContext.isScanning) {
                                            stop();
                                        } else {
                                            startInventory();
                                        }
                                    }
                                }
                                if (keycode == 2) {
                                    if (mContext.isScanning) {
                                        stop();
                                        SystemClock.sleep(100);
                                    }
                                    // MR20
                                    singleInventory();
                                }
                            }
                        }
                    }

                    @Override
                    public void onKeyUp(int keycode) {
                        Log.d(TAG, "  keycode =" + keycode + "   ,isExit=" + isExit);
                        if (keycode == 4) {
                            stop();
                        }
                    }
                });
            }
        }, 200);
    }

    class ConnectStatus implements MainActivity.IConnectStatus {
        @Override
        public void getStatus(ConnectionStatus connectionStatus) {
            Log.i(TAG, "getStatus connectionStatus=" + connectionStatus);
            if (connectionStatus == ConnectionStatus.CONNECTED) {
                if (!mContext.isScanning) {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    btnInventory.setEnabled(true);
                    btnSingleInventory.setEnabled(true);
                }

                cbFilter.setEnabled(true);

                // 连接成功时重新设置按键回调，确保切换模式后按键功能正常
                setupKeyEventCallback();
            } else if (connectionStatus == ConnectionStatus.DISCONNECTED) {
                stop();
                btClear.setEnabled(true);
                btStop.setEnabled(false);
                btnInventory.setEnabled(false);
                btnSingleInventory.setEnabled(false);

                cbFilter.setChecked(false);
                cbFilter.setEnabled(false);
            }
        }
    }

    // -----------------------------
    private int selectIndex = -1;

    public final class ViewHolder {
        public TextView tvTag;
        public TextView tvTagCount;
        public TextView tvTagRssi;
        public TextView tvPhase;
    }

    public class MyAdapter extends BaseAdapter {
        private LayoutInflater mInflater;

        public MyAdapter(Context context) {
            this.mInflater = LayoutInflater.from(context);
        }

        public int getCount() {
            return tagList.size();
        }

        public Object getItem(int arg0) {
            return tagList.get(arg0);
        }

        public long getItemId(int arg0) {
            return arg0;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder = null;
            if (convertView == null) {
                holder = new ViewHolder();
                convertView = mInflater.inflate(R.layout.listtag_items, null);
                holder.tvTag = convertView.findViewById(R.id.TvTag);
                holder.tvTagCount = convertView.findViewById(R.id.TvTagCount);
                holder.tvTagRssi = convertView.findViewById(R.id.TvTagRssi);
                holder.tvPhase = convertView.findViewById(R.id.TvPhase);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            holder.tvTag.setText(tagList.get(position).getExtraData(KEY_TAG));
            holder.tvTagCount.setText(String.valueOf(tagList.get(position).getCount()));
            holder.tvTagRssi.setText(tagList.get(position).getRssi());
            holder.tvPhase.setText(String.valueOf(tagList.get(position).getPhase()));

            if (position == selectIndex) {
                convertView.setBackgroundColor(mContext.getResources().getColor(R.color.lightblue3));
            } else {
                convertView.setBackgroundColor(Color.TRANSPARENT);
            }
            return convertView;
        }

    }

    /**
     * 当隐藏功能被启用时调用，更新UI显示实时速率
     */
    public void onSecretFeatureEnabled() {
        if (tv_rate != null) {
            tv_rate.setVisibility(View.VISIBLE);
        }
    }

}
