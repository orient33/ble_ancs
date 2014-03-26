package jz.ancs.parse;
/**
 * a notice from iPhone ANCS<br>
 * */
public class IOSNotification {
	/** the unique identifier (UID) for the iOS notification
	 */
	public int uid;
	/** title for the iOS notification*/
	public String title;
	/** subtitle  for the iOS notification*/
	public String subtitle;
	/** message(content) for the iOS notification*/
	public String message;
	/** size (how many byte) of message*/
	public String messageSize;
	/** the time  for the iOS notification */
	public String date;
	public IOSNotification(){}
	public IOSNotification(String t, String s, String m, String ms, String d) {
		title = t;
		subtitle = s;
		message = m;
		messageSize = ms;
		date = d;
	}

	boolean isAllInit(){
		return title != null && subtitle != null && message != null
				&& messageSize != null && date != null;
	}
	
	static void log(String s){
		android.util.Log.i("ble",s);
	} 
	static void logw(String s){
		android.util.Log.w("ble",s);
	} 
	static void loge(String s){
		android.util.Log.e("ble",s);
	} 
}
