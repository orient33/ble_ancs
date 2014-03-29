package jz.ancs.parse;

import java.util.ArrayList;
import java.util.UUID;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;

public class ANCSGattCallback extends BluetoothGattCallback {
	public static final int BleDisconnect = 0;//this is same to onConnectionStateChange()'s state
	public static final int BleAncsConnected = 10;// connected to iOS's ANCS
	public static final int BleBuildStart = 1;//after connectGatt(), before onConnectionStateChange()
	public static final int BleBuildConnectedGatt=2; 	//onConnectionStateChange() state==2
	public static final int BleBuildDiscoverService=3;//discoverServices()... this block
	public static final int BleBuildDiscoverOver=4;		//discoverServices() ok
	public static final int BleBuildSetingANCS=5;		//settingANCS	eg. need pwd...
	
	public int mBleState;
	public static 
	ANCSParser mANCSHandler;
	private BluetoothGatt mBluetoothGatt;
	BluetoothGattService mANCSservice;//连 ANCS主服务
	boolean mWritedNS,mWriteNS_DespOk;
	private ArrayList<StateListener> mStateListeners=new ArrayList<StateListener>();
	/** 连接状态的监听接口*/
	public interface StateListener{
		/** 连接状态改变时的回调  */
		public void onStateChanged(int state);
	}
	
	public ANCSGattCallback(Context c,ANCSParser ancs){
		mANCSHandler = ancs;
	}
	/** 添加一个监听者(监听此BLE连接的连接状态) */
	public void addStateListen(StateListener sl){
		if(!mStateListeners.contains(sl)){
			mStateListeners.add(sl);
			sl.onStateChanged(mBleState);
		}
	}

	/** 不用时调用以释放资源 */
	public void stop(){
		log("stop connectGatt..");
		mBleState = BleDisconnect;
		for(StateListener sl: mStateListeners){
			sl.onStateChanged(mBleState);
		}
		if(null != mBluetoothGatt){
			mBluetoothGatt.disconnect();
			mBluetoothGatt.close();
		}
		mBluetoothGatt = null;
		mANCSservice = null;
		mStateListeners.clear();
	}

	/** 设置btGatt， 应为 连接时 connectGatt() 的返回值为参数*/
	public void setBluetoothGatt(BluetoothGatt BluetoothGatt) {
		mBluetoothGatt = BluetoothGatt;
	}
	
	public void setStateStart(){
		mBleState = BleBuildStart;
		for (StateListener sl : mStateListeners) {
			sl.onStateChanged(mBleState);
		}
	}

	public String getState() {
		String state = "[unknown]" ;
		switch (mBleState) {
		case BleDisconnect: // 0
			state = "GATT [Disconnected]\n\n";
			break;
		case BleBuildStart: // 1
			state = "waiting state change after connectGatt()\n\n";
			break;
		case BleBuildConnectedGatt: // 2
			state = "GATT [Connected]\n\n";
			break;
		case BleBuildDiscoverService: // 3
			state = "GATT [Connected]\n"+"discoverServices...\n";
			break;
		case BleBuildDiscoverOver: // 4
			state = "GATT [Connected]\n"+"discoverServices OVER\n";
			break;
		case BleBuildSetingANCS: // 5
			state = "GATT [Connected]\n"+"discoverServices OVER\n"+"setting ANCS...password";
			break;
		case BleAncsConnected: // 10
			state = "GATT [Connected]\n"+"discoverServices OVER\n"+"ANCS[Connected] success !!";
			break;
		}
		return state;
	}

