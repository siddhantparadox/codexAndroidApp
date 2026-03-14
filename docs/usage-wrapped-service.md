# Usage Wrapped Service

The Android app reads historical Codex usage from a small desktop sidecar service.

## Default port

- Codex app-server: `4500`
- Usage wrapped service: `4501`

The mobile client derives the usage service port as `activeHost.port + 1`.

## Start the service

From the repo root:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
./gradlew.bat :usage-wrapped-service:run --args="--listen 0.0.0.0 --port 4501"
```

Optional flags:

- `--listen <host>`
- `--port <port>`
- `--codex-home <path>`

By default the service reads from:

- `%CODEX_HOME%\sessions` when `CODEX_HOME` is set
- otherwise `~/.codex/sessions`

## Endpoints

- `GET /healthz`
- `GET /usage-wrapped`

`/usage-wrapped` returns a compact JSON summary with:

- overview metrics
- token totals
- activity-by-day
- highlights for most active day, project, and source
