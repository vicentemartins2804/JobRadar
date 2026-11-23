# JobRadar

AI-powered dashboard that connects to your Outlook inbox and automatically extracts job application information — company, role, status, dates, and next steps.

## Tech Stack

- **Frontend:** Angular 21 + SCSS
- **Backend:** Java 25 + Spring Boot 4.2.0 + Maven
- **Auth:** Microsoft OAuth2 (Microsoft Graph API)
- **AI:** Claude API (Haiku) for email parsing

## Project Structure

```
JobRadar/
├── frontend/                          # Angular app
│   └── src/
│       ├── environments/
│       │   └── environment.ts         # apiUrl → http://localhost:8080/api
│       ├── styles.scss                # global styles + CSS variables (colors)
│       └── app/
│           ├── app.config.ts          # providers: router + HttpClient
│           ├── app.routes.ts          # '' → ApplicationList
│           ├── app.html / app.scss    # layout: header + <router-outlet>
│           ├── models/
│           │   └── job-application.ts # JobApplication interface + ApplicationStatus type
│           ├── services/
│           │   └── job-application.service.ts  # HTTP calls to the backend
│           └── components/
│               └── application-list/  # table of applications
│
└── backend/                           # Spring Boot API
    └── src/main/java/com/jobrador/backend/
        ├── BackendApplication.java    # entry point
        ├── config/
        │   └── SecurityConfig.java    # Spring Security + CORS
        ├── controller/
        │   └── JobApplicationController.java   # REST endpoints
        ├── service/
        │   └── JobApplicationService.java      # business logic
        ├── model/
        │   ├── JobApplication.java    # record (the data shape)
        │   └── ApplicationStatus.java # enum of statuses
        └── repository/
            ├── JobApplicationRepository.java          # interface
            └── InMemoryJobApplicationRepository.java  # mock data (temporary)
```

## Getting Started

### Prerequisites
- Node.js 24+
- Java 25
- Maven 3.10+
- Angular CLI 21

### Run both apps (two terminals)

```bash
# terminal 1 — backend on http://localhost:8080
cd backend
mvnw spring-boot:run
```

```bash
# terminal 2 — frontend on http://localhost:4200
cd frontend
npm install
ng serve
```

Open `http://localhost:4200` — you should see the table with 5 mock applications.

### Run the tests

```bash
cd backend && mvnw test      # 4 tests (context + endpoint + CORS)
cd frontend && ng test       # 5 tests (app + service + component)
```

## How it works (current state)

```
Angular (localhost:4200)                     Spring Boot (localhost:8080)
┌──────────────────────┐   GET /api/applications   ┌─────────────────────────────┐
│ ApplicationList      │ ────────────────────────▶ │ SecurityConfig (CORS check) │
│   ↓ uses             │                           │   ↓                         │
│ JobApplicationService│ ◀──────── JSON ────────── │ Controller → Service →      │
│   (HttpClient)       │                           │ Repository (mock data)      │
└──────────────────────┘                           └─────────────────────────────┘
```

1. The Angular `ApplicationList` component loads and calls `JobApplicationService.getAll()`.
2. The service makes `GET http://localhost:8080/api/applications`.
3. Because the request comes from a different origin (port 4200 → 8080), the browser checks **CORS**. The backend allows only `http://localhost:4200`.
4. The request goes Controller → Service → Repository. For now the repository returns hardcoded mock data.
5. The JSON comes back and the component renders the table.

### Backend layers

| Layer | Responsibility | Today |
|---|---|---|
| **Controller** | Receives HTTP requests, returns JSON | `GET /api/applications` |
| **Service** | Business logic | Just forwards to the repository |
| **Repository** | Data access | In-memory list with 5 mock applications |
| **Model** | Data shape | `JobApplication` record + `ApplicationStatus` enum |
| **Config** | Security and CORS | `/api/**` is public, CORS allows `localhost:4200` |

The repository is an **interface** on purpose: when real persistence (database) arrives, only the implementation changes — Service and Controller stay the same.

### API

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/applications` | List all job applications (mock data) |

Example response:

```json
[
  {
    "id": 1,
    "company": "Farfetch",
    "role": "Frontend Developer",
    "status": "INTERVIEW",
    "appliedDate": "2026-11-02",
    "lastUpdate": "2026-11-18",
    "nextStep": "Technical interview on 26 Nov"
  }
]
```

Possible `status` values: `APPLIED`, `SCREENING`, `INTERVIEW`, `OFFER`, `REJECTED`. `nextStep` can be `null`.

### Configuration

| File | Setting | Purpose |
|---|---|---|
| `backend/src/main/resources/application.properties` | `app.cors.allowed-origins=http://localhost:4200` | Which frontend origins may call the API |
| `frontend/src/environments/environment.ts` | `apiUrl: 'http://localhost:8080/api'` | Where the frontend sends requests |

## Important notes

- **Spring Security is already on the classpath.** Without configuration it blocks every endpoint (401). That's why `SecurityConfig` exists: it enables CORS inside Security and currently **permits `/api/**` without login**. There is a `TODO` to require authentication once Microsoft OAuth2 is implemented.
- **CSRF is disabled** — fine for a stateless JSON API, revisit when cookies/sessions are used for login.
- **Data is mock.** Restarting the backend always returns the same 5 applications.

## Progress

### Done
- [x] Backend package structure (controller, service, model, repository, config)
- [x] `GET /api/applications` endpoint returning mock data
- [x] CORS configured for the Angular dev server (`localhost:4200`)
- [x] Angular `HttpClient` setup + `JobApplicationService`
- [x] Applications table with status badges, loading, empty and error states (with Retry)
- [x] Backend tests (endpoint + CORS allowed/blocked) and frontend tests (service + component)

### Planned
- [ ] Microsoft OAuth2 login
- [ ] Outlook email sync via Microsoft Graph API
- [ ] AI-powered email classification (job applications)
- [ ] Auto-extract: company, role, status, date, next step
- [ ] Persistence (database) to replace the in-memory repository
- [ ] Dashboard with application timeline
- [ ] Follow-up alerts
