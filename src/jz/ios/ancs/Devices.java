package jz.ios.ancs;

import java.util.ArrayList;
import java.util.List;

import android.app.ListActivity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothAdapter.LeScanCallback;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

public class Devices extends ListActivity {
	public static final String TAG = "sw2df";
	
	private static final long SCAN_PERIOD = 5000;
	private Handler mHandler= new Handler();
	private BluetoothAdapter mBluetoothAdapter;
	private boolean mLEscaning = false;
	private List<BluetoothDevice> mList = new ArrayList<BluetoothDevice>();
	private BaseAdapter mListAdapter = new BaseAdapter() {

		@Override
		public View getView(int i, View arg1, ViewGroup arg2) {
			TextView tv = (TextView) arg1;
			if (null == tv) {
				tv = new TextView(Devices.this);
				tv.setPadding(10, 10, 10, 10);
				tv.setTextSize(20);
			}
			BluetoothDevice dev = mList.get(i);
			String name = dev.getName();
			if (TextUtils.isEmpty(name)) {
				name = dev.getAddress();
			}
			tv.setText(name);
			return tv;
		}

		@Override
		public long getItemId(int arg0) {
			return 0;
		}

		@Override
		public Object getItem(int arg0) {
			return null;
		}

		@Override
		public int getCount() {
			return mList.size();
		}
	};
	
	private LeScanCallback mLEScanCallback = new LeScanCallback() {

		@Override
		public void onLeScan(final BluetoothDevice device, int rssi,
				byte[] scanRecord) {
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					boolean found = false;
					for (BluetoothDevice dev : mList) {
						if (dev.getAddress().equals(device.getAddress())) {
							found = true;
							break;
						}
					}
					if (!found) {
						mList.add(device);
						mListAdapter.notifyDataSetChanged();
					}
				}
			});

		}
	};
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_devices);	
		PackageManager pm = getPackageManager();
		pm.checkPermission("aaa", "bbb.ccc");
		boolean support = pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);
		log(" BLE support: "+ support);
		if (!support) {
			show("此设备不支持 BLE");
			finish();
			return;
		}
		BluetoothManager mgr = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
		mBluetoothAdapter = mgr.getAdapter();
		if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
		    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
		    startActivityForResult(enableBtIntent, 1);
		}
		
		mList.clear();
		scan(true);
		getListView().setAdapter(mListAdapter);
	}
	
	void scan(final boolean enable) {
		if (enable) {
			// Stops scanning after a pre-defined scan period.
			mHandler.postDelayed(new Runnable() {
				@Override
				public void run() {
					if (mLEscaning) {
						mBluetoothAdapter.stopLeScan(mLEScanCallback);
						mLEscaning = false;
						log("停止扫描");
					}
				}
			}, SCAN_PERIOD);

			log("开始扫描 BLE 设备...");
			mLEscaning = true;
			mBluetoothAdapter.startLeScan(mLEScanCallback);
		} else {
			if (mLEscaning) {
				mBluetoothAdapter.stopLeScan(mLEScanCallback);
				mLEscaning = false;
				log("停止扫描");
			}
		}
	}
	
	@Override
	protected void onDestroy() {
		scan(false);
		super.onDestroy();
	}
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.devices, menu);
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch(item.getItemId()){
		case R.id.scan:
			mList.clear();
			scan(true);
			break;
		}
		return true;
	}
	protected void onListItemClick(ListView l, View v, int position, long id) {
		BluetoothDevice dev = mList.get(position);
		scan(false);
		Intent intent = new Intent(this,  BLEConnect.class);
		intent.putExtra("addr", dev.getAddress());
		startActivity(intent);
		finish();
	}

	private void show(final String text) {
//		Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
	}
	
	static void log(String s){
		Log.d(TAG, "[BLE_ancs] " + s);
	} 
	static void logw(String s){
		Log.w(TAG,  s);
	}
	static void loge(String s){
		Log.e(TAG,  s);
	}
}
