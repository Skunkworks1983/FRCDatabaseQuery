package com.skunkworks.db.tba;

import gvjava.org.json.JSONArray;
import gvjava.org.json.JSONException;
import gvjava.org.json.JSONObject;
import gvjava.org.json.JSONTokener;

import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.Writer;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import com.skunkworks.util.Filter;

public class Event {
	private static final String API_PATH = "http://www.thebluealliance.com/api/v1/events/list?year=2014";
	private static final String API_DETAILS_PATH = "http://www.thebluealliance.com/api/v1/event/details?event=";
	private static final File DETAILS_CACHE = new File("db/event-details-cache");
	private static JSONArray api_object;
	private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat(
			"yyyy-MM-dd HH:mm:ss");
	private static Map<String, Event> eventsByName = new HashMap<String, Event>();
	private static Map<String, Event> eventsByShortName = new HashMap<String, Event>();
	private static Map<String, Event> eventsByKey = new HashMap<String, Event>();

	public static void checkEventDetails() {
		checkAPI();
		for (Event e : eventsByName.values()) {
			e.updateDetails();
		}
	}

	public static void checkAPI() {
		if (api_object == null) {
			try {
				URL url = new URL(API_PATH + "&X-TBA-App-Id="
						+ BlueAllianceAPI.APP_ID);
				api_object = new JSONArray(new JSONTokener(
						new InputStreamReader(url.openStream())));
				for (int i = 0; i < api_object.length(); i++) {
					Event obj = new Event(api_object.getJSONObject(i));
					eventsByName.put(obj.getName().toLowerCase().trim(), obj);
					eventsByKey.put(obj.getKey().toLowerCase().trim(), obj);
					eventsByShortName.put(obj.getShortName().toLowerCase()
							.trim(), obj);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	public static Event getEventByName(String name) {
		checkAPI();
		return eventsByName.get(name.toLowerCase());
	}

	public static Event getEventByShortName(String key) {
		checkAPI();
		return eventsByShortName.get(key.toLowerCase());
	}

	public static Event getEventByAPIName(String name) {
		checkAPI();
		return eventsByKey.get(name.toLowerCase());
	}

	public static Event getEventByNameContains(String name) {
		checkAPI();
		for (Entry<String, Event> evt : eventsByName.entrySet()) {
			if (evt.getKey().contains(name.toLowerCase())) {
				return evt.getValue();
			}
		}
		return null;
	}

	public static Iterator<Event> iterator() {
		checkAPI();
		return eventsByName.values().iterator();
	}

	public static Filter<Event> createDateFilter(final Date d) {
		return new Filter<Event>() {
			public boolean accept(Event e) {
				if (e.getStart() == null || e.getEnd() == null) {
					return false;
				}
				return d.getTime() >= e.getStart().getTime()
						&& d.getTime() <= e.getEnd().getTime();
			}
		};
	}

	public static Filter<Event> createTeamFilter(final String... teams) {
		return new Filter<Event>() {
			public boolean accept(Event e) {
				for (String team : teams) {
					for (String other : e.getTeams()) {
						if (team.equalsIgnoreCase(other)) {
							return true;
						}
					}
				}
				return false;
			}
		};
	}

	public static Event getUpcomingEvent(Iterator<Event> scan) {
		Event best = null;
		long bestEnd = Long.MAX_VALUE;
		while (scan.hasNext()) {
			Event e = scan.next();
			if (e.getEnd() != null
					&& e.getEnd().getTime() > System.currentTimeMillis()
					&& e.getEnd().getTime() < bestEnd) {
				best = e;
				bestEnd = e.getEnd().getTime();
			}
		}
		return best;
	}

	private final String shortName;
	private final String name;
	private final String key;
	private final JSONObject back;
	private Date startDate;
	private Date endDate;
	// private EventRankings eventRankings;
	private String[] teams = new String[0];
	private String[] matches = new String[0];
	private Date updateTime = new Date();
	private JSONObject details;

	private Event(JSONObject jsonObject) throws JSONException {
		this.back = jsonObject;
		this.shortName = back.getString("short_name");
		this.name = back.getString("name");
		this.key = back.getString("key");
		try {
			this.startDate = DATE_FORMAT.parse(back.getString("start_date")
					.replace('T', ' '));
			this.endDate = DATE_FORMAT.parse(back.getString("end_date")
					.replace('T', ' '));
			this.endDate.setTime(this.endDate.getTime() + (1000 * 60 * 60 * 24)
					- 1); // The very end of the day
		} catch (Exception e) {
			e.printStackTrace();
			this.startDate = null;
			this.endDate = null;
		}
		// this.eventRankings = new EventRankings(key);
		// this.eventRankings.updateInformation();
	}

	public Date getStart() {
		return startDate;
	}

	public Date getEnd() {
		return endDate;
	}

	public String getName() {
		return name;
	}

	public String getKey() {
		return key;
	}

	public String getShortName() {
		return shortName;
	}

	@SuppressWarnings("deprecation")
	public void updateDetails() {
		try {
			URL url;
			if (!DETAILS_CACHE.isDirectory()) {
				DETAILS_CACHE.mkdirs();
			}
			File cache = new File(DETAILS_CACHE, getKey());
			if (cache.isFile()) {
				url = cache.toURL();
			} else {
				url = new URL(API_DETAILS_PATH + getKey() + "&X-TBA-App-Id="
						+ BlueAllianceAPI.APP_ID);
				System.out.println("Loading event " + getKey() + " from TBA");
			}
			details = new JSONObject(new JSONTokener(new InputStreamReader(
					url.openStream())));
			JSONArray matches = details.getJSONArray("matches");
			JSONArray teams = details.getJSONArray("teams");
			String[] tempMatches = new String[matches.length()];
			String[] tempTeams = new String[teams.length()];
			for (int i = 0; i < tempTeams.length; i++) {
				tempTeams[i] = teams.getString(i);
			}
			for (int i = 0; i < tempMatches.length; i++) {
				tempMatches[i] = matches.getString(i);
			}
			this.teams = tempTeams;
			this.matches = tempMatches;
			updateTime.setTime(System.currentTimeMillis());
			if (!cache.isFile()/* && getEnd().before(new Date()) */) {
				try {
					Writer w = new FileWriter(cache);
					details.write(w);
					w.close();
				} catch (Exception e) {
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public String[] getTeams() {
		if (details == null) {
			updateDetails();
		}
		return teams;
	}

	public String[] getMatches() {
		if (details == null) {
			updateDetails();
		}
		return matches;
	}
}