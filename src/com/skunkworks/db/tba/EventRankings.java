package com.skunkworks.db.tba;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.skunkworks.util.HttpUtils;

public class EventRankings {
	private static final String API_PATH = "http://www2.usfirst.org/2014comp/Events/${EVENT_CODE}/rankings.html";
	private final String event;
	private long updateTime;
	private Map<Integer, Rank> ranksByRank = new HashMap<Integer, Rank>();
	private Map<Integer, Rank> ranksByTeam = new HashMap<Integer, Rank>();
	private static final long RANKING_EXPIRY_TIME = 1000 * 60 * 2;

	private static Map<String, EventRankings> rankingsByKey = new HashMap<String, EventRankings>();

	public static EventRankings getEventRankingsByKey(String s) {
		EventRankings m = rankingsByKey.get(s);
		if (m == null) {
			m = new EventRankings(s);
			rankingsByKey.put(s, m);
		}
		m.checkInformation();
		return m;
	}

	private EventRankings(String event) {
		this.event = event;
	}

	public String getEvent() {
		return event;
	}

	public Rank getRankByRank(int rank) {
		return ranksByRank.get(rank);
	}

	public Rank getRankByTeam(int team) {
		return ranksByTeam.get(team);
	}

	public void checkInformation() {
		if (updateTime + RANKING_EXPIRY_TIME < System.currentTimeMillis()) {
			updateInformation();
		}
	}

	public void updateInformation() {
		try {
			String page = HttpUtils
					.makeAPIRequest(
							API_PATH.replace("${EVENT_CODE}",
									event.substring(4)), null).toLowerCase();
			int currentRow = 0;
			main: while ((currentRow = page.indexOf(
					"<tr style=\"background-color:#ffffff;\"", currentRow + 1)) != -1) {
				// Now that we have a row, let us find the rest and hope to god
				// it's formatted right
				currentRow = page.indexOf('<', currentRow + 1);
				String[] chunks = new String[10];
				for (int i = 0; i < chunks.length; i++) {
					int closeBracket = page.indexOf('>', currentRow);
					int openBracket = page.indexOf('<', closeBracket);
					if (closeBracket != -1 && openBracket != -1) {
						// Now we can append this junk
						chunks[i] = page.substring(closeBracket + 1,
								openBracket).trim();
					} else {
						continue main;
					}
					currentRow = page.indexOf('<', openBracket + 1);
				}

				Rank r = new Rank(chunks);
				ranksByTeam.put(r.getTeam(), r);
				ranksByRank.put(r.getRank(), r);
			}
			updateTime = System.currentTimeMillis();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static class Rank {
		private final int wins, losses, ties, played;
		private final int rank;
		private final int team;
		private final float qScore, autoScore, assistScore, teleScore, tcScore;
		private final int dq;

		/*
		 * Rank Team QS AP CP TP Record (W-L-T) DQ Played
		 */
		private Rank(String[] back) throws NumberFormatException, IOException {
			this.rank = Integer.valueOf(back[0]);
			played = Integer.valueOf(back[9]);
			team = Integer.valueOf(back[1]);
			tcScore = Float.valueOf(back[5]);
			qScore = Float.valueOf(back[2]);
			teleScore = Float.valueOf(back[6]);
			dq = Integer.valueOf(back[8]);
			assistScore = Float.valueOf(back[3]);
			autoScore = Float.valueOf(back[4]);

			String[] record = back[7].split("-");
			if (record.length != 3) {
				throw new IOException("Bad record value! " + back[6]);
			}
			wins = Integer.valueOf(record[0]);
			losses = Integer.valueOf(record[1]);
			ties = Integer.valueOf(record[2]);
			if (wins + losses + ties != played) {
				System.err.println("Sketchy stuff...");
			}
		}

		public int getWins() {
			return wins;
		}

		public int getLosses() {
			return losses;
		}

		public int getTies() {
			return ties;
		}

		public int getRank() {
			return rank;
		}

		public int getPlayed() {
			return played;
		}

		public int getTeam() {
			return team;
		}

		public float getQualificationScore() {
			return qScore;
		}

		public float getAutoScore() {
			return autoScore;
		}
		
		public float getTCScore() {
			return tcScore;
		}

		public float getAssistScore() {
			return assistScore;
		}

		public float getTeleScore() {
			return teleScore;
		}

		public int getDisQualified() {
			return dq;
		}

		public String toString() {
			return "Rank: " + rank + ", Team: " + team + ", QS: " + qScore
					+ " AP: " + autoScore + " ASSIST: " + assistScore + " T/C: " + tcScore + " TP: "
					+ teleScore + " Wins/Losses/Ties: " + wins + "/" + losses
					+ "/" + ties;
		}
	}
}
