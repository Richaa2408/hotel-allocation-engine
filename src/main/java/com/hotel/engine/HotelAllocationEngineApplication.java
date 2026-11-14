package com.hotel.engine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

import java.util.Map;
import java.util.TimeZone;

@SpringBootApplication
@ConfigurationPropertiesScan
public class HotelAllocationEngineApplication {

	/**
	 * Windows reports some time zones by legacy names that PostgreSQL rejects when the JDBC driver sends
	 * them ("invalid value for parameter TimeZone"). Map them to their current names before connecting.
	 */
	private static final Map<String, String> LEGACY_ZONES = Map.of(
			"Asia/Calcutta", "Asia/Kolkata",
			"Asia/Katmandu", "Asia/Kathmandu",
			"Asia/Saigon", "Asia/Ho_Chi_Minh",
			"Asia/Rangoon", "Asia/Yangon");

	public static void main(String[] args) {
		String zone = TimeZone.getDefault().getID();
		if (LEGACY_ZONES.containsKey(zone)) {
			TimeZone.setDefault(TimeZone.getTimeZone(LEGACY_ZONES.get(zone)));
		}
		SpringApplication.run(HotelAllocationEngineApplication.class, args);
	}

}
