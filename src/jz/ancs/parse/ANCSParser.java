package jz.ancs.parse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import android.app.NotificationManager;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.os.Handler;
import android.os.Message;
/** 解析 iOS的ANCS 通知中心的通知信息<br>
 *  并将此通知以android的Notification形式发到状态栏
 * */
public class ANCSParser {
	// ANCS constants
	public final static int NotificationAttributeIDAppIdentifier = 0;
	public final static int NotificationAttributeIDTitle = 1; //, (Needs to be followed by a 2-bytes max length parameter)
	public final static int NotificationAttributeIDSubtitle = 2; //, (Needs to be followed by a 2-bytes max length parameter)
	public final static int NotificationAttributeIDMessage = 3; //, (Needs to be followed by a 2-bytes max length parameter)
	public final static int NotificationAttributeIDMessageSize = 4; //,
	public final static int NotificationAttributeIDDate = 5; //,
	public final static int AppAttributeIDDisplayName = 0;

	public final static int CommandIDGetNotificationAttributes = 0;
	public final static int CommandIDGetAppAttributes = 1;

	public final static int EventFlagSilent = (1 << 0);
	public final static int EventFlagImportant = (1 << 1);
	public final static int EventIDNotificationAdded = 0;
	public final static int EventIDNotificationModified = 1;
	public final static int EventIDNotificationRemoved = 2;

	public final static int CategoryIDOther = 0;
	public final static int CategoryIDIncomingCall = 1;
	public final static int CategoryIDMissedCall = 2;
	public final static int CategoryIDVoicemail = 3;
	public final static int CategoryIDSocial = 4;
	public final static int CategoryIDSchedule = 5;
	public final static int CategoryIDEmail = 6;
	public final static int CategoryIDNews = 7;
	public final static int CategoryIDHealthAndFitness = 8;
	public final static int CategoryIDBusinessAndFinance = 9;
	public final static int CategoryIDLocation = 10;
	public final static int CategoryIDEntertainment = 11;

	// !ANCS constants

	private final static int MSG_ADD_NOTIFICATION = 100;
	private final static int MSG_DO_NOTIFICATION = 101;
	private final static int MSG_RESET = 102;
	private final static int MSG_ERR = 103;
	private final static int MSG_CHECK_TIME = 104;
	private final static int MSG_FINISH = 105;
	private final static int FINISH_DELAY = 500;// 500 ms
	private final static int TIMEOUT = 5 * 1000;

	private List<ANCSData> mPendingNotifcations = new LinkedList<ANCSData>();
	private Handler mHandler;
	/** 封装了一次 NC向NS请求数据、和 对应的DS的回复此次请求数据 */
	private ANCSData mCurData;
	BluetoothGatt mGatt;
	/**ANCS 主服务*/
	BluetoothGattService mService;
	Context mContext;
	private static ANCSParser sInst;
	NotificationManager mNotificationManager;
	
	private ArrayList<onIOSNotification> mListeners=new ArrayList<onIOSNotification>(); 
	public interface onIOSNotification{
		void onIOSNotificationAdd(IOSNotification n);
		void onIOSNotificationRemove(int uid);
	}
	
