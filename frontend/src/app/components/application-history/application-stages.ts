import { Application, ApplicationStatus, EmailCategory } from '../../models/job-dashboard';

export const STATUS_LABELS: Record<ApplicationStatus, string> = {
  APPLICATION_CONFIRMATION: 'Applied',
  APPLICATION_UPDATE: 'In progress',
  INTERVIEW: 'Interview',
  OFFER: 'Offer',
  REJECTION: 'Rejected',
};

/** Options for the "change category" dropdown in manual corrections. */
export const CATEGORY_OPTIONS: { value: EmailCategory; label: string }[] = [
  ...(Object.entries(STATUS_LABELS) as [ApplicationStatus, string][]).map(([value, label]) => ({ value, label })),
  { value: 'JOB_OPENING', label: 'Job opening' },
  { value: 'NOT_JOB_RELATED', label: 'Not job related (hide)' },
];

/**
 * - done: the application went through this stage (an email says so)
 * - skipped: a later stage was reached without an email for this one (e.g. straight to interview)
 * - current: the latest stage
 * - todo: not reached yet
 * - rejected: the application ended here
 */
export type StageState = 'done' | 'skipped' | 'current' | 'todo' | 'rejected';

export interface Stage {
  label: string;
  state: StageState;
}

const PIPELINE: { status: ApplicationStatus; label: string }[] = [
  { status: 'APPLICATION_CONFIRMATION', label: 'Applied' },
  { status: 'APPLICATION_UPDATE', label: 'In progress' },
  { status: 'INTERVIEW', label: 'Interview' },
  { status: 'OFFER', label: 'Offer' },
];

/** Turns an application's history into the Applied → In progress → Interview → Offer progress bar. */
export function applicationStages(app: Application): Stage[] {
  const visited = new Set(app.timeline.map((e) => e.status));
  const reached = Math.max(-1, ...PIPELINE.map((s, i) => (visited.has(s.status) ? i : -1)));

  if (app.status === 'REJECTION') {
    return [
      ...PIPELINE.slice(0, reached + 1).map(({ status, label }) => ({
        label,
        state: (visited.has(status) ? 'done' : 'skipped') as StageState,
      })),
      { label: 'Rejected', state: 'rejected' },
    ];
  }

  return PIPELINE.map(({ status, label }, i) => ({
    label,
    state: i === reached ? 'current' : i > reached ? 'todo' : visited.has(status) ? 'done' : 'skipped',
  }));
}
