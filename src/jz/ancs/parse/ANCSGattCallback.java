package jz.ancs.parse;

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
	BluetoothGattService mBluetoothGattService;//连 ANCS主服务
	boolean mWriteNotiDesp,mWriteNotiDespOk;
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
		mBluetoothGattService = null;
		mStateListeners.clear();
	}

	/** 设置btGatt， 应为 连接时 connectGatt() 的返回值为参数*/
	public void setBluetoothGatt(BluetoothGatt BluetoothGatt) {
		mBluetoothGatt = BluetoothGatt;
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
			state = "GATT [Connected]+\n\n";
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
	public void onCharacteristicWrite(BluetoothGatt gatt,
			BluetoothGattCharacteristic characteristic, int status) {
		log("onCharacteristicWrite()" + status);
		mANCSHandler.onWrite(characteristic, status);
	}

	@Override
	public void onConnectionStateChange(BluetoothGatt gatt, int status,
			int newState) {
		log("onConnectionStateChange() " + status + "  " + newState);
		mBleState = newState;
		for (StateListener sl : mStateListeners) {
			sl.onStateChanged(mBleState);
		}
		if (newState == BluetoothProfile.STATE_CONNECTED
				/*&& mBluetoothGattService == null*/) {
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

	@Override//the result of a descriptor write operation.
	public void onDescriptorWrite(BluetoothGatt gatt,
			BluetoothGattDescriptor descriptor, int status) {
		log("onDescriptorWrite() " + descriptor.getUuid() + " -> "
				+ status);// 15-5 需要输入密码
		if (15 == status || 5 == status) {
			mBleState = BleBuildSetingANCS;//5
			for (StateListener sl : mStateListeners) {
				sl.onStateChanged(mBleState);
			}
		}else if(133 == status)
			Thread.dumpStack();
		if (0 == status && mWriteNotiDesp && mWriteNotiDespOk) {
			for (StateListener sl : mStateListeners) {
				mBleState = BleAncsConnected;
				sl.onStateChanged(mBleState);
			}
		}
		if (status == BluetoothGatt.GATT_SUCCESS && mBluetoothGattService != null
				&& !mWriteNotiDesp) {
			BluetoothGattCharacteristic cha = mBluetoothGattService
					.getCharacteristic(GattConstant.Apple.sUUIDChaNotify);
			if(cha == null){
				log("can not find ANCS's NotificationSource cha");
				return;
			}
			boolean registerNS=mBluetoothGatt.setCharacteristicNotification(cha, true);
			if ( !registerNS) {
				log(" Enable notifications/indications failed (NS) ");
				return;
			} 
			BluetoothGattDescriptor desp = cha
					.getDescriptor(GattConstant.CHARACTERISTIC_UPDATE_NOTIFICATION_DESCRIPTOR_UUID);
			if (null != desp) {
				desp.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
				boolean r=mBluetoothGatt.writeDescriptor(desp);
				log("write NS's descriptor2: " + r);
				mWriteNotiDespOk = r;
			} else {
				log("null descriptor2");
				return;
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
		if (services == null) 
			return;
		for (BluetoothGattService svr : services) {
			log("onServicesDiscovered: " + svr.getUuid());
			if (svr.getUuid().equals(GattConstant.Apple.sUUIDANCService)) {
				/*
				 * 发现了 iphone 的ANCS主服务: 从主服务获取 DS的 Characteristic，和其descriptor，
				 * CP的 characteristic 保留 ANCS服务的‘指针’ 重设 ANCSHandler return.
				 */
				BluetoothGattCharacteristic cha = svr.getCharacteristic(GattConstant.Apple.sUUIDDataSource);
				if(cha == null){
					log("can not find ANCS's DataSource characteristic");
					break;
				}
				boolean registerDS=mBluetoothGatt.setCharacteristicNotification(cha,true);
				if ( !registerDS) {
					log(" Enable notifications/indications failed. (DS)");
					break;
				} 
				BluetoothGattDescriptor descriptor = cha
						.getDescriptor(GattConstant.CHARACTERISTIC_UPDATE_NOTIFICATION_DESCRIPTOR_UUID);
				//Value used to enable notification for a client configuration descriptor
				if (null != descriptor) {
					boolean r=descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
					boolean rr=mBluetoothGatt.writeDescriptor(descriptor);
					log("Descriptor.setValue(): "+r+",then BluetoothGatt writeDescriptor(): " +rr);
				} else {
					log("can not find descriptor from ANCS's DataSource");
				}
				
				mWriteNotiDespOk = mWriteNotiDesp = false;

				cha = svr.getCharacteristic(GattConstant.Apple.sUUIDControl);
				if (cha == null) {
					log("can not find ANCS's ControlPoint cha ");
				}

				mBluetoothGattService = svr;
				mANCSHandler.setService(svr, mBluetoothGatt);

				ANCSParser.get().reset();
				log("found ANCS service & character OK!");
				return;
			}
		}
		log("bad service found; not find ANCS uuid");
//		stop();
	}
}