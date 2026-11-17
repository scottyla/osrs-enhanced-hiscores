package com.osrsenhancedhiscores;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.QuestState;
import static net.runelite.api.QuestState.FINISHED;
import static net.runelite.api.QuestState.IN_PROGRESS;
import static net.runelite.api.QuestState.NOT_STARTED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Before;
import org.junit.Test;

public class QuestTrackerTest
{
	private static final int COOKS = 1;
	private static final int DRAGON = 2;
	private static final Map<Integer, String> NAMES = Map.of(COOKS, "Cook's Assistant", DRAGON, "Dragon Slayer I");

	private QuestTracker tracker;

	@Before
	public void setUp()
	{
		// No client/config needed: update() and onTick() are pure.
		tracker = new QuestTracker(null, null, null);
		tracker.reset("profile", new HashMap<>(), 0);
	}

	private static Map<Integer, QuestState> states(QuestState cooks, QuestState dragon)
	{
		Map<Integer, QuestState> m = new HashMap<>();
		m.put(COOKS, cooks);
		m.put(DRAGON, dragon);
		return m;
	}

	private void play(long from, long to)
	{
		for (long t = from; t <= to; t += 600)
		{
			tracker.onTick(t);
		}
	}

	@Test
	public void firstCheckSendsEveryQuestWithoutTiming()
	{
		tracker.update(states(FINISHED, IN_PROGRESS), NAMES, 1_000);
		List<Submission.QuestProgress> sent = tracker.drain();
		assertEquals(2, sent.size());
		for (Submission.QuestProgress q : sent)
		{
			// Already finished / started before we were watching: no times are known.
			assertNull(q.getStartedAt());
			assertNull(q.getCompletedAt());
			assertNull(q.getPlayedMs());
		}
		tracker.update(states(FINISHED, IN_PROGRESS), NAMES, 2_000);
		assertEquals("nothing changed", 0, tracker.drain().size());
	}

	@Test
	public void timesAQuestStartedAndFinishedWhileTracking()
	{
		play(0, 60_000);
		tracker.update(states(NOT_STARTED, NOT_STARTED), NAMES, 60_000);
		tracker.drain();

		play(60_600, 120_000);
		tracker.update(states(IN_PROGRESS, NOT_STARTED), NAMES, 120_000);
		Submission.QuestProgress started = tracker.drain().get(0);
		assertEquals("IN_PROGRESS", started.getState());
		// Start is taken as the last check where it hadn't started.
		assertEquals(Long.valueOf(60_000), started.getStartedAt());

		// Logged out for an hour: counts as elapsed but not played.
		tracker.onTick(3_720_000);
		play(3_720_600, 3_840_000);
		tracker.update(states(FINISHED, NOT_STARTED), NAMES, 3_840_000);
		Submission.QuestProgress done = tracker.drain().get(0);
		assertEquals("FINISHED", done.getState());
		assertEquals(Long.valueOf(3_840_000), done.getCompletedAt());
		assertEquals(Long.valueOf(3_780_000), done.getElapsedMs());
		// 1 minute played before logging out + 2 minutes after.
		assertEquals(Long.valueOf(180_000), done.getPlayedMs());
	}

	@Test
	public void finishingAQuestStartedBeforeTrackingHasCompletionButNoDuration()
	{
		tracker.update(states(IN_PROGRESS, NOT_STARTED), NAMES, 1_000);
		tracker.drain();
		tracker.update(states(FINISHED, NOT_STARTED), NAMES, 5_000);
		Submission.QuestProgress done = tracker.drain().get(0);
		assertEquals(Long.valueOf(5_000), done.getCompletedAt());
		assertNull(done.getStartedAt());
		assertNull(done.getPlayedMs());
	}

	@Test
	public void startTimesSurviveARestart()
	{
		// Simulate a client restart: saved starts and played time are restored, states are re-read.
		Map<Integer, QuestTracker.QuestStart> saved = new HashMap<>();
		saved.put(DRAGON, new QuestTracker.QuestStart(10_000, 0));
		tracker.reset("profile", saved, 50_000);
		tracker.update(states(NOT_STARTED, IN_PROGRESS), NAMES, 100_000);
		assertEquals(Long.valueOf(10_000), tracker.drain().stream()
			.filter(q -> q.getId() == DRAGON).findFirst().get().getStartedAt());

		tracker.onTick(100_000);
		tracker.onTick(102_000);
		tracker.update(states(NOT_STARTED, FINISHED), NAMES, 102_000);
		Submission.QuestProgress done = tracker.drain().get(0);
		assertEquals(Long.valueOf(10_000), done.getStartedAt());
		assertEquals(Long.valueOf(92_000), done.getElapsedMs());
		assertEquals(Long.valueOf(52_000), done.getPlayedMs());
	}
}