	private ANCSParser(Context c,int id) {
		mContext = c;
		mNotificationManager = (NotificationManager) c
				.getSystemService(Context.NOTIFICATION_SERVICE);
		mHandler = new Handler(c.getMainLooper()) {
			@Override
			public void handleMessage(Message msg) {
				int what = msg.what;
				if (MSG_CHECK_TIME == what) {
					if (mCurData == null) {
						return;
					}
					if (System.currentTimeMillis() >= mCurData.timeExpired) {
						IOSNotification.loge("msg timeout !");
					}
				} else if (MSG_ADD_NOTIFICATION == what) {
					mPendingNotifcations.add(new ANCSData((byte[]) msg.obj));
					mHandler.sendEmptyMessage(MSG_DO_NOTIFICATION);
				} else if (MSG_DO_NOTIFICATION == what) {
					processNotificationList();
				} else if (MSG_RESET == what) {
					mHandler.removeMessages(MSG_ADD_NOTIFICATION);
					mHandler.removeMessages(MSG_DO_NOTIFICATION);
					mHandler.removeMessages(MSG_RESET);
					mHandler.removeMessages(MSG_ERR);
					mPendingNotifcations.clear();
					mCurData = null;
					IOSNotification.log("ANCSHandler reseted");
				} else if (MSG_ERR == what) {
					IOSNotification.log("error, skip cur data");
					mCurData.clear();
					mCurData = null;
					mHandler.sendEmptyMessage(MSG_DO_NOTIFICATION);
				} else if (MSG_FINISH == what) {
					IOSNotification.log("msg  data.finish()");
					if(null!=mCurData)
					mCurData.finish();
				}
			}
		};
//		Notice.logw("JNI log , add == "+add(1,9));
	}
	
	public void listenIOSNotification(onIOSNotification lis){
		if(!mListeners.contains(lis))
			mListeners.add(lis);
	}
	
	/** 设置连接成功后的一些索引
	 * BluetoothGattService BluetoothGatt
	 * */
	public void setService(BluetoothGattService bgs, BluetoothGatt bg) {
		mGatt = bg;
		mService = bgs;
	}
	/** 初始化一个实例*/
	public static ANCSParser getDefault(Context c, int id) {
		if (sInst == null) {
			sInst = new ANCSParser(c, id);
		}
		return sInst;
	}
	/** 获取此单例，必须在 init()之后调用*/
	public static ANCSParser get() {
		return sInst;
	}

	private void sendNotification(final IOSNotification noti) {
		IOSNotification.log("[Add Notification] : "+noti.uid);
		for(onIOSNotification lis: mListeners){
			lis.onIOSNotificationAdd(noti);
		}
//		NotificationCompat.Builder build  = new NotificationCompat.Builder(mContext)
//	    .setSmallIcon(icon_id)
//	    .setContentTitle(noti.title)
//	    .setContentText(noti.message);
//		mNotificationManager.notify(noti.uid, build.build());
	}
	private void cancelNotification(int uid){
		IOSNotification.log("[cancel Notification] : "+uid);
		for(onIOSNotification lis: mListeners){
			lis.onIOSNotificationRemove(uid);
		}
//		mNotificationManager.cancel(uid);
	}
	
	private class ANCSData {
		long timeExpired;
		int curStep = 0;
		/**  NS通知给NC的通知信息的数据 */
		final byte[] notifyData; // 8 bytes
		/** NC向NS请求此通知(notifyData)的属性<br>
		 *  DS回复NC的此通知 属性的数据 */
		ByteArrayOutputStream bout;
		IOSNotification noti;

		ANCSData(byte[] data) {
			notifyData = data;
			curStep = 0;
			timeExpired = System.currentTimeMillis();
			noti=new  IOSNotification();
		}

		void clear() {
			if (bout != null) {
				bout.reset();
			}
			bout = null;
			curStep = 0;
		}

/*
		int getEvtId() {
			return (notifyData[0]);
		}

		int getCategoryId() {
			return (notifyData[2] >> 2);
		}

		int getCategoryCount() {
			return (notifyData[3] >> 3);
		}*/

		int getUID() {
			return (0xff & notifyData[7] << 24) | (0xff & notifyData[6] << 16)
					| (0xff & notifyData[5] << 8) | (0xff & notifyData[4]);
		}


