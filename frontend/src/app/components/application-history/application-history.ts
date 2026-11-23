import { DatePipe } from '@angular/common';
import { Component, computed, inject, input, output, signal } from '@angular/core';

import {
  Application,
  ApplicationStatus,
  EmailCategory,
  JobDashboard,
  TimelineEvent,
} from '../../models/job-dashboard';
import { JobTrackerService } from '../../services/job-tracker.service';
import { CATEGORY_OPTIONS, STATUS_LABELS, applicationStages } from './application-stages';

/**
 * Progress bar (Applied → In progress → Interview → Offer) plus the list of emails behind it.
 * Each email can be corrected by hand: category, company and role.
 */
@Component({
  selector: 'app-application-history',
  imports: [DatePipe],
  templateUrl: './application-history.html',
  styleUrl: './application-history.scss',
})
export class ApplicationHistory {
  private readonly service = inject(JobTrackerService);

  readonly app = input.required<Application>();
  /** Emits the updated dashboard after a correction is saved. */
  readonly changed = output<JobDashboard>();

  protected readonly stages = computed(() => applicationStages(this.app()));
  protected readonly categoryOptions = CATEGORY_OPTIONS;

  /** emailId of the event being edited, or null. */
  protected readonly editing = signal<string | null>(null);
  protected readonly draftCategory = signal<EmailCategory>('APPLICATION_CONFIRMATION');
  protected readonly draftCompany = signal('');
  protected readonly draftRole = signal('');
  protected readonly saving = signal(false);
  protected readonly saveError = signal(false);

  protected statusLabel(status: ApplicationStatus): string {
    return STATUS_LABELS[status];
  }

  protected edit(event: TimelineEvent): void {
    this.editing.set(event.emailId);
    this.draftCategory.set(event.status);
    this.draftCompany.set(event.company);
    this.draftRole.set(event.role);
    this.saveError.set(false);
  }

  protected cancel(): void {
    this.editing.set(null);
  }

  protected save(emailId: string): void {
    this.saving.set(true);
    this.saveError.set(false);
    this.service
      .correctEmail(emailId, {
        category: this.draftCategory(),
        company: this.draftCompany(),
        role: this.draftRole(),
      })
      .subscribe({
        next: (dashboard) => {
          this.saving.set(false);
          this.editing.set(null);
          this.changed.emit(dashboard);
        },
        error: () => {
          this.saving.set(false);
          this.saveError.set(true);
        },
      });
  }

  protected inputValue(event: Event): string {
    return (event.target as HTMLInputElement | HTMLSelectElement).value;
  }
}
