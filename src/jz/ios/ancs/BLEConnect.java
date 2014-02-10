package jz.ios.ancs;

import java.util.List;

import jz.ancs.parse.ANCSGattCallback;
import jz.ancs.parse.ANCSGattCallback.StateListener;
import jz.ancs.parse.ANCSParser;
import jz.ancs.parse.IOSNotification;
import android.app.Activity;
import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;


public class BLEConnect extends Activity implements StateListener,ANCSParser.onIOSNotification{

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
	private List<IOSNotification> mList;
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
			IOSNotification n = mList.get(i);
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
		mANCSHandler.listenIOSNotification(this);
		BluetoothGatt btGatt = dev.connectGatt(this, true, mANCScb);
//		Devices.log("start connectGatt..connect()");
//		btGatt.connect();
		mANCScb.setBluetoothGatt(btGatt);
		mANCScb.addStateListen(this);
	}

	@Override
	public void onStop() {
		mANCScb.stop();
		super.onStop();
	}

	private String state1="",state2="",state3="";
	@Override
	public void onStateChanged(final int type, final String state) {
		this.runOnUiThread(new Runnable() {
			public void run() {
				if(0 == type){
					state1=state;
					if ("GATT [Disconnected]".equals(state))
						state2 = state3 = "";
				}else if(1==type)
					state2=state;
				else if(2==type)
					state3=state;
				mViewState.setText(state1+"\n"+state2+"\n"+state3);
			}
		});
	}

	@Override
	public void onIOSNotificationAdd(IOSNotification noti) {
		NotificationCompat.Builder build = new
		NotificationCompat.Builder(this)
		.setSmallIcon(R.drawable.ic_launcher)
		.setContentTitle(noti.title)
		.setContentText(noti.message);		
		((NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE)).notify(noti.uid, build.build());
	}

	@Override
	public void onIOSNotificationRemove(int uid) {
		((NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE)).cancel(uid);
	}
}
