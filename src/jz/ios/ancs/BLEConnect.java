package jz.ios.ancs;

import java.util.List;

import jz.ancs.parse.ANCSGattCallback;
import jz.ancs.parse.ANCSParser;
import jz.ancs.parse.Notice;
import jz.ancs.parse.ANCSGattCallback.StateListener;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothProfile;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;


public class BLEConnect extends Activity implements StateListener{

	public static final int Disconnected = BluetoothProfile.STATE_DISCONNECTED,
			Connecting = BluetoothProfile.STATE_CONNECTING,
			Connected = BluetoothProfile.STATE_CONNECTED,
			Disconnecting=BluetoothProfile.STATE_DISCONNECTING;
	int mState = Disconnected;

	private BluetoothAdapter mBluetoothAdapter;
	String addr;
	
	TextView mViewState;
	ListView mViewMsgs;
	ANCSParser mANCSHandler;
	ANCSGattCallback mANCScb;
	private List<Notice> mList;
	private BaseAdapter mListAdapter = new BaseAdapter() {

		@Override
		public int getCount() {
			return mList.size();
		}

		@Override
		public Object getItem(int arg0) {
			return null;
		}

		@Override
		public long getItemId(int arg0) {
			return 0;
		}

		@Override
		public View getView(int i, View v, ViewGroup arg2) {
			ViewGroup vg = (ViewGroup)v;
			Notice n = mList.get(i);
			if(null == vg)
				vg = (ViewGroup)View.inflate(BLEConnect.this, R.layout.noti_item, null);

			((TextView) vg.findViewById(R.id.title)).setText(n.title);
			((TextView) vg.findViewById(R.id.subtitle)).setText(n.subtitle);
			((TextView) vg.findViewById(R.id.message)).setText(n.message);
			((TextView) vg.findViewById(R.id.ms)).setText("["+n.messageSize+"]");
			((TextView) vg.findViewById(R.id.date)).setText(n.date + "");
			return vg;
		}};
	@Override
	public void onCreate(Bundle b) {
		super.onCreate(b);
		setContentView(R.layout.ble_connect);
		mANCSHandler = ANCSParser.getDefault(this,R.drawable.ic_launcher);
		mViewState = (TextView)findViewById(R.id.ble_state);
		mViewMsgs = (ListView)findViewById(R.id.lv);
//		mViewMsgs.setAdapter(mListAdapter);
		
		addr = getIntent().getStringExtra("addr");
		if (!BluetoothAdapter.checkBluetoothAddress(addr)) {
			finish();
			return;
		}
		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

//		mList.clear();
		Devices.log("start connectGatt");
		BluetoothDevice dev = mBluetoothAdapter.getRemoteDevice(addr);
		mANCScb = new ANCSGattCallback(this, mANCSHandler);
		BluetoothGatt btGatt = dev.connectGatt(this, true, mANCScb);
		mANCScb.setBluetoothGatt(btGatt);
		mANCScb.addStateListen(this);
	}

	@Override
	public void onDestroy() {
		mANCScb.stop();
		super.onDestroy();
	}

	@Override
	public void onStateChanged(String state) {
		mViewState.setText(state);
	}
}
