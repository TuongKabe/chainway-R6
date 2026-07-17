package com.example.uhf_bt;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WifiDeviceListActivity extends BaseActivity {
    public static final String TAG = "WifiDeviceListActivity";
    private static final int UDP_PORT = 1111;
    private static final long SCAN_PERIOD = 20000; // 20秒扫描时间

    private TextView mEmptyList;
    private TextView tvTitle;
    private List<WifiDevice> deviceList;
    private DeviceAdapter deviceAdapter;
    private Handler mHandler = new Handler();
    private boolean mScanning = false;
    private DatagramSocket udpSocket;
    private Thread udpReceiveThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);

        // 设置窗体背景透明
        getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        setContentView(R.layout.device_list);

        init();
    }

    private void init() {
        tvTitle = findViewById(R.id.title_devices);
        mEmptyList = (TextView) findViewById(R.id.empty);
        findViewById(R.id.close).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                stopScanning();
                finish();
            }
        });

        deviceList = new ArrayList<>();
        deviceAdapter = new DeviceAdapter(this, deviceList);

        Button cancelButton = (Button) findViewById(R.id.btn_cancel);
        cancelButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mScanning) {
                    startScanning();
                } else {
                    stopScanning();
                    finish();
                }
            }
        });

        // 隐藏清除历史记录按钮（WiFi模式不需要）
        Button btnClearHistory = findViewById(R.id.btnClearHistory);
        btnClearHistory.setVisibility(View.GONE);

        tvTitle.setText(R.string.wifi_search_title);
        mEmptyList.setText(R.string.wifi_searching);

        ListView newDevicesListView = (ListView) findViewById(R.id.new_devices);
        newDevicesListView.setAdapter(deviceAdapter);
        newDevicesListView.setOnItemClickListener(mDeviceClickListener);

        startScanning();
    }

    private void startScanning() {
        if (mScanning) {
            return;
        }

        mScanning = true;
        mEmptyList.setText(R.string.wifi_searching);
        mEmptyList.setVisibility(View.VISIBLE);
        deviceList.clear();
        deviceAdapter.notifyDataSetChanged();

        Button cancelButton = (Button) findViewById(R.id.btn_cancel);
        cancelButton.setText("取消");

        // 启动UDP接收线程
        udpReceiveThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    udpSocket = new DatagramSocket(UDP_PORT);
                    udpSocket.setSoTimeout(1000);

                    byte[] buffer = new byte[1024];
                    long startTime = System.currentTimeMillis();

                    while (mScanning && (System.currentTimeMillis() - startTime) < SCAN_PERIOD) {
                        try {
                            // 检查socket是否已关闭
                            if (udpSocket == null || udpSocket.isClosed()) {
                                break;
                            }

                            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                            udpSocket.receive(packet);

                            // 解析数据包
                            byte[] data = new byte[packet.getLength()];
                            System.arraycopy(packet.getData(), 0, data, 0, packet.getLength());

                            if (data.length >= 12) {
                                // 验证数据包格式：23 D4 95 29 D9 AC C0 A8 60 F0 1F 90
                                // 提取IP地址（字节6-9）
                                String ip = String.format("%d.%d.%d.%d",
                                        data[6] & 0xFF,
                                        data[7] & 0xFF,
                                        data[8] & 0xFF,
                                        data[9] & 0xFF);

                                // 提取端口（字节10-11，大端序）
                                int port = ((data[10] & 0xFF) << 8) | (data[11] & 0xFF);

                                Log.d(TAG, "收到设备广播: IP=" + ip + ", Port=" + port);

                                final WifiDevice device = new WifiDevice(ip, port);
                                mHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        addDevice(device);
                                    }
                                });
                            }
                        } catch (java.net.SocketTimeoutException e) {
                            // 超时是正常的，继续循环
                            continue;
                        } catch (java.net.SocketException e) {
                            // Socket关闭或错误，正常退出
                            if (mScanning) {
                                // 如果是主动停止扫描，这是正常的
                                Log.d(TAG, "Socket已关闭，停止接收");
                            }
                            break;
                        } catch (Exception e) {
                            Log.e(TAG, "接收UDP数据包错误", e);
                            if (mScanning && udpSocket != null && !udpSocket.isClosed()) {
                                // 如果还在扫描且socket未关闭，继续
                                continue;
                            } else {
                                break;
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "UDP Socket创建错误", e);
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(WifiDeviceListActivity.this, R.string.wifi_udp_error, Toast.LENGTH_SHORT)
                                    .show();
                        }
                    });
                } finally {
                    if (udpSocket != null && !udpSocket.isClosed()) {
                        udpSocket.close();
                    }
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            mScanning = false;
                            Button cancelButton = (Button) findViewById(R.id.btn_cancel);
                            if (cancelButton != null) {
                                cancelButton.setText(R.string.wifi_rescan);
                            }
                            if (deviceList.isEmpty()) {
                                mEmptyList.setText(R.string.wifi_search_no_devices);
                                mEmptyList.setVisibility(View.VISIBLE);
                            }
                        }
                    });
                }
            }
        });
        udpReceiveThread.start();

        // 设置超时停止扫描
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mScanning) {
                    stopScanning();
                }
            }
        }, SCAN_PERIOD);
    }

    private void stopScanning() {
        mScanning = false;
        // 先设置标志，让接收循环退出
        // 然后关闭socket，这样接收线程会正常退出
        if (udpSocket != null && !udpSocket.isClosed()) {
            try {
                udpSocket.close();
            } catch (Exception e) {
                Log.e(TAG, "关闭UDP Socket错误", e);
            }
        }
        // 中断线程（如果还在等待）
        if (udpReceiveThread != null && udpReceiveThread.isAlive()) {
            udpReceiveThread.interrupt();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopScanning();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopScanning();
    }

    private void addDevice(WifiDevice device) {
        boolean deviceFound = false;
        for (WifiDevice listDev : deviceList) {
            if (listDev.getIp().equals(device.getIp()) && listDev.getPort() == device.getPort()) {
                deviceFound = true;
                break;
            }
        }

        if (!deviceFound) {
            deviceList.add(device);
            mEmptyList.setVisibility(View.GONE);
            deviceAdapter.notifyDataSetChanged();
        }
    }

    private OnItemClickListener mDeviceClickListener = new OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            WifiDevice device = deviceList.get(position);
            stopScanning();

            String ip = device.getIp();
            int port = device.getPort();

            if (!TextUtils.isEmpty(ip) && port > 0) {
                Intent result = new Intent();
                result.putExtra("WIFI_IP", ip);
                result.putExtra("WIFI_PORT", port);
                setResult(Activity.RESULT_OK, result);
                finish();
            } else {
                showToast(R.string.wifi_invalid_device);
            }
        }
    };

    class WifiDevice {
        private String ip;
        private int port;

        public WifiDevice(String ip, int port) {
            this.ip = ip;
            this.port = port;
        }

        public String getIp() {
            return ip;
        }

        public void setIp(String ip) {
            this.ip = ip;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getDisplayName() {
            return ip + ":" + port;
        }
    }

    class DeviceAdapter extends BaseAdapter {
        android.content.Context context;
        List<WifiDevice> devices;
        LayoutInflater inflater;

        public DeviceAdapter(android.content.Context context, List<WifiDevice> devices) {
            this.context = context;
            inflater = LayoutInflater.from(context);
            this.devices = devices;
        }

        @Override
        public int getCount() {
            return devices.size();
        }

        @Override
        public Object getItem(int position) {
            return devices.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewGroup vg;

            if (convertView != null) {
                vg = (ViewGroup) convertView;
            } else {
                vg = (ViewGroup) inflater.inflate(R.layout.wifi_device_element, null);
            }

            WifiDevice device = devices.get(position);
            final TextView tvDeviceInfo = ((TextView) vg.findViewById(R.id.device_info));

            // 显示IP:端口
            tvDeviceInfo.setText(device.getDisplayName());
            tvDeviceInfo.setTextColor(Color.BLACK);

            return vg;
        }
    }
}
