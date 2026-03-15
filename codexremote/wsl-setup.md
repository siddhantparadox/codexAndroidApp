# WSL2 Setup

This document covers the current manual WSL2 setup for using Codex Mobile with
`codex app-server`.

Right now, `codexremote` is not WSL-aware. If you are running Codex inside WSL2,
use one of the setups below and then connect from the Android app with
`Advanced` manual entry.

## Start Codex In WSL

Run this inside your WSL shell:

```bash
codex app-server --listen ws://0.0.0.0:4500
```

After that, choose one networking setup.

## Preferred: Mirrored Networking

Mirrored networking is the cleanest WSL2 path on newer Windows 11 builds
because it allows direct network access into WSL.

1. Create or edit `%UserProfile%\.wslconfig` on Windows:

```ini
[wsl2]
networkingMode=mirrored
```

2. Restart WSL from PowerShell:

```powershell
wsl --shutdown
```

3. Open an elevated PowerShell window and allow inbound TCP `4500` to the WSL VM:

```powershell
New-NetFirewallHyperVRule -Name "Codex4500" -DisplayName "Codex 4500" -Direction Inbound -VMCreatorId '{40E0AC32-46A5-438A-A0B2-2B479E8F2E90}' -Protocol TCP -LocalPorts 4500
```

4. Back in WSL, find the WSL IP:

```bash
hostname -I
```

5. In the Android app, use manual entry with:
- host: the WSL IPv4 address from `hostname -I`
- port: `4500`

## Fallback: Default WSL2 NAT With Windows Port Proxy

Use this if mirrored networking is unavailable.

1. Keep `codex app-server` running inside WSL on `0.0.0.0:4500`.

2. Open an elevated PowerShell window and forward Windows port `4500` into WSL:

```powershell
$wslIp = (wsl hostname -I).Trim().Split()[0]
netsh interface portproxy delete v4tov4 listenport=4500 listenaddress=0.0.0.0
netsh interface portproxy add v4tov4 listenport=4500 listenaddress=0.0.0.0 connectport=4500 connectaddress=$wslIp
New-NetFirewallRule -DisplayName "Codex 4500" -Direction Inbound -Action Allow -Protocol TCP -LocalPort 4500
```

3. Find your Windows LAN IP:

```powershell
ipconfig
```

Use the IPv4 address for your active Wi-Fi or Ethernet adapter.

4. In the Android app, use manual entry with:
- host: the Windows LAN IPv4 address
- port: `4500`

5. If WSL restarts and its IP changes, rerun the port proxy commands.

## App Connection Notes

- Phone and desktop must be on the same local network.
- Manual entry is currently the correct path for WSL2.
- The app server is exposed over raw WebSocket on your LAN, so only use this on
a trusted network.

## References

- [Codex App Server](https://developers.openai.com/codex/app-server/)
- [WSL Networking](https://learn.microsoft.com/en-us/windows/wsl/networking)
- [WSL Config](https://learn.microsoft.com/en-us/windows/wsl/wsl-config)
- [Hyper-V Firewall](https://learn.microsoft.com/en-us/windows/security/operating-system-security/network-security/windows-firewall/hyper-v-firewall)