	void log(String s){
		IOSNotification.log(s);
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
//			log("收到来自 iphone端DS 的字节数据 {{ Tid: "+android.os.Process.myTid());
			byte[] data = cha.getValue();
			mANCSHandler.onDSNotification(data);
		} else {
		}
	}

	@Override
	public void onConnectionStateChange(BluetoothGatt gatt, int status,
			int newState) {
		log("onConnectionStateChange() " + status + "  " + newState+", Tid="+android.os.Process.myTid());
		mBleState = newState;
		for (StateListener sl : mStateListeners) {
			sl.onStateChanged(mBleState);
		}
		if (newState == BluetoothProfile.STATE_CONNECTED
				&& status == BluetoothGatt.GATT_SUCCESS) {
			log("start discover service: ");
			mBleState = BleBuildDiscoverService;
			for(StateListener sl: mStateListeners){
				sl.onStateChanged(mBleState);
			}
			log(" discover service:  end "+mBluetoothGatt.discoverServices());
			mBleState = BleBuildDiscoverOver;
			for(StateListener sl: mStateListeners){
				sl.onStateChanged(mBleState);
			}
		} else if (0 == newState/* && mDisconnectReq*/ && mBluetoothGatt != null) {
		}
	}

	@Override	// New services discovered
	public void onServicesDiscovered(BluetoothGatt gatt, int status) {
		log("onServicesDiscovered() status=" + status);
		if(status != 0 ) return;
		BluetoothGattService ancs = gatt.getService(GattConstant.Apple.sUUIDANCService);
		if (ancs == null) {
			log("bad services found; not find ANCS uuid");
			return;
		}
		log("find ANCS service !");
		/*
		 * 发现了 iphone 的ANCS主服务: 从主服务获取并设置DS的Characteristic，和其descriptor.
		 * 重设 ANCSHandler .
		 */
		BluetoothGattCharacteristic DScha = ancs.getCharacteristic(GattConstant.Apple.sUUIDDataSource);
		if (DScha == null) {
			log("can not find DataSource(DS) characteristic");
			return;
		}
		boolean registerDS = mBluetoothGatt.setCharacteristicNotification(DScha,true);
		if (!registerDS) {
			log(" Enable (DS) notifications failed. ");
			return;
		}
		BluetoothGattDescriptor descriptor = DScha.getDescriptor(GattConstant.DESCRIPTOR_UUID);
		if (null != descriptor) {
			boolean r = descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
			boolean rr = mBluetoothGatt.writeDescriptor(descriptor);
			log("(DS)Descriptor.setValue(): " + r + ",writeDescriptor(): " + rr);
		} else {
			log("can not find descriptor from (DS)");
		}
		mWriteNS_DespOk = mWritedNS = false;
		DScha = ancs.getCharacteristic(GattConstant.Apple.sUUIDControl);
		if (DScha == null) {
			log("can not find ANCS's ControlPoint cha ");
		}
		
		mANCSservice = ancs;
		mANCSHandler.setService(ancs, mBluetoothGatt);
		ANCSParser.get().reset();
		log("found ANCS service & set DS character,descriptor OK !");
	}

	@Override//the result of a descriptor write operation.
	public void onDescriptorWrite(BluetoothGatt gatt,
			BluetoothGattDescriptor descriptor, int status) {

		log("onDescriptorWrite() " + GattConstant.getNameforUUID(descriptor.getUuid()) + " -> "
				+ status);// 15-5 需要输入密码
		if (15 == status || 5 == status) {
			mBleState = BleBuildSetingANCS;//5
			for (StateListener sl : mStateListeners) {
				sl.onStateChanged(mBleState);
			}
			return;
		}
		if (status != BluetoothGatt.GATT_SUCCESS)
			return;
		// status is 0, SUCCESS. 
		if (mWritedNS && mWriteNS_DespOk) {
			for (StateListener sl : mStateListeners) {
				mBleState = BleAncsConnected;
				sl.onStateChanged(mBleState);
			}
		}
		if (mANCSservice != null && !mWritedNS) {	// set NS
			mWritedNS = true;
			BluetoothGattCharacteristic cha = mANCSservice
					.getCharacteristic(GattConstant.Apple.sUUIDChaNotify);
			if (cha == null) {
				log("can not find ANCS's NS cha");
				return;
			}
			boolean registerNS = mBluetoothGatt.setCharacteristicNotification(
					cha, true);
			if (!registerNS) {
				log(" Enable (NS) notifications failed  ");
				return;
			}
			BluetoothGattDescriptor desp = cha.getDescriptor(GattConstant.DESCRIPTOR_UUID);
			if (null != desp) {
				boolean r=desp.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
				boolean rr = mBluetoothGatt.writeDescriptor(desp);
				mWriteNS_DespOk = rr;
				log("(NS)Descriptor.setValue(): " + r + ",writeDescriptor(): " + rr);
			} else {
				log("null descriptor");
			}
		}
	}

}