package jz.ancs.exposed;

import java.util.UUID;

import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;

public class ExposedService extends Service {
	private static final String TAG = "ble";
	final int properties = BluetoothGattCharacteristic.PROPERTY_BROADCAST
			| BluetoothGattCharacteristic.PROPERTY_READ
			| BluetoothGattCharacteristic.PROPERTY_WRITE
			| BluetoothGattCharacteristic.PROPERTY_NOTIFY
			| BluetoothGattCharacteristic.PROPERTY_INDICATE
			| BluetoothGattCharacteristic.PROPERTY_SIGNED_WRITE
			| BluetoothGattCharacteristic.FORMAT_UINT8;


	final int permissions = BluetoothGattCharacteristic.PERMISSION_READ
			| BluetoothGattCharacteristic.PERMISSION_WRITE
			| BluetoothGattCharacteristic.PERMISSION_WRITE_SIGNED;

	final int descPermissions = BluetoothGattDescriptor.PERMISSION_READ
			| BluetoothGattDescriptor.PERMISSION_WRITE
			| BluetoothGattDescriptor.PERMISSION_WRITE_SIGNED;
	BluetoothManager bm;
	BluetoothGattServer sGattServer = null;
	Context mContext;
	boolean mIsExposed=false;

	int mHeartRate = 0;
	boolean isDead = false;
	BluetoothGattCharacteristic mBluetoothGattCharacteristic;
	
	@Override
	public IBinder onBind(Intent arg0) {
		return null;
	}

	@Override
	public void onCreate() {
		super.onCreate();
		mContext=this.getApplicationContext();		
		log("onCreate()");
		bm = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		log("onDestroy()");
		mSimulatorHandler.removeMessages(0);
		mSimulatorHandler.removeMessages(1);
		stopExposed();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		startExposedService();
		return super.onStartCommand(intent, flags, startId);
	}
	
	public void startExposedService(){
		log("startExposedService()");
		if(mIsExposed) return;
		mIsExposed = true;
		BtGattServerCb cb = new BtGattServerCb();
		if (sGattServer != null) {
			Log.e(TAG, "There is a gatt server has not closed yet. so close it");
			sGattServer.close();
			sGattServer = null;
		}
		sGattServer = bm.openGattServer(this, cb);
		cb.setBtGattServer(sGattServer);
		if (null == sGattServer) {
			log("There is no gatt server.");
		}
		BluetoothGattService bs = new BluetoothGattService(
				UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb"),
				BluetoothGattService.SERVICE_TYPE_PRIMARY);
		BluetoothGattCharacteristic gattChar = new BluetoothGattCharacteristic(
				UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb"),
				properties, permissions);
		gattChar.setValue("Heart Rate Demo");
		BluetoothGattDescriptor gattDesc = new BluetoothGattDescriptor(
				UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"),
				descPermissions);
		String descStr = new String("measure demo");
		gattDesc.setValue(descStr.getBytes());

		gattChar.addDescriptor(gattDesc);
		bs.addCharacteristic(gattChar);
		mBluetoothGattCharacteristic = gattChar;

		if (sGattServer != null) {
			log("bt  server add service.");
			sGattServer.addService(bs);
		}
//
//        Message msg = mSimulatorHandler.obtainMessage();
//		mSimulatorHandler.sendMessageDelayed(msg, 2000);
	}
	
	public void stopExposed(){
		log(" stopExposed ()");
		if(!mIsExposed) return;
		mIsExposed=false;
		if (sGattServer != null) {
			Log.e(TAG, "close  GattServer 's  service !.");
			sGattServer.close();
			sGattServer = null;
		}
	}


