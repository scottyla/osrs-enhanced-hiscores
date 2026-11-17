package com.osrsenhancedhiscores;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Sends submissions to the hiscores server one at a time, in order.
 * <p>
 * Requests are sequential so the auth token returned by the first request for an account
 * is saved before the next request for that account goes out.
 * <p>
 * Failed requests stay at the head of the queue and are retried on the next {@link #pump()}.
 */
@Slf4j
@Singleton
class HiscoresClient
{
	static final String TOKEN_KEY = "token";
	private static final String TOKEN_HEADER = "X-Hiscores-Token";
	private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
	private static final int MAX_QUEUED = 50;

	private final OkHttpClient okHttpClient;
	private final Gson gson;
	private final ConfigManager configManager;
	private final OsrsEnhancedHiscoresConfig config;

	private final Deque<Submission> queue = new ArrayDeque<>();
	private Call inFlight;
	/** Incremented on reset so callbacks from cancelled requests are ignored. */
	private int generation;
	/** Problems the player should see once in the log, not every 30 seconds. */
	private boolean warnedBadUrl;
	private volatile boolean warnedRejected;

	@Inject
	HiscoresClient(OkHttpClient okHttpClient, Gson gson, ConfigManager configManager, OsrsEnhancedHiscoresConfig config)
	{
		this.okHttpClient = okHttpClient;
		this.gson = gson;
		this.configManager = configManager;
		this.config = config;
	}

	synchronized void enqueue(Submission submission)
	{
		if (queue.size() >= MAX_QUEUED)
		{
			log.debug("Submission queue full, dropping oldest submission");
			queue.pollFirst();
		}
		queue.addLast(submission);
	}

	/** Start sending the next queued submission if nothing is in flight. Serializes JSON, so call it off the client thread. */
	synchronized void pump()
	{
		if (inFlight != null || queue.isEmpty())
		{
			return;
		}

		HttpUrl base = serverUrl(config.serverUrl());
		if (base == null)
		{
			if (!warnedBadUrl)
			{
				log.warn("Invalid hiscores server URL: {}", config.serverUrl());
				warnedBadUrl = true;
			}
			queue.clear();
			return;
		}

		Submission submission = queue.peekFirst();
		HttpUrl url = base.newBuilder().addPathSegments("api/v1/submit").build();
		Request.Builder request = new Request.Builder()
			.url(url)
			.post(RequestBody.create(JSON, gson.toJson(submission)));

		String token = configManager.getConfiguration(OsrsEnhancedHiscoresConfig.GROUP, submission.getProfileKey(), TOKEN_KEY);
		if (token != null)
		{
			request.header(TOKEN_HEADER, token);
		}

		final int gen = generation;
		inFlight = okHttpClient.newCall(request.build());
		inFlight.enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.debug("Hiscores submission failed, will retry", e);
				finish(gen, false, false);
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (response)
				{
					handleResponse(submission, response);
					int code = response.code();
					boolean retry = code == 429 || code >= 500;
					finish(gen, !retry, !retry);
				}
			}
		});
	}

	/** The configured server, or the OSRS Enhanced Hiscores website when the setting is blank. */
	static HttpUrl serverUrl(String configured)
	{
		String url = configured == null ? "" : configured.trim();
		return HttpUrl.parse(url.isEmpty() ? OsrsEnhancedHiscoresConfig.DEFAULT_SERVER_URL : url);
	}

	private void handleResponse(Submission submission, Response response)
	{
		log.debug("Submitted {} [{} {}]: {} sources, {} drops, {} clog items, stats={} -> HTTP {}",
			submission.getUsername(), submission.getProfileType(), submission.getGameMode(),
			submission.getLootSources().size(), submission.getDrops().size(),
			submission.getCollectionLogItems().size(), submission.getStats() != null, response.code());

		if (!response.isSuccessful())
		{
			if (response.code() == 403 && !warnedRejected)
			{
				log.warn("Hiscores server rejected submissions for {}: token mismatch", submission.getUsername());
				warnedRejected = true;
			}
			else
			{
				log.debug("Hiscores submission failed with HTTP {}", response.code());
			}
			return;
		}

		ResponseBody body = response.body();
		if (body == null)
		{
			return;
		}

		try
		{
			SubmitResponse parsed = gson.fromJson(body.charStream(), SubmitResponse.class);
			if (parsed != null && parsed.token != null && !parsed.token.isEmpty())
			{
				configManager.setConfiguration(OsrsEnhancedHiscoresConfig.GROUP, submission.getProfileKey(), TOKEN_KEY, parsed.token);
			}
		}
		catch (JsonParseException e)
		{
			log.debug("Unable to parse hiscores response", e);
		}
	}

	private synchronized void finish(int gen, boolean removeHead, boolean sendNext)
	{
		if (gen != generation)
		{
			return;
		}

		inFlight = null;
		if (removeHead)
		{
			queue.pollFirst();
		}
		if (sendNext)
		{
			pump();
		}
	}

	/** Drop everything queued and cancel any request in flight. */
	synchronized void reset()
	{
		generation++;
		warnedBadUrl = false;
		warnedRejected = false;
		queue.clear();
		if (inFlight != null)
		{
			inFlight.cancel();
			inFlight = null;
		}
	}

	private static class SubmitResponse
	{
		String token;
	}
}
