package com.dazuo.smb.tools;

import android.os.Message;
import android.text.TextUtils;

import com.blankj.utilcode.util.LogUtils;
import com.blankj.utilcode.util.NetworkUtils;

import com.dazuo.smb.bean.DeviceInfo;
import com.dazuo.smb.utils.SmbUtils;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import jcifs.smb1.UniAddress;

public class ScanDeviceTools {
	private static final String TAG = ScanDeviceTools.class.getSimpleName();
	private static final int MSG_END = 0xaa;
	private static final int CORE_POOL_SIZE = 1;  //核心池大小
	private static final int MAX_POOL_SIZE = 255; //线程池最大线程数
	private String mDevAddress;// 本机IP地址-完整
	private String mPrefixAddress;// 局域网IP地址头,如：192.168.1.
	private Runtime mRuntime = Runtime.getRuntime();// 获取当前运行环境，来执行ping，相当于windows的cmd
	private Process mProcess = null;// 进程
	private String mPing = "ping -c 1 -w 2 ";// 其中 -c 1为发送的次数，-w 表示发送后等待响应的时间
	private ArrayList<DeviceInfo> mDeviceList = new ArrayList<>();// ping成功的设备
	private ThreadPoolExecutor mExecutor;// 线程池对象
	private OwerHandler handler = new OwerHandler(this);
	private ScannerListener listener;

	public void setScannerListener(ScannerListener listener) {
		this.listener = listener;
	}


	public ScanDeviceTools() {
		if (NetworkUtils.isWifiConnected()) {
			mDevAddress = NetworkUtils.getIpAddressByWifi();// 获取本机IP地址
		}
		LogUtils.w(TAG, mDevAddress);
		mPrefixAddress = SmbUtils.getLocalIPPrefix(mDevAddress);
		if (TextUtils.isEmpty(mPrefixAddress)) {
			throw new IllegalArgumentException("ip address maybe illegal.");
		}
	}

	/**
	 * 开始扫描
	 */
	public void startScanning() {
		mDeviceList.clear();
		Runnable runnable = new Runnable() {
			@Override
			public void run() {
				scanning();
				handler.sendEmptyMessage(MSG_END);
			}
		};
		new Thread(runnable).start();
	}

	/**
	 * 扫描局域网内ip，找到对应服务器
	 */
	private ArrayList<DeviceInfo> scanning() {
		mExecutor = new ThreadPoolExecutor(CORE_POOL_SIZE, MAX_POOL_SIZE, 2000,
				TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(CORE_POOL_SIZE));
		for (int i = 1; i < 255; i++) {// 创建256个Runnable分别去ping
			final int lastIPNO = i;// 存放ip最后一位地址 1-255
			Runnable run = new Runnable() {
				@Override
				public void run() {
					ping(lastIPNO);
				}
			};
			mExecutor.execute(run);
		}
		mExecutor.shutdown();

		while (true) {
			try {
				if (mExecutor.isTerminated()) {// 扫描结束,开始验证
					LogUtils.w(TAG, "ping结束,总共成功扫描到" + mDeviceList.size() + "个设备.");
					return mDeviceList;
				}
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	private void ping(int lastIPNO) {
		String ping = ScanDeviceTools.this.mPing + mPrefixAddress + lastIPNO;
		String currentIp = mPrefixAddress + lastIPNO;
		if (mDevAddress.equals(currentIp)) // 如果与本机IP地址相同,跳过
			return;
		try {
			mProcess = mRuntime.exec(ping);
			int result = mProcess.waitFor();
			LogUtils.i(TAG, "正在ping的IP地址为：" + currentIp + "返回值为：" + result);
			if (result == 0) {
				LogUtils.d(TAG, "ping成功,IP地址为：" + currentIp);
				UniAddress uniAddress = UniAddress.getByName(currentIp);
				uniAddress.firstCalledName();
				String deviceName = uniAddress.nextCalledName();
				if (!TextUtils.isEmpty(deviceName)) {
					DeviceInfo deviceInfo = new DeviceInfo(deviceName, currentIp);
					mDeviceList.add(deviceInfo);
					LogUtils.v(TAG, "ping成功,设备名称为：" + deviceName + ", IP = " + currentIp);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			LogUtils.e(TAG, "ping异常: " + e.toString());
		} finally {
			if (mProcess != null)
				mProcess.destroy();
		}
	}




	public interface ScannerListener {

		void onScanFinished(List<DeviceInfo> result);
	}


	private static class OwerHandler extends android.os.Handler {
		private WeakReference<ScanDeviceTools> reference;

		private OwerHandler(ScanDeviceTools tool) {
			reference = new WeakReference<>(tool);
		}

		@Override
		public void handleMessage(Message msg) {
			super.handleMessage(msg);
			ScanDeviceTools engine = reference.get();
			if (engine == null) return;
			if (msg.what == MSG_END) {
				if (engine.listener != null) {
					engine.listener.onScanFinished(engine.mDeviceList);
				}
			}
		}
	}

	/**
	 * 销毁正在执行的线程池
	 */
	public void destory() {
		if (mExecutor != null) {
			mExecutor.shutdownNow();
		}
		if (handler != null) {
			handler.removeCallbacksAndMessages(null);
		}
	}
}
