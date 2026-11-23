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
├── frontend/   # Angular app
└── backend/    # Spring Boot API
```

## Getting Started

### Prerequisites
- Node.js 24+
- Java 25
- Maven 3.10+
- Angular CLI 21

### Frontend
```bash
cd frontend
npm install
ng serve
```

### Backend
```bash
cd backend
mvn spring-boot:run
```

## Features (planned)
- [ ] Microsoft OAuth2 login
- [ ] Outlook email sync via Microsoft Graph API
- [ ] AI-powered email classification (job applications)
- [ ] Auto-extract: company, role, status, date, next step
- [ ] Dashboard with application timeline
- [ ] Follow-up alerts