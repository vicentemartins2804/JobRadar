import { Application, ApplicationStatus } from '../../models/job-dashboard';
import { applicationStages } from './application-stages';

function app(status: ApplicationStatus, history: ApplicationStatus[]): Application {
  return {
    company: 'Farfetch',
    role: 'Dev',
    status,
    firstSeenAt: '',
    lastUpdateAt: '',
    nextStep: '',
    summary: '',
    emailCount: history.length,
    foundInJunk: false,
    timeline: history.map((s, i) => ({
      emailId: String(i),
      at: '',
      status: s,
      company: 'Farfetch',
      role: 'Dev',
      summary: '',
      emailSubject: '',
      folder: 'INBOX',
      corrected: false,
    })),
  };
}

const states = (a: Application) => applicationStages(a).map((s) => `${s.label}:${s.state}`);

describe('applicationStages', () => {
  it('marks the latest stage as current and later ones as todo', () => {
    expect(states(app('APPLICATION_UPDATE', ['APPLICATION_CONFIRMATION', 'APPLICATION_UPDATE']))).toEqual([
      'Applied:done',
      'In progress:current',
      'Interview:todo',
      'Offer:todo',
    ]);
  });

  it('marks stages without an email as skipped', () => {
    expect(states(app('INTERVIEW', ['INTERVIEW']))).toEqual([
      'Applied:skipped',
      'In progress:skipped',
      'Interview:current',
      'Offer:todo',
    ]);
  });

  it('ends the pipeline at the rejection', () => {
    expect(states(app('REJECTION', ['APPLICATION_CONFIRMATION', 'INTERVIEW', 'REJECTION']))).toEqual([
      'Applied:done',
      'In progress:skipped',
      'Interview:done',
      'Rejected:rejected',
    ]);
  });

  it('handles a rejection with no earlier emails', () => {
    expect(states(app('REJECTION', ['REJECTION']))).toEqual(['Rejected:rejected']);
  });
});
