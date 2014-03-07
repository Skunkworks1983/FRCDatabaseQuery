package com.skunkworks.db.tba;


public class BlueAllianceAPI {
	public static String APP_ID = "frc1983:text-api:v2014";

	public static void preload() {
		System.out.println("Preloading Event Information...");
		Event.checkAPI();
		System.out.println("Preloading Event Details...");
		Event.checkEventDetails();
		System.out.println("Preloading Team Information...");
		Team.checkAPI();
		System.out.println("Checked teams");
	}
}