	private Handler mSimulatorHandler = new SimulatorHandler();
	private class SimulatorHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case 1:
				if (mConnectionState == BluetoothProfile.STATE_CONNECTED) {
					// stopExposed();
					Intent i = new Intent();
					// i.setClassName("jz.ios.ancs", "BLEConnect");
					i.setAction(CON_ACION);
					i.putExtra("addr", mBluetoothDevice.getAddress());
					log("send addr to BLEConnect. with addr ");
					mContext.sendBroadcast(i);
				}
				break;
			case 0:
				mHeartRate++;
				if (mHeartRate > 90) {
					mHeartRate = 62;
				}
				if (sGattServer != null
						&& mConnectionState == BluetoothProfile.STATE_CONNECTED
						&& mBluetoothDevice != null) {
					BluetoothGattCharacteristic characteristic = mBluetoothGattCharacteristic;
					int num = mHeartRate;
					byte[] value = transToBytes(num);

					characteristic.setValue(value);
					boolean r = sGattServer.notifyCharacteristicChanged(
							mBluetoothDevice, characteristic, false);
					log("notifyCharacteristicChanged() result=" + r);
				} else {
					log("notifyCharacteristicChanged() " + sGattServer + "; "
							+ mConnectionState + " , " + mBluetoothDevice);
				}
				Message mymsg = obtainMessage(0);
				// if (mConnectionState == BluetoothProfile.STATE_CONNECTED)
				sendMessageDelayed(mymsg, 30000);
				break;
			}
		}
	}

	private void log(String s) {
		android.util.Log.d(TAG, "[ExposedService] " + s);
	}

	private byte[] transToBytes(int num) {
		byte[] vals = new byte[3];

		vals[1] = (byte) num;

		return vals;
	}

	int mConnectionState;
	BluetoothDevice mBluetoothDevice = null;

	public static final String CON_ACION = "jz.ancs.gatt_connect";

	class BtGattServerCb extends BluetoothGattServerCallback {
		public static final String TAG = "ble";

		private BluetoothGattServer sGattServer = null;

		// A remote client has requested to read a local characteristic.
		@Override
		public void onCharacteristicReadRequest(BluetoothDevice device,
				int requestId, int offset,
				BluetoothGattCharacteristic characteristic) {
			log("onCharacteristicReadRequest()------");
			// dumpCharacteristic(characteristic);
			int num = mHeartRate;
			byte[] value = transToBytes(num);
			sGattServer.sendResponse(device, requestId,
					BluetoothGatt.GATT_SUCCESS, offset, value);
		}

		// A remote client has requested to write to a local characteristic.
		@Override
		public void onCharacteristicWriteRequest(BluetoothDevice device,
				int requestId, BluetoothGattCharacteristic characteristic,
				boolean preparedWrite, boolean responseNeeded, int offset,
				byte[] value) {
			String myValue = new String(value);
			log("onCharacteristicWriteRequest value====" + myValue);
		}

		// Callback indicating when a remote device has been connected or
		// disconnected.
		@Override
		public void onConnectionStateChange(BluetoothDevice device, int status,
				int newState) {
			// super.onConnectionStateChange(device, status, newState);

			mBluetoothDevice = device;
			mConnectionState = newState;
			log("onConnectionStateChange() status=" + status + ",newState="
					+ newState + ",device -- " + device.getAddress());
			if (status == BluetoothGatt.GATT_SUCCESS
					&& newState == BluetoothProfile.STATE_CONNECTED) {
				mSimulatorHandler.removeMessages(1);
				mSimulatorHandler.sendEmptyMessageDelayed(1, 5000);
			}
		}

		// A remote client has requested to read a local descriptor
		@Override
		public void onDescriptorReadRequest(BluetoothDevice device,
				int requestId, int offset, BluetoothGattDescriptor descriptor) {
			log("onDescriptorReadRequest-=============");
		}

		// A remote client has requested to write to a local descriptor.
		@Override
		public void onDescriptorWriteRequest(BluetoothDevice device,
				int requestId, BluetoothGattDescriptor descriptor,
				boolean preparedWrite, boolean responseNeeded, int offset,
				byte[] value) {
			log("onDescriptorWriteRequest============");
		}

		// Execute all pending write operations for this device.
		@Override
		public void onExecuteWrite(BluetoothDevice device, int requestId,
				boolean execute) {
			log("onExecuteWrite(device, requestId, execute)=====================");
		}

		// Indicates whether a local service has been added successfully.
		@Override
		public void onServiceAdded(int status, BluetoothGattService service) {
			log("onServiceAdded() status=" + status);
			if (status == BluetoothGatt.GATT_SUCCESS) {
				sGattServer.startAdvertise();
			}
		}

		//
		public void setCha(BluetoothGattCharacteristic s) {
			mBluetoothGattCharacteristic = s;
		}

		// ===== public method
		public void setBtGattServer(BluetoothGattServer s) {
			sGattServer = s;
		}

		private void log(String s) {
			android.util.Log.d(TAG, "[BtGattServerCb] " + s);
		}

	}

}
