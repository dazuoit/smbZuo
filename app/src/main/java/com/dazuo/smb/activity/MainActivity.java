package com.dazuo.smb.activity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.widget.ListView;
import android.widget.TextView;

import com.blankj.utilcode.util.LogUtils;
import com.blankj.utilcode.util.NetworkUtils;
import com.blankj.utilcode.util.PermissionUtils;
import com.blankj.utilcode.util.ToastUtils;
import com.dazuo.smb.bean.DeviceInfo;
import com.dazuo.smb.R;
import com.dazuo.smb.adapter.DeviceListAdapter;
import com.dazuo.smb.tools.ScanDeviceTools;
import com.tbruyelle.rxpermissions2.RxPermissions;
import java.util.List;


public class MainActivity extends FragmentActivity {

	private static final String TAG = MainActivity.class.getSimpleName();
	private ScanDeviceTools engine;
	private ListView listview;
	private DeviceListAdapter adapter;
	private List<DeviceInfo> cacheData;
	final RxPermissions rxPermissions = new RxPermissions(this);

	//测试
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		initView();
		engine = new ScanDeviceTools();
	}

	private void initView() {
		((TextView) findViewById(R.id.tv_local_ip)).setText(" 本机IP: " + NetworkUtils.getIpAddressByWifi());
		(findViewById(R.id.btn_scan)).setOnClickListener(v -> getPromiss());
		listview = (ListView) findViewById(R.id.device_listview);
		listview.setOnItemClickListener((parent, view, position, id) -> {
			if (adapter == null) return;
			DeviceInfo o = adapter.getItem(position);
			connectPC(o.getIp());
		});
	}


	@SuppressLint("CheckResult")
	public void getPromiss() {
		rxPermissions.request(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
				.subscribe(granted -> {
					if (granted) {
						startScan();
					} else {
						ToastUtils.showShort("需要" + "读取" + "权限");
						PermissionUtils.launchAppDetailsSettings();
					}
				});
	}

	private void startScan() {
		ProgressDialog dialog = ProgressDialog.show(this, "搜索", "正在搜索设备...");
		ToastUtils.showLong("正在搜索请稍后....");
		engine.startScanning();
		engine.setScannerListener(result -> {
			if (result != null && !result.isEmpty()) {
				cacheData = result;
			}
			if (result == null || result.isEmpty()) {
				result = cacheData;
			}
			adapter = new DeviceListAdapter(MainActivity.this, result);
			listview.setAdapter(adapter);
			if (dialog != null) dialog.dismiss();
		});
	}

	private void connectPC(String ip) {
		StringBuilder baseUrlBuilder = new StringBuilder("smb://");
		baseUrlBuilder.append("dazuo");
		baseUrlBuilder.append(':');
		baseUrlBuilder.append("123456");
		baseUrlBuilder.append('@');
		baseUrlBuilder.append(ip);
		while (baseUrlBuilder.lastIndexOf("/") == baseUrlBuilder.length() - 1) {
			baseUrlBuilder.deleteCharAt(baseUrlBuilder.length() - 1);
		}
		baseUrlBuilder.append('/');

		//保存到属性汇总
		String baseUrl = baseUrlBuilder.toString();
		String path = baseUrl;
		startActivity(new Intent(this, DirListActivity.class)
				.putExtra("path", path));
		LogUtils.w(TAG, "连接成功 : " + path);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		cacheData.clear();
		engine.destory();
	}
}
