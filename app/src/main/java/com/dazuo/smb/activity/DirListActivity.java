package com.dazuo.smb.activity;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.os.Message;
import android.widget.ListView;

import com.blankj.utilcode.util.LogUtils;
import com.blankj.utilcode.util.ObjectUtils;
import com.blankj.utilcode.util.ToastUtils;
import com.dazuo.smb.R;
import com.dazuo.smb.adapter.DirectoryAdapter;
import com.dazuo.smb.utils.AliThreadUtils;
import com.dazuo.smb.utils.DialogHelper;
import com.dazuo.smb.utils.SmbUtils;
import com.hb.dialog.dialog.LoadingDialog;
import com.litesuits.common.io.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import jcifs.smb.SmbFile;
import jcifs.smb.SmbFileInputStream;
import jcifs.smb.SmbFileOutputStream;

public class DirListActivity extends Activity {
	private static final String TAG = DirListActivity.class.getSimpleName();
	private String currentPath;
	private ListView listView;
	private DirListActivity.OxHandler handler = new DirListActivity.OxHandler(this);
	private List<SmbFile> smbFiles = new ArrayList<>();
	private DirectoryAdapter adapter;
	private ProgressDialog dialog;
	public static final int REQUEST_FILE_PICKER = 10;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_directory);
		currentPath = getIntent().getStringExtra("path");
		listView = (ListView) findViewById(R.id.list_listview);
		findViewById(R.id.btn_create_dir).setOnClickListener(v -> {
			createDir();
		});
		findViewById(R.id.btn_create_file).setOnClickListener(v -> {
			createFile();
		});
		listView.setOnItemClickListener((parent, view, position, id) ->
				AliThreadUtils.runOnSubThread(() -> onFileClicked(adapter.getItem(position))));

		listView.setOnItemLongClickListener((parent, view, position, id) -> {
			delete(adapter.getItem(position));
			return false;
		});
		AliThreadUtils.runOnSubThread(() -> connectFile(currentPath));

	}

	private void createDir() {
		//弹出输入框
		DialogHelper.inputDialog(this, "新建文件夹", "输入文件夹名称", (re) -> {
			//弹出创建进度框
			LoadingDialog loadingDialog = DialogHelper.loadDialog(this, "创建中...");
			AliThreadUtils.runOnSubThread(() -> {
				try {
					//验证输入
					if (ObjectUtils.isEmpty(re) || !re.matches("[^/\\\\]+")) {
						ToastUtils.showShort("输入有误");
						return;
					}
					if (ObjectUtils.isEmpty(currentPath)) {
						ToastUtils.showShort("路径异常");
						return;
					}
					//创建文件夹
					SmbFile newDir = new SmbFile(currentPath + re.trim());
					newDir.mkdirs();
					newDir.close();
					ToastUtils.showShort("创建成功");
					//重新获取目录
					connectFile(currentPath);
				} catch (Exception e) {
					ToastUtils.showShort("创建失败");
					LogUtils.e(TAG + " 创建失败", e.toString());
				} finally {
					loadingDialog.dismiss();
				}
			});

		});
	}

	private void createFile() {
		Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
		intent.setType("*/*");//设置类型，我这里是任意类型，任意后缀的可以这样写。
		intent.addCategory(Intent.CATEGORY_OPENABLE);
		startActivityForResult(intent, REQUEST_FILE_PICKER);
	}


	private void connectFile(String filePath) {
		try {
			AliThreadUtils.runOnUiThread(() -> dialog = ProgressDialog.show(DirListActivity.this, "搜索", "正在搜索文件..."));
			smbFiles.clear();
			LogUtils.w(TAG + "filePath", filePath);
			SmbFile smbFile = new SmbFile(filePath);
			SmbFile[] files = smbFile.listFiles();
			for (SmbFile file : files) {
				LogUtils.w(TAG + "smbFile_file", filePath);
				Message message = Message.obtain();
				message.obj = file;
				message.what = 100;
				handler.sendMessage(message);
			}
			handler.sendEmptyMessage(200); //完毕
		} catch (Exception e) {
			LogUtils.e(TAG + "connectFile:: ", e.toString());
			e.printStackTrace();
		} finally {
			AliThreadUtils.runOnUiThread(() -> dialog.dismiss());
		}
	}

	private static class OxHandler extends android.os.Handler {
		private WeakReference<DirListActivity> reference;

		private OxHandler(DirListActivity f) {
			reference = new WeakReference<>(f);
		}

		@Override
		public void handleMessage(Message msg) {
			super.handleMessage(msg);
			DirListActivity activity = reference.get();
			if (activity == null) return;
			if (msg.what == 100) {
				activity.smbFiles.add(((SmbFile) msg.obj));
			} else if (msg.what == 200) {
				if (activity.adapter == null) {
					activity.adapter = new DirectoryAdapter(activity, activity.smbFiles);
					activity.listView.setAdapter(activity.adapter);
				} else {
					activity.adapter.notifyDataSetChanged();
				}
			}
		}
	}


	public void onFileClicked(SmbFile file) {
		String canonicalPath = file.getCanonicalPath();
		try {
			LogUtils.w(TAG + "canonicalPath:  ", canonicalPath);
			if (file.isDirectory()) {
				AliThreadUtils.runOnUiThread(() -> {
					startActivity(new Intent(DirListActivity.this, DirListActivity.class)
							.putExtra("path", canonicalPath));
				});
			} else if (file.isFile()) {
				save(file);
			}
		} catch (Exception e) {
			ToastUtils.showShort("保存失败");
			LogUtils.e(TAG, "canonicalPath_error: " + e.toString());
			e.printStackTrace();
		}
	}


	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == REQUEST_FILE_PICKER && ObjectUtils.isNotEmpty(data)) {
			String path = SmbUtils.getFilePathForN(data.getData(), this);
			if (ObjectUtils.isEmpty(path)) {
				ToastUtils.showShort("路径错误");
				return;
			}
			LogUtils.w("TAG_onActivityResult", path);
			LoadingDialog loadingDialog = DialogHelper.loadDialog(this, "上传中...");
			AliThreadUtils.runOnSubThread(() -> {
				String fileName = path.substring(path.lastIndexOf("/") + 1);

				try {
					SmbFile remoteFile = new SmbFile(currentPath + fileName);
					FileInputStream fi = new FileInputStream(new java.io.File(path));
					SmbFileOutputStream fo = new SmbFileOutputStream(remoteFile);
					IOUtils.copy(fi, fo);

					//重新获取目录
					connectFile(currentPath);
					ToastUtils.showShort("上传成功");
				} catch (Exception e) {
					LogUtils.e(TAG + "upLoad:: ", e.toString());
					ToastUtils.showShort("上传失败");
				} finally {
					AliThreadUtils.runOnUiThread(() -> loadingDialog.dismiss());
				}
			});
		}
	}


	public void save(SmbFile file) {
		AliThreadUtils.runOnUiThread(() -> dialog = ProgressDialog.show(DirListActivity.this, "搜索", "正在保存..."));
		try {
			File myRoot = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "smbZuo");
			if (!myRoot.exists()) {
				myRoot.mkdirs();
			}
			SmbFileInputStream fi = new SmbFileInputStream(file);
			FileOutputStream fo = new FileOutputStream(new File(myRoot, file.getName()));
			IOUtils.copy(fi, fo);
			file.close();
			ToastUtils.showShort("保存成功");
		} catch (Exception e) {
			LogUtils.e(TAG + "save_fail_", e.toString());
			ToastUtils.showShort("保存失败");
		} finally {
			AliThreadUtils.runOnUiThread(() -> dialog.dismiss());
		}
	}

	public void delete(SmbFile file) {
		LoadingDialog loadingDialog = DialogHelper.loadDialog(this, "删除中...");
		AliThreadUtils.runOnSubThread( () -> {
			try {
				file.delete();
				ToastUtils.showShort("删除成功");
				//重新获取目录
				connectFile(currentPath);
			} catch (Exception e) {
				LogUtils.e(TAG + "delete_fail_", e.toString());
				ToastUtils.showShort("删除失败");
			} finally {
				AliThreadUtils.runOnUiThread(() -> loadingDialog.dismiss());
			}
		});
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		smbFiles.clear();
	}
}
