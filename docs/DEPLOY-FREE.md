# Deploy for $0 (no credit card)

| Part | Free service | Notes |
|---|---|---|
| Database | **MongoDB Atlas** M0 | 512 MB free, no card, replica set (transactions work) |
| API | **Render** free web service (Docker) | Sleeps after 15 min idle; first request takes ~30–60 s to wake |
| Frontend | **Cloudflare Pages** (or Netlify) | Unlimited static hosting |
| CI | GitHub Actions | Free for public repos |

## 1. Push to GitHub (public repo)
```powershell
cd C:\Users\Dell\carryon
git push
```

## 2. Database on MongoDB Atlas
1. cloud.mongodb.com → sign up → **Create a free cluster (M0)**, any nearby region.
2. Database Access → add a user (username + strong password).
3. Network Access → add `0.0.0.0/0` (Render has no fixed IP; the database user password is your protection).
4. Connect → Drivers → copy the connection string and put the database name before the `?`:
   `mongodb+srv://<user>:<password>@<cluster>.mongodb.net/carryon?retryWrites=true&w=majority`

## 3. API on Render
1. render.com → sign up with GitHub → **New → Blueprint** → pick the repo (it reads `render.yaml`).
2. Fill the prompted env vars: `MONGODB_URI` (from step 2), a strong `ADMIN_PASSWORD`, and `CORS_ORIGIN` (a placeholder for now, fixed after step 4).
3. Deploy. When live, open `https://<name>.onrender.com/actuator/health` and expect `{"status":"UP"}`. Indexes are created on first start.

## 4. Frontend on Cloudflare Pages
1. dash.cloudflare.com → Workers & Pages → Create → Pages → connect the repo.
2. Root directory `frontend`, build command `npm run build`, output directory `dist`.
3. Environment variable: `VITE_API_BASE=https://<name>.onrender.com`.
4. Deploy. You get `https://<project>.pages.dev`.
5. Back in Render, set `CORS_ORIGIN` to that exact URL (no trailing slash) and redeploy.

## 5. Check
Open the Pages URL, register, and run the flow. If the first call hangs, the API is waking up: wait a minute and retry.
