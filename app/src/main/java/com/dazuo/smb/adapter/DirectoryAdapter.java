package com.dazuo.smb.adapter;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.dazuo.smb.R;
import com.dazuo.smb.utils.FormatUtil;


import java.util.List;

import jcifs.smb.SmbFile;


/**
 * 目录adapter
 * <p>
 * Created by hudq on 2016/12/15.
 */

public class DirectoryAdapter extends BaseAdapter {

	private Context mContext;
	private List<SmbFile> files;
	Drawable folderImg, fileImg;

	public DirectoryAdapter(Context context, List<SmbFile> files) {
		this.mContext = context;
		this.files = files;

		folderImg = mContext.getResources().getDrawable(R.mipmap.format_folder);
		fileImg = mContext.getResources().getDrawable(R.mipmap.format_file);
	}

	@Override
	public int getCount() {
		return files != null ? files.size() : 0;
	}

	@Override
	public SmbFile getItem(int position) {
		return files.get(position);
	}

	@Override
	public long getItemId(int position) {
		return 0;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		ViewHolder holder = null;
		if (convertView == null) {
			convertView = View.inflate(mContext, R.layout.item_list_direcory, null);
			holder = new ViewHolder();
			holder.icon = (ImageView) convertView.findViewById(R.id.item_dire_icon);
			holder.name = (TextView) convertView.findViewById(R.id.item_dire_name);
			holder.info = (TextView) convertView.findViewById(R.id.item_dire_info);
			holder.time = (TextView) convertView.findViewById(R.id.item_dire_time);
			convertView.setTag(holder);
		} else {
			holder = (ViewHolder) convertView.getTag();
		}
		try {
			SmbFile file = files.get(position);
			if (file.isDirectory()) {
				holder.icon.setImageDrawable(folderImg);
				holder.info.setText("文件夹");
			} else if (file.isFile()) {
				holder.icon.setImageDrawable(fileImg);
				holder.info.setText(FormatUtil.formatSize(file.length()));
			} else {
				holder.icon.setImageDrawable(null);
			}
			holder.name.setText(file.getName());
			if (file.getLastModified() > 0)
				holder.time.setText(FormatUtil.formatMills(file.getLastModified()));

		} catch (Exception e) {
			e.printStackTrace();
		}
		return convertView;
	}


	private static class ViewHolder {
		ImageView icon;
		TextView name;
		TextView info;
		TextView time;
	}

}
