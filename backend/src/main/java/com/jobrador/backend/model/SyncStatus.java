package com.jobrador.backend.model;

/**
 * Progress of the background analysis.
 *
 * @param running  true while emails are still being analyzed
 * @param total    emails queued for analysis in the current/last sync
 * @param analyzed how many of them are done
 * @param failed   how many failed (they stay pending and are retried on the next sync)
 */
public record SyncStatus(boolean running, int total, int analyzed, int failed) {

	public static final SyncStatus IDLE = new SyncStatus(false, 0, 0, 0);

}
