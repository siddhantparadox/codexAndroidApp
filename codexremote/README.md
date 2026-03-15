# codexremote

Start `codex app-server` on your desktop, start the local Usage Wrapped sidecar,
and print a QR code so Codex Mobile can connect over your local network.

## What It Does

`codexremote`:

- starts `codex app-server` on port `4500`
- starts the Usage Wrapped HTTP service on port `4501`
- reuses an existing healthy Codex app-server on `4500` if one is already running
- reuses an existing healthy Usage Wrapped service on `4501` if one is already running
- detects your LAN IPv4 address
- prints a QR code and a short connection code in the terminal

The intended flow is:

1. Run `npx codexremote` on your desktop
2. Open Codex Mobile on your Android phone
3. Tap `Scan QR Code`
4. Scan the QR from the terminal
5. Tap `Connect`
6. Open `Usage Wrapped` in the app without starting any second desktop process

## Requirements

- Node.js `18+`
- Codex CLI installed and authenticated on the desktop machine
- Phone and desktop on the same trusted local network

## Quick Start

Run without installing:

```bash
npx codexremote
```

Or install it globally:

```bash
npm install -g codexremote
codexremote
```

## What You Should See

When it works, the terminal prints:

- `Mobile access is on.` or reuse status for port `4500`
- `Usage Wrapped is on.` or reuse status for port `4501`
- `Address: ws://<your-lan-ip>:4500`
- `Usage Wrapped: http://<your-lan-ip>:4501/usage-wrapped`
- `Connection code: <short-code>`
- a QR code

Keep that terminal open while you use the mobile app. Press `Ctrl+C` to stop
the services that `codexremote` started.

## Phone Connection

In Codex Mobile:

1. Open the connection screen
2. Tap `Scan QR Code`
3. Scan the QR from your desktop terminal
4. Review the desktop details
5. Tap `Connect`

`Usage Wrapped` will use the same desktop host automatically.

If scanning is unavailable, use:

- `Type short code instead`
- `Advanced` -> manual host and port entry

## Platform Support

- macOS: supported
- Linux: supported
- Windows: supported
- WSL2: manual setup required right now

For WSL2, see [wsl-setup.md](./wsl-setup.md).

## Troubleshooting

### `spawn codex ENOENT`

The Codex CLI is not available in your terminal session.

Check that this works first:

```bash
codex --help
```

If that fails, install Codex and make sure it is on your `PATH`.

### `Port 4500 is already in use`

Another process is already using port `4500`.

- If it is already `codex app-server`, `codexremote` will reuse it.
- Otherwise, stop the conflicting process and rerun `codexremote`.

### `Port 4501 is already in use`

Another process is already using port `4501`.

- If it is already a healthy Usage Wrapped service, `codexremote` will reuse it.
- Otherwise, stop the conflicting process and rerun `codexremote`.

### No LAN IPv4 address found

Connect the desktop to Wi-Fi or Ethernet and rerun the command.

### Phone cannot connect

Check these first:

- the terminal is still running
- the phone and desktop are on the same network
- Windows Firewall or another firewall is allowing private network access
- you are not trying to use the WSL2 path without the extra setup

### Usage Wrapped is unavailable

Check these first:

- `codexremote` is still running
- nothing else has taken over port `4501`
- your desktop has readable Codex session history in `%CODEX_HOME%/sessions` or `~/.codex/sessions`

## Security

`codex app-server` is exposed over raw WebSocket on your local network and
`Usage Wrapped` is exposed over local HTTP.

Only use `codexremote` on a trusted local network. Do not expose ports `4500`
or `4501` to the public internet.

## Local Development

From this package directory:

```bash
npm install
node ./bin/codexremote.mjs
```

Run tests:

```bash
npm test
```

To test the linked CLI locally:

```bash
npm link
codexremote
```

## References

- [Codex App Server](https://developers.openai.com/codex/app-server/)
- [WSL2 setup](./wsl-setup.md)
