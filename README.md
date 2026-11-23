# JobRadar

AI-powered dashboard that connects to your Outlook inbox (and spam folder), finds every email about your job search, and organizes it into **interviews**, **applications** and **job openings**.

## Tech Stack

- **Frontend:** Angular 21 + SCSS
- **Backend:** Java 25 + Spring Boot 4.2.0 + Maven
- **Auth:** Microsoft OAuth2 (Microsoft Graph API)
- **AI:** Claude API (`claude-haiku-5-5`) via the official `anthropic-java` SDK

## How it works

```
Angular (localhost:4200)                          Spring Boot (localhost:8080)
┌───────────────────────┐   POST /api/sync     ┌───────────────────────────────────────────────────┐
│ Dashboard             │ ───────────────────▶ │ JobTrackerService.sync()                          │
│  • progress bar       │                      │  1. Graph: Inbox + Junk since last sync           │
│  • Interviews         │ ◀── returns fast ─── │     (first sync: last 3 months, all pages)        │
│  • Applications       │                      │  2. queue emails not analyzed before ("pending")  │
│  • Job openings       │                      │  ── background (5 in parallel) ─────────────────  │
│                       │  GET /api/dashboard  │  3. Claude Haiku classifies each pending email    │
│  polls every 2s while │ ───────────────────▶ │  4. store result, update progress                 │
│  sync.running = true  │ ◀── JobDashboard ─── │  5. group into interviews/applications/jobs       │
└───────────────────────┘                      └───────────────────────────────────────────────────┘
```

