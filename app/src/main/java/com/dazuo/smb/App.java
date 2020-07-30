package com.dazuo.smb;

import android.app.Application;

public class App extends Application {

	@Override
	public void onCreate() {
		super.onCreate();
		initConfig();
	}

	private void initConfig() {
		System.setProperty("jcifs.smb.client.dfs.disabled", "true");
		System.setProperty("jcifs.smb.client.soTimeout", "30000");
		System.setProperty("jcifs.smb.client.responseTimeout", "30000");
	}

}
