package com.skunkworks.db.tba;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.skunkworks.util.HttpUtils;

public class Match implements Comparable<Match> {
	private static final String API_RESULT_PATH = "http://www2.usfirst.org/${YEAR}comp/events/${EVENT_CODE}/matchresults.html";
	private static Map<String, Match> matchesByKey = new HashMap<String, Match>();
	public static final SimpleDateFormat SCHEDULE_DATE_FORMAT = new SimpleDateFormat(
			"hh:mm a");
	private static Map<String, Long> updateTimes = new HashMap<String, Long>();
	private static Map<String, Set<String>> matchesByEvent = new HashMap<String, Set<String>>();

	private static final long MATCH_EXPIRY_TIME = 1000 * 60 * 2;

	private static Match parseTeamRow(String event, String[] chunks)
			throws Exception {
		event = event.toLowerCase();
		String matchID;
		int set = -1;
		int subset = -1;
		int offset = 2;
		int chunkLength = chunks.length;
		for (int i = 0; i < chunks.length; i++) {
			if (chunks[i] == null) {
				chunkLength = i;
				break;
			}
		}

		CompetitionLevel level = CompetitionLevel.Qualifiers;
		// Try to determine matchID
		if (chunkLength == 11 || chunkLength == 9) {
			// We, my good friend, are in elims
			String desc = chunks[1].toLowerCase();
			for (CompetitionLevel l : CompetitionLevel.values()) {
				Matcher m = l.pattern.matcher(desc.trim());
				if (m.matches()) {
					try {
						level = l;
						set = Integer.valueOf(m.group(1));
						subset = Integer.valueOf(m.group(2));
						break;
					} catch (Exception e) {
					}
				}
			}
			// We now have a matchID
			matchID = event
					+ "_"
					+ (level == CompetitionLevel.Finals ? "f"
							: (level == CompetitionLevel.QuarterFinals ? "qf"
									: "sf")) + chunks[2];
			offset = 3;
		} else {
			// Quals
			matchID = event + "_qm" + chunks[1];
		}
		matchID = matchID.trim().toLowerCase();

		Match match = matchesByKey.get(matchID);
		Set<String> mSet = matchesByEvent.get(event);
		if (mSet == null) {
			mSet = new TreeSet<String>();
			matchesByEvent.put(event.toLowerCase(), mSet);
		}
		mSet.add(matchID);
		if (match == null) {
			match = new Match(matchID);
			matchesByKey.put(matchID.toLowerCase(), match);
		}
		if (set != -1) {
			match.setNumber = set;
		}
		if (subset != -1) {
			match.matchNumber = subset;
		} else {
			match.matchNumber = Integer.valueOf(chunks[1]);
		}
		match.competitionLevel = level.tag;
		match.redAlliance = new String[3];
		match.blueAlliance = new String[3];
		match.teams = new String[match.redAlliance.length
				+ match.blueAlliance.length];
		for (int i = 0; i < 3; i++) {
			match.redAlliance[i] = "frc" + chunks[offset + i].trim();
			match.blueAlliance[i] = "frc" + chunks[offset + i + 3].trim();
		}
		System.arraycopy(match.redAlliance, 0, match.teams, 0,
				match.redAlliance.length);
		System.arraycopy(match.blueAlliance, 0, match.teams,
				match.redAlliance.length, match.blueAlliance.length);
		if (chunkLength > offset + 6 && chunks[offset + 6].trim().length() > 0
				&& chunks[offset + 7].trim().length() > 0) {
			match.redScore = Integer.valueOf(chunks[offset + 6]);
			match.blueScore = Integer.valueOf(chunks[offset + 7]);
		} else {
			match.redScore = -1;
			match.blueScore = -1;
		}
		return match;
	}

	private static void updateMatches(String event, String sURL)
			throws IOException {
		String page = HttpUtils.makeAPIRequest(sURL, null).toLowerCase();
		int currentRow = 0;
		long lastMatch = 0;
		while ((currentRow = page.indexOf(
				"<tr style=\"background-color:#ffffff;\"", currentRow + 1)) != -1) {
			// Now that we have a row, let us find the rest and hope to
			// god
			// it's formatted right
			currentRow = page.indexOf('<', currentRow + 1);
			String[] chunks = new String[11];
			for (int i = 0; i < chunks.length; i++) {
				int nextTR = page.indexOf("<tr", currentRow);
				int closeBracket = page.indexOf('>', currentRow);
				int openBracket = page.indexOf('<', closeBracket);
				if (closeBracket != -1 && openBracket != -1
						&& nextTR > openBracket) {
					// Now we can append this junk
					chunks[i] = page.substring(closeBracket + 1, openBracket)
							.trim();
				} else {
					break;
				}
				currentRow = page.indexOf('<', openBracket + 1);
			}
			try {
				Match m = parseTeamRow(event, chunks);
				m.rawMatchTime = chunks[0].trim();
				long time = SCHEDULE_DATE_FORMAT.parse(chunks[0].trim())
						.getTime()
						+ Calendar.getInstance().getTimeZone().getRawOffset();
				// If we are in elims add a day. Just cause that is how
				// it works.
				if (m.getCompetitionLevelID() != CompetitionLevel.Qualifiers) {
					time += (1000 * 60 * 60 * 24);
				}
				// And if we passed over the night add a day
				if (lastMatch > time) {
					time += (1000 * 60 * 60 * 24);
				}
				lastMatch = time;
				m.matchTime = time;
			} catch (Exception e) {
			}
		}
	}

