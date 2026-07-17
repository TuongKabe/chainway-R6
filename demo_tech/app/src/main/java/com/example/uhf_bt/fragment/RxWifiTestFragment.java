package com.example.uhf_bt.fragment;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.uhf_bt.MainActivity;
import com.example.uhf_bt.R;
import com.rscja.deviceapi.RxWifi;

import java.util.ArrayList;
import java.util.List;

public class RxWifiTestFragment extends Fragment implements View.OnClickListener, AdapterView.OnItemClickListener {
	private Button btnGetWifiMode, btnSetWifiMode, btnGetSsidAndPassword, btnSetSsidAndPassword, btnGetReaderIPAndPort,
			btnSetReaderIPAndPort, btnGetWifiList;
	private EditText etSsid, etPassword, etReaderPort;
	private Spinner spWifiMode;
	private ListView lvWifiList;
	private MainActivity context;
	private RxWifi rxWifi;
	private List<RxWifi.WifiInfo> wifiInfoList;
	private ArrayAdapter<String> wifiModeAdapter;
	private WifiListAdapter wifiListAdapter;
	// 在类成员变量中添加
	private ProgressBar progressBar;

	private EditText etIpAddress, etGateway, etSubnetMask, etDns1;
	private Switch switchDHCP;
	private Button btnGetReaderIP, btnSetReaderIP;

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.fragment_rxwifi_test, container, false);
		context = (MainActivity) getActivity();

		etIpAddress = view.findViewById(R.id.etIpAddress);
		etGateway = view.findViewById(R.id.etGateway);
		etSubnetMask = view.findViewById(R.id.etSubnetMask);
		etDns1 = view.findViewById(R.id.etDns1);
		switchDHCP = view.findViewById(R.id.switchDHCP);
		btnGetReaderIP = view.findViewById(R.id.btnGetReaderIP);
		btnSetReaderIP = view.findViewById(R.id.btnSetReaderIP);
		btnGetReaderIP.setOnClickListener(this);
		btnSetReaderIP.setOnClickListener(this);

		// 在 onCreateView 中添加
		progressBar = view.findViewById(R.id.progressBar);
		// 初始化按钮
		btnGetWifiMode = view.findViewById(R.id.btnGetWifiMode);
		btnSetWifiMode = view.findViewById(R.id.btnSetWifiMode);
		btnGetSsidAndPassword = view.findViewById(R.id.btnGetSsidAndPassword);
		btnSetSsidAndPassword = view.findViewById(R.id.btnSetSsidAndPassword);
		btnGetReaderIPAndPort = view.findViewById(R.id.btnGetReaderIPAndPort);
		btnSetReaderIPAndPort = view.findViewById(R.id.btnSetReaderIPAndPort);
		btnGetWifiList = view.findViewById(R.id.btnGetWifiList);

		// 初始化输入框
		spWifiMode = view.findViewById(R.id.spWifiMode);
		etSsid = view.findViewById(R.id.etSsid);
		etPassword = view.findViewById(R.id.etPassword);
		etReaderPort = view.findViewById(R.id.etReaderPort);
		lvWifiList = view.findViewById(R.id.lvWifiList);

		// 设置点击监听
		btnGetWifiMode.setOnClickListener(this);
		btnSetWifiMode.setOnClickListener(this);
		btnGetSsidAndPassword.setOnClickListener(this);
		btnSetSsidAndPassword.setOnClickListener(this);
		btnGetReaderIPAndPort.setOnClickListener(this);
		btnSetReaderIPAndPort.setOnClickListener(this);
		btnGetWifiList.setOnClickListener(this);

		// 如果是WiFi模式，隐藏搜索WiFi按钮
		if (context != null && context.getConnectionMode() == MainActivity.ConnectionMode.WIFI) {
			btnGetWifiList.setVisibility(View.GONE);
		}

		// 初始化WiFi列表适配器（持久化，不清空）
		wifiInfoList = new ArrayList<>();
		wifiListAdapter = new WifiListAdapter(getContext(), wifiInfoList);
		lvWifiList.setAdapter(wifiListAdapter);
		lvWifiList.setOnItemClickListener(this);
		lvWifiList.setOnTouchListener(new View.OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				v.getParent().requestDisallowInterceptTouchEvent(true);
				return false; // 继续交给 ListView 自身处理（保持点击/滚动正常）
			}
		});

		return view;
	}

	@Override
	public void onResume() {
		super.onResume();
		// 根据连接模式显示/隐藏搜索WiFi按钮
		if (context != null && btnGetWifiList != null) {
			if (context.getConnectionMode() == MainActivity.ConnectionMode.WIFI) {
				btnGetWifiList.setVisibility(View.GONE);
			} else {
				btnGetWifiList.setVisibility(View.VISIBLE);
			}
		}
	}

	@Override
	public void onClick(View v) {
		// 每次操作前动态获取最新的 rxWifi 实例，确保在切换连接模式后能获取到正确的实例
		rxWifi = context.uhf.getRxWifi();
		if (rxWifi == null) {
			Log.d("RxWifiTestFragment", "rxWifi is null, please check connection");
			return;
		}

		int id = v.getId();
		if (id == R.id.btnGetWifiMode) {
			int mode = rxWifi.getWifiMode();
			if (mode >= 0 && mode <= 2) {
				spWifiMode.setSelection(mode);
				Toast.makeText(getContext(), R.string.get_succ, Toast.LENGTH_SHORT).show();
			} else {
				Toast.makeText(getContext(), R.string.get_fail, Toast.LENGTH_SHORT).show();
			}
		} else if (id == R.id.btnSetWifiMode) {
			int mode = spWifiMode.getSelectedItemPosition();
			boolean result = rxWifi.setWifiMode(mode);
			if (result) {
				context.showToast(R.string.setting_succ);
			} else {
				context.showToast(R.string.setting_fail);
			}
		} else if (id == R.id.btnGetSsidAndPassword) {
			RxWifi.SSIDAndPasswordInfo info = rxWifi.getSsidAndPassword();
			if (info != null) {
				etSsid.setText(info.getSsid());
				etPassword.setText(info.getPassword());
				Toast.makeText(getContext(), R.string.get_succ, Toast.LENGTH_SHORT).show();
			} else {
				Toast.makeText(getContext(), R.string.get_fail, Toast.LENGTH_SHORT).show();
			}
		} else if (id == R.id.btnSetSsidAndPassword) {
			String ssid = etSsid.getText().toString();
			String password = etPassword.getText().toString();
			if (ssid == null || ssid.isEmpty()) {
				context.showToast(R.string.ssid_cannot_be_empty);
				return;
			}
			if (password == null || password.isEmpty()) {
				context.showToast(R.string.password_cannot_be_empty);
				return;
			}
			boolean result = rxWifi.setSsidAndPassword(ssid, password);
			if (result) {
				context.showToast(R.string.setting_succ);
			} else {
				context.showToast(R.string.setting_fail);
			}
		} else if (id == R.id.btnGetReaderIPAndPort) {
			int port = rxWifi.getReaderPort();
			if (port > -1) {
				etReaderPort.setText(String.valueOf(port));
				Toast.makeText(getContext(), R.string.get_succ, Toast.LENGTH_SHORT).show();
			} else {
				Toast.makeText(getContext(), R.string.get_fail, Toast.LENGTH_SHORT).show();
			}
		} else if (id == R.id.btnSetReaderIPAndPort) {
			String portStr = etReaderPort.getText().toString();
			if (portStr == null || portStr.isEmpty()) {
				context.showToast(R.string.port_cannot_be_empty);
				return;
			}
			int port = Integer.parseInt(portStr);

			if (port < 1 || port > 65535) {
				context.showToast(R.string.port_invalid_range);
				return;
			}
			boolean result = rxWifi.setReaderPort(port);
			if (result) {
				context.showToast(R.string.setting_succ);
			} else {
				context.showToast(R.string.setting_fail);
			}
		} else if (id == R.id.btnGetWifiList) {

			boolean result = rxWifi.getWifiList(new RxWifi.IWifiListCallback() {
				@Override
				public void onWifiListReceived(List<RxWifi.WifiInfo> newList) {
					if (newList == null || newList.isEmpty())
						return;
					wifiListAdapter.mergeWifiList(newList);
					if (getActivity() != null) {
						getActivity().runOnUiThread(new Runnable() {
							@Override
							public void run() {
								wifiListAdapter.notifyDataSetChanged();
								// Toast.makeText(getContext(), "新增 " + newList.size() + " 条WiFi记录",
								// Toast.LENGTH_SHORT).show();
							}
						});
					}
				}
			});
			if (result) {
				showWifiListProgress();
				// 10秒后自动隐藏进度条
				new android.os.Handler().postDelayed(new Runnable() {
					@Override
					public void run() {
						hideWifiListProgress();
					}
				}, 10000);
				// 增量回调：不清空旧数据，合并新结果
			}
		} else if (id == R.id.btnGetReaderIP) {
			// 获取读写器IP配置
			com.rscja.deviceapi.entity.ReaderIP readerIP = rxWifi.getReaderIP();

			if (readerIP != null) {
				Log.d("DeviceAPI", "ipinfo=" + readerIP.toString());
				etIpAddress.setText(readerIP.getIp());
				etGateway.setText(readerIP.getGateway());
				etSubnetMask.setText(readerIP.getSubnetMask());
				etDns1.setText(readerIP.getDns1());
				switchDHCP.setChecked(readerIP.isDHCP());
				Toast.makeText(getContext(), R.string.get_succ, Toast.LENGTH_SHORT).show();
			} else {
				Toast.makeText(getContext(), R.string.get_fail, Toast.LENGTH_SHORT).show();
			}
		} else if (id == R.id.btnSetReaderIP) {
			// 设置读写器IP配置
			com.rscja.deviceapi.entity.ReaderIP readerIP = new com.rscja.deviceapi.entity.ReaderIP();
			readerIP.setIp(etIpAddress.getText().toString())
					.setGateway(etGateway.getText().toString())
					.setSubnetMask(etSubnetMask.getText().toString())
					.setDns1(etDns1.getText().toString())
					.setDHCP(switchDHCP.isChecked());

			boolean result = rxWifi.setReaderIP(readerIP);
			if (result) {
				context.showToast(R.string.setting_succ);
			} else {
				context.showToast(R.string.setting_fail);
			}
		}
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
		if (parent == lvWifiList && wifiInfoList != null && position < wifiInfoList.size()) {
			RxWifi.WifiInfo wifi = wifiInfoList.get(position);
			etSsid.setText(wifi.getSsid());
			// Toast.makeText(getContext(), "已选择WiFi: " +
			// (wifi.getSsid().isEmpty()?"隐藏网络":wifi.getSsid()), Toast.LENGTH_SHORT).show();
		}
	}

	// 合并新WiFi列表到现有列表（按 SSID + Channel 去重，并更新RSSI/安全类型）

	// WiFi列表适配器
	private class WifiListAdapter extends ArrayAdapter<RxWifi.WifiInfo> {
		public WifiListAdapter(android.content.Context context, List<RxWifi.WifiInfo> wifiList) {
			super(context, 0, wifiList);
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			if (convertView == null) {
				convertView = LayoutInflater.from(getContext()).inflate(R.layout.item_wifi_list, parent, false);
			}

			RxWifi.WifiInfo wifi = getItem(position);
			if (wifi != null) {
				TextView tvSsid = convertView.findViewById(R.id.tvWifiSsid);
				TextView tvSecurity = convertView.findViewById(R.id.tvWifiSecurity);
				TextView tvRssi = convertView.findViewById(R.id.tvWifiRssi);
				TextView tvChannel = convertView.findViewById(R.id.tvWifiChannel);

				tvSsid.setText(wifi.getSsid() == null || wifi.getSsid().isEmpty() ? "" : wifi.getSsid());
				tvSecurity.setText("Security: " + wifi.getSecurityType());
				tvRssi.setText("Rssi: " + wifi.getRssi() + "dBm");
				tvChannel.setText("Channel: " + wifi.getChannel());
			}

			return convertView;
		}

		private void mergeWifiList(List<RxWifi.WifiInfo> newList) {
			for (RxWifi.WifiInfo nw : newList) {
                if (nw == null || nw.getSsid() == null || nw.getSsid().trim().isEmpty()) {
                    continue;
                }
                int idx = indexOfWifi(nw);
				if (idx == -1) {
					wifiInfoList.add(nw);
				} else {
					RxWifi.WifiInfo old = wifiInfoList.get(idx);
					// 更新更强的RSSI与信息
					if (nw.getRssi() > old.getRssi()) {
						old.setRssi(nw.getRssi());
					}
					old.setSecurityType(nw.getSecurityType());
				}
			}
		}

		private int indexOfWifi(RxWifi.WifiInfo target) {
			for (int i = 0; i < wifiInfoList.size(); i++) {
				RxWifi.WifiInfo w = wifiInfoList.get(i);
				String ssid1 = w.getSsid() == null ? "" : w.getSsid();
				String ssid2 = target.getSsid() == null ? "" : target.getSsid();
				if (ssid1.equals(ssid2) && w.getChannel() == target.getChannel()) {
					return i;
				}
			}
			return -1;
		}
	}

	// 添加显示进度条方法
	private void showWifiListProgress() {
		progressBar.setVisibility(View.VISIBLE);
	}

	// 添加隐藏进度条方法
	private void hideWifiListProgress() {
		progressBar.setVisibility(View.GONE);
	}
}
