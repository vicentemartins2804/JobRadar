import { DatePipe, TitleCasePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';

import { JobApplication } from '../../models/job-application';
import { JobApplicationService } from '../../services/job-application.service';

@Component({
  selector: 'app-application-list',
  imports: [DatePipe, TitleCasePipe],
  templateUrl: './application-list.html',
  styleUrl: './application-list.scss',
})
export class ApplicationList implements OnInit {
  private readonly service = inject(JobApplicationService);

  protected readonly applications = signal<JobApplication[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.service.getAll().subscribe({
      next: (apps) => {
        this.applications.set(apps);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Could not reach the backend. Is it running on port 8080?');
        this.loading.set(false);
      },
    });
  }
}
