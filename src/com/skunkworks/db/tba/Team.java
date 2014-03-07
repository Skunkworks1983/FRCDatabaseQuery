package com.skunkworks.db.tba;

import gvjava.org.json.JSONArray;
import gvjava.org.json.JSONException;
import gvjava.org.json.JSONObject;
import gvjava.org.json.JSONTokener;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class Team {
	private static final String API_PATH = "http://www.thebluealliance.com/api/v1/teams/show?teams=";
	private static final File CACHE_PATH = new File("db/team-cache");
	private static boolean fetchedTeams = false;
	private static Map<String, Team> teamsByName = new HashMap<String, Team>();
	private static Map<String, Team> teamsByKey = new HashMap<String, Team>();
	private static Map<String, Team> teamsByNick = new HashMap<String, Team>();

	private static void checkTeam(String team) {
		if (!CACHE_PATH.isDirectory()) {
			CACHE_PATH.mkdirs();
		}
		File cache = new File(CACHE_PATH, team);
		try {
			if (teamsByKey.containsKey(team)) {
				return;
			}
			URL url;
			if (new File(cache.getAbsolutePath().concat(".blank")).exists()) {
				return;
			}
			if (cache.isFile()) {
				url = cache.toURL();
			} else {
				url = new URL(API_PATH + team + "&X-TBA-App-Id="
						+ BlueAllianceAPI.APP_ID);
				System.out.println("Loading " + team
						+ " from the blue alliance.");
			}
			JSONArray root = new JSONArray(new JSONTokener(
					new InputStreamReader(url.openStream())));
			if (root.length() == 0) {
				if (cache.isFile()) {
					cache.delete();
				} else {
					new File(cache.getAbsolutePath().concat(".blank"))
							.createNewFile();
				}
				throw new Exception("Null");
			}
			for (int i = 0; i < root.length(); i++) {
				Team obj = new Team(root.getJSONObject(i));
				teamsByName.put(obj.getName().toLowerCase().trim(), obj);
				teamsByNick.put(obj.getNickname().toLowerCase().trim(), obj);
				teamsByKey.put(obj.getKey(), obj);
			}
			if (!cache.isFile()) {
				try {
					cache.createNewFile();
					FileWriter w = new FileWriter(cache);
					root.write(w);
					w.close();
				} catch (Exception e) {
					cache.delete();
				}
			}
		} catch (Exception e) {
			if (e.toString().contains("500")) {
				try {
					new File(cache.getAbsolutePath().concat(".blank"))
							.createNewFile();
				} catch (IOException e1) {
				}
			}
		}
	}

	public static void checkAPI() {
		if (!fetchedTeams) {
			for (int team = 0; team < 6000; team++) { // This is sketchy...
				try {
					checkTeam("frc" + team);
				} catch (Exception e) {
				}
			}
			fetchedTeams = true;
		}
	}

	public static Team getTeamByName(String name) {
		checkAPI();
		return teamsByName.get(name.toLowerCase());
	}

	public static Team getTeamByNick(String id) {
		checkAPI();
		return teamsByNick.get(id);
	}

	public static Team getTeamByKey(String number) {
		try {
			checkTeam(number);
		} catch (Exception e) {
		}
		return teamsByKey.get(number);
	}

	public static Set<Team> getTeamByNameContains(String name) {
		checkAPI();
		Set<Team> teams = new HashSet<Team>();
		for (Entry<String, Team> evt : teamsByName.entrySet()) {
			if (evt.getKey().contains(name.toLowerCase())) {
				teams.add(evt.getValue());
			}
		}
		return teams;
	}

	public static Set<Team> getTeamByNickContains(String name) {
		checkAPI();
		Set<Team> teams = new HashSet<Team>();
		for (Entry<String, Team> evt : teamsByNick.entrySet()) {
			if (evt.getKey().contains(name.toLowerCase())) {
				teams.add(evt.getValue());
			}
		}
		return teams;
	}

	private final String name;
	private final int number;
	private final String locality;
	private final String region;
	private final String location;
	private final String[] events;
	private final String country;
	private final String nick;
	private final String website;
	private final String key;

	private final JSONObject back;

	public Team(JSONObject jsonObject) throws JSONException {
		this.back = jsonObject;
		this.name = back.getString("name");
		this.number = back.getInt("team_number");
		this.website = back.getString("website");
		this.locality = back.getString("locality");
		this.location = back.getString("location");
		this.region = back.getString("region");
		this.key = back.getString("key");
		this.nick = back.getString("nickname");
		this.country = back.getString("country_name");
		JSONArray events = back.getJSONArray("events");
		this.events = new String[events.length()];
		for (int i = 0; i < this.events.length; i++) {
			this.events[i] = events.getString(i);
		}
	}

	public String getName() {
		return name;
	}

	public String getNickname() {
		return nick;
	}

	public int getNumber() {
		return number;
	}

	public String getLocality() {
		return locality;
	}

	public String getRegion() {
		return region;
	}

	public String getLocation() {
		return location;
	}

	public String[] getEvents() {
		return events;
	}

	public String getCountry() {
		return country;
	}

	public String getWebsite() {
		return website;
	}

	public String getKey() {
		return key;
	}

	public int hashCode() {
		return key.hashCode();
	}

	public boolean equals(Object o) {
		if (o instanceof Team) {
			return ((Team) o).key.equalsIgnoreCase(key);
		}
		return false;
	}
}