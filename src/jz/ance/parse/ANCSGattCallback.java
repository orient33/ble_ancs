package jz.ance.parse;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;

public class ANCSGattCallback extends BluetoothGattCallback {
	public static final int Disconnected = BluetoothProfile.STATE_DISCONNECTED,
			Connecting = BluetoothProfile.STATE_CONNECTING,
			Connected = BluetoothProfile.STATE_CONNECTED,
			Disconnecting=BluetoothProfile.STATE_DISCONNECTING;
	int mState = Disconnected, mStatus;
	ANCSHandler mANCSHandler;
	BluetoothGatt mBluetoothGatt;
	BluetoothGattService mBluetoothGattService;//连 ANCS主服务
	boolean mWriteNotiDesp;
	private ArrayList<StateListener> mStateListeners=new ArrayList<StateListener>();
	/** 连接状态的监听接口*/
	public interface StateListener{
		/** 连接状态改变时的回调  */
		public void onStateChanged(String state);
	}
	
	public ANCSGattCallback(Context c,ANCSHandler ancs){
		mANCSHandler = ancs;
	}
	/** 添加一个监听者(监听此BLE连接的连接状态) */
	public void addStateListen(StateListener sl){
		if(!mStateListeners.contains(sl)){
			mStateListeners.add(sl);
			sl.onStateChanged(getState());
		}
	}

	/** 不用时调用以释放资源 */
	public void stop(){
		log("stop connectGatt");
		try {
			mBluetoothGatt.close();
		} catch (Exception e) {
		}
		mBluetoothGatt = null;
		mBluetoothGattService = null;
		mANCSHandler.setService(mBluetoothGattService, mBluetoothGatt);
	}

	/** 设置btGatt， 应为 连接时 connectGatt() 的返回值为参数*/
	public void setBluetoothGatt(BluetoothGatt BluetoothGatt) {
		mBluetoothGatt = BluetoothGatt;
	}
	
	private String getState() {
		String  state="[unknown]" /*,OPresult="unknown"*/;
		switch(mState){
		case Disconnected:
			state = "[Disconnected]";
			break;
		case Disconnecting:
			state ="[Disconnecting]";
			break;
		case Connected:
			state = "[Connected]";
			break;
		case Connecting:
			state = "[Connecting]";
			break;
		}
//		switch(mStatus){
//		case BluetoothGatt.GATT_SUCCESS:
//			OPresult="[GATT_SUCCESS]";
//			break;
//		case BluetoothGatt.GATT_FAILURE:
//			OPresult="[GATT_FAILURE]";
//			break;
//		case BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION:
//			OPresult="[INSUFFICIENT_AUTHENTICATION]";
//			break;
//		case BluetoothGatt.GATT_INSUFFICIENT_ENCRYPTION:
//			OPresult="[INSUFFICIENT_ENCRYPTION]";
//			break;
//		case BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH:
//			OPresult="[INVALID_ATTRIBUTE_LENGTH]";
//			break;
//		case BluetoothGatt.GATT_INVALID_OFFSET:
//			OPresult="[INVALID_OFFSET]";
//			break;
//		case BluetoothGatt.GATT_READ_NOT_PERMITTED:
//			OPresult="[READ_NOT_PERMITTED]";
//			break;
//		case BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED:
//			OPresult="[REQUEST_NOT_SUPPORTED]";
//			break;
//		case BluetoothGatt.GATT_WRITE_NOT_PERMITTED:
//			OPresult="[WRITE_NOT_PERMITTED]";
//			break;
//		}

		return state;
	}
	
	void log(String s){
		Notice.log(s);
	}
	
	@Override
	public void onCharacteristicChanged(BluetoothGatt gatt,
			BluetoothGattCharacteristic cha) {
		UUID uuid = cha.getUuid();
		if (uuid.equals(GattConstant.Apple.sUUIDChaNotify)) {
			//收到来自 iphone端NS 的字节数据 data[]
			log("收到来自 iphone端NS 的字节数据");
			byte[] data = cha.getValue();
			mANCSHandler.onNotification(data);
		} else if (uuid.equals(GattConstant.Apple.sUUIDDataSource)) {
			//收到来自 iphone端DS 的字节数据 data[]
			log("收到来自 iphone端DS 的字节数据"+android.os.Process.myTid());
			byte[] data = cha.getValue();
			mANCSHandler.onDSNotification(data);
		} else {
		}
	}