	private static void checkAPI(String event) {
		Long l = updateTimes.get(event);
		if (l == null || l + MATCH_EXPIRY_TIME < System.currentTimeMillis()) {
			try {
				String sURL = CompetitionLevel.Qualifiers.schedule.replace(
						"${EVENT_CODE}", event.toLowerCase().substring(4))
						.replace("${YEAR}", event.substring(0, 4));
				updateMatches(event, sURL);
				sURL = CompetitionLevel.QuarterFinals.schedule.replace(
						"${EVENT_CODE}", event.toLowerCase().substring(4))
						.replace("${YEAR}", event.substring(0, 4));
				updateMatches(event, sURL);
				sURL = API_RESULT_PATH.replace("${EVENT_CODE}",
						event.toLowerCase().substring(4)).replace("${YEAR}",
						event.substring(0, 4));
				updateMatches(event, sURL);
			} catch (Exception e) {
				e.printStackTrace();
			}
			updateTimes.put(event, System.currentTimeMillis());
		}
	}

	public static Match getMatchByKey(String s) {
		Match m = matchesByKey.get(s);
		if (m == null) {
			m = new Match(s);
			matchesByKey.put(s, m);
		}
		m.checkInformation();
		return m;
	}

	public static Set<String> getMatchesByEvent(String s) {
		checkAPI(s);
		Set<String> m = matchesByEvent.get(s.toLowerCase());
		if (m == null) {
			m = new HashSet<String>();
			matchesByEvent.put(s, m);
		}
		return m;
	}

	private final String match;
	private String[] blueAlliance = new String[0], redAlliance = new String[0];
	private String[] teams = new String[0];
	private String rawMatchTime = null;

	private int blueScore = -1, redScore = -1;
	private int setNumber;
	private String competitionLevel;
	private int matchNumber;
	private String event;
	private long matchTime = -1;

	private Match(final String match) {
		this.match = match;
		this.event = match.substring(0, match.indexOf('_'));
	}

	public String getOriginalTime() {
		return rawMatchTime;
	}

	public void checkInformation() {
		checkAPI(event);
	}

	public long getRawMatchTime() {
		return matchTime;
	}

	public Date getCookedMatchTime() {
		if (matchTime > 0) {
			Event e = Event.getEventByAPIName(getEvent());
			if (e != null && e.getStart() != null) {
				return new Date(e.getStart().getTime() + (1000 * 60 * 60 * 24)
						+ matchTime);
			}
		}
		return null;
	}

	public boolean isPlayed() {
		return redScore >= 0 && blueScore >= 0;
	}

	public String[] getBlueAlliance() {
		return blueAlliance;
	}

	public int getBlueScore() {
		return blueScore;
	}

	public String getMatch() {
		return match;
	}

	public String[] getRedAlliance() {
		return redAlliance;
	}

	public int getRedScore() {
		return redScore;
	}

	public int getSetNumber() {
		return setNumber;
	}

	public String getCompetitionLevel() {
		return competitionLevel;
	}

	public int getMatchNumber() {
		return matchNumber;
	}

	public String getEvent() {
		return event;
	}

	public String[] getTeams() {
		return teams;
	}

	public CompetitionLevel getCompetitionLevelID() {
		return CompetitionLevel.parse(competitionLevel);
	}

	public static enum CompetitionLevel {
		Qualifiers(
				"qual",
				"http://www2.usfirst.org/${YEAR}comp/events/${EVENT_CODE}/schedulequal.html",
				"${MATCH}"), QuarterFinals(
				"quart",
				"http://www2.usfirst.org/${YEAR}comp/events/${EVENT_CODE}/scheduleelim.html",
				"qtr ${SET}-${MATCH}"), SemiFinals(
				"semi",
				"http://www2.usfirst.org/${YEAR}comp/events/${EVENT_CODE}/scheduleelim.html",
				"semi ${SET}-${MATCH}"), Finals(
				"final",
				"http://www2.usfirst.org/${YEAR}comp/events/${EVENT_CODE}/scheduleelim.html",
				"final ${SET}-${MATCH}");
		private final String tag;
		private final String schedule;
		private final String fmt;
		private final Pattern pattern;

		private CompetitionLevel(String tag, String schd, String fmt) {
			this.tag = tag;
			this.schedule = schd;
			this.fmt = fmt;
			this.pattern = Pattern.compile(fmt.replace("${SET}", "([0-9]+)")
					.replace("${MATCH}", "([0-9]+)"));
		}

		public static CompetitionLevel parse(String s) {
			for (CompetitionLevel l : values()) {
				if (s.toLowerCase().startsWith(l.tag.toLowerCase())) {
					return l;
				}
			}
			return null;
		}
	}

	public int hashCode() {
		int id = getMatchNumber() + (50 * getSetNumber())
				+ (500 * getCompetitionLevelID().ordinal());
		return new Integer(id).hashCode();
	}

	public boolean equals(Object o) {
		if (o instanceof Match) {
			Match e = (Match) o;
			int id = getMatchNumber() + (500 * getSetNumber())
					+ (50000 * getCompetitionLevelID().ordinal());
			int eid = e.getMatchNumber() + (500 * e.getSetNumber())
					+ (50000 * e.getCompetitionLevelID().ordinal());
			return id == eid;
		}
		return false;
	}

	public int compareTo(Match e) {
		/*
		 * if (getCookedMatchTime() != null && e.getCookedMatchTime() != null) {
		 * return getCookedMatchTime().compareTo(e.getCookedMatchTime()); } else
		 * {
		 */
		int id = getMatchNumber() + (500 * getSetNumber())
				+ (50000 * getCompetitionLevelID().ordinal());
		int eid = e.getMatchNumber() + (500 * e.getSetNumber())
				+ (50000 * e.getCompetitionLevelID().ordinal());
		return new Integer(id).compareTo(eid);
		// }
	}

	public String toString() {
		return getOriginalTime() + "\t" + getCompetitionLevel() + ":"
				+ getSetNumber() + "," + getMatchNumber();
	}
}