		void finish() {
			if (null == bout) {

				return;
			}
			//来自 DS的回复 数据
			final byte[] data = bout.toByteArray();
			if (data.length < 5) {
				return; // 
			}
			// check if finished ?
			int cmdId = data[0]; // should be 0								//0 commandID
			if (cmdId != 0) {
				IOSNotification.log("bad cmdId: " + cmdId);
				return;
			}
			int uid = ((0xff&data[4]) << 24) | ((0xff &data[3]) << 16)			// 1234 是通知UID
					| ((0xff & data[2]) << 8) | ((0xff &data[1]));
			if (uid != mCurData.getUID()) {
				IOSNotification.log("bad uid: " + uid + " -> " + mCurData.getUID());
				return;
			}

			// read attributes
			noti.uid = uid;
			int curIdx = 5; // 5开始，是属性列表
			while (true) {
				if (noti.isAllInit()) {
					break; // 已获取到所有属性
				}
				if (data.length < curIdx + 3) {
					return;
				}
				// attributes head
				int attrId = data[curIdx];
				int attrLen = ((data[curIdx + 1])&0xFF) | (0xFF&(data[curIdx + 2] << 8));
				curIdx += 3;
				if (data.length < curIdx + attrLen) {
					return;
				}
				String val = new String(data, curIdx, attrLen);// 从字节数组中获取string
				if (attrId == NotificationAttributeIDTitle) { // title属性
					noti.title = val;
				} else if (attrId == NotificationAttributeIDMessage) {// message属性
					noti.message = val;
				} else if (attrId == NotificationAttributeIDDate) { // date属性
					noti.date = val;
				} else if (attrId == NotificationAttributeIDSubtitle) {
					noti.subtitle = val;
				} else if (attrId == NotificationAttributeIDMessageSize) {
					noti.messageSize = val;
				}
				curIdx += attrLen;
			}
			IOSNotification.log("got a notification! data size = "+data.length);
			mCurData = null;
//			mHandler.sendEmptyMessage(MSG_DO_NOTIFICATION); // continue next!
			sendNotification(noti);
		}
	}


	private void processNotificationList() {
		mHandler.removeMessages(MSG_DO_NOTIFICATION);
		// handle curData!
		if (mCurData == null) {
			if (mPendingNotifcations.size() == 0) {
				return;
			}
			/* 首次必定到这里，从 list中取出第一个元素赋予 mCurData
			 * */
			mCurData = mPendingNotifcations.remove(0);
			IOSNotification.log("ANCS New CurData");
		} else if (mCurData.curStep == 0) { // parse notify data
			/* 第二次到这里，处理data */
			do {
				if (mCurData.notifyData == null
						|| mCurData.notifyData.length != 8) {
					mCurData = null; // ignore
					//不合格的ANCS的NS数据，
					IOSNotification.logw("ANCS Bad Head!");
					break;
				}
				if(EventIDNotificationRemoved ==mCurData.notifyData[0]){
					//是remove，就取消一个notification
					int uid=(mCurData.notifyData[4]&0xff) |
							(mCurData.notifyData[5]&0xff<<8)|
							(mCurData.notifyData[6]&0xff<<16)|
							(mCurData.notifyData[7]&0xff<<24);
					cancelNotification(uid);
					mCurData = null;
					break;
				}
				if (EventIDNotificationAdded != mCurData.notifyData[0]) {
					//若不是add的通知，先不处理
					mCurData = null; // ignore
					IOSNotification.logw("ANCS NOT Add!");
					break;
				}
				// get attribute if needed!
				BluetoothGattCharacteristic cha2 = mService	//获取DS
						.getCharacteristic(GattConstant.Apple.sUUIDDataSource);
				BluetoothGattCharacteristic cha = mService	//获取CP
						.getCharacteristic(GattConstant.Apple.sUUIDControl);
				if (null != cha && null != cha2) {
					ByteArrayOutputStream bout = new ByteArrayOutputStream();
					// 组装 请求通知属性 的数据
					// command ，commandID， 固定为0，
					bout.write((byte) 0); 
					// notify id ， 通知的UID
					bout.write(mCurData.notifyData[4]);
					bout.write(mCurData.notifyData[5]);
					bout.write(mCurData.notifyData[6]);
					bout.write(mCurData.notifyData[7]);

					// title，  通知的属性1： title
					bout.write(NotificationAttributeIDTitle);
					bout.write(50);	// 后跟 2个字节的内容，表示 请求此属性的最大长度，
					bout.write(0);	//
					// subtitle
					bout.write(NotificationAttributeIDSubtitle);
					bout.write(100);
					bout.write(0);

					// message 
					bout.write(NotificationAttributeIDMessage);
					bout.write(500);
					bout.write(0);

					// message size
					bout.write(NotificationAttributeIDMessageSize);
					bout.write(10);
					bout.write(0);
					// date 
					bout.write(NotificationAttributeIDDate);
					bout.write(10);
					bout.write(0);

					byte[] data = bout.toByteArray();

					cha.setValue(data);// 设置data到characteristic

					IOSNotification.log("ANCS 请求 成功？ =  "
							+ mGatt.writeCharacteristic(cha));//发起 请求
					mCurData.curStep = 1;	//	状态(步骤设置为1)
					mCurData.bout = new ByteArrayOutputStream();
					mCurData.timeExpired = System.currentTimeMillis() + TIMEOUT;

					mHandler.removeMessages(MSG_CHECK_TIME);
					mHandler.sendEmptyMessageDelayed(MSG_CHECK_TIME, TIMEOUT);
					return;
				} else {
					IOSNotification.logw("ANCS No Control & DS!");
					// has no control!// just vibrate ...
					mCurData.bout = null;
					mCurData.curStep = 1;
				}

			} while (false);
		} else if (mCurData.curStep == 1) {
			// check if finished!	
//			mCurData.finish();
			return;
		} else {
			return;
		}
		mHandler.sendEmptyMessage(MSG_DO_NOTIFICATION); // do next step
	}

