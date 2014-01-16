package jz.ance.parse;

import java.util.UUID;

public class GattConstant {
	public static final UUID CHARACTERISTIC_UPDATE_NOTIFICATION_DESCRIPTOR_UUID = UUID
			.fromString("00002902-0000-1000-8000-00805f9b34fb");

	public static class Apple {
		/** iphone ANCS service UUID */
		public static final UUID sUUIDANCService = UUID
				.fromString("7905F431-B5CE-4E99-A40F-4B1E122D00D0");
		/** Notification Source: UUID */
		public static final UUID sUUIDChaNotify = UUID
				.fromString("9FBF120D-6301-42D9-8C58-25E699A21DBD");
		/** Control Point: UUID */
		public static final UUID sUUIDControl = UUID
				.fromString("69D1D8F3-45E1-49A8-9821-9BBDFDAAD9D9");
		/** Data Source: UUID */
		public static final UUID sUUIDDataSource = UUID
				.fromString("22EAC6E9-24D6-4BB5-BE44-B36ACE7C7BFB");
	}
}