1. You sign in with Microsoft (see [Login flow](#login-flow-microsoft-oauth2)).
2. The dashboard loads `GET /api/dashboard` (instant, no Outlook/AI calls). If you've never synced, it immediately calls `POST /api/sync`.
3. **Sync** reads emails from **Inbox** and **Junk (spam)** via Microsoft Graph, as plain text, following every page:
   - **first sync:** everything received in the **last 3 months**;
   - **every later sync:** only emails received **since the previous sync** (minus a 10-minute overlap for late deliveries).
4. New emails go into a per-user **pending** queue and `POST /api/sync` returns immediately. The analysis then runs **in the background**, 5 emails at a time.
5. Each pending email is sent to Claude, which returns structured JSON (`EmailAnalysis`): category, company, role, interview date, next step, summary, and the list of jobs for job alerts.
6. While `sync.running` is true, the dashboard polls `GET /api/dashboard` every 2 seconds, showing a progress bar ("Analyzing emails… 312 of 540") and filling in as results arrive.
7. If an email fails (e.g. rate limit after 5 retries), it **stays pending** and is retried on the next sync — even though that sync won't fetch it from Outlook again.
8. The backend organizes everything into a `JobDashboard`:
   - **Interviews** — every `INTERVIEW` email, newest first.
   - **Applications** — all application emails for the same *company + role* collapsed into one row, with the **latest** status (Applied → In progress → Interview → Offer / Rejected). Clicking a row opens its **timeline**: a stage bar showing how far the application got (stages reached without their own email are drawn dashed; a rejection ends the bar in red) and every email behind it, oldest first.
   - **Job openings** — every job from job alerts / recruiter emails, de-duplicated by company + role.
   - Anything found in spam is tagged **Spam**.

### Auto-sync

While the dashboard is open, it calls `POST /api/sync` every **15 minutes** (skipped if a sync is already running). Since later syncs only read new emails, this is cheap. The **Auto-sync** checkbox turns it off; the choice is remembered in the browser (`localStorage`).

It runs in the browser rather than as a server-side scheduler on purpose: the Microsoft token lives in the browser session, and all state is in memory anyway, so a server job would only work while you're logged in.

### Manual corrections

Claude can get things wrong, so every analysis can be fixed by hand (`PATCH /api/emails/{id}`):

- **Applications → expand a row → Edit** on any email: change its **category**, **company** or **role**.
  - Category **Not job related** hides it everywhere.
  - Giving two applications the **same company + role merges them** into one row (that's how grouping works).
- **Interviews / Job openings → Hide**: marks that email as not job related (for an alert, this hides every job from that email).
- Corrected emails show an **Edited** tag. They're never re-analyzed, so a later sync won't undo a fix.
- Emails Claude marked as *not job related* aren't shown anywhere, so a missed application can't be fixed from the UI yet.

### Email categories (decided by Claude)

| Category | Meaning | Shown in |
|---|---|---|
| `JOB_OPENING` | Job alerts (LinkedIn, Indeed…), recruiter outreach, lists of open positions | Job openings |
| `APPLICATION_CONFIRMATION` | "We received your application" | Applications (Applied) |
| `APPLICATION_UPDATE` | In review, online test, request for documents | Applications (In progress) |
| `INTERVIEW` | Interview invite / scheduling / confirmation | Interviews + Applications |
| `OFFER` | Job offer | Applications (Offer) |
| `REJECTION` | Not moving forward | Applications (Rejected) |
| `NOT_JOB_RELATED` | Everything else | hidden |

The prompt lives in `ClaudeEmailAnalyzer.SYSTEM_PROMPT`, and the field descriptions in `EmailAnalysis` / `JobOpening` (`@JsonPropertyDescription`) are also instructions to the model. Email content is treated as untrusted data (the prompt tells Claude never to follow instructions inside emails — relevant for spam).

## Project Structure

```
JobRadar/
├── frontend/                          # Angular app
│   └── src/
│       ├── environments/environment.ts        # backendUrl / apiUrl
│       ├── styles.scss                        # global styles + CSS variables
│       └── app/
│           ├── app.config.ts                  # router + HttpClient + interceptor
│           ├── app.routes.ts                  # /login, '' → Dashboard (guarded)
│           ├── app.html / app.scss            # header (user + sign out) + <router-outlet>
│           ├── models/
│           │   ├── job-dashboard.ts           # JobDashboard, Interview, Application, JobOpening
│           │   └── user.ts                    # { name, email }
│           ├── services/
│           │   ├── job-tracker.service.ts     # GET /api/dashboard, POST /api/sync
│           │   └── auth.service.ts            # login / logout / current user
│           ├── guards/auth.guard.ts           # protects routes, redirects to /login
│           ├── interceptors/credentials.interceptor.ts  # sends session cookie
│           └── components/
│               ├── login/                     # "Sign in with Microsoft"
│               ├── dashboard/                 # 3 sections, Sync + Auto-sync, progress bar, Hide buttons
│               └── application-history/       # expanded row: stage bar + email timeline + Edit form
│                   └── application-stages.ts  # history → Applied/In progress/Interview/Offer states
│
└── backend/                           # Spring Boot API
    ├── .env.example                   # template for secrets (copy to .env)
    └── src/main/java/com/jobrador/backend/
        ├── config/
        │   ├── SecurityConfig.java    # OAuth2 login, /api/** protected, CORS
        │   ├── GraphClientConfig.java # RestClient for Graph with the user's token (auto-refresh)
        │   ├── AnthropicConfig.java   # AnthropicClient bean (API key from .env, 5 retries)
        │   └── AnalysisExecutorConfig.java  # thread pool for background analysis (5 parallel)
        ├── client/
        │   ├── MicrosoftGraphClient.java  # reads a folder (Inbox / Junk) since a date, all pages
        │   ├── GraphMessages.java         # Graph JSON response shape
        │   └── ClaudeEmailAnalyzer.java   # prompt + structured output → EmailAnalysis
        ├── service/
        │   └── JobTrackerService.java # sync (fetch → queue → background analysis) + build dashboard
        ├── repository/
        │   └── AnalyzedEmailRepository.java  # in-memory per user: analyses, pending queue, last sync, progress
        ├── controller/
        │   ├── JobTrackerController.java     # GET /api/dashboard, POST /api/sync, PATCH /api/emails/{id}
        │   └── UserController.java           # GET /api/me
        └── model/
            ├── Email.java, MailFolder.java   # raw email from Graph
            ├── EmailAnalysis.java, EmailCategory.java, JobOpening.java  # Claude's output
            ├── AnalyzedEmail.java            # email metadata + analysis + corrected flag (stored)
            ├── EmailCorrection.java          # manual fix: category / company / role
            ├── JobDashboard.java             # organized response for the frontend
            ├── SyncStatus.java               # { running, total, analyzed, failed }
            └── UserInfo.java
```

## Getting Started

### Prerequisites
- Node.js 24+
- Java 25
- Maven 3.10+
- Angular CLI 21
- An Anthropic API key ([console.anthropic.com](https://console.anthropic.com) → API Keys)

### One-time setup: secrets

```bash
cd backend
cp .env.example .env     # then edit .env
```

```
MS_CLIENT_SECRET=...     # Azure → Entra ID → Registos de aplicações → JobRadar → Certificados e segredos ("Valor")
ANTHROPIC_API_KEY=...    # console.anthropic.com → API Keys
```

`backend/.env` is gitignored and loaded automatically by Spring (`spring.config.import`). If a value is missing, the backend refuses to start with `Could not resolve placeholder '...'`.

The Microsoft **client ID** is already in `application.properties`. The app registration ("JobRadar", personal Microsoft accounts only, scopes `User.Read Mail.Read offline_access openid profile email`) lives in the personal Azure tenant `vicentemartinsoutlook.onmicrosoft.com`.

### Run both apps (two terminals)

```bash
# terminal 1 — backend on http://localhost:8080 (restart it after every backend change)
cd backend
mvnw spring-boot:run
```

```bash
# terminal 2 — frontend on http://localhost:4200 (reloads automatically)
cd frontend
npm install
ng serve
```

Open `http://localhost:4200` → **Sign in with Microsoft** → the first sync starts automatically and reads the last 3 months. Depending on how many emails you have it can take several minutes; the progress bar shows how far it is, and results appear as they're analyzed.

### Run the tests

```bash
cd backend && mvnw test      # 28 tests
cd frontend && ng test       # 21 tests
```

Tests use fake secrets and mocks — they never call Microsoft or Claude, and cost nothing.

## API

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/dashboard` | Organized view from emails analyzed so far + sync progress — instant |
| `POST` | `/api/sync` | Fetch new emails from Inbox + Junk and start analyzing them in the background. Returns after fetching; poll `/api/dashboard` while `sync.running`. Calling it during a running sync does nothing |
| `PATCH` | `/api/emails/{emailId}` | Manual correction `{ "category"?, "company"?, "role"? }` (omitted = unchanged). Returns the updated dashboard; 404 if the email isn't yours / not analyzed |
| `GET` | `/api/me` | Logged-in user `{ name, email }` — 401 if not logged in |
| `POST` | `/api/logout` | Ends the session |
| `GET` | `/oauth2/authorization/microsoft` | Starts the Microsoft login (browser navigation) |

All `/api/**` endpoints require login (401 otherwise).

`JobDashboard` response:

```json
{
  "interviews":   [{ "company": "Farfetch", "role": "Frontend Developer", "interviewAt": "2026-11-26T14:00",
                     "nextStep": "Prepare system design", "summary": "...", "receivedAt": "...", "folder": "JUNK" }],
  "applications": [{ "company": "Talkdesk", "role": "Java Engineer", "status": "APPLICATION_CONFIRMATION",
                     "firstSeenAt": "...", "lastUpdateAt": "...", "nextStep": "", "summary": "...",
                     "emailCount": 2, "foundInJunk": false,
                     "timeline": [{ "at": "...", "status": "APPLICATION_CONFIRMATION", "summary": "...",
                                    "emailSubject": "Thanks for applying", "folder": "INBOX" },
                                  { "at": "...", "status": "INTERVIEW", "summary": "...",
                                    "emailSubject": "Interview invitation", "folder": "JUNK" }] }],
  "openings":     [{ "company": "OutSystems", "role": "Full Stack Dev", "location": "Remote",
                     "url": "https://...", "receivedAt": "...", "folder": "INBOX", "emailSubject": "..." }],
  "lastSyncAt": "2026-11-23T19:40:00Z",
  "emailsAnalyzed": 87,
  "sync": { "running": true, "total": 540, "analyzed": 311, "failed": 1 }
}
```

## Login flow (Microsoft OAuth2)

The backend does the whole OAuth2 dance; Angular never sees a token. The browser just holds a session cookie (`JSESSIONID`) for `localhost:8080`.

```
1. Angular route '' → authGuard → GET /api/me
      401 → redirect to /login page
2. "Sign in with Microsoft" → browser goes to http://localhost:8080/oauth2/authorization/microsoft
3. Spring redirects to login.microsoftonline.com/consumers/... (login + consent)
4. Microsoft redirects back to http://localhost:8080/login/oauth2/code/microsoft?code=...
5. Spring exchanges the code for tokens, creates the session, redirects to http://localhost:4200/
6. authGuard → GET /api/me (cookie sent) → 200 → dashboard
```

- The Microsoft access token (`Mail.Read`) stays in the backend session. `GraphClientConfig` adds it to every Graph call and refreshes it automatically (`offline_access`).
- Cookies reach the backend because `credentialsInterceptor` sets `withCredentials: true`, and CORS allows credentials from `localhost:4200`.

## Configuration

| File | Setting | Purpose |
|---|---|---|
| `backend/src/main/resources/application.properties` | `app.frontend-url` | Redirect after login; allowed CORS origin |
| | `spring.security.oauth2.client.*` | Microsoft client ID, scopes, `consumers` endpoints |
| | `anthropic.model=claude-haiku-5-5` | Model used to classify emails |
| `backend/.env` (not committed) | `MS_CLIENT_SECRET`, `ANTHROPIC_API_KEY` | Secrets |
| `JobTrackerService` | `FIRST_SYNC_PERIOD = 3 months` | How far back the first sync reads |
| `JobTrackerService` | `SYNC_OVERLAP = 10 min` | Later syncs re-read this much before the previous sync |
| `MicrosoftGraphClient` | `MAX_EMAILS_PER_FOLDER = 2000` | Safety cap per folder per sync |
| `AnalysisExecutorConfig` | `PARALLEL_ANALYSES = 5` | Emails analyzed at the same time (raise if your API rate limit allows) |
| `AnthropicConfig` | `maxRetries(5)` | Automatic retries with backoff on rate limits / overload |
| `ClaudeEmailAnalyzer` | `MAX_BODY_CHARS = 15000` | Long emails are cut after this many characters (job alerts can be huge) |
| `frontend/src/environments/environment.ts` | `backendUrl` / `apiUrl` | Where the frontend sends requests |

## Important notes

- **Cost:** each email is analyzed once. With Haiku 5.5 ($0.10 / 1M input tokens) a 3-month first sync of ~250 emails costs roughly 5–8 cents; later syncs only pay for new emails.
- **Storage is in memory.** Restarting the backend forgets everything (analyses, pending queue, last-sync date) and logs you out — the next sync is a full 3-month sync again.
- **Outlook.com empties Junk automatically** (after ~10 days), so older spam can't be read.
- **Failed analyses stay pending** and are retried on the next sync. If Claude declines an email (`refusal`), it's stored as not job related so it isn't retried forever.
- **Only personal Microsoft accounts** (outlook.com / hotmail / live) can log in.
- **CSRF is disabled.** The session cookie is `SameSite=Lax`, acceptable on localhost; `TODO` in `SecurityConfig` before deploying.

## Progress

### Done
- [x] Spring Boot + Angular base, CORS
- [x] Azure tenant + Entra ID app registration (personal accounts, `Mail.Read`)
- [x] Microsoft OAuth2 login (session-based), `/api/me`, `/api/logout`, Angular login page + guard
- [x] Read Outlook **Inbox and Junk** via Microsoft Graph (plain-text bodies, auto token refresh)
- [x] AI classification with Claude Haiku (structured output) into 7 categories
- [x] Auto-extract company, role, interview date, next step, summary, and job listings
- [x] Dashboard: Interviews, Applications (grouped, latest status), Job openings (de-duplicated), Spam tags
- [x] Per-user cache so each email is analyzed only once
- [x] First sync reads the last 3 months (all pages); later syncs only read new emails
- [x] Background analysis with live progress bar; failed emails retried on next sync
- [x] Application timeline: stage bar (Applied → In progress → Interview → Offer / Rejected) + email history
- [x] Manual corrections: edit category / company / role per email, merge applications, hide false positives
- [x] Auto-sync every 15 min while the dashboard is open (can be turned off)

### Planned
- [ ] Persistence (database) instead of in-memory storage
- [ ] Follow-up alerts (e.g. no reply after 2 weeks)
- [ ] "Hidden emails" view to recover emails wrongly marked as not job related