	@Override
	public void onCharacteristicWrite(BluetoothGatt gatt,
			BluetoothGattCharacteristic characteristic, int status) {
		log("onCharacteristicWrite()" + status);
		mANCSHandler.onWrite(characteristic, status);
	}

	@Override
	public void onConnectionStateChange(BluetoothGatt gatt, int status,
			int newState) {
		log("onConnectionStateChange() " + status + "  " + newState);
		mState = newState;
		String state = getState();
		for(StateListener sl: mStateListeners){
			sl.onStateChanged(state);
		}
		if (newState == BluetoothProfile.STATE_CONNECTED
				&& mBluetoothGattService == null) {
			log("discover service: " + mBluetoothGatt.discoverServices());
		} else {
//			reconnect();
		}
	}

	@Override//the result of a descriptor write operation.
	public void onDescriptorWrite(BluetoothGatt gatt,
			BluetoothGattDescriptor descriptor, int status) {
		log("onDescriptorWrite() " + descriptor.getUuid() + " -> "
				+ status);
		if (status == BluetoothGatt.GATT_SUCCESS && mBluetoothGattService != null
				&& !mWriteNotiDesp) {
			BluetoothGattCharacteristic cha = mBluetoothGattService
					.getCharacteristic(GattConstant.Apple.sUUIDChaNotify);
			if (cha == null
					|| !mBluetoothGatt.setCharacteristicNotification(cha, true)) {
				log("no Notify cha found " + cha);
			} else {
				BluetoothGattDescriptor desp = cha
						.getDescriptor(GattConstant.CHARACTERISTIC_UPDATE_NOTIFICATION_DESCRIPTOR_UUID);
				if (null != desp) {
					desp.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
					log("write descriptor2: "
							+ mBluetoothGatt.writeDescriptor(desp));
				} else {
					log("null descriptor2");
				}
			}
			mWriteNotiDesp = true;
			return;
		}
	}

	@Override	// New services discovered
	public void onServicesDiscovered(BluetoothGatt gatt, int status) {
		log("onServicesDiscovered");
		//得到远端设备(iphone)提供的GATT服务列表
		List<BluetoothGattService> services = gatt.getServices();
		if (services != null) {
			for (BluetoothGattService svr : services) {
				if (svr.getUuid()
						.equals(GattConstant.Apple.sUUIDANCService)) {
					 /*发现了 iphone 的ANCS主服务:
					 * 从主服务获取
					 * DS的 Characteristic，和其descriptor，
					 * CP的 characteristic
					 * 保留 ANCS服务的‘指针’
					 * 重设 ANCSHandler
					 * return.
					 */
					BluetoothGattCharacteristic cha = svr
							.getCharacteristic(GattConstant.Apple.sUUIDDataSource);
					if (cha == null
							|| !mBluetoothGatt.setCharacteristicNotification(cha,
									true)) {
						log("no DS cha found " + cha);
						break;
					} else {
						BluetoothGattDescriptor descriptor = cha
								.getDescriptor(GattConstant.CHARACTERISTIC_UPDATE_NOTIFICATION_DESCRIPTOR_UUID);
						if (null != descriptor) {
							descriptor
									.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
							log("write ds desp: "
									+ mBluetoothGatt.writeDescriptor(descriptor));
						} else {
							log("null ds desp");
						}
					}
					mWriteNotiDesp = false;

					cha = svr.getCharacteristic(GattConstant.Apple.sUUIDControl);
					if (cha == null) {
						log("no control cha found");
					}

					mBluetoothGattService = svr;
					mANCSHandler.setService(svr, mBluetoothGatt);
					
					ANCSHandler.get().reset();
					log("found ANCS service & character OK!");
					return;
				}
			}
		}
		log("bad service found");
		stop();
	}
}