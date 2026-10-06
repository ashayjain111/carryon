# Deploy for $0 (no credit card)

| Part | Free service | Notes |
|---|---|---|
| Database | **Neon** PostgreSQL | 0.5 GB free, no card |
| API | **Render** free web service (Docker) | Sleeps after 15 min idle; first request takes ~30–60 s to wake |
| Frontend | **Cloudflare Pages** (or Netlify) | Unlimited static hosting |
| CI | GitHub Actions | Free for public repos |

## 1. Push to GitHub (public repo)
```powershell
cd C:\Users\Dell\carryon
git remote add origin https://github.com/<you>/carryon.git
git push -u origin main
```

## 2. Database on Neon
1. neon.tech → sign up with GitHub → create project `carryon` (any region near you).
2. From the connection details, note **host**, **database**, **user**, **password**.
3. Your JDBC URL is: `jdbc:postgresql://<host>/<database>?sslmode=require`

## 3. API on Render
1. render.com → sign up with GitHub → **New → Blueprint** → pick the repo (it reads `render.yaml`).
2. Fill the prompted env vars: `DB_URL`, `DB_USER`, `DB_PASSWORD`, a strong `ADMIN_PASSWORD`, and `CORS_ORIGIN` (put a placeholder now, fix after step 4).
3. Deploy. When live, open `https://<name>.onrender.com/actuator/health` and expect `{"status":"UP"}`. Flyway creates the tables on first start.

## 4. Frontend on Cloudflare Pages
1. dash.cloudflare.com → Workers & Pages → Create → Pages → connect the repo.
2. Root directory `frontend`, build command `npm run build`, output directory `dist`.
3. Environment variable: `VITE_API_BASE=https://<name>.onrender.com`.
4. Deploy. You get `https://<project>.pages.dev`.
5. Back in Render, set `CORS_ORIGIN` to that exact URL (no trailing slash) and redeploy.

## 5. Check
Open the Pages URL, register, and run the flow. If the first call hangs, the API is waking up: wait a minute and retry.

