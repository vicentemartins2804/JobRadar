export type MailFolder = 'INBOX' | 'JUNK';

export type ApplicationStatus =
  | 'APPLICATION_CONFIRMATION'
  | 'APPLICATION_UPDATE'
  | 'INTERVIEW'
  | 'OFFER'
  | 'REJECTION';

/** Every category Claude (or a manual correction) can assign to an email. */
export type EmailCategory = ApplicationStatus | 'JOB_OPENING' | 'NOT_JOB_RELATED';

/** Manual fix for one email; omitted fields stay unchanged. */
export interface EmailCorrection {
  category?: EmailCategory;
  company?: string;
  role?: string;
}

export interface JobOpening {
  emailId: string;
  company: string;
  role: string;
  location: string;
  url: string;
  receivedAt: string;
  folder: MailFolder;
  emailSubject: string;
}

export interface Application {
  company: string;
  role: string;
  status: ApplicationStatus;
  firstSeenAt: string;
  lastUpdateAt: string;
  nextStep: string;
  summary: string;
  emailCount: number;
  foundInJunk: boolean;
  /** Every email about this application, oldest first. */
  timeline: TimelineEvent[];
}

export interface TimelineEvent {
  emailId: string;
  at: string;
  status: ApplicationStatus;
  /** This email's own company/role (what a correction edits). */
  company: string;
  role: string;
  summary: string;
  emailSubject: string;
  folder: MailFolder;
  /** True once fixed by hand. */
  corrected: boolean;
}

export interface Interview {
  emailId: string;
  company: string;
  role: string;
  interviewAt: string;
  nextStep: string;
  summary: string;
  receivedAt: string;
  folder: MailFolder;
}

export interface SyncStatus {
  running: boolean;
  total: number;
  analyzed: number;
  /** Failed emails are retried on the next sync. */
  failed: number;
}

export interface JobDashboard {
  openings: JobOpening[];
  applications: Application[];
  interviews: Interview[];
  lastSyncAt: string | null;
  emailsAnalyzed: number;
  sync: SyncStatus;
}
