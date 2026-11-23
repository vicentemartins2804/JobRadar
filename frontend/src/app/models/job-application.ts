export type ApplicationStatus = 'APPLIED' | 'SCREENING' | 'INTERVIEW' | 'OFFER' | 'REJECTED';

export interface JobApplication {
  id: number;
  company: string;
  role: string;
  status: ApplicationStatus;
  appliedDate: string;
  lastUpdate: string;
  nextStep: string | null;
}