	/** 收到来自DS端的字节数组，在这里处理<br>
	 *  即NC端请求通知属性，iphone的ANCS的回复数据<br>
	 *  data符合 ANCS的规范<br>
	 *  format of a response to a Get Notification Attributes command
	 * */
	public void onDSNotification(byte[] data) {
		if (mCurData == null) {
			IOSNotification.logw("got ds notify without cur data");
			return;
		}
		try {
			mHandler.removeMessages(MSG_FINISH);
			mCurData.bout.write(data);
			mHandler.sendEmptyMessageDelayed(MSG_FINISH, FINISH_DELAY);
		} catch (IOException e) {
			IOSNotification.loge(e.toString());
		}
	}

	void onWrite(BluetoothGattCharacteristic characteristic, int status) {
		if (status != BluetoothGatt.GATT_SUCCESS) {
			IOSNotification.log("write err: " + status);
			mHandler.sendEmptyMessage(MSG_ERR);
		} else {
			IOSNotification.log("write OK");
			mHandler.sendEmptyMessage(MSG_DO_NOTIFICATION);
		}
	}
	/** 收到来自NS端的字节数组，在这里处理<br>
	 *  即iphone的ANCS的通知中心 添加、删除或更新了一个通知<br>
	 *  data应为8个字节数组，符合ANCS的规范
	 *  format of GATT notifications delivered through the Notification Source characteristic
	 *  */
	public void onNotification(byte[] data) {
		if (data == null || data.length != 8) {
			IOSNotification.loge("bad ANCS notification data");
			return;
		}logD(data);
		Message msg = mHandler.obtainMessage(MSG_ADD_NOTIFICATION);
		msg.obj = data;
		msg.sendToTarget();
	}

	public void reset() {
		mHandler.sendEmptyMessage(MSG_RESET);
	}
	
	void logD(byte[] d){
		StringBuffer sb=new StringBuffer();
		int len = d.length;
		for(int i=0;i<len;i++){
			sb.append(d[i]+", ");
		}
		IOSNotification.log("log Data size["+len+"] : "+sb);
	}
	
}
