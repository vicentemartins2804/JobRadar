import { DatePipe } from '@angular/common';
import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { interval, switchMap, takeWhile, timer } from 'rxjs';

import { Application, ApplicationStatus, JobDashboard } from '../../models/job-dashboard';
import { JobTrackerService } from '../../services/job-tracker.service';
import { ApplicationHistory } from '../application-history/application-history';
import { STATUS_LABELS } from '../application-history/application-stages';

/** How often the dashboard refreshes while emails are being analyzed. */
export const POLL_INTERVAL_MS = 2000;

/** How often to check Outlook for new emails while the page is open (when auto-sync is on). */
export const AUTO_SYNC_INTERVAL_MS = 15 * 60 * 1000;

export const AUTO_SYNC_STORAGE_KEY = 'jobradar.autoSync';

/** Auto-sync is on unless the user turned it off. */
function readAutoSyncSetting(): boolean {
  try {
    return localStorage.getItem(AUTO_SYNC_STORAGE_KEY) !== 'false';
  } catch {
    return true;
  }
}

@Component({
  selector: 'app-dashboard',
  imports: [DatePipe, ApplicationHistory],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class Dashboard implements OnInit {
  private readonly service = inject(JobTrackerService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly data = signal<JobDashboard | null>(null);
  protected readonly loading = signal(true);
  /** True while POST /sync is reading emails from Outlook. */
  protected readonly fetching = signal(false);
  protected readonly error = signal<string | null>(null);
  private readonly expanded = signal<ReadonlySet<string>>(new Set());
  protected readonly autoSync = signal(readAutoSyncSetting());

  protected readonly analyzing = computed(() => this.data()?.sync.running ?? false);
  protected readonly busy = computed(() => this.fetching() || this.analyzing());
  protected readonly progressPercent = computed(() => {
    const sync = this.data()?.sync;
    return sync && sync.total > 0 ? Math.round(((sync.analyzed + sync.failed) / sync.total) * 100) : 0;
  });

  ngOnInit(): void {
    this.startAutoSync();
    this.service.getDashboard().subscribe({
      next: (data) => {
        this.data.set(data);
        this.loading.set(false);
        if (data.sync.running) {
          this.pollUntilDone();
        } else if (!data.lastSyncAt) {
          // First visit: nothing analyzed yet, so read the mailbox straight away
          this.sync();
        }
      },
      error: () => {
        this.error.set('Could not reach the backend.');
        this.loading.set(false);
      },
    });
  }

  protected sync(): void {
    this.fetching.set(true);
    this.error.set(null);
    this.service.sync().subscribe({
      next: (data) => {
        this.data.set(data);
        this.fetching.set(false);
        if (data.sync.running) {
          this.pollUntilDone();
        }
      },
      error: () => {
        this.error.set('Sync failed. Check the backend logs and try again.');
        this.fetching.set(false);
      },
    });
  }

  /** Refreshes the dashboard every few seconds until the background analysis finishes. */
  private pollUntilDone(): void {
    timer(POLL_INTERVAL_MS, POLL_INTERVAL_MS)
      .pipe(
        switchMap(() => this.service.getDashboard()),
        takeWhile((data) => data.sync.running, true),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (data) => this.data.set(data),
        error: () => this.error.set('Lost connection to the backend while syncing.'),
      });
  }

  /** Manual correction: marks an email as not job related, which removes it from every section. */
  protected hideEmail(emailId: string): void {
    this.service.correctEmail(emailId, { category: 'NOT_JOB_RELATED' }).subscribe({
      next: (data) => this.data.set(data),
      error: () => this.error.set('Could not hide that email, try again.'),
    });
  }

  protected setAutoSync(enabled: boolean): void {
    this.autoSync.set(enabled);
    try {
      localStorage.setItem(AUTO_SYNC_STORAGE_KEY, String(enabled));
    } catch {
      // Storage unavailable (private mode): the setting just isn't remembered
    }
  }

  /** While the page is open, sync every few minutes (later syncs only read new emails, so it's cheap). */
  private startAutoSync(): void {
    interval(AUTO_SYNC_INTERVAL_MS)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        if (this.autoSync() && !this.busy() && !this.loading()) {
          this.sync();
        }
      });
  }

  protected statusLabel(status: ApplicationStatus): string {
    return STATUS_LABELS[status];
  }

  /** Keyed by company + role so rows stay open while the dashboard refreshes during a sync. */
  protected isExpanded(app: Application): boolean {
    return this.expanded().has(this.key(app));
  }

  protected toggle(app: Application): void {
    const key = this.key(app);
    this.expanded.update((open) => {
      const next = new Set(open);
      if (!next.delete(key)) {
        next.add(key);
      }
      return next;
    });
  }

  private key(app: Application): string {
    return `${app.company}|${app.role}`.toLowerCase();
  }

  /** Only render real web links coming from emails. */
  protected isWebLink(url: string): boolean {
    return /^https?:\/\//i.test(url);
  }
}
