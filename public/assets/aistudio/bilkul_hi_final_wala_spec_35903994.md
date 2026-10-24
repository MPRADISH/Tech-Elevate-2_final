# CampusGuard — current specification and complete first-party source

Updated 24 October 2026. Node.js 20.20.2 · Windows · Express + ws · local LAN and real Bluetooth LE.

## Current implementation

This is the same docs/SPEC.md, updated in place. It supersedes earlier software-only simulator descriptions. Real Windows Bluetooth advertising is now the default; LAN remains the main browser delivery path. There is no automatic simulated fallback. No ESP32, Arduino or external beacon is required.

The single shared zone table is src/zones.js: 0 ALL, 1 Visvesvaraya, 2 Abdul Kalam, 3 Ramanujan, 4 Kalpana Chawla. Backend validation, room routing, admin checkboxes, student enrollment, map/history labels, seed data and Bluetooth events use it. The obsolete config/zones.json file is removed.

The admin UI now presents one checkbox per hostel, with ALL mutually exclusive. It shows per-hostel connection/acknowledgement counts and real BLE status. Old loaded tabs were refreshed and the real browser was checked: new names appeared in both admin and student views, a two-hostel alert was received, and its acknowledgement updated the dashboard. Public assets revalidate and the offline cache has a new version. Signed historical message text is retained unchanged; it is not rewritten to rename hostels.

## Final folder structure

```text
campusguard/
  server.js                       Canonical entry, LAN printout, lifecycle
  package.json, package-lock.json, .env.example
  START-CAMPUSGUARD.cmd, STOP-CAMPUSGUARD.cmd, START-HERE.txt
  README.md, PROTOCOL.md, VERIFIED.md, docs/SPEC.md
  src/
    app.js                        Express/WS/SSE routes and server lifecycle
    alerts.js                     Validation, signing, durable creation, receipts/acks
    incidents.js                  Incident metrics, help/safe responses, reports, contacts
    clients.js                    Registration, credential checks and hostel changes
    store.js                      Preserved atomic JSON store
    config.js                     Environment loader and project paths
    zones.js                      Shared hostel IDs/names and routing helpers
    additions.js                  Replay snapshots, read/stats endpoints, mDNS
    transportManager.js           Independent adapter dispatch and health
    transports/
      base.js, ws.js, sse.js, polling.js, udp.js, multicast.js
      webrtc-signal.js, ble-real.js, ble-sim.js
  bridge/ble_bridge.ps1            Real Windows BLE publisher / JSON child bridge
  agent/udp-listener.js, notify.ps1
  bridges/udp-receiver.js          Existing standalone receiver implementation
  shared/protocol.js              Full envelope/signatures, binary codecs, CRC
  shared/campus.js                Shared football/cricket assembly choices
  public/
    admin.html, index.html, setup.html, styles.css
    manifest.webmanifest, sw.js, icons/
    js/admin.js, admin-incidents.js, client.js, connection.js, peer-relay.js, alarm.js, ui.js
  scripts/
    seed.js, curl-tests.ps1, ble-smoke.js, setup-https.js, check.js, running.js, stop.ps1
  tests/
    protocol.test.js, integration.test.js, additions.test.js, hostels.test.js, ble-real.test.js, incidents.test.js
  server/                         Thin compatibility aliases for earlier imports/entry
  runtime/node.exe, LICENSE        Windows runtime included in the runnable ZIP
  node_modules/                   Installed dependencies included in Windows ZIP
  data/alerts.json                 Generated private state; not distributed
  certs/, .env                     Generated private certificates/config; not distributed
```

## Requirements and behavior

- POST /api/alerts accepts zones arrays such as [3], [1,2] or [0]. Empty, nonnumeric, unknown or mixed ALL/hostel targets are rejected. Duplicate IDs normalize. Existing scalar requests and saved alert signatures remain compatible.
- New signed envelopes include canonical zones and the legacy zone projection (one ID, or null for multiple targets). Identity is installation ID plus monotonically persisted sequence. TTL never extends on retransmission. Dedupe is by full ID; clear markers prevent delayed reopening.
- A WS hello carries client credentials, zone and lastSeq. Each authenticated socket joins its own hostel and ALL. Selected room unions prevent duplicate sends; replay contains only matching unexpired/uncleared alerts and clear notices. Heartbeats use native ping/pong plus application metadata.
- SSE /api/stream and /api/events, polling /api/sync, and client read APIs accept zone queries and enforce registration. A hostel change updates rooms and closes stale SSE streams.
- Every transport implements send(alert) and health() with sent/failed/errors counters. Dispatch is independent with bounded adapter waits; native BLE queues work and counts an OS advertising start only after confirmation. A failed adapter does not roll back saved alerts or stop other paths.
- UDP broadcast uses port 41234 and carries full alerts plus a target array. Multicast uses 239.255.42.1–.4 per hostel and .99 for ALL; receivers join their own group and .99. Maximum datagram is 1200 bytes.
- WebRTC signaling stays on the server; signed alert gossip uses browser DataChannels, inventory/want exchange, dedupe and a four-hop forwarding limit. No external STUN/TURN is configured.
- Acknowledgements are unique per alert/client and capture their hostel at receipt. acksByZone supplements the original table. Hostel changes do not move past acks. GET /api/stats/zones returns per-hostel recently seen client counts, cumulative acks and last matching alert time.
- Atomic data/alerts.json writes use temporary write/flush/backup/replace. Legacy state.json migrates once without deleting the original. History and credentials survive restart; runtime counters reset. One process owns the file.
- Bonjour locally advertises campus-alert.local. Startup prints actual LAN IPs. The printed IP is the fallback when mDNS is unavailable. Runtime operation makes no internet/cloud/CDN calls.
- The seed sends five advisory DEMO ONLY alerts: one per hostel and one ALL. Curl tests cover all REST APIs, enrollment/session flows, SSE and WS upgrade. Node tests cover full WS replay/signaling and failure isolation.

## Real Bluetooth boundary and binary payload

The real adapter wraps a hidden Windows PowerShell child. An in-memory .NET helper compiled against installed Windows metadata constructs manufacturer data. No Python or runtime download is required. The laptop's adapter reported LE and peripheral-role support, and Windows reported advertising started in an actual radio smoke test and live dashboard checks.

Manufacturer ID is 0xFFFF. Its data is 24 bytes: the 21-byte core defined in PROTOCOL.md and three zero padding bytes. The core contains version, installation ID, sequence, type, severity, one zone byte, reserved zero byte, TTL, timestamp and CRC-16. Multi-hostel alerts rotate one packet per target with the same alert ID; ALL is zone 0. The total logical layout fits 31 legacy advertisement bytes. Windows controls the Flags section; diagnostics do not claim to be captured radio frames.

The dashboard reports LOCAL TRANSMIT, adapter state and queued packets. sent means confirmed Windows advertising starts, not remote reception or measured coverage. A separate native BLE receiver/scanner must verify reception and range. The browser PWA receives over LAN and does not automatically receive Bluetooth advertisements or form a BLE mesh. Compact summaries omit custom message text/signatures; their CRC detects corruption and is not authentication.

## Failure matrix

| Failure | Behavior |
|---|---|
| Internet lost, LAN intact | Local alert paths continue. |
| LAN/router fails | Browser/IP delivery stops; cached instructions remain. Real BLE may continue if the server/radio remain available, and needs native receivers. |
| Server crashes | Saved state survives; established peers may relay cached signed alerts. New authoritative alerts and local BLE advertising wait for restart. |
| Phone Wi-Fi drops | Local cached state and pending responses remain; reconnect repairs delivery. Phone browsers do not switch to native BLE. |
| Late reconnect | Matching active alerts and persistent clear markers synchronize; expired ordinary alerts do not sound. |
| One transport fails | Other adapters continue. Unavailable Bluetooth is shown honestly. |
| Phone browser closes/suspends | Background delivery/alarms are not guaranteed. |
| mDNS blocked | Use the printed LAN address. |

## Build priorities

The implemented priorities are durable creation/authentication, reliable browser delivery/acknowledgements, reconnection/expiry/clearing, hostel targeting, health/history, UDP/peer relay, and real BLE advertising. Further work should prioritize physical phone/network/BLE reception checks before polish. More peer topology work or decorative additions can be cut without removing the core alert/response/recovery flow.

## Assumptions resolving earlier prompts

1. The latest request for real Bluetooth without simulation takes precedence over the earlier automatic ble-real-to-ble-sim fallback. Production uses real BLE; failure leaves LAN running. ble-sim is an explicitly injected test/development adapter only.
2. Windows is the supported native advertiser platform for this laptop. PowerShell/.NET uses built-in APIs and replaces the earlier proposed Python bridge dependency.
3. Existing server/ paths remain thin compatibility aliases so old launch/import flows work; server.js and src/ contain the canonical implementation.
4. BLE advertising is a compact secondary radio channel, not a browser transport or a promise of campus-wide range. No native phone receiver is bundled.
5. Historical acknowledgements without a zone are attributed once from registration during migration; future records capture the hostel when received.

## Setup, commands and complete protocol

The following operation/protocol/verification documents are embedded in full. The source appendix afterward includes every first-party file, one block per path. It excludes generated secrets/state and this document itself. The runnable ZIP includes third-party dependencies and the Node runtime.


## Student safety and incident management update

Both student and admin screens are implemented with working backend storage, not static mockups. All four hostels use src/zones.js. Shared assembly choices in shared/campus.js are Football ground (default) and Cricket ground; the admin chooses one per alert, and assemblyPoint is signed with the alert. Existing signed alerts remain valid without this optional field.

Students have calm/active screens, sound setup, hostel selection, safety instructions, safe/help buttons, editable help details, contacts, assembly illustration and incident reporting. Safe/help responses replace the previous status for that device and incident. Pending responses and reports persist locally and retry on reconnection; idempotent request IDs prevent duplicate reports. Maps are illustrative, not GPS or verified evacuation routes. Browser background notifications are not guaranteed.

The medical number is 8571893627 as supplied for the prototype. Security is Not configured until an admin enters it. Contacts are editable and persisted. Tel links initiate calls only when the user selects them.

Admin incident cards show actual confirmed deliveries, latest safe/help counts, response roster/filter, timestamps and all-clear. The audience is a snapshot of registered matching devices, including late devices that actually receive/respond; it is not total campus enrollment or a guarantee of delivery. Safe percentage uses safe responses among confirmed deliveries. Staff-notified events are manual admin confirmations; the application does not send wardens messages or place calls. Reports are stored for admin review and can be resolved, without automatically broadcasting alerts.

New endpoints (all JSON):

| Endpoint | Access / body |
|---|---|
| GET /api/campus | Public contacts and assemblyPoints |
| PATCH /api/campus | Admin; {medicalPhone, securityPhone}, optional fields |
| GET /api/incidents | Admin; {incidents:[summary]} |
| GET /api/incidents/:id | Admin; summary plus recipient roster |
| GET /api/responses/me | Client bearer; {responses:{alertId:response}} |
| POST /api/responses | Client bearer; {alertId,status:"safe" or "needs-help",message?} |
| POST /api/incidents/:id/notify | Admin; {note?}, manual confirmation only |
| POST /api/incidents/:id/all-clear | Admin; {message?}, same hostels/assembly, signed clear notice |
| POST /api/reports | Client bearer; {requestId,category,message}, category security/medical/fire/other |
| GET /api/reports | Admin; {reports:[...]} |
| PATCH /api/reports/:id | Admin; {status:"resolved"} |

Admin accepts the existing session or x-admin-pin; client bearer is id.token from enrollment. Help details max 400 UTF-8 bytes; report details max 800. /api/sync now also supplies contacts and the authenticated client's saved responses. Existing /api/ack and /api/acks use the same response service. Data remains atomically persisted in data/alerts.json, with incidents, responses, reports and campus keys added without deleting existing history or keys.

Verification: 11 tests passed, 46 JavaScript files passed syntax checks. Live browser checks covered Cricket ground, help request appearing in admin, safe replacing help, response roster, report submission/resolution, manual timeline event and all-clear returning the student to the calm screen. Real phone/network reception remains a separate physical demonstration.

## README.md

## CampusGuard — LAN alerts with real Bluetooth LE

Node.js 20, Express and ws. No cloud, CDN, external fonts, STUN or TURN at runtime. The Windows laptop serves the dashboard/student PWA, persists alerts and advertises compact summaries using its built-in Bluetooth adapter. No microcontroller or external beacon is required.

### Run on this laptop

The Windows ZIP includes Node 20 and installed dependencies. Extract the whole ZIP and double-click START-CAMPUSGUARD.cmd. Keep its window open.

```powershell
Set-Location "C:\Users\DELL\Documents\Codex\2026-10-24\we-have-to-m\outputs\campusguard"
.\START-CAMPUSGUARD.cmd
```

Admin: http://localhost:8080/admin.html. Default demo PIN: **404404**. Set ADMIN_PIN in .env and restart to change it. Phones use the current LAN URL printed by the server or its locally generated QR. Restart the launcher after changing Wi-Fi so HTTPS includes the current IP.

For a source-only checkout, install Node.js 20 and run once while online:

```powershell
npm.cmd ci
npm.cmd run setup:https
npm.cmd start
```

To stop, use Ctrl+C in the server window or STOP-CAMPUSGUARD.cmd. Existing launchers/imports using server/index.js remain compatible, but server.js is the canonical entry.

### Manual setup

- Connect the laptop and phones to one router/hotspot. Allow the requested Windows Node.js firewall permission on the trusted demo network; do not disable the firewall.
- Keep Bluetooth enabled on the laptop for the real advertiser. Transport lab shows supported/unavailable/advertising states. An unavailable radio does not stop LAN delivery.
- Open the student page on each phone, choose a hostel, tap Enable alerts & test sound, and keep the page active. Device sound/vibration/background behavior still needs testing.
- HTTP supports the main WS/SSE/polling demo. Trusted local HTTPS is required for phone PWA caching/installation and peer relay. Follow /setup.html to install the generated public CA yourself. Remove the demo CA after the event; never share private keys from certs/.
- Reload old admin/student tabs after applying this update. The UI uses the shared zone module directly, public assets revalidate, and the offline cache version has changed. Existing saved credentials/history remain intact.

### Hostels and targeting

src/zones.js is the only executable lookup table: 0 ALL, 1 Visvesvaraya, 2 Abdul Kalam, 3 Ramanujan, 4 Kalpana Chawla. The admin has hostel checkboxes; ALL is mutually exclusive with individual hostels. The student picker, map, history labels, BLE events, seed and API validation use this table.

POST /api/alerts accepts zones:[3], zones:[1,2], or zones:[0]. Arrays are normalized and validated. Legacy scalar zone requests and stored signatures are supported. New signed alerts include the canonical zones array and a compatibility zone projection: the single ID, or null for multiple targets. Use alertZones()/applicable() for targeting.

WS hello carries clientId, token, lastSeq, zone and optional serverId. Each socket joins its hostel and ALL; room unions prevent duplicate sends. SSE and polling accept ?zone= and enforce the registered hostel. A client moves through PATCH /api/clients/me; its WS room changes and old SSE streams close for reconnection.

Acknowledgements are unique by alert/client, capture the hostel at receipt, and are indexed in acksByZone alongside the existing ack table. Moving hostels does not move old acknowledgements. Legacy acks without a captured hostel are attributed once from registration during migration. GET /api/stats/zones reports unique clients seen within 16 seconds, cumulative acks and the most recent matching alert time, including ALL/history.

### Real BLE behavior

The default ble transport is src/transports/ble-real.js. It wraps bridge/ble_bridge.ps1, which uses built-in Windows Runtime Bluetooth APIs and an in-memory .NET helper for WinRT collections. No Python, third-party Bluetooth package, downloaded compiler or internet call is needed. Windows 10/11 and a peripheral-capable BLE adapter are required.

Alerts enter a bounded queue and rotate by target, normally every 1.2 seconds. Each target keeps the original ID/sequence/timestamp/TTL. A multi-hostel alert produces one summary per target; ALL encodes zone 0. The bridge submits 24 manufacturer bytes: the 21-byte core plus three reserved zeros, company ID 0xFFFF. Together with the manufacturer header and OS-managed Flags, this fits a legacy 31-byte advertising budget. Windows controls the actual Flags; the full hex layout in diagnostics is a logical view, not a captured radio frame.

sent counts **Windows advertising starts**, not remote receptions or the number of over-air repeats. The dashboard says LOCAL TRANSMIT and never draws inferred BLE coverage. Native receiver/scanner reception and physical range require a separate device. Compact packets contain a CRC, not the full alert text or its cryptographic signature.

The phone browser PWA receives over LAN. It does not automatically scan these advertisements or become a Bluetooth mesh. Direct BLE display/alarm on iPhone/Android needs a native receiver/app. There is no automatic simulated fallback. If Bluetooth is unavailable, its health reports that honestly while LAN continues. ble-sim.js remains only as an explicit, dependency-injected test/development adapter; production never selects it.

Test the actual adapter separately when another CampusGuard advertiser is not running:

```powershell
npm.cmd run ble:probe
.\runtime\node.exe scripts/ble-smoke.js
```

The smoke test broadcasts a short DEMO ONLY summary and stops. It proves Windows started advertising, not that a phone received it.

### Network transports and recovery

- WS primary → fetch-stream SSE → two-second polling. Heartbeats and reconnect synchronization repair gaps; dedupe stops repeated alarms. Persistent clear markers suppress delayed cleared alerts.
- UDP broadcast carries all signed full alerts on port 41234. Multicast uses 239.255.42.1 through .4 for targeted hostels and 239.255.42.99 for ALL. Receivers join their own group plus .99.
- Browser WebRTC uses server signaling only, local ICE candidates, inventory/want gossip and a four-hop forwarding limit. Secure-context clients verify signatures before relaying.
- Bonjour advertises campus-alert.local locally. Use the printed LAN IP when mDNS is unsupported or blocked. MDNS_ENABLED=false disables discovery.
- Each transport implements send(alert) and health(), with sent/failed counters. Adapter failures/timeouts are isolated. A queued BLE job is distinct from a confirmed OS advertising start.
- data/alerts.json uses temporary write, flush, backup and atomic replacement. Existing state.json migrates once without deleting the original. Transport counters reset on restart; alerts/acks/receipts persist. A single process owns the file.

### Commands and endpoint tests

Use another terminal in the project folder:

```powershell
.\runtime\node.exe scripts/seed.js
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/curl-tests.ps1
.\runtime\node.exe scripts/check.js
.\runtime\node.exe --test --test-concurrency=1 tests/protocol.test.js tests/integration.test.js tests/additions.test.js tests/hostels.test.js tests/ble-real.test.js
```

The seed sends exactly five advisory DEMO ONLY alerts, one for each hostel and one ALL. The curl tests create demo alerts/test clients, test every REST API including login/logout, enrollment changes, history, QR, hostel stats, SSE and WS upgrade, and reject simulated BLE mode. Full authenticated WS replay/signaling is checked by the Node tests. Test fixtures are explicitly injected and do not use the real radio.

Optional UDP receiver, in a separate terminal:

```powershell
.\runtime\node.exe agent/udp-listener.js --zone 1
```

The listener logs alerts, uses Windows popup/OS sound, and deduplicates IDs. --mute silences it; --no-notify disables popup/sound. It validates fields/TTL/hostel but does not authenticate UDP signatures. Optional receipt reporting uses --server and --credential from a registered client of the same hostel. UDP_INTERFACE selects the Wi-Fi adapter on multi-adapter machines; UDP_BROADCAST overrides limited broadcast.

### Failure boundaries

| Failure | Behavior |
|---|---|
| Internet lost, LAN intact | Local paths continue. |
| Entire LAN/router down | Browser/IP delivery stops; cached instructions remain. Real BLE can continue only while the laptop/server/radio remain available and needs native receivers. |
| Server crash | Saved state survives. Established peers can exchange cached alerts; new authoritative alerts and BLE advertising wait for restart. |
| Phone Wi-Fi drop | Cached alerts/pending responses remain; reconnect synchronizes. Phone browsers do not receive automatic BLE fallback. |
| Late reconnect | Matching active alerts and clear markers repair state. Expired ordinary alerts do not sound. |
| One transport fails | Other transports continue. |
| App closed/suspended | Background alarms/delivery are not guaranteed. |

The project is a hackathon prototype. Multi-device routing, actual phone behavior and BLE range/reception remain manual verification tasks. See PROTOCOL.md, VERIFIED.md and docs/SPEC.md for the complete protocol, evidence and source snapshot.

### Student safety and incident management update

Both student and admin screens are implemented with working backend storage, not static mockups. All four hostels use src/zones.js. Shared assembly choices in shared/campus.js are Football ground (default) and Cricket ground; the admin chooses one per alert, and assemblyPoint is signed with the alert. Existing signed alerts remain valid without this optional field.

Students have calm/active screens, sound setup, hostel selection, safety instructions, safe/help buttons, editable help details, contacts, assembly illustration and incident reporting. Safe/help responses replace the previous status for that device and incident. Pending responses and reports persist locally and retry on reconnection; idempotent request IDs prevent duplicate reports. Maps are illustrative, not GPS or verified evacuation routes. Browser background notifications are not guaranteed.

The medical number is 8571893627 as supplied for the prototype. Security is Not configured until an admin enters it. Contacts are editable and persisted. Tel links initiate calls only when the user selects them.

Admin incident cards show actual confirmed deliveries, latest safe/help counts, response roster/filter, timestamps and all-clear. The audience is a snapshot of registered matching devices, including late devices that actually receive/respond; it is not total campus enrollment or a guarantee of delivery. Safe percentage uses safe responses among confirmed deliveries. Staff-notified events are manual admin confirmations; the application does not send wardens messages or place calls. Reports are stored for admin review and can be resolved, without automatically broadcasting alerts.

New endpoints (all JSON):

| Endpoint | Access / body |
|---|---|
| GET /api/campus | Public contacts and assemblyPoints |
| PATCH /api/campus | Admin; {medicalPhone, securityPhone}, optional fields |
| GET /api/incidents | Admin; {incidents:[summary]} |
| GET /api/incidents/:id | Admin; summary plus recipient roster |
| GET /api/responses/me | Client bearer; {responses:{alertId:response}} |
| POST /api/responses | Client bearer; {alertId,status:"safe" or "needs-help",message?} |
| POST /api/incidents/:id/notify | Admin; {note?}, manual confirmation only |
| POST /api/incidents/:id/all-clear | Admin; {message?}, same hostels/assembly, signed clear notice |
| POST /api/reports | Client bearer; {requestId,category,message}, category security/medical/fire/other |
| GET /api/reports | Admin; {reports:[...]} |
| PATCH /api/reports/:id | Admin; {status:"resolved"} |

Admin accepts the existing session or x-admin-pin; client bearer is id.token from enrollment. Help details max 400 UTF-8 bytes; report details max 800. /api/sync now also supplies contacts and the authenticated client's saved responses. Existing /api/ack and /api/acks use the same response service. Data remains atomically persisted in data/alerts.json, with incidents, responses, reports and campus keys added without deleting existing history or keys.

Verification: 11 tests passed, 46 JavaScript files passed syntax checks. Live browser checks covered Cricket ground, help request appearing in admin, safe replacing help, response roster, report submission/resolution, manual timeline event and all-clear returning the student to the calm screen. Real phone/network reception remains a separate physical demonstration.

## PROTOCOL.md

## CampusGuard protocol v1

All full-alert transports carry an identical signed immutable alert. Runtime `receivedVia` is local metadata and is excluded from signing.

```json
{"v":1,"kind":"alert","alert":{"v":1,"id":"7ac912ef:42","serverId":"7ac912ef","seq":42,"timestamp":"2026-10-24T10:00:00.000Z","ttl":300,"type":"fire","severity":3,"zone":1,"zones":[1],"message":"DEMO ONLY: use the east exit.","signature":"BASE64URL_ECDSA_P256_SIGNATURE"}}
```

- `serverId`: persistent random 32-bit installation ID, eight lowercase hex characters. `seq`: persistent unsigned 32-bit counter, allocated once per accepted alert. `id`: `${serverId}:${seq}`.
- `timestamp`: server-issued ISO UTC time, whole-second precision. `ttl`: original lifetime in seconds, 1–3600. Retransmissions do not extend expiry.
- Types: fire, earthquake, medical, security, drill, all-clear. Severity: 1 advisory, 2 urgent, 3 critical. Zone IDs: 0 ALL; 1 Visvesvaraya; 2 Abdul Kalam; 3 Ramanujan; 4 Kalpana Chawla.
- Message: 1–400 UTF-8 bytes. Entire UDP envelope must also fit 1,200 bytes. JSON escaping can expand unusual messages.
- ECDSA P-256 / SHA-256 signature uses raw 64-byte IEEE-P1363 R||S format, Base64URL encoded. Signed UTF-8 content is `JSON.stringify([v,id,serverId,seq,timestamp,ttl,type,severity,zone,message,zones])` for new alerts. Historical alerts without zones retain the original form without the last element. Public JWK comes from same-origin `/api/config`. Key persists with server state. HTTPS protects initial key distribution. Secure-context clients verify before accepting or relaying. HTTP clients without WebCrypto accept direct server transports and disable peer relay.
- Deduplicate by `id`, not a highest-seen-sequence cutoff. Keep sequence cursor separate from peer delivery; advance it only after successful server synchronization.
- All-clear establishes a per-zone sequence marker. Zone 0 clears all earlier alerts for every zone. A zone-specific clear suppresses every earlier alert as displayed in that zone, including an earlier ALL alert. Newer alerts remain active. Clear markers persist after TTL; peer inventory retains latest signed clear notices as tombstones.

### REST

JSON body/response; errors are `{error: string}`. Admin routes accept `x-admin-pin` or the existing HttpOnly, SameSite=Strict session cookie (Secure on HTTPS). Client routes require `Authorization: Bearer CLIENT_ID.TOKEN`. Server hashes stored client tokens. Mutating browser requests reject foreign Origin headers. Login rate limit: ten attempts per IP per minute. Session lifetime: eight hours.

| Method / route | Contract |
|---|---|
| GET `/api/alerts?since=N` | Client Bearer token or admin authentication; zone-filtered replay for clients; returns alerts, cursor/lastSeq, clear markers and reset flag |
| GET `/api/latest` | Client or admin; latest stored matching alert (possibly expired), plus an explicit expired flag |
| GET `/api/stats/zones` | Admin; four hostel rows with id, name, connectedClients, acksReceived and lastAlertTime |
| GET `/api/stats` | Admin; persistent per-alert receipt/safe counts and runtime transport health |
| GET `/api/transports` | Admin; all transport health entries, including sent/failed/errors |
| GET `/api/config` | Public zones, types, serverId, serverTime, signing publicKey, HTTPS availability |
| POST `/api/login` | `{pin}` → session cookie |
| POST `/api/logout` | Admin; expires session cookie |
| POST `/api/clients` | `{zone,name?}` → `{id,token,zone,name,createdAt}`; zone must be 1–4 |
| PATCH `/api/clients/me` | Client; `{zone}` → updated zone |
| POST `/api/alerts` | Admin; `{type,severity,zones:[1,2],message,ttl?}` → `{alert,transports}`; persistence precedes dispatch |
| GET `/api/sync?after=N&serverId=ID&transport=MODE` | Client; `{serverId,serverTime,cursor,alerts,clearMarkers,reset,zone}`. Initial/reset sync returns current active alerts. Incremental sync includes matching new unexpired alerts and all-clear records. |
| POST `/api/receipts` | Client; `{alertId,via}` → `{recorded,duplicate}` |
| POST `/api/ack` | Alias of `/api/acks`; registered client authentication remains required |
| POST `/api/acks` | Client; `{alertId,status:"safe",via?}` → `{recorded,duplicate}` |
| GET `/api/stream` | Alias of `/api/events`; supports since or Last-Event-ID replay |
| GET `/api/events` | Client; SSE ready/heartbeat/alert events, alert event ID = alert ID |
| GET `/api/history` | Admin; latest 200 alerts with unique receipt/safe counts |
| GET `/api/health` | Admin; runtime transport counters, health, zone connections and local join URLs |
| POST `/api/transports/NAME` | Admin; `{enabled?}`. BLE supports enabled/disabled and mode:"native" (or "real"); simulator mode is rejected. Unavailable hardware is reported without simulated delivery |
| GET `/api/qr?url=URL` | Admin; locally generated QR SVG for local addresses only |

Receipt and acknowledgement uniqueness is `(alertId,clientId)`. Token identity wins over any supplied clientId. Receipt = application reports reception; safe acknowledgement = explicit user action. Neither means physical rescue verification.

### WebSocket and SSE

`/ws` must receive `{kind:"hello",clientId,token,lastSeq,zone}` within five seconds. Hello also accepts `lastSeq` (or legacy `cursor`) and optional `serverId`. The server responds with ready metadata, replays matching unexpired/uncleared alerts and signed clear notices, then sends `{kind:"replay-complete",cursor,clearMarkers,reset}`. Wrong installations or a cursor beyond the server head reset replay safely. It sends canonical alert envelopes, `{kind:"heartbeat",serverTime}`, and `{kind:"peers",enabled,peers:[{id,zone}]}`. Client may send `{kind:"receipt",alertId,via:"ws"}` and `{kind:"ack",alertId,status:"safe"}`; REST equivalents work for every connection mode.

SSE has event names `ready`, `heartbeat`, `alert`; data for an alert is the canonical JSON envelope, with `id: SERVERID:SEQ`. The client streams via fetch so credentials stay in Authorization headers, not URLs. Cursor sync repairs reconnect gaps independently of SSE event IDs.

### BLE binary

21 application bytes plus three reserved manufacturer padding bytes within a legacy 31-byte advertising budget; little-endian multibyte core values.

| Offset | Size | Value |
|---|---|---|
| 0 | 1 | version = 1 |
| 1 | 4 | serverId as uint32 |
| 5 | 4 | seq uint32 |
| 9 | 1 | type: 1 fire, 2 earthquake, 3 medical, 4 security, 5 drill, 6 all-clear |
| 10 | 1 | severity 1–3 |
| 11 | 1 | zone ID from src/zones.js (0 ALL, 1–4 hostels) |
| 12 | 1 | reserved = 0; preserves the original 21-byte layout |
| 13 | 2 | original TTL seconds |
| 15 | 4 | issued-at Unix seconds |
| 19 | 2 | CRC-16/CCITT-FALSE over bytes 0–18; little-endian stored checksum |

CRC: polynomial 0x1021, initial 0xffff, refin/refout false, xorout 0. Check value for ASCII `123456789` is 0x29b1. CRC is not a signature.

The logical 31-byte layout is [02 01 06] Flags, [1B FF FF FF] manufacturer length/type/test company ID, the 21-byte core, and three reserved zero bytes. The real bridge submits company ID 0xFFFF and the final 24 manufacturer bytes to Windows; Windows controls the Flags section. The dashboard shows actual submitted manufacturerHex and confirmed Windows advertising starts, not a captured over-air frame or a remote receipt.

Real BLE is the production default. Requests queue up to 128 target summaries, rotate with bounded dwell, and retain original expiry. One multi-hostel alert uses separate target-zone summaries with the same identity. ALL uses zone 0. Expired jobs are removed. Native failures leave LAN transports independent; no automatic simulator fallback is used.

The bridge protocol is newline-delimited JSON: input {op:"publish",id,hex,expiresAt} where hex is the 21-byte core; {op:"stop",id} stops a publisher. Output kinds are ready, published, stopped, expired and error. published is emitted only after Windows reports Started. EOF/process exit stops the publisher. Manufacturer data is built using local Windows metadata/.NET APIs; no runtime download is involved.

Dashboard sessions receive {kind:"ble-beacon",zone,beacon} with simulated:false, transmitted:true, received:false, manufacturerHex and a local timestamp. Dashboard hello is {kind:"hello",role:"dashboard"}, authenticated by the existing session cookie. The browser student application does not scan these advertisements. A native receiver/scanner is required for direct phone BLE reception; CRC does not authenticate these compact summaries.

### UDP

UTF-8 datagram `{v:1,kind:"alert",transport:"udp-broadcast"|"udp-multicast",zone:[1,2],alert: FULL_SIGNED_ALERT}`. Maximum 1,200 bytes. Broadcast to configured subnet/limited broadcast; multicast to 239.255.42.1–239.255.42.4 for selected hostels or 239.255.42.99 for ALL, port 41234, IP TTL 1. A targeted packet does not go to the ALL group. Receivers join their own hostel group and the ALL group. Burst at 0/250/750 ms, repeated every five seconds while active. Receivers deduplicate by ID and apply alert expiry separately from IP TTL. Ordinary PWAs need a native receiver bridge for raw UDP. The included command-line receiver validates fields/TTL, deduplicates, logs, and uses a built-in Windows notification/sound helper; signature verification is performed by the browser peer path, not this demonstration receiver.

### WebRTC gossip

Server forwards `{kind:"signal",to,type:"offer"|"answer"|"ice",data}` as `{kind:"signal",from,type,data}`. No server DataChannel and no external ICE servers. Clients create reliable ordered `campus-alerts` channels in a deterministic ring with up to two outgoing neighbor choices. Simultaneous offers use client-ID ordering to resolve glare. Incoming connections may increase degree.

DataChannel messages: `{kind:"inventory",ids:[...]}`, `{kind:"want",ids:[...]}`, `{kind:"relay",alert,hops:1..4}`. Inventories run every five seconds. Signature, identity, time and schema are checked; expired ordinary alerts are ignored. Signed clear tombstones remain valid suppression records. Dedupe prevents repeated display and loops. Non-target clients may carry a verified packet without displaying it. Established channels survive lost signaling links; new links require the server. Browser link/forward counters are reported as `{kind:"peer-health",connections,forwards}` while WebSocket remains available.

### Multi-hostel compatibility and attribution

`zones` is a sorted unique non-empty array validated by src/zones.js. `[0]` denotes ALL and cannot be combined with hostel IDs. New envelopes retain numeric `zone` for a single target and set it to null for multiple targets; consumers use the canonical `zones` array. Legacy stored envelopes with numeric zone and no zones array remain accepted and preserve their original signed payload.

WS hello zone must match client registration; omitted zone supports older clients. Live room sends and reconnect replay include only matching hostel or ALL alerts. SSE and polling accept zone query parameters with the same authenticated-hostel restriction. All-clear updates each selected target marker; clearing one hostel does not clear a multi-hostel alert for other hostels.

BLE multi-target summaries encode each target separately with the same alert identity. A real transmit event zone is its advertised target (0 ALL or a selected hostel). It does not represent a measured receiver location. These summaries are CRC-checked and do not carry the full signed multi-hostel envelope.

Acknowledgements remain unique by alert/client, capture `zone` at receipt and are indexed in `acksByZone`. Changing enrollment cannot reattribute an old acknowledgement. GET /api/stats/zones reports cumulative saved acks, unique clients seen within 16 seconds, and the most recent matching alert timestamp (including ALL/history), or null. Legacy entries without a zone are attributed once using registration during index migration.

### Canonical backend modules and compatibility

server.js starts src/app.js. src/alerts.js handles signed durable creation/receipts; src/clients.js handles registration/authentication; src/store.js contains the preserved atomic store; src/transportManager.js dispatches adapters. Each transport has a module under src/transports. Older server/ paths are compatibility re-exports/entry aliases and do not create a second backend. Only src/zones.js is served publicly from the server source folder; public/ and shared protocol assets are served locally with cache revalidation.

No automatic simulation fallback is enabled because the latest project requirement is real Bluetooth without simulated delivery. ble-sim.js is explicitly injected by isolated tests only. The supported native bridge is Windows PowerShell/.NET rather than a Python dependency. No native phone receiver is bundled; the PWA remains a LAN client. These choices resolve the earlier specification's conflicting simulator and bridge-language requirements.

### Student safety and incident management update

Both student and admin screens are implemented with working backend storage, not static mockups. All four hostels use src/zones.js. Shared assembly choices in shared/campus.js are Football ground (default) and Cricket ground; the admin chooses one per alert, and assemblyPoint is signed with the alert. Existing signed alerts remain valid without this optional field.

Students have calm/active screens, sound setup, hostel selection, safety instructions, safe/help buttons, editable help details, contacts, assembly illustration and incident reporting. Safe/help responses replace the previous status for that device and incident. Pending responses and reports persist locally and retry on reconnection; idempotent request IDs prevent duplicate reports. Maps are illustrative, not GPS or verified evacuation routes. Browser background notifications are not guaranteed.

The medical number is 8571893627 as supplied for the prototype. Security is Not configured until an admin enters it. Contacts are editable and persisted. Tel links initiate calls only when the user selects them.

Admin incident cards show actual confirmed deliveries, latest safe/help counts, response roster/filter, timestamps and all-clear. The audience is a snapshot of registered matching devices, including late devices that actually receive/respond; it is not total campus enrollment or a guarantee of delivery. Safe percentage uses safe responses among confirmed deliveries. Staff-notified events are manual admin confirmations; the application does not send wardens messages or place calls. Reports are stored for admin review and can be resolved, without automatically broadcasting alerts.

New endpoints (all JSON):

| Endpoint | Access / body |
|---|---|
| GET /api/campus | Public contacts and assemblyPoints |
| PATCH /api/campus | Admin; {medicalPhone, securityPhone}, optional fields |
| GET /api/incidents | Admin; {incidents:[summary]} |
| GET /api/incidents/:id | Admin; summary plus recipient roster |
| GET /api/responses/me | Client bearer; {responses:{alertId:response}} |
| POST /api/responses | Client bearer; {alertId,status:"safe" or "needs-help",message?} |
| POST /api/incidents/:id/notify | Admin; {note?}, manual confirmation only |
| POST /api/incidents/:id/all-clear | Admin; {message?}, same hostels/assembly, signed clear notice |
| POST /api/reports | Client bearer; {requestId,category,message}, category security/medical/fire/other |
| GET /api/reports | Admin; {reports:[...]} |
| PATCH /api/reports/:id | Admin; {status:"resolved"} |

Admin accepts the existing session or x-admin-pin; client bearer is id.token from enrollment. Help details max 400 UTF-8 bytes; report details max 800. /api/sync now also supplies contacts and the authenticated client's saved responses. Existing /api/ack and /api/acks use the same response service. Data remains atomically persisted in data/alerts.json, with incidents, responses, reports and campus keys added without deleting existing history or keys.

Verification: 11 tests passed, 46 JavaScript files passed syntax checks. Live browser checks covered Cricket ground, help request appearing in admin, safe replacing help, response roster, report submission/resolution, manual timeline event and all-clear returning the student to the calm screen. Real phone/network reception remains a separate physical demonstration.

## VERIFIED.md

## Verification record — hostel frontend/backend and real BLE

24 October 2026 · Windows · bundled Node.js 20.20.2.

### Automated checks

- Eleven passing tests and 46 JavaScript syntax checks, including the new server.js entry and src modules.
- Existing signature, atomic store, restart/sequence, expiry, clear markers, authentication, WS/SSE/polling, replay, dedupe and signaling checks remain passing after restructuring.
- Hostel tests cover four rooms plus ALL, target unions, reconnect/query filtering, wrong-hostel rejection, per-hostel BLE summaries/multicast groups, acknowledgement dedupe/attribution, hostel moves and restart persistence.
- Real BLE adapter tests cover rotating targets, confirmed OS-start accounting, unavailable hardware, continued LAN dispatch and no simulated fallback. They use explicit fake bridge dependencies, not the physical adapter.
- ble-sim is used only as an explicitly injected fixture by earlier compatibility tests; it is not selected by the running application.

### Actual Windows and UI checks

- Windows adapter probe returned lowEnergySupported=true and peripheralSupported=true on the laptop's Intel Bluetooth adapter.
- Real bridge smoke test reached Windows advertising status, with simulated=false and a submitted 24-byte manufacturer payload. It stopped cleanly afterward.
- The running server's real BLE transport reported advertising, realAvailable=true, sent>0, failed=0 and automaticSimulationFallback=false.
- Admin browser was reloaded and shows ALL, Visvesvaraya, Abdul Kalam, Ramanujan and Kalpana Chawla as checkboxes. Selecting two hostels clears ALL; the review lists both names.
- A DEMO ONLY two-hostel alert was sent through the actual admin UI, received on the registered student page via WS, acknowledged, and shown in the admin safety count. Student enrollment lists all four new names.
- Transport lab shows REAL BLE / ADVERTISING and local per-target transmission events with no inferred receiver coverage.
- Public assets return Cache-Control: no-cache; the student offline cache version is updated. Registrations/history are preserved. Old user-authored message text is not rewritten because alerts are signed immutable records.
- Updated Windows curl tests cover all REST APIs, hostel filtering/validation, per-hostel statistics, both SSE paths, session logout, QR and WS upgrade. Full WS messages/signaling are checked by integration tests.
- Earlier live UDP broadcast/multicast reception, campus-alert.local resolution, atomic state migration, browser fallback, peer-only delivery and offline queued-ack recovery were recorded; those implementations were preserved or moved without behavior changes.
- All five multicast groups were received locally in a separate verification (port 41235); production defaults to 41234.

### Remaining physical-device checks

- A second BLE receiver must confirm the advertised bytes over the air. Windows Started is not a remote delivery or range measurement.
- Native phone BLE reception needs a scanner/app; the browser PWA receives LAN alerts. No native phone alert receiver is bundled.
- Actual phone siren/vibration/background/PWA behavior, trusted HTTPS, firewall and hotspot client isolation remain device-specific rehearsal tasks.
- Multi-device multicast routing and mDNS need the intended demonstration network.

The Windows ZIP excludes .env, data, signing keys and private certificates. The app makes no runtime cloud/CDN calls. No ESP32, Arduino or external beacon is required.

### Student safety and incident management update

Both student and admin screens are implemented with working backend storage, not static mockups. All four hostels use src/zones.js. Shared assembly choices in shared/campus.js are Football ground (default) and Cricket ground; the admin chooses one per alert, and assemblyPoint is signed with the alert. Existing signed alerts remain valid without this optional field.

Students have calm/active screens, sound setup, hostel selection, safety instructions, safe/help buttons, editable help details, contacts, assembly illustration and incident reporting. Safe/help responses replace the previous status for that device and incident. Pending responses and reports persist locally and retry on reconnection; idempotent request IDs prevent duplicate reports. Maps are illustrative, not GPS or verified evacuation routes. Browser background notifications are not guaranteed.

The medical number is 8571893627 as supplied for the prototype. Security is Not configured until an admin enters it. Contacts are editable and persisted. Tel links initiate calls only when the user selects them.

Admin incident cards show actual confirmed deliveries, latest safe/help counts, response roster/filter, timestamps and all-clear. The audience is a snapshot of registered matching devices, including late devices that actually receive/respond; it is not total campus enrollment or a guarantee of delivery. Safe percentage uses safe responses among confirmed deliveries. Staff-notified events are manual admin confirmations; the application does not send wardens messages or place calls. Reports are stored for admin review and can be resolved, without automatically broadcasting alerts.

New endpoints (all JSON):

| Endpoint | Access / body |
|---|---|
| GET /api/campus | Public contacts and assemblyPoints |
| PATCH /api/campus | Admin; {medicalPhone, securityPhone}, optional fields |
| GET /api/incidents | Admin; {incidents:[summary]} |
| GET /api/incidents/:id | Admin; summary plus recipient roster |
| GET /api/responses/me | Client bearer; {responses:{alertId:response}} |
| POST /api/responses | Client bearer; {alertId,status:"safe" or "needs-help",message?} |
| POST /api/incidents/:id/notify | Admin; {note?}, manual confirmation only |
| POST /api/incidents/:id/all-clear | Admin; {message?}, same hostels/assembly, signed clear notice |
| POST /api/reports | Client bearer; {requestId,category,message}, category security/medical/fire/other |
| GET /api/reports | Admin; {reports:[...]} |
| PATCH /api/reports/:id | Admin; {status:"resolved"} |

Admin accepts the existing session or x-admin-pin; client bearer is id.token from enrollment. Help details max 400 UTF-8 bytes; report details max 800. /api/sync now also supplies contacts and the authenticated client's saved responses. Existing /api/ack and /api/acks use the same response service. Data remains atomically persisted in data/alerts.json, with incidents, responses, reports and campus keys added without deleting existing history or keys.

Verification: 11 tests passed, 46 JavaScript files passed syntax checks. Live browser checks covered Cricket ground, help request appearing in admin, safe replacing help, response roster, report submission/resolution, manual timeline event and all-clear returning the student to the calm screen. Real phone/network reception remains a separate physical demonstration.

All-clear selection fix: newly sent incidents are automatically selected; inactive incidents show why all-clear is disabled. Browser verified the active incident opens the all-clear confirmation. No all-clear was submitted during this UI check.

Compact incident overview: cleared/expired incidents replace the full blue card with a short status row and View responses. Active incidents retain metrics and timeline. Empty history has a compact ready status. No backend records are removed.

## Complete first-party source inventory

| Path | Bytes | SHA-256 |
|---|---:|---|
| `.env.example` | 520 | `bc976ef62605796f8fb2efe77b822481ec77fa575c770e37851a96aad282b920` |
| `.gitignore` | 55 | `1daf08cfb3dcf03cf3ff2f8d9cad6bdda983d93e764d8968d569ee20c8994bf8` |
| `PROTOCOL.md` | 16567 | `c0a5975c6459b001e0004491e17c0a39a3cab3bfbad3009f432b70218b94ac63` |
| `README.md` | 12845 | `4a4cdf5b30fc1072780a7c02d07810909aa8f0849551ef8e5dd282e527cc53e2` |
| `START-CAMPUSGUARD.cmd` | 1021 | `d003d0866db32bad82e4797e2ab2ad203491ab7a6e7fa8cfebaba944b94a3a7e` |
| `START-HERE.txt` | 5053 | `792b0bf44eace944df141cf04291165cf739028831118167ad11a6ce581865a0` |
| `STOP-CAMPUSGUARD.cmd` | 103 | `dad46696215398538a2efeec7e714884e4bf5940f2d650b332b64837a95a7839` |
| `VERIFIED.md` | 7484 | `72bb870eb9268ffb3ce5637c68aeac9c90645583159e742eefa54d75da6261ca` |
| `agent/notify.ps1` | 816 | `1f4ff743fbbecbe536b70dec6f1edf06299d4200ee2f61fd5696868efe09fcae` |
| `agent/udp-listener.js` | 111 | `9a103d2f0b487748a1c2866452bcac2b89ed9e26604403e2f4fb3b2b6a3b5cab` |
| `bridge/ble_bridge.ps1` | 7091 | `62abfa65d9e281ba7fed195535b10dd307d722c21530a8546627fef5bd1991bd` |
| `bridges/udp-receiver.js` | 3396 | `247d2e2fff54a1bd63372251876d926434e1518cc5abd97a1f9e049f55ed8feb` |
| `package-lock.json` | 44125 | `c406d6f7144293a0a80bfe94224b4c814cca8399648f7670a849bde0ad5945e7` |
| `package.json` | 938 | `f2091187adb65d85d7bc4247e5e114cdfae7178c5c019d820fc94546ea4ecd54` |
| `public/admin.html` | 11111 | `4f9fcfd01bf597b9f610dbd09a3fbf7920e611aa944d5592c8c9940bd5128894` |
| `public/icons/icon-192.png` | 1485 | `9cf703b4a7f4ec26854f323c851f5b7fdb29160d896b7eaba99f50196f8ae522` |
| `public/icons/icon-512.png` | 4681 | `42a7dc894af25de7d8b26e1c4eef7735b18e6b1faeb98893f0e111800d0c8005` |
| `public/icons/shield.svg` | 365 | `6c53dc580f0558ee9060058562995de0126d7b076aec315b5abc536be8026e8c` |
| `public/index.html` | 7493 | `169f967c0abb786c4b4b836e5fa040ab903085a027a740177602ce9f596beef2` |
| `public/js/admin-incidents.js` | 7915 | `a89586d5b20b3296a1063d93cd1722071766af57c1d3a1cfc9251eab8000293f` |
| `public/js/admin.js` | 14605 | `3f471a65a747a9ff8f4b0606422838994bb2e6f00b46be63e4cea511ba6c4d88` |
| `public/js/alarm.js` | 1085 | `a45dac747f4d313d2c46d6049a5188cecc1ce1819076cab5e9be280c85696dea` |
| `public/js/client.js` | 20157 | `494576de872421bdf1bdb6ab27fd83bdb66d521f3724a6efa890ccedaf8e57c0` |
| `public/js/connection.js` | 5318 | `edacbd88867693b681027b09b8a5c5850844fb5aad73b40edfcb233587b664d0` |
| `public/js/peer-relay.js` | 4503 | `06f72b7384ecddb3fd4b165fddbde621ac44682bdc332b8cb21c8f060896ad64` |
| `public/js/ui.js` | 1886 | `74fd8203d87d8d6fd4a504b7a038c2d528b7a4f7fd4722d960349bf5e6891d31` |
| `public/manifest.webmanifest` | 364 | `bdd63bf23be3b8c45e16c209ac54c5c087ee65dea0e81a1c809fdf92ba94c89d` |
| `public/setup.html` | 3890 | `74a58f06968d8d7b72ee21749af7e640f7d505a62998866df4f212500c52af5a` |
| `public/styles.css` | 34892 | `0340578a3b2a4a3e13a1daba4985cb6b160b9a0689b9e7d0971954b7bb98696e` |
| `public/sw.js` | 1144 | `bbfdffd8ec44246887998d31b882886e6bc09edf8027741a6d89ac070ac90149` |
| `scripts/ble-smoke.js` | 1167 | `1b9ca6af2345983d1c9c65d7fe53c5b93c3db9a00a441563d1924e8973d49ecb` |
| `scripts/check.js` | 715 | `311b3b2dcb23ad58b1c898c05d698166fc436843f63027b11bc967b3b62a72d2` |
| `scripts/curl-tests.ps1` | 10659 | `01d385e13a17bd5b29ff3647c0fdd55bf828229cb1099cdfb1af99b47080b7a3` |
| `scripts/running.js` | 256 | `2768731aa21f54f340d8eaa8b285e20b6beb27a7be1c4dc9a8cac9b03403420f` |
| `scripts/seed.js` | 998 | `304855bcff9f3bd271898b11e5678501346e228e655343f21b8da039f67af331` |
| `scripts/setup-https.js` | 3013 | `e56831fc360816a15c26436bf38949c855fbe3709bdde2a96866608d23b9ae6e` |
| `scripts/stop.ps1` | 989 | `7f595a999f74142df9fc24eb1d3ab518168d787c3f19fe9a88c61a1fdca6e963` |
| `server.js` | 1912 | `76514f5cdbc48393c41c0a31965921a73619083404f62d248d38967ab559ef9d` |
| `server/additions.js` | 103 | `856f67e6b89bc3d294546231e9e2c597ecd448fc115c3635b5873e3c91f26713` |
| `server/config.js` | 49 | `56cede2c56495eabe52fd423bcd4b115d54b602a8da29a9ce2c3aabbedf85c81` |
| `server/index.js` | 319 | `4cbb37192daca21cb8fdef87822e402f2eeb9e074787aa8e599dbd1207671c25` |
| `server/store.js` | 42 | `b18dfc2a9798f8ef3bd71799bae180c852184524e5f14011b2cf756609c00793` |
| `server/transports/index.js` | 137 | `611d7cb670af7f1490b823bbd3ba1e00f5d165f2b56ccc828e888446ac5af034` |
| `shared/campus.js` | 350 | `2cf61804e2a4f9d3e9bc3e18d0f7b1d68455a222493cf9021ef506caa21b4935` |
| `shared/protocol.js` | 4488 | `56c592c0d5f6726af77fa241fcccfe4b20ecff680e397c8081a0a69d628afcec` |
| `src/additions.js` | 5436 | `67cd67c95ecb20708570fe049de54a48d9ab64cd0a17b620cfacd974ebe31fc1` |
| `src/alerts.js` | 3036 | `07ac2fc67088161d4e9869e4099b2257e8fbf5f3f432d0a4e1592ec486d5cc8b` |
| `src/app.js` | 20375 | `3bae954a5ceca39b304efbdf342ff4fc1e29b5f661588bd8b2b16d1e236a7ae1` |
| `src/clients.js` | 1085 | `1cbc782033183be3997d4a7e585d802d23fc9971969631e1cd093d109752b1d4` |
| `src/config.js` | 634 | `b2eec1a65185e50e77bf32cce5264b75e55b15a136a1bcfd83ba466bdc7a7277` |
| `src/incidents.js` | 8036 | `b8ad571ac190876067ebfabf92d767543e98839c210ccd8a3fad703525a7ea52` |
| `src/store.js` | 1285 | `e2f046c706bf481488ff367daa852fb3465aebcf0c69899cde212d392e4f9942` |
| `src/transportManager.js` | 1384 | `54b25b503e5eb326c9a94adab3bb87a1f69024951a3abc82af0c61357cbfcaa7` |
| `src/transports/base.js` | 804 | `1e328bde680e478cee8431951574d8159343545d4cc6d33cd2f32338c4d8756b` |
| `src/transports/ble-real.js` | 9294 | `b8e77ee9a70cfe00fce06981e15cd4be74fe5a023762c4ff160e98f73dcb946b` |
| `src/transports/ble-sim.js` | 1824 | `678548de4ec4cde6e774f1aa4c3e93156f2931845d9bc6b69edb9de82756fe56` |
| `src/transports/multicast.js` | 128 | `646bd76f99b769a2fe3cdd42ba96dc16a445c6d682f67e374db2fd481389d1ae` |
| `src/transports/polling.js` | 217 | `e1e995e4c7b3d40cd478f86ccb2f925b791b59c762c9453fe6ac43fb80113d46` |
| `src/transports/sse.js` | 613 | `2bd9eebe6257cd24e7c39afb55b1e361644d7064ccd35d369cb480a4ae77ab64` |
| `src/transports/udp.js` | 1615 | `893ed8dac678d8043d91de41b18ecb56e4fdadac8d9823802b55c8d13b058fce` |
| `src/transports/webrtc-signal.js` | 569 | `5982616a84486936ff0576a9ade7bad2758a9e330dbddf79caf1b2e8bdfbbcf3` |
| `src/transports/ws.js` | 1153 | `b52fe724371a069fa38b58c1ab15e84c49816ea1b7f8b5a1abda367a25372284` |
| `src/zones.js` | 1408 | `b9b468080e1407c25c25528e5b33fa1ba0da1df0830424a9515b4e00cf5cbb76` |
| `tests/additions.test.js` | 6747 | `f36c4435f2dddb3c6d3e6293624f542a5c6f306c3ad9a1a7be6c0a3f3a40da1c` |
| `tests/ble-real.test.js` | 2395 | `59f3495c7d3a8b79b18c015556664bd3a431aa7361dd43612667caa1d6450df4` |
| `tests/hostels.test.js` | 8921 | `3e4b36e2f490f7b98f7bf7f8c1abfd66f8703a70b8b8fe906043f2844973f53a` |
| `tests/incidents.test.js` | 5019 | `96733b76c5db8b2607855da08a6e072fec90dcf42cd87493a4084a58a70fc015` |
| `tests/integration.test.js` | 6379 | `7cf43a5a7ce1b5781931a1789a531f0cc053fa562e53eb0b3fe292982ebe4b95` |
| `tests/protocol.test.js` | 1834 | `ded328f90676f04a314a27c2bb5fab9ff15df0d5369d6cc706e8f1a8f6e5f008` |

### campusguard/.env.example

<!-- CAMPUSGUARD_SOURCE {"path": ".env.example", "encoding": "utf-8", "sha256": "bc976ef62605796f8fb2efe77b822481ec77fa575c770e37851a96aad282b920"} -->
```text
ADMIN_PIN=404404
HTTP_PORT=8080
HTTPS_PORT=8443
MDNS_ENABLED=true
UDP_ENABLED=true
# Optional: subnet broadcast, e.g. 192.168.1.255. Defaults to limited broadcast.
UDP_BROADCAST=255.255.255.255
UDP_PORT=41234
# Multicast groups are derived from src/zones.js; receivers join their hostel + ALL.
# Optional IPv4 of the Wi-Fi adapter (for multicast on multi-adapter laptops).
UDP_INTERFACE=

# Real BLE uses built-in Windows APIs; there is no automatic simulated fallback.
BLE_ENABLED=true
BLE_DWELL_MS=1200
```

### campusguard/.gitignore

<!-- CAMPUSGUARD_SOURCE {"path": ".gitignore", "encoding": "utf-8", "sha256": "1daf08cfb3dcf03cf3ff2f8d9cad6bdda983d93e764d8968d569ee20c8994bf8"} -->
```text
node_modules/
.env
certs/*
data/*
!data/.gitkeep
*.log
```

### campusguard/PROTOCOL.md

<!-- CAMPUSGUARD_SOURCE {"path": "PROTOCOL.md", "encoding": "utf-8", "sha256": "c0a5975c6459b001e0004491e17c0a39a3cab3bfbad3009f432b70218b94ac63"} -->
````markdown
# CampusGuard protocol v1

All full-alert transports carry an identical signed immutable alert. Runtime `receivedVia` is local metadata and is excluded from signing.

```json
{"v":1,"kind":"alert","alert":{"v":1,"id":"7ac912ef:42","serverId":"7ac912ef","seq":42,"timestamp":"2026-10-24T10:00:00.000Z","ttl":300,"type":"fire","severity":3,"zone":1,"zones":[1],"message":"DEMO ONLY: use the east exit.","signature":"BASE64URL_ECDSA_P256_SIGNATURE"}}
```

- `serverId`: persistent random 32-bit installation ID, eight lowercase hex characters. `seq`: persistent unsigned 32-bit counter, allocated once per accepted alert. `id`: `${serverId}:${seq}`.
- `timestamp`: server-issued ISO UTC time, whole-second precision. `ttl`: original lifetime in seconds, 1–3600. Retransmissions do not extend expiry.
- Types: fire, earthquake, medical, security, drill, all-clear. Severity: 1 advisory, 2 urgent, 3 critical. Zone IDs: 0 ALL; 1 Visvesvaraya; 2 Abdul Kalam; 3 Ramanujan; 4 Kalpana Chawla.
- Message: 1–400 UTF-8 bytes. Entire UDP envelope must also fit 1,200 bytes. JSON escaping can expand unusual messages.
- ECDSA P-256 / SHA-256 signature uses raw 64-byte IEEE-P1363 R||S format, Base64URL encoded. Signed UTF-8 content is `JSON.stringify([v,id,serverId,seq,timestamp,ttl,type,severity,zone,message,zones])` for new alerts. Historical alerts without zones retain the original form without the last element. Public JWK comes from same-origin `/api/config`. Key persists with server state. HTTPS protects initial key distribution. Secure-context clients verify before accepting or relaying. HTTP clients without WebCrypto accept direct server transports and disable peer relay.
- Deduplicate by `id`, not a highest-seen-sequence cutoff. Keep sequence cursor separate from peer delivery; advance it only after successful server synchronization.
- All-clear establishes a per-zone sequence marker. Zone 0 clears all earlier alerts for every zone. A zone-specific clear suppresses every earlier alert as displayed in that zone, including an earlier ALL alert. Newer alerts remain active. Clear markers persist after TTL; peer inventory retains latest signed clear notices as tombstones.

## REST

JSON body/response; errors are `{error: string}`. Admin routes accept `x-admin-pin` or the existing HttpOnly, SameSite=Strict session cookie (Secure on HTTPS). Client routes require `Authorization: Bearer CLIENT_ID.TOKEN`. Server hashes stored client tokens. Mutating browser requests reject foreign Origin headers. Login rate limit: ten attempts per IP per minute. Session lifetime: eight hours.

| Method / route | Contract |
|---|---|
| GET `/api/alerts?since=N` | Client Bearer token or admin authentication; zone-filtered replay for clients; returns alerts, cursor/lastSeq, clear markers and reset flag |
| GET `/api/latest` | Client or admin; latest stored matching alert (possibly expired), plus an explicit expired flag |
| GET `/api/stats/zones` | Admin; four hostel rows with id, name, connectedClients, acksReceived and lastAlertTime |
| GET `/api/stats` | Admin; persistent per-alert receipt/safe counts and runtime transport health |
| GET `/api/transports` | Admin; all transport health entries, including sent/failed/errors |
| GET `/api/config` | Public zones, types, serverId, serverTime, signing publicKey, HTTPS availability |
| POST `/api/login` | `{pin}` → session cookie |
| POST `/api/logout` | Admin; expires session cookie |
| POST `/api/clients` | `{zone,name?}` → `{id,token,zone,name,createdAt}`; zone must be 1–4 |
| PATCH `/api/clients/me` | Client; `{zone}` → updated zone |
| POST `/api/alerts` | Admin; `{type,severity,zones:[1,2],message,ttl?}` → `{alert,transports}`; persistence precedes dispatch |
| GET `/api/sync?after=N&serverId=ID&transport=MODE` | Client; `{serverId,serverTime,cursor,alerts,clearMarkers,reset,zone}`. Initial/reset sync returns current active alerts. Incremental sync includes matching new unexpired alerts and all-clear records. |
| POST `/api/receipts` | Client; `{alertId,via}` → `{recorded,duplicate}` |
| POST `/api/ack` | Alias of `/api/acks`; registered client authentication remains required |
| POST `/api/acks` | Client; `{alertId,status:"safe",via?}` → `{recorded,duplicate}` |
| GET `/api/stream` | Alias of `/api/events`; supports since or Last-Event-ID replay |
| GET `/api/events` | Client; SSE ready/heartbeat/alert events, alert event ID = alert ID |
| GET `/api/history` | Admin; latest 200 alerts with unique receipt/safe counts |
| GET `/api/health` | Admin; runtime transport counters, health, zone connections and local join URLs |
| POST `/api/transports/NAME` | Admin; `{enabled?}`. BLE supports enabled/disabled and mode:"native" (or "real"); simulator mode is rejected. Unavailable hardware is reported without simulated delivery |
| GET `/api/qr?url=URL` | Admin; locally generated QR SVG for local addresses only |

Receipt and acknowledgement uniqueness is `(alertId,clientId)`. Token identity wins over any supplied clientId. Receipt = application reports reception; safe acknowledgement = explicit user action. Neither means physical rescue verification.

## WebSocket and SSE

`/ws` must receive `{kind:"hello",clientId,token,lastSeq,zone}` within five seconds. Hello also accepts `lastSeq` (or legacy `cursor`) and optional `serverId`. The server responds with ready metadata, replays matching unexpired/uncleared alerts and signed clear notices, then sends `{kind:"replay-complete",cursor,clearMarkers,reset}`. Wrong installations or a cursor beyond the server head reset replay safely. It sends canonical alert envelopes, `{kind:"heartbeat",serverTime}`, and `{kind:"peers",enabled,peers:[{id,zone}]}`. Client may send `{kind:"receipt",alertId,via:"ws"}` and `{kind:"ack",alertId,status:"safe"}`; REST equivalents work for every connection mode.

SSE has event names `ready`, `heartbeat`, `alert`; data for an alert is the canonical JSON envelope, with `id: SERVERID:SEQ`. The client streams via fetch so credentials stay in Authorization headers, not URLs. Cursor sync repairs reconnect gaps independently of SSE event IDs.

## BLE binary

21 application bytes plus three reserved manufacturer padding bytes within a legacy 31-byte advertising budget; little-endian multibyte core values.

| Offset | Size | Value |
|---|---|---|
| 0 | 1 | version = 1 |
| 1 | 4 | serverId as uint32 |
| 5 | 4 | seq uint32 |
| 9 | 1 | type: 1 fire, 2 earthquake, 3 medical, 4 security, 5 drill, 6 all-clear |
| 10 | 1 | severity 1–3 |
| 11 | 1 | zone ID from src/zones.js (0 ALL, 1–4 hostels) |
| 12 | 1 | reserved = 0; preserves the original 21-byte layout |
| 13 | 2 | original TTL seconds |
| 15 | 4 | issued-at Unix seconds |
| 19 | 2 | CRC-16/CCITT-FALSE over bytes 0–18; little-endian stored checksum |

CRC: polynomial 0x1021, initial 0xffff, refin/refout false, xorout 0. Check value for ASCII `123456789` is 0x29b1. CRC is not a signature.

The logical 31-byte layout is [02 01 06] Flags, [1B FF FF FF] manufacturer length/type/test company ID, the 21-byte core, and three reserved zero bytes. The real bridge submits company ID 0xFFFF and the final 24 manufacturer bytes to Windows; Windows controls the Flags section. The dashboard shows actual submitted manufacturerHex and confirmed Windows advertising starts, not a captured over-air frame or a remote receipt.

Real BLE is the production default. Requests queue up to 128 target summaries, rotate with bounded dwell, and retain original expiry. One multi-hostel alert uses separate target-zone summaries with the same identity. ALL uses zone 0. Expired jobs are removed. Native failures leave LAN transports independent; no automatic simulator fallback is used.

The bridge protocol is newline-delimited JSON: input {op:"publish",id,hex,expiresAt} where hex is the 21-byte core; {op:"stop",id} stops a publisher. Output kinds are ready, published, stopped, expired and error. published is emitted only after Windows reports Started. EOF/process exit stops the publisher. Manufacturer data is built using local Windows metadata/.NET APIs; no runtime download is involved.

Dashboard sessions receive {kind:"ble-beacon",zone,beacon} with simulated:false, transmitted:true, received:false, manufacturerHex and a local timestamp. Dashboard hello is {kind:"hello",role:"dashboard"}, authenticated by the existing session cookie. The browser student application does not scan these advertisements. A native receiver/scanner is required for direct phone BLE reception; CRC does not authenticate these compact summaries.

## UDP

UTF-8 datagram `{v:1,kind:"alert",transport:"udp-broadcast"|"udp-multicast",zone:[1,2],alert: FULL_SIGNED_ALERT}`. Maximum 1,200 bytes. Broadcast to configured subnet/limited broadcast; multicast to 239.255.42.1–239.255.42.4 for selected hostels or 239.255.42.99 for ALL, port 41234, IP TTL 1. A targeted packet does not go to the ALL group. Receivers join their own hostel group and the ALL group. Burst at 0/250/750 ms, repeated every five seconds while active. Receivers deduplicate by ID and apply alert expiry separately from IP TTL. Ordinary PWAs need a native receiver bridge for raw UDP. The included command-line receiver validates fields/TTL, deduplicates, logs, and uses a built-in Windows notification/sound helper; signature verification is performed by the browser peer path, not this demonstration receiver.

## WebRTC gossip

Server forwards `{kind:"signal",to,type:"offer"|"answer"|"ice",data}` as `{kind:"signal",from,type,data}`. No server DataChannel and no external ICE servers. Clients create reliable ordered `campus-alerts` channels in a deterministic ring with up to two outgoing neighbor choices. Simultaneous offers use client-ID ordering to resolve glare. Incoming connections may increase degree.

DataChannel messages: `{kind:"inventory",ids:[...]}`, `{kind:"want",ids:[...]}`, `{kind:"relay",alert,hops:1..4}`. Inventories run every five seconds. Signature, identity, time and schema are checked; expired ordinary alerts are ignored. Signed clear tombstones remain valid suppression records. Dedupe prevents repeated display and loops. Non-target clients may carry a verified packet without displaying it. Established channels survive lost signaling links; new links require the server. Browser link/forward counters are reported as `{kind:"peer-health",connections,forwards}` while WebSocket remains available.

## Multi-hostel compatibility and attribution

`zones` is a sorted unique non-empty array validated by src/zones.js. `[0]` denotes ALL and cannot be combined with hostel IDs. New envelopes retain numeric `zone` for a single target and set it to null for multiple targets; consumers use the canonical `zones` array. Legacy stored envelopes with numeric zone and no zones array remain accepted and preserve their original signed payload.

WS hello zone must match client registration; omitted zone supports older clients. Live room sends and reconnect replay include only matching hostel or ALL alerts. SSE and polling accept zone query parameters with the same authenticated-hostel restriction. All-clear updates each selected target marker; clearing one hostel does not clear a multi-hostel alert for other hostels.

BLE multi-target summaries encode each target separately with the same alert identity. A real transmit event zone is its advertised target (0 ALL or a selected hostel). It does not represent a measured receiver location. These summaries are CRC-checked and do not carry the full signed multi-hostel envelope.

Acknowledgements remain unique by alert/client, capture `zone` at receipt and are indexed in `acksByZone`. Changing enrollment cannot reattribute an old acknowledgement. GET /api/stats/zones reports cumulative saved acks, unique clients seen within 16 seconds, and the most recent matching alert timestamp (including ALL/history), or null. Legacy entries without a zone are attributed once using registration during index migration.

## Canonical backend modules and compatibility

server.js starts src/app.js. src/alerts.js handles signed durable creation/receipts; src/clients.js handles registration/authentication; src/store.js contains the preserved atomic store; src/transportManager.js dispatches adapters. Each transport has a module under src/transports. Older server/ paths are compatibility re-exports/entry aliases and do not create a second backend. Only src/zones.js is served publicly from the server source folder; public/ and shared protocol assets are served locally with cache revalidation.

No automatic simulation fallback is enabled because the latest project requirement is real Bluetooth without simulated delivery. ble-sim.js is explicitly injected by isolated tests only. The supported native bridge is Windows PowerShell/.NET rather than a Python dependency. No native phone receiver is bundled; the PWA remains a LAN client. These choices resolve the earlier specification's conflicting simulator and bridge-language requirements.

## Student safety and incident management update

Both student and admin screens are implemented with working backend storage, not static mockups. All four hostels use src/zones.js. Shared assembly choices in shared/campus.js are Football ground (default) and Cricket ground; the admin chooses one per alert, and assemblyPoint is signed with the alert. Existing signed alerts remain valid without this optional field.

Students have calm/active screens, sound setup, hostel selection, safety instructions, safe/help buttons, editable help details, contacts, assembly illustration and incident reporting. Safe/help responses replace the previous status for that device and incident. Pending responses and reports persist locally and retry on reconnection; idempotent request IDs prevent duplicate reports. Maps are illustrative, not GPS or verified evacuation routes. Browser background notifications are not guaranteed.

The medical number is 8571893627 as supplied for the prototype. Security is Not configured until an admin enters it. Contacts are editable and persisted. Tel links initiate calls only when the user selects them.

Admin incident cards show actual confirmed deliveries, latest safe/help counts, response roster/filter, timestamps and all-clear. The audience is a snapshot of registered matching devices, including late devices that actually receive/respond; it is not total campus enrollment or a guarantee of delivery. Safe percentage uses safe responses among confirmed deliveries. Staff-notified events are manual admin confirmations; the application does not send wardens messages or place calls. Reports are stored for admin review and can be resolved, without automatically broadcasting alerts.

New endpoints (all JSON):

| Endpoint | Access / body |
|---|---|
| GET /api/campus | Public contacts and assemblyPoints |
| PATCH /api/campus | Admin; {medicalPhone, securityPhone}, optional fields |
| GET /api/incidents | Admin; {incidents:[summary]} |
| GET /api/incidents/:id | Admin; summary plus recipient roster |
| GET /api/responses/me | Client bearer; {responses:{alertId:response}} |
| POST /api/responses | Client bearer; {alertId,status:"safe" or "needs-help",message?} |
| POST /api/incidents/:id/notify | Admin; {note?}, manual confirmation only |
| POST /api/incidents/:id/all-clear | Admin; {message?}, same hostels/assembly, signed clear notice |
| POST /api/reports | Client bearer; {requestId,category,message}, category security/medical/fire/other |
| GET /api/reports | Admin; {reports:[...]} |
| PATCH /api/reports/:id | Admin; {status:"resolved"} |

Admin accepts the existing session or x-admin-pin; client bearer is id.token from enrollment. Help details max 400 UTF-8 bytes; report details max 800. /api/sync now also supplies contacts and the authenticated client's saved responses. Existing /api/ack and /api/acks use the same response service. Data remains atomically persisted in data/alerts.json, with incidents, responses, reports and campus keys added without deleting existing history or keys.

Verification: 11 tests passed, 46 JavaScript files passed syntax checks. Live browser checks covered Cricket ground, help request appearing in admin, safe replacing help, response roster, report submission/resolution, manual timeline event and all-clear returning the student to the calm screen. Real phone/network reception remains a separate physical demonstration.
````

### campusguard/README.md

<!-- CAMPUSGUARD_SOURCE {"path": "README.md", "encoding": "utf-8", "sha256": "4a4cdf5b30fc1072780a7c02d07810909aa8f0849551ef8e5dd282e527cc53e2"} -->
````markdown
# CampusGuard — LAN alerts with real Bluetooth LE

Node.js 20, Express and ws. No cloud, CDN, external fonts, STUN or TURN at runtime. The Windows laptop serves the dashboard/student PWA, persists alerts and advertises compact summaries using its built-in Bluetooth adapter. No microcontroller or external beacon is required.

## Run on this laptop

The Windows ZIP includes Node 20 and installed dependencies. Extract the whole ZIP and double-click START-CAMPUSGUARD.cmd. Keep its window open.

```powershell
Set-Location "C:\Users\DELL\Documents\Codex\2026-10-24\we-have-to-m\outputs\campusguard"
.\START-CAMPUSGUARD.cmd
```

Admin: http://localhost:8080/admin.html. Default demo PIN: **404404**. Set ADMIN_PIN in .env and restart to change it. Phones use the current LAN URL printed by the server or its locally generated QR. Restart the launcher after changing Wi-Fi so HTTPS includes the current IP.

For a source-only checkout, install Node.js 20 and run once while online:

```powershell
npm.cmd ci
npm.cmd run setup:https
npm.cmd start
```

To stop, use Ctrl+C in the server window or STOP-CAMPUSGUARD.cmd. Existing launchers/imports using server/index.js remain compatible, but server.js is the canonical entry.

## Manual setup

- Connect the laptop and phones to one router/hotspot. Allow the requested Windows Node.js firewall permission on the trusted demo network; do not disable the firewall.
- Keep Bluetooth enabled on the laptop for the real advertiser. Transport lab shows supported/unavailable/advertising states. An unavailable radio does not stop LAN delivery.
- Open the student page on each phone, choose a hostel, tap Enable alerts & test sound, and keep the page active. Device sound/vibration/background behavior still needs testing.
- HTTP supports the main WS/SSE/polling demo. Trusted local HTTPS is required for phone PWA caching/installation and peer relay. Follow /setup.html to install the generated public CA yourself. Remove the demo CA after the event; never share private keys from certs/.
- Reload old admin/student tabs after applying this update. The UI uses the shared zone module directly, public assets revalidate, and the offline cache version has changed. Existing saved credentials/history remain intact.

## Hostels and targeting

src/zones.js is the only executable lookup table: 0 ALL, 1 Visvesvaraya, 2 Abdul Kalam, 3 Ramanujan, 4 Kalpana Chawla. The admin has hostel checkboxes; ALL is mutually exclusive with individual hostels. The student picker, map, history labels, BLE events, seed and API validation use this table.

POST /api/alerts accepts zones:[3], zones:[1,2], or zones:[0]. Arrays are normalized and validated. Legacy scalar zone requests and stored signatures are supported. New signed alerts include the canonical zones array and a compatibility zone projection: the single ID, or null for multiple targets. Use alertZones()/applicable() for targeting.

WS hello carries clientId, token, lastSeq, zone and optional serverId. Each socket joins its hostel and ALL; room unions prevent duplicate sends. SSE and polling accept ?zone= and enforce the registered hostel. A client moves through PATCH /api/clients/me; its WS room changes and old SSE streams close for reconnection.

Acknowledgements are unique by alert/client, capture the hostel at receipt, and are indexed in acksByZone alongside the existing ack table. Moving hostels does not move old acknowledgements. Legacy acks without a captured hostel are attributed once from registration during migration. GET /api/stats/zones reports unique clients seen within 16 seconds, cumulative acks and the most recent matching alert time, including ALL/history.

## Real BLE behavior

The default ble transport is src/transports/ble-real.js. It wraps bridge/ble_bridge.ps1, which uses built-in Windows Runtime Bluetooth APIs and an in-memory .NET helper for WinRT collections. No Python, third-party Bluetooth package, downloaded compiler or internet call is needed. Windows 10/11 and a peripheral-capable BLE adapter are required.

Alerts enter a bounded queue and rotate by target, normally every 1.2 seconds. Each target keeps the original ID/sequence/timestamp/TTL. A multi-hostel alert produces one summary per target; ALL encodes zone 0. The bridge submits 24 manufacturer bytes: the 21-byte core plus three reserved zeros, company ID 0xFFFF. Together with the manufacturer header and OS-managed Flags, this fits a legacy 31-byte advertising budget. Windows controls the actual Flags; the full hex layout in diagnostics is a logical view, not a captured radio frame.

sent counts **Windows advertising starts**, not remote receptions or the number of over-air repeats. The dashboard says LOCAL TRANSMIT and never draws inferred BLE coverage. Native receiver/scanner reception and physical range require a separate device. Compact packets contain a CRC, not the full alert text or its cryptographic signature.

The phone browser PWA receives over LAN. It does not automatically scan these advertisements or become a Bluetooth mesh. Direct BLE display/alarm on iPhone/Android needs a native receiver/app. There is no automatic simulated fallback. If Bluetooth is unavailable, its health reports that honestly while LAN continues. ble-sim.js remains only as an explicit, dependency-injected test/development adapter; production never selects it.

Test the actual adapter separately when another CampusGuard advertiser is not running:

```powershell
npm.cmd run ble:probe
.\runtime\node.exe scripts/ble-smoke.js
```

The smoke test broadcasts a short DEMO ONLY summary and stops. It proves Windows started advertising, not that a phone received it.

## Network transports and recovery

- WS primary → fetch-stream SSE → two-second polling. Heartbeats and reconnect synchronization repair gaps; dedupe stops repeated alarms. Persistent clear markers suppress delayed cleared alerts.
- UDP broadcast carries all signed full alerts on port 41234. Multicast uses 239.255.42.1 through .4 for targeted hostels and 239.255.42.99 for ALL. Receivers join their own group plus .99.
- Browser WebRTC uses server signaling only, local ICE candidates, inventory/want gossip and a four-hop forwarding limit. Secure-context clients verify signatures before relaying.
- Bonjour advertises campus-alert.local locally. Use the printed LAN IP when mDNS is unsupported or blocked. MDNS_ENABLED=false disables discovery.
- Each transport implements send(alert) and health(), with sent/failed counters. Adapter failures/timeouts are isolated. A queued BLE job is distinct from a confirmed OS advertising start.
- data/alerts.json uses temporary write, flush, backup and atomic replacement. Existing state.json migrates once without deleting the original. Transport counters reset on restart; alerts/acks/receipts persist. A single process owns the file.

## Commands and endpoint tests

Use another terminal in the project folder:

```powershell
.\runtime\node.exe scripts/seed.js
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/curl-tests.ps1
.\runtime\node.exe scripts/check.js
.\runtime\node.exe --test --test-concurrency=1 tests/protocol.test.js tests/integration.test.js tests/additions.test.js tests/hostels.test.js tests/ble-real.test.js
```

The seed sends exactly five advisory DEMO ONLY alerts, one for each hostel and one ALL. The curl tests create demo alerts/test clients, test every REST API including login/logout, enrollment changes, history, QR, hostel stats, SSE and WS upgrade, and reject simulated BLE mode. Full authenticated WS replay/signaling is checked by the Node tests. Test fixtures are explicitly injected and do not use the real radio.

Optional UDP receiver, in a separate terminal:

```powershell
.\runtime\node.exe agent/udp-listener.js --zone 1
```

The listener logs alerts, uses Windows popup/OS sound, and deduplicates IDs. --mute silences it; --no-notify disables popup/sound. It validates fields/TTL/hostel but does not authenticate UDP signatures. Optional receipt reporting uses --server and --credential from a registered client of the same hostel. UDP_INTERFACE selects the Wi-Fi adapter on multi-adapter machines; UDP_BROADCAST overrides limited broadcast.

## Failure boundaries

| Failure | Behavior |
|---|---|
| Internet lost, LAN intact | Local paths continue. |
| Entire LAN/router down | Browser/IP delivery stops; cached instructions remain. Real BLE can continue only while the laptop/server/radio remain available and needs native receivers. |
| Server crash | Saved state survives. Established peers can exchange cached alerts; new authoritative alerts and BLE advertising wait for restart. |
| Phone Wi-Fi drop | Cached alerts/pending responses remain; reconnect synchronizes. Phone browsers do not receive automatic BLE fallback. |
| Late reconnect | Matching active alerts and clear markers repair state. Expired ordinary alerts do not sound. |
| One transport fails | Other transports continue. |
| App closed/suspended | Background alarms/delivery are not guaranteed. |

The project is a hackathon prototype. Multi-device routing, actual phone behavior and BLE range/reception remain manual verification tasks. See PROTOCOL.md, VERIFIED.md and docs/SPEC.md for the complete protocol, evidence and source snapshot.

## Student safety and incident management update

Both student and admin screens are implemented with working backend storage, not static mockups. All four hostels use src/zones.js. Shared assembly choices in shared/campus.js are Football ground (default) and Cricket ground; the admin chooses one per alert, and assemblyPoint is signed with the alert. Existing signed alerts remain valid without this optional field.

Students have calm/active screens, sound setup, hostel selection, safety instructions, safe/help buttons, editable help details, contacts, assembly illustration and incident reporting. Safe/help responses replace the previous status for that device and incident. Pending responses and reports persist locally and retry on reconnection; idempotent request IDs prevent duplicate reports. Maps are illustrative, not GPS or verified evacuation routes. Browser background notifications are not guaranteed.

The medical number is 8571893627 as supplied for the prototype. Security is Not configured until an admin enters it. Contacts are editable and persisted. Tel links initiate calls only when the user selects them.

Admin incident cards show actual confirmed deliveries, latest safe/help counts, response roster/filter, timestamps and all-clear. The audience is a snapshot of registered matching devices, including late devices that actually receive/respond; it is not total campus enrollment or a guarantee of delivery. Safe percentage uses safe responses among confirmed deliveries. Staff-notified events are manual admin confirmations; the application does not send wardens messages or place calls. Reports are stored for admin review and can be resolved, without automatically broadcasting alerts.

New endpoints (all JSON):

| Endpoint | Access / body |
|---|---|
| GET /api/campus | Public contacts and assemblyPoints |
| PATCH /api/campus | Admin; {medicalPhone, securityPhone}, optional fields |
| GET /api/incidents | Admin; {incidents:[summary]} |
| GET /api/incidents/:id | Admin; summary plus recipient roster |
| GET /api/responses/me | Client bearer; {responses:{alertId:response}} |
| POST /api/responses | Client bearer; {alertId,status:"safe" or "needs-help",message?} |
| POST /api/incidents/:id/notify | Admin; {note?}, manual confirmation only |
| POST /api/incidents/:id/all-clear | Admin; {message?}, same hostels/assembly, signed clear notice |
| POST /api/reports | Client bearer; {requestId,category,message}, category security/medical/fire/other |
| GET /api/reports | Admin; {reports:[...]} |
| PATCH /api/reports/:id | Admin; {status:"resolved"} |

Admin accepts the existing session or x-admin-pin; client bearer is id.token from enrollment. Help details max 400 UTF-8 bytes; report details max 800. /api/sync now also supplies contacts and the authenticated client's saved responses. Existing /api/ack and /api/acks use the same response service. Data remains atomically persisted in data/alerts.json, with incidents, responses, reports and campus keys added without deleting existing history or keys.

Verification: 11 tests passed, 46 JavaScript files passed syntax checks. Live browser checks covered Cricket ground, help request appearing in admin, safe replacing help, response roster, report submission/resolution, manual timeline event and all-clear returning the student to the calm screen. Real phone/network reception remains a separate physical demonstration.
````

### campusguard/START-CAMPUSGUARD.cmd

<!-- CAMPUSGUARD_SOURCE {"path": "START-CAMPUSGUARD.cmd", "encoding": "utf-8", "sha256": "d003d0866db32bad82e4797e2ab2ad203491ab7a6e7fa8cfebaba944b94a3a7e"} -->
```bat
@echo off
setlocal
cd /d "%~dp0"
set "CG_NODE=node"
if exist "runtime\node.exe" set "CG_NODE=%~dp0runtime\node.exe"
"%CG_NODE%" --version >nul 2>&1
if errorlevel 1 (
  echo Install Node.js 20 or newer, then open this file again.
  pause
  exit /b 1
)
if not exist "node_modules\express\package.json" (
  echo Dependencies are missing. Run npm install once while online.
  pause
  exit /b 1
)
"%CG_NODE%" scripts\running.js
if not errorlevel 1 (
  echo CampusGuard is already running on this laptop.
  echo Open http://localhost:8080/admin.html and use PIN 404404 unless you changed it.
  echo To restart, open STOP-CAMPUSGUARD.cmd first.
  pause
  exit /b 0
)
echo Preparing local HTTPS for the current network...
"%CG_NODE%" scripts\setup-https.js
if errorlevel 1 (
  echo HTTPS setup failed. See the message above.
  pause
  exit /b 1
)
echo.
echo Open http://localhost:8080/admin.html after the server starts.
echo Keep this window open during the demo.
"%CG_NODE%" server.js
pause
```

### campusguard/START-HERE.txt

<!-- CAMPUSGUARD_SOURCE {"path": "START-HERE.txt", "encoding": "utf-8", "sha256": "792b0bf44eace944df141cf04291165cf739028831118167ad11a6ce581865a0"} -->
```text
CAMPUSGUARD — CURRENT BUILD
==========================
1. Extract the entire Windows ZIP and run START-CAMPUSGUARD.cmd.
2. Open http://localhost:8080/admin.html. Demo PIN: 404404.
3. Connect phones to the laptop's Wi-Fi/router/hotspot and open its printed LAN URL.
4. Choose Visvesvaraya, Abdul Kalam, Ramanujan or Kalpana Chawla on student pages.
5. Tap Enable alerts & test sound and keep pages open.
6. Admin: tick one or more hostel checkboxes, or ALL alone. Send a DEMO ONLY drill.
7. Students acknowledge; hostel response totals and alert history update.

AFTER UPDATING
Restart the server and reload old admin/student tabs. The old zone names are
replaced throughout. Saved historical message text remains signed and unchanged.

REAL BLUETOOTH
Keep the Windows laptop's Bluetooth enabled. Transport lab shows actual adapter
health and Windows advertising starts. No simulated coverage is shown. The phone
browser pages receive over LAN; direct BLE reception needs a native scanner/app.
An OS advertising start does not prove phone reception or campus-wide range.

FALLBACK DEMO
Disable WebSocket in Transport lab: clients use SSE. Disable SSE too: polling.
Use Restore connections afterward. Unavailable Bluetooth does not stop LAN.

HTTPS / STOP
Use /setup.html for trusted local HTTPS, PWA caching and browser peer relay.
Use Ctrl+C or STOP-CAMPUSGUARD.cmd to stop. History and responses stay saved.

Node.js 20 and dependencies are included. README.md has exact Windows commands.
docs/SPEC.md contains the current specification and complete first-party source.

## Student safety and incident management update

Both student and admin screens are implemented with working backend storage, not static mockups. All four hostels use src/zones.js. Shared assembly choices in shared/campus.js are Football ground (default) and Cricket ground; the admin chooses one per alert, and assemblyPoint is signed with the alert. Existing signed alerts remain valid without this optional field.

Students have calm/active screens, sound setup, hostel selection, safety instructions, safe/help buttons, editable help details, contacts, assembly illustration and incident reporting. Safe/help responses replace the previous status for that device and incident. Pending responses and reports persist locally and retry on reconnection; idempotent request IDs prevent duplicate reports. Maps are illustrative, not GPS or verified evacuation routes. Browser background notifications are not guaranteed.

The medical number is 8571893627 as supplied for the prototype. Security is Not configured until an admin enters it. Contacts are editable and persisted. Tel links initiate calls only when the user selects them.

Admin incident cards show actual confirmed deliveries, latest safe/help counts, response roster/filter, timestamps and all-clear. The audience is a snapshot of registered matching devices, including late devices that actually receive/respond; it is not total campus enrollment or a guarantee of delivery. Safe percentage uses safe responses among confirmed deliveries. Staff-notified events are manual admin confirmations; the application does not send wardens messages or place calls. Reports are stored for admin review and can be resolved, without automatically broadcasting alerts.

New endpoints (all JSON):

| Endpoint | Access / body |
|---|---|
| GET /api/campus | Public contacts and assemblyPoints |
| PATCH /api/campus | Admin; {medicalPhone, securityPhone}, optional fields |
| GET /api/incidents | Admin; {incidents:[summary]} |
| GET /api/incidents/:id | Admin; summary plus recipient roster |
| GET /api/responses/me | Client bearer; {responses:{alertId:response}} |
| POST /api/responses | Client bearer; {alertId,status:"safe" or "needs-help",message?} |
| POST /api/incidents/:id/notify | Admin; {note?}, manual confirmation only |
| POST /api/incidents/:id/all-clear | Admin; {message?}, same hostels/assembly, signed clear notice |
| POST /api/reports | Client bearer; {requestId,category,message}, category security/medical/fire/other |
| GET /api/reports | Admin; {reports:[...]} |
| PATCH /api/reports/:id | Admin; {status:"resolved"} |

Admin accepts the existing session or x-admin-pin; client bearer is id.token from enrollment. Help details max 400 UTF-8 bytes; report details max 800. /api/sync now also supplies contacts and the authenticated client's saved responses. Existing /api/ack and /api/acks use the same response service. Data remains atomically persisted in data/alerts.json, with incidents, responses, reports and campus keys added without deleting existing history or keys.

Verification: 11 tests passed, 46 JavaScript files passed syntax checks. Live browser checks covered Cricket ground, help request appearing in admin, safe replacing help, response roster, report submission/resolution, manual timeline event and all-clear returning the student to the calm screen. Real phone/network reception remains a separate physical demonstration.
```

### campusguard/STOP-CAMPUSGUARD.cmd

<!-- CAMPUSGUARD_SOURCE {"path": "STOP-CAMPUSGUARD.cmd", "encoding": "utf-8", "sha256": "dad46696215398538a2efeec7e714884e4bf5940f2d650b332b64837a95a7839"} -->
```bat
@echo off
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\stop.ps1
pause
```

### campusguard/VERIFIED.md

<!-- CAMPUSGUARD_SOURCE {"path": "VERIFIED.md", "encoding": "utf-8", "sha256": "72bb870eb9268ffb3ce5637c68aeac9c90645583159e742eefa54d75da6261ca"} -->
```markdown
# Verification record — hostel frontend/backend and real BLE

24 October 2026 · Windows · bundled Node.js 20.20.2.

## Automated checks

- Eleven passing tests and 46 JavaScript syntax checks, including the new server.js entry and src modules.
- Existing signature, atomic store, restart/sequence, expiry, clear markers, authentication, WS/SSE/polling, replay, dedupe and signaling checks remain passing after restructuring.
- Hostel tests cover four rooms plus ALL, target unions, reconnect/query filtering, wrong-hostel rejection, per-hostel BLE summaries/multicast groups, acknowledgement dedupe/attribution, hostel moves and restart persistence.
- Real BLE adapter tests cover rotating targets, confirmed OS-start accounting, unavailable hardware, continued LAN dispatch and no simulated fallback. They use explicit fake bridge dependencies, not the physical adapter.
- ble-sim is used only as an explicitly injected fixture by earlier compatibility tests; it is not selected by the running application.

## Actual Windows and UI checks

- Windows adapter probe returned lowEnergySupported=true and peripheralSupported=true on the laptop's Intel Bluetooth adapter.
- Real bridge smoke test reached Windows advertising status, with simulated=false and a submitted 24-byte manufacturer payload. It stopped cleanly afterward.
- The running server's real BLE transport reported advertising, realAvailable=true, sent>0, failed=0 and automaticSimulationFallback=false.
- Admin browser was reloaded and shows ALL, Visvesvaraya, Abdul Kalam, Ramanujan and Kalpana Chawla as checkboxes. Selecting two hostels clears ALL; the review lists both names.
- A DEMO ONLY two-hostel alert was sent through the actual admin UI, received on the registered student page via WS, acknowledged, and shown in the admin safety count. Student enrollment lists all four new names.
- Transport lab shows REAL BLE / ADVERTISING and local per-target transmission events with no inferred receiver coverage.
- Public assets return Cache-Control: no-cache; the student offline cache version is updated. Registrations/history are preserved. Old user-authored message text is not rewritten because alerts are signed immutable records.
- Updated Windows curl tests cover all REST APIs, hostel filtering/validation, per-hostel statistics, both SSE paths, session logout, QR and WS upgrade. Full WS messages/signaling are checked by integration tests.
- Earlier live UDP broadcast/multicast reception, campus-alert.local resolution, atomic state migration, browser fallback, peer-only delivery and offline queued-ack recovery were recorded; those implementations were preserved or moved without behavior changes.
- All five multicast groups were received locally in a separate verification (port 41235); production defaults to 41234.

## Remaining physical-device checks

- A second BLE receiver must confirm the advertised bytes over the air. Windows Started is not a remote delivery or range measurement.
- Native phone BLE reception needs a scanner/app; the browser PWA receives LAN alerts. No native phone alert receiver is bundled.
- Actual phone siren/vibration/background/PWA behavior, trusted HTTPS, firewall and hotspot client isolation remain device-specific rehearsal tasks.
- Multi-device multicast routing and mDNS need the intended demonstration network.

The Windows ZIP excludes .env, data, signing keys and private certificates. The app makes no runtime cloud/CDN calls. No ESP32, Arduino or external beacon is required.

## Student safety and incident management update

Both student and admin screens are implemented with working backend storage, not static mockups. All four hostels use src/zones.js. Shared assembly choices in shared/campus.js are Football ground (default) and Cricket ground; the admin chooses one per alert, and assemblyPoint is signed with the alert. Existing signed alerts remain valid without this optional field.

Students have calm/active screens, sound setup, hostel selection, safety instructions, safe/help buttons, editable help details, contacts, assembly illustration and incident reporting. Safe/help responses replace the previous status for that device and incident. Pending responses and reports persist locally and retry on reconnection; idempotent request IDs prevent duplicate reports. Maps are illustrative, not GPS or verified evacuation routes. Browser background notifications are not guaranteed.

The medical number is 8571893627 as supplied for the prototype. Security is Not configured until an admin enters it. Contacts are editable and persisted. Tel links initiate calls only when the user selects them.

Admin incident cards show actual confirmed deliveries, latest safe/help counts, response roster/filter, timestamps and all-clear. The audience is a snapshot of registered matching devices, including late devices that actually receive/respond; it is not total campus enrollment or a guarantee of delivery. Safe percentage uses safe responses among confirmed deliveries. Staff-notified events are manual admin confirmations; the application does not send wardens messages or place calls. Reports are stored for admin review and can be resolved, without automatically broadcasting alerts.

New endpoints (all JSON):

| Endpoint | Access / body |
|---|---|
| GET /api/campus | Public contacts and assemblyPoints |
| PATCH /api/campus | Admin; {medicalPhone, securityPhone}, optional fields |
| GET /api/incidents | Admin; {incidents:[summary]} |
| GET /api/incidents/:id | Admin; summary plus recipient roster |
| GET /api/responses/me | Client bearer; {responses:{alertId:response}} |
| POST /api/responses | Client bearer; {alertId,status:"safe" or "needs-help",message?} |
| POST /api/incidents/:id/notify | Admin; {note?}, manual confirmation only |
| POST /api/incidents/:id/all-clear | Admin; {message?}, same hostels/assembly, signed clear notice |
| POST /api/reports | Client bearer; {requestId,category,message}, category security/medical/fire/other |
| GET /api/reports | Admin; {reports:[...]} |
| PATCH /api/reports/:id | Admin; {status:"resolved"} |

Admin accepts the existing session or x-admin-pin; client bearer is id.token from enrollment. Help details max 400 UTF-8 bytes; report details max 800. /api/sync now also supplies contacts and the authenticated client's saved responses. Existing /api/ack and /api/acks use the same response service. Data remains atomically persisted in data/alerts.json, with incidents, responses, reports and campus keys added without deleting existing history or keys.

Verification: 11 tests passed, 46 JavaScript files passed syntax checks. Live browser checks covered Cricket ground, help request appearing in admin, safe replacing help, response roster, report submission/resolution, manual timeline event and all-clear returning the student to the calm screen. Real phone/network reception remains a separate physical demonstration.

All-clear selection fix: newly sent incidents are automatically selected; inactive incidents show why all-clear is disabled. Browser verified the active incident opens the all-clear confirmation. No all-clear was submitted during this UI check.

Compact incident overview: cleared/expired incidents replace the full blue card with a short status row and View responses. Active incidents retain metrics and timeline. Empty history has a compact ready status. No backend records are removed.
```

### campusguard/agent/notify.ps1

<!-- CAMPUSGUARD_SOURCE {"path": "agent/notify.ps1", "encoding": "utf-8", "sha256": "1f4ff743fbbecbe536b70dec6f1edf06299d4200ee2f61fd5696868efe09fcae"} -->
```powershell
$ErrorActionPreference = 'Stop'
$notification = [Console]::In.ReadToEnd() | ConvertFrom-Json
Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
$icon = New-Object System.Windows.Forms.NotifyIcon
try {
    $icon.Icon = [System.Drawing.SystemIcons]::Warning
    $icon.Text = 'CampusGuard'
    $icon.Visible = $true
    $icon.ShowBalloonTip(5000, [string]$notification.title, [string]$notification.message, [System.Windows.Forms.ToolTipIcon]::Warning)
    if ($notification.sound) { [System.Media.SystemSounds]::Exclamation.Play() }
    $deadline = [DateTime]::UtcNow.AddSeconds(5)
    while ([DateTime]::UtcNow -lt $deadline) {
        [System.Windows.Forms.Application]::DoEvents()
        Start-Sleep -Milliseconds 50
    }
} finally {
    $icon.Visible = $false
    $icon.Dispose()
}
```

### campusguard/agent/udp-listener.js

<!-- CAMPUSGUARD_SOURCE {"path": "agent/udp-listener.js", "encoding": "utf-8", "sha256": "9a103d2f0b487748a1c2866452bcac2b89ed9e26604403e2f4fb3b2b6a3b5cab"} -->
```javascript
// Reuse the existing listener; its CLI arguments pass through unchanged.
import '../bridges/udp-receiver.js';
```

### campusguard/bridge/ble_bridge.ps1

<!-- CAMPUSGUARD_SOURCE {"path": "bridge/ble_bridge.ps1", "encoding": "utf-8", "sha256": "62abfa65d9e281ba7fed195535b10dd307d722c21530a8546627fef5bd1991bd"} -->
```powershell
param([switch]$Probe)
$ErrorActionPreference = 'Stop'
[Console]::InputEncoding = New-Object System.Text.UTF8Encoding($false)
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)
function Emit($value) { [Console]::Out.WriteLine(($value | ConvertTo-Json -Compress -Depth 6)); [Console]::Out.Flush() }
$publisher = $null
try {
    Add-Type -AssemblyName System.Runtime.WindowsRuntime
    # Compile against the installed Windows metadata. C# handles the WinRT
    # IVector/IBuffer projections that Windows PowerShell exposes as COM objects.
    $winrt = [AppDomain]::CurrentDomain.GetAssemblies() | Where-Object { $_.GetName().Name -eq 'System.Runtime.WindowsRuntime' } | Select-Object -First 1
    $runtimeFacade = Get-ChildItem -LiteralPath "$env:SystemRoot\Microsoft.NET\assembly\GAC_MSIL\System.Runtime" -Filter System.Runtime.dll -Recurse | Select-Object -First 1
    $collectionsFacade = Get-ChildItem -LiteralPath "$env:SystemRoot\Microsoft.NET\assembly\GAC_MSIL\System.Collections" -Filter System.Collections.dll -Recurse | Select-Object -First 1
    $references = @($winrt.Location, $runtimeFacade.FullName, $collectionsFacade.FullName,
        "$env:SystemRoot\System32\WinMetadata\Windows.Foundation.winmd",
        "$env:SystemRoot\System32\WinMetadata\Windows.Devices.winmd",
        "$env:SystemRoot\System32\WinMetadata\Windows.Storage.winmd")
    $source = @'
using System;
using System.Threading.Tasks;
using Windows.Devices.Bluetooth.Advertisement;
using Windows.Storage.Streams;
public static class CampusGuardBleInput {
    public static Task<string> NextLine() { return Task.Run(() => Console.ReadLine()); }
    public static BluetoothLEAdvertisementPublisher Create(byte[] bytes) {
        var manufacturer = new BluetoothLEManufacturerData();
        manufacturer.CompanyId = 65535;
        using (var writer = new DataWriter()) {
            writer.WriteBytes(bytes);
            manufacturer.Data = writer.DetachBuffer();
        }
        var publisher = new BluetoothLEAdvertisementPublisher();
        publisher.Advertisement.ManufacturerData.Add(manufacturer);
        return publisher;
    }
}
'@
    $compiler = New-Object Microsoft.CSharp.CSharpCodeProvider
    $options = New-Object System.CodeDom.Compiler.CompilerParameters
    $options.GenerateInMemory = $true
    foreach ($reference in $references) { $null = $options.ReferencedAssemblies.Add($reference) }
    $result = $compiler.CompileAssemblyFromSource($options, $source)
    if ($result.Errors.HasErrors) { throw (($result.Errors | ForEach-Object { $_.ToString() }) -join '; ') }
    $adapterType = [Windows.Devices.Bluetooth.BluetoothAdapter,Windows.Devices.Bluetooth,ContentType=WindowsRuntime]
    $null = [Windows.Devices.Bluetooth.Advertisement.BluetoothLEAdvertisementPublisher,Windows.Devices.Bluetooth,ContentType=WindowsRuntime]
    $null = [Windows.Devices.Bluetooth.Advertisement.BluetoothLEManufacturerData,Windows.Devices.Bluetooth,ContentType=WindowsRuntime]
    $null = [Windows.Storage.Streams.DataWriter,Windows.Storage.Streams,ContentType=WindowsRuntime]
    $null = [Windows.Storage.Streams.IBuffer,Windows.Storage.Streams,ContentType=WindowsRuntime]
    $method = [System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
        $_.Name -eq 'AsTask' -and $_.IsGenericMethodDefinition -and $_.GetGenericArguments().Count -eq 1 -and $_.GetParameters().Count -eq 1
    } | Select-Object -First 1
    $operation = [Windows.Devices.Bluetooth.BluetoothAdapter]::GetDefaultAsync()
    $task = $method.MakeGenericMethod($adapterType).Invoke($null, @($operation))
    if (-not $task.Wait(5000)) { throw 'Bluetooth adapter probe timed out' }
    $adapter = $task.Result
    if ($null -eq $adapter) { throw 'No Windows Bluetooth adapter was found' }
    if (-not $adapter.IsLowEnergySupported) { throw 'This adapter does not support Bluetooth LE' }
    if (-not $adapter.IsPeripheralRoleSupported) { throw 'This adapter cannot advertise in the BLE peripheral role' }
    Emit @{kind='ready'; status='ready'; peripheralSupported=$true; lowEnergySupported=$true}
    if ($Probe) { exit 0 }
    $read = [CampusGuardBleInput]::NextLine()
    $active = $null
    $deadline = 0
    $lastStatus = ''
    while ($true) {
        if ($read.IsCompleted) {
            $line = $read.GetAwaiter().GetResult()
            if ($null -eq $line) { break }
            $read = [CampusGuardBleInput]::NextLine()
            $command = $null
            try {
                $command = $line | ConvertFrom-Json
                if ($command.op -eq 'stop') {
                    if ($publisher) { $publisher.Stop(); $publisher = $null }
                    $active = $null
                    Emit @{kind='stopped';id=$command.id;status='ready'}
                    continue
                }
                if ($command.op -ne 'publish' -or $command.hex -notmatch '^[0-9a-fA-F]{42}$') { throw 'Expected publish with a 21-byte hexadecimal core' }
                $expires = [long]$command.expiresAt
                if ($expires -le [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()) { throw 'Advertisement has expired' }
                if ($publisher) { $publisher.Stop(); $publisher = $null }
                # 21-byte application core plus 3 zero padding bytes. With the
                # manufacturer header and OS Flags section this fits 31 bytes.
                $bytes = New-Object byte[] 24
                for ($i = 0; $i -lt 21; $i++) { $bytes[$i] = [Convert]::ToByte($command.hex.Substring($i * 2, 2), 16) }
                $publisher = [CampusGuardBleInput]::Create($bytes)
                $active = [string]$command.id
                $deadline = $expires
                $lastStatus = ''
                $publisher.Start()
            } catch {
                Emit @{kind='error';id=$command.id;status='unavailable';error=$_.Exception.Message}
                if ($publisher) { try { $publisher.Stop() } catch {}; $publisher = $null }
                $active = $null
            }
        }
        if ($publisher -and $active) {
            $status = $publisher.Status.ToString()
            if ($status -ne $lastStatus) {
                $lastStatus = $status
                if ($status -eq 'Started') { Emit @{kind='published';id=$active;status='advertising'} }
                elseif ($status -eq 'Aborted') {
                    Emit @{kind='error';id=$active;status='unavailable';error='Windows aborted BLE advertising; check Bluetooth, adapter support and other advertisers'}
                    $publisher.Stop(); $publisher = $null; $active = $null
                }
            }
            if ($publisher -and [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() -ge $deadline) {
                $publisher.Stop(); $publisher = $null
                Emit @{kind='expired';id=$active;status='ready'}
                $active = $null
            }
        }
        Start-Sleep -Milliseconds 25
    }
} catch { Emit @{kind='error';status='unsupported';error=$_.Exception.Message}; exit 1 }
finally { if ($publisher) { try { $publisher.Stop() } catch {} } }
```

### campusguard/bridges/udp-receiver.js

<!-- CAMPUSGUARD_SOURCE {"path": "bridges/udp-receiver.js", "encoding": "utf-8", "sha256": "247d2e2fff54a1bd63372251876d926434e1518cc5abd97a1f9e049f55ed8feb"} -->
```javascript
import dgram from 'node:dgram';
import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { validAlert,expiresAt,applicable } from '../shared/protocol.js';
import { isHostel, multicastGroup, zoneName, zoneLabel } from '../src/zones.js';
const args=process.argv.slice(2),arg=(name,fallback)=>{const i=args.indexOf(name);return i<0?fallback:args[i+1];};
const extraGroup=arg('--group',null),port=Number(arg('--port','41234')),iface=arg('--interface',undefined),zone=Number(arg('--zone','1')),server=arg('--server',null),credential=arg('--credential',null);
if(!isHostel(zone))throw new Error('--zone must be a hostel ID from src/zones.js');
const groups=[...new Set([multicastGroup(zone),multicastGroup(0),...(extraGroup?[extraGroup]:[])])];
const socket=dgram.createSocket({type:'udp4',reuseAddr:true}),seen=new Set();
const helpers=new Set();
function notify(a){
  if(args.includes('--no-notify'))return;
  if(process.platform!=='win32'){if(!args.includes('--mute'))process.stdout.write('\u0007');console.log('Desktop popups use Windows; this platform receives console alerts.');return;}
  if(helpers.size>=3){console.warn('Notification queue busy; alert remains visible in the console.');return;}
  const child=spawn('powershell.exe',['-NoProfile','-STA','-ExecutionPolicy','Bypass','-File',fileURLToPath(new URL('../agent/notify.ps1',import.meta.url))],{windowsHide:true,stdio:['pipe','ignore','pipe']});
  helpers.add(child);child.on('exit',()=>helpers.delete(child));child.on('error',e=>{helpers.delete(child);console.error('Notification:',e.message);});
  child.stdin.on('error',()=>{});child.stderr.on('data',b=>console.error('Notification:',b.toString().trim()));
  child.stdin.end(JSON.stringify({title:`CampusGuard: ${a.type.toUpperCase()}`,message:a.message.slice(0,240),sound:!args.includes('--mute')&&a.type!=='all-clear'}));
}
socket.on('error',e=>console.error('UDP:',e.message));
socket.on('message',async (packet,source)=>{
  if(packet.length>1200)return;
  try{const m=JSON.parse(packet.toString());const a=m.alert;if(m.v!==1||m.kind!=='alert'||!validAlert(a)||expiresAt(a)<=Date.now()||!applicable(a,zone)||seen.has(a.id))return;seen.add(a.id);
    console.log(`\n[${m.transport}] ${a.id} from ${source.address}\n${a.type.toUpperCase()} / severity ${a.severity} / hostels ${zoneLabel(a)}\n${a.message}\nExpires: ${new Date(expiresAt(a)).toISOString()}`);
    notify(a);
    if(server&&credential){const res=await fetch(`${server}/api/receipts`,{method:'POST',headers:{'Content-Type':'application/json',Authorization:`Bearer ${credential}`},body:JSON.stringify({alertId:a.id,via:m.transport}),signal:AbortSignal.timeout(3000)});console.log(res.ok?'Receipt recorded by server.':'Receipt rejected; check credential zone.');}
  }catch(e){console.error('Packet ignored:',e.message);}
});
socket.bind(port,()=>{for(const group of groups)try{socket.addMembership(group,iface);}catch(e){console.error(`Multicast ${group} unavailable: ${e.message}. Broadcast reception can still work.`);}console.log(`Listening on UDP :${port}, multicast ${groups.join(', ')}, hostel ${zoneName(zone)}.\nOrdinary PWAs do not receive raw UDP. This is the software receiver.\nCtrl+C to stop.`);});
for(const signal of ['SIGINT','SIGTERM'])process.on(signal,()=>{for(const child of helpers)child.kill();socket.close(()=>process.exit(0));});
```

### campusguard/package-lock.json

<!-- CAMPUSGUARD_SOURCE {"path": "package-lock.json", "encoding": "utf-8", "sha256": "c406d6f7144293a0a80bfe94224b4c814cca8399648f7670a849bde0ad5945e7"} -->
```json
{
  "name": "campusguard",
  "version": "1.0.0",
  "lockfileVersion": 3,
  "requires": true,
  "packages": {
    "": {
      "name": "campusguard",
      "version": "1.0.0",
      "dependencies": {
        "bonjour-service": "^1.4.4",
        "express": "^4.21.2",
        "node-forge": "^1.3.1",
        "qrcode": "^1.5.4",
        "ws": "^8.18.0"
      },
      "engines": {
        "node": ">=20"
      }
    },
    "node_modules/@leichtgewicht/ip-codec": {
      "version": "2.0.5",
      "resolved": "https://registry.npmjs.org/@leichtgewicht/ip-codec/-/ip-codec-2.0.5.tgz",
      "integrity": "sha512-Vo+PSpZG2/fmgmiNzYK9qWRh8h/CHrwD0mo1h1DzL4yzHNSfWYujGTYsWGreD000gcgmZ7K4Ys6Tx9TxtsKdDw==",
      "license": "MIT"
    },
    "node_modules/accepts": {
      "version": "1.3.8",
      "resolved": "https://registry.npmjs.org/accepts/-/accepts-1.3.8.tgz",
      "integrity": "sha512-PYAthTa2m2VKxuvSD3DPC/Gy+U+sOA1LAuT8mkmRuvw+NACSaeXEQ+NHcVF7rONl6qcaxV3Uuemwawk+7+SJLw==",
      "license": "MIT",
      "dependencies": {
        "mime-types": "~2.1.34",
        "negotiator": "0.6.3"
      },
      "engines": {
        "node": ">= 0.6"
      }
    },
    "node_modules/ansi-regex": {
      "version": "5.0.1",
      "resolved": "https://registry.npmjs.org/ansi-regex/-/ansi-regex-5.0.1.tgz",
      "integrity": "sha512-quJQXlTSUGL2LH9SUXo8VwsY4soanhgo6LNSm84E1LBcE8s3O0wpdiRzyR9z/ZZJMlMWv37qOOb9pdJlMUEKFQ==",
      "license": "MIT",
      "engines": {
        "node": ">=8"
      }
    },
    "node_modules/ansi-styles": {
      "version": "4.3.0",
      "resolved": "https://registry.npmjs.org/ansi-styles/-/ansi-styles-4.3.0.tgz",
      "integrity": "sha512-zbB9rCJAT1rbjiVDb2hqKFHNYLxgtk8NURxZ3IZwD3F6NtxbXZQCnnSi1Lkx+IDohdPlFp222wVALIheZJQSEg==",
      "license": "MIT",
      "dependencies": {
        "color-convert": "^2.0.1"
      },
      "engines": {
        "node": ">=8"
      },
      "funding": {
        "url": "https://github.com/chalk/ansi-styles?sponsor=1"
      }
    },
    "node_modules/array-flatten": {
      "version": "1.1.1",
      "resolved": "https://registry.npmjs.org/array-flatten/-/array-flatten-1.1.1.tgz",
      "integrity": "sha512-PCVAQswWemu6UdxsDFFX/+gVeYqKAod3D3UVm91jHwynguOwAvYPhx8nNlM++NqRcK6CxxpUafjmhIdKiHibqg==",
      "license": "MIT"
    },
    "node_modules/body-parser": {
      "version": "1.20.9",
      "resolved": "https://registry.npmjs.org/body-parser/-/body-parser-1.20.9.tgz",
      "integrity": "sha512-FISIQxV5u91Y9gvuFq3q/tA/qTqLJn3AhRcEvyozyhJTh5OBFFtv827AdHU7Rbyct4r+Ba1/99oFa1Y3yuFsdw==",
      "license": "MIT",
      "dependencies": {
        "bytes": "~3.1.2",
        "content-type": "~1.0.5",
        "debug": "2.6.9",
        "depd": "2.0.0",
        "destroy": "~1.2.0",
        "http-errors": "~2.0.1",
        "iconv-lite": "~0.4.24",
        "on-finished": "~2.4.1",
        "qs": "~6.16.0",
        "raw-body": "~2.5.3",
        "type-is": "~1.6.18",
        "unpipe": "~1.0.0"
      },
      "engines": {
        "node": ">= 0.8",
        "npm": "1.2.8000 || >= 1.4.16"
      }
    },
    "node_modules/bonjour-service": {
      "version": "1.4.4",
      "resolved": "https://registry.npmjs.org/bonjour-service/-/bonjour-service-1.4.4.tgz",
      "integrity": "sha512-jCZcVv7eoc4QesRscwEZtSROBen+6LpKAmBIsQYQrsAeVHLyMXWX/t6eIV5KiRZYNUBl8eVqImEEMQ8L5+c/Kw==",
      "license": "MIT",
      "dependencies": {
        "fast-deep-equal": "^3.1.3",
        "multicast-dns": "^7.2.5"
      }
    },
    "node_modules/bytes": {
      "version": "3.1.2",
      "resolved": "https://registry.npmjs.org/bytes/-/bytes-3.1.2.tgz",
      "integrity": "sha512-/Nf7TyzTx6S3yRJObOAV7956r8cr2+Oj8AC5dt8wSP3BQAoeX58NoHyCU8P8zGkNXStjTSi6fzO6F0pBdcYbEg==",
      "license": "MIT",
      "engines": {
        "node": ">= 0.8"
      }
    },
    "node_modules/call-bind-apply-helpers": {
      "version": "1.0.2",
      "resolved": "https://registry.npmjs.org/call-bind-apply-helpers/-/call-bind-apply-helpers-1.0.2.tgz",
      "integrity": "sha512-Sp1ablJ0ivDkSzjcaJdxEunN5/XvksFJ2sMBFfq6x0ryhQV/2b/KwFe21cMpmHtPOSij8K99/wSfoEuTObmuMQ==",
      "license": "MIT",
      "dependencies": {
        "es-errors": "^1.3.0",
        "function-bind": "^1.1.2"
      },
      "engines": {
        "node": ">= 0.4"
      }
    },
    "node_modules/call-bound": {
      "version": "1.0.4",
      "resolved": "https://registry.npmjs.org/call-bound/-/call-bound-1.0.4.tgz",
      "integrity": "sha512-+ys997U96po4Kx/ABpBCqhA9EuxJaQWDQg7295H4hBphv3IZg0boBKuwYpt4YXp6MZ5AmZQnU/tyMTlRpaSejg==",
      "license": "MIT",
      "dependencies": {
        "call-bind-apply-helpers": "^1.0.2",
        "get-intrinsic": "^1.3.0"
      },
      "engines": {
        "node": ">= 0.4"
      },
      "funding": {
        "url": "https://github.com/sponsors/ljharb"
      }
    },
    "node_modules/camelcase": {
      "version": "5.3.1",
      "resolved": "https://registry.npmjs.org/camelcase/-/camelcase-5.3.1.tgz",
      "integrity": "sha512-L28STB170nwWS63UjtlEOE3dldQApaJXZkOI1uMFfzf3rRuPegHaHesyee+YxQ+W6SvRDQV6UrdOdRiR153wJg==",
      "license": "MIT",
      "engines": {
        "node": ">=6"
      }
    },
    "node_modules/cliui": {
      "version": "6.0.0",
      "resolved": "https://registry.npmjs.org/cliui/-/cliui-6.0.0.tgz",
      "integrity": "sha512-t6wbgtoCXvAzst7QgXxJYqPt0usEfbgQdftEPbLL/cvv6HPE5VgvqCuAIDR0NgU52ds6rFwqrgakNLrHEjCbrQ==",
      "license": "ISC",
      "dependencies": {
        "string-width": "^4.2.0",
        "strip-ansi": "^6.0.0",
        "wrap-ansi": "^6.2.0"
      }
    },
    "node_modules/color-convert": {
      "version": "2.0.1",
      "resolved": "https://registry.npmjs.org/color-convert/-/color-convert-2.0.1.tgz",
      "integrity": "sha512-RRECPsj7iu/xb5oKYcsFHSppFNnsj/52OVTRKb4zP5onXwVF3zVmmToNcOfGC+CRDpfK/U584fMg38ZHCaElKQ==",
      "license": "MIT",
      "dependencies": {
        "color-name": "~1.1.4"
      },
      "engines": {
        "node": ">=7.0.0"
      }
    },
    "node_modules/color-name": {
      "version": "1.1.4",
      "resolved": "https://registry.npmjs.org/color-name/-/color-name-1.1.4.tgz",
      "integrity": "sha512-dOy+3AuW3a2wNbZHIuMZpTcgjGuLU/uBL/ubcZF9OXbDo8ff4O8yVp5Bf0efS8uEoYo5q4Fx7dY9OgQGXgAsQA==",
      "license": "MIT"
    },
    "node_modules/content-disposition": {
      "version": "0.5.4",
      "resolved": "https://registry.npmjs.org/content-disposition/-/content-disposition-0.5.4.tgz",
      "integrity": "sha512-FveZTNuGw04cxlAiWbzi6zTAL/lhehaWbTtgluJh4/E95DqMwTmha3KZN1aAWA8cFIhHzMZUvLevkw5Rqk+tSQ==",
      "license": "MIT",
      "dependencies": {
        "safe-buffer": "5.2.1"
      },
      "engines": {
        "node": ">= 0.6"
      }
    },
    "node_modules/content-type": {
      "version": "1.0.5",
      "resolved": "https://registry.npmjs.org/content-type/-/content-type-1.0.5.tgz",
      "integrity": "sha512-nTjqfcBFEipKdXCv4YDQWCfmcLZKm81ldF0pAopTvyrFGVbcR6P/VAAd5G7N+0tTr8QqiU0tFadD6FK4NtJwOA==",
      "license": "MIT",
      "engines": {
        "node": ">= 0.6"
      }
    },
    "node_modules/cookie": {
      "version": "0.7.2",
      "resolved": "https://registry.npmjs.org/cookie/-/cookie-0.7.2.tgz",
      "integrity": "sha512-yki5XnKuf750l50uGTllt6kKILY4nQ1eNIQatoXEByZ5dWgnKqbnqmTrBE5B4N7lrMJKQ2ytWMiTO2o0v6Ew/w==",
      "license": "MIT",
      "engines": {
        "node": ">= 0.6"
      }
    },
    "node_modules/cookie-signature": {
      "version": "1.0.7",
      "resolved": "https://registry.npmjs.org/cookie-signature/-/cookie-signature-1.0.7.tgz",
      "integrity": "sha512-NXdYc3dLr47pBkpUCHtKSwIOQXLVn8dZEuywboCOJY/osA0wFSLlSawr3KN8qXJEyX66FcONTH8EIlVuK0yyFA==",
      "license": "MIT"
    },
    "node_modules/debug": {
      "version": "2.6.9",
      "resolved": "https://registry.npmjs.org/debug/-/debug-2.6.9.tgz",
      "integrity": "sha512-bC7ElrdJaJnPbAP+1EotYvqZsb3ecl5wi6Bfi6BJTUcNowp6cvspg0jXznRTKDjm/E7AdgFBVeAPVMNcKGsHMA==",
      "license": "MIT",
      "dependencies": {
        "ms": "2.0.0"
      }
    },
    "node_modules/decamelize": {
      "version": "1.2.0",
      "resolved": "https://registry.npmjs.org/decamelize/-/decamelize-1.2.0.tgz",
      "integrity": "sha512-z2S+W9X73hAUUki+N+9Za2lBlun89zigOyGrsax+KUQ6wKW4ZoWpEYBkGhQjwAjjDCkWxhY0VKEhk8wzY7F5cA==",
      "license": "MIT",
      "engines": {
        "node": ">=0.10.0"
      }
    },
    "node_modules/depd": {
      "version": "2.0.0",
      "resolved": "https://registry.npmjs.org/depd/-/depd-2.0.0.tgz",
      "integrity": "sha512-g7nH6P6dyDioJogAAGprGpCtVImJhpPk/roCzdb3fIh61/s/nPsfR6onyMwkCAR/OlC3yBC0lESvUoQEAssIrw==",
      "license": "MIT",
      "engines": {
        "node": ">= 0.8"
      }
    },
    "node_modules/destroy": {
      "version": "1.2.0",
      "resolved": "https://registry.npmjs.org/destroy/-/destroy-1.2.0.tgz",
      "integrity": "sha512-2sJGJTaXIIaR1w4iJSNoN0hnMY7Gpc/n8D4qSCJw8QqFWXf7cuAgnEHxBpweaVcPevC2l3KpjYCx3NypQQgaJg==",
      "license": "MIT",
      "engines": {
        "node": ">= 0.8",
        "npm": "1.2.8000 || >= 1.4.16"
      }
    },
    "node_modules/dijkstrajs": {
      "version": "1.0.3",
      "resolved": "https://registry.npmjs.org/dijkstrajs/-/dijkstrajs-1.0.3.tgz",
      "integrity": "sha512-qiSlmBq9+BCdCA/L46dw8Uy93mloxsPSbwnm5yrKn2vMPiy8KyAskTF6zuV/j5BMsmOGZDPs7KjU+mjb670kfA==",
      "license": "MIT"
    },
    "node_modules/dns-packet": {
      "version": "5.6.1",
      "resolved": "https://registry.npmjs.org/dns-packet/-/dns-packet-5.6.1.tgz",
      "integrity": "sha512-l4gcSouhcgIKRvyy99RNVOgxXiicE+2jZoNmaNmZ6JXiGajBOJAesk1OBlJuM5k2c+eudGdLxDqXuPCKIj6kpw==",
      "license": "MIT",
      "dependencies": {
        "@leichtgewicht/ip-codec": "^2.0.1"
      },
      "engines": {
        "node": ">=6"
      }
    },
    "node_modules/dunder-proto": {
      "version": "1.0.1",
      "resolved": "https://registry.npmjs.org/dunder-proto/-/dunder-proto-1.0.1.tgz",
      "integrity": "sha512-KIN/nDJBQRcXw0MLVhZE9iQHmG68qAVIBg9CqmUYjmQIhgij9U5MFvrqkUL5FbtyyzZuOeOt0zdeRe4UY7ct+A==",
      "license": "MIT",
      "dependencies": {
        "call-bind-apply-helpers": "^1.0.1",
        "es-errors": "^1.3.0",
        "gopd": "^1.2.0"
      },
      "engines": {
        "node": ">= 0.4"
      }
    },
    "node_modules/ee-first": {
      "version": "1.1.1",
      "resolved": "https://registry.npmjs.org/ee-first/-/ee-first-1.1.1.tgz",
      "integrity": "sha512-WMwm9LhRUo+WUaRN+vRuETqG89IgZphVSNkdFgeb6sS/E4OrDIN7t48CAewSHXc6C8lefD8KKfr5vY61brQlow==",
      "license": "MIT"
    },
    "node_modules/emoji-regex": {
      "version": "8.0.0",
      "resolved": "https://registry.npmjs.org/emoji-regex/-/emoji-regex-8.0.0.tgz",
      "integrity": "sha512-MSjYzcWNOA0ewAHpz0MxpYFvwg6yjy1NG3xteoqz644VCo/RPgnr1/GGt+ic3iJTzQ8Eu3TdM14SawnVUmGE6A==",
      "license": "MIT"
    },
    "node_modules/encodeurl": {
      "version": "2.0.0",
      "resolved": "https://registry.npmjs.org/encodeurl/-/encodeurl-2.0.0.tgz",
      "integrity": "sha512-Q0n9HRi4m6JuGIV1eFlmvJB7ZEVxu93IrMyiMsGC0lrMJMWzRgx6WGquyfQgZVb31vhGgXnfmPNNXmxnOkRBrg==",
      "license": "MIT",
      "engines": {
        "node": ">= 0.8"
      }
    },
    "node_modules/es-define-property": {
      "version": "1.0.1",
      "resolved": "https://registry.npmjs.org/es-define-property/-/es-define-property-1.0.1.tgz",
      "integrity": "sha512-e3nRfgfUZ4rNGL232gUgX06QNyyez04KdjFrF+LTRoOXmrOgFKDg4BCdsjW8EnT69eqdYGmRpJwiPVYNrCaW3g==",
      "license": "MIT",
      "engines": {
        "node": ">= 0.4"
      }
    },
    "node_modules/es-errors": {
      "version": "1.3.0",
      "resolved": "https://registry.npmjs.org/es-errors/-/es-errors-1.3.0.tgz",
      "integrity": "sha512-Zf5H2Kxt2xjTvbJvP2ZWLEICxA6j+hAmMzIlypy4xcBg1vKVnx89Wy0GbS+kf5cwCVFFzdCFh2XSCFNULS6csw==",
      "license": "MIT",
      "engines": {
        "node": ">= 0.4"
      }
    },
    "node_modules/es-object-atoms": {
      "version": "1.1.2",
      "resolved": "https://registry.npmjs.org/es-object-atoms/-/es-object-atoms-1.1.2.tgz",
      "integrity": "sha512-HWcBoN6NileqtSydK2FqHbS/LoDd2pqrnQHLyJzBj4kOp/ky2MWMN694xOfkK8/SnUsW2DH7EfyVlydKCsm1Zw==",
      "license": "MIT",
      "dependencies": {
        "es-errors": "^1.3.0"
      },
      "engines": {
        "node": ">= 0.4"
      }
    },
    "node_modules/escape-html": {
      "version": "1.0.3",
      "resolved": "https://registry.npmjs.org/escape-html/-/escape-html-1.0.3.tgz",
      "integrity": "sha512-NiSupZ4OeuGwr68lGIeym/ksIZMJodUGOSCZ/FSnTxcrekbvqrgdUxlJOMpijaKZVjAJrWrGs/6Jy8OMuyj9ow==",
      "license": "MIT"
    },
    "node_modules/etag": {
      "version": "1.8.1",
      "resolved": "https://registry.npmjs.org/etag/-/etag-1.8.1.tgz",
      "integrity": "sha512-aIL5Fx7mawVa300al2BnEE4iNvo1qETxLrPI/o05L7z6go7fCw1J6EQmbK4FmJ2AS7kgVF/KEZWufBfdClMcPg==",
      "license": "MIT",
      "engines": {
        "node": ">= 0.6"
      }
    },
    "node_modules/express": {
      "version": "4.22.3",
      "resolved": "https://registry.npmjs.org/express/-/express-4.22.3.tgz",
      "integrity": "sha512-Bdcs4+3qlpVlx2NRn6fgX2Ue2/gGRaPeawebgclM0ERSCqDpA+owF1fdPwjJUTAJWMTuAaxjDf+hzb0/4eKvvw==",
      "license": "MIT",
      "dependencies": {
        "accepts": "~1.3.8",
        "array-flatten": "1.1.1",
        "body-parser": "~1.20.5",
        "content-disposition": "~0.5.4",
        "content-type": "~1.0.4",
        "cookie": "~0.7.1",
        "cookie-signature": "~1.0.6",
        "debug": "2.6.9",
        "depd": "2.0.0",
        "encodeurl": "~2.0.0",
        "escape-html": "~1.0.3",
        "etag": "~1.8.1",
        "finalhandler": "~1.3.1",
        "fresh": "~0.5.2",
        "http-errors": "~2.0.0",
        "merge-descriptors": "1.0.3",
        "methods": "~1.1.2",
        "on-finished": "~2.4.1",
        "parseurl": "~1.3.3",
        "path-to-regexp": "~0.1.13",
        "proxy-addr": "~2.0.7",
        "qs": "~6.16.0",
        "range-parser": "~1.2.1",
        "safe-buffer": "5.2.1",
        "send": "~0.19.0",
        "serve-static": "~1.16.2",
        "setprototypeof": "1.2.0",
        "statuses": "~2.0.1",
        "type-is": "~1.6.18",
        "utils-merge": "1.0.1",
        "vary": "~1.1.2"
      },
      "engines": {
        "node": ">= 0.10.0"
      },
      "funding": {
        "type": "opencollective",
        "url": "https://opencollective.com/express"
      }
    },
    "node_modules/fast-deep-equal": {
      "version": "3.1.3",
      "resolved": "https://registry.npmjs.org/fast-deep-equal/-/fast-deep-equal-3.1.3.tgz",
      "integrity": "sha512-f3qQ9oQy9j2AhBe/H9VC91wLmKBCCU/gDOnKNAYG5hswO7BLKj09Hc5HYNz9cGI++xlpDCIgDaitVs03ATR84Q==",
      "license": "MIT"
    },
    "node_modules/finalhandler": {
      "version": "1.3.2",
      "resolved": "https://registry.npmjs.org/finalhandler/-/finalhandler-1.3.2.tgz",
      "integrity": "sha512-aA4RyPcd3badbdABGDuTXCMTtOneUCAYH/gxoYRTZlIJdF0YPWuGqiAsIrhNnnqdXGswYk6dGujem4w80UJFhg==",
      "license": "MIT",
      "dependencies": {
        "debug": "2.6.9",
        "encodeurl": "~2.0.0",
        "escape-html": "~1.0.3",
        "on-finished": "~2.4.1",
        "parseurl": "~1.3.3",
        "statuses": "~2.0.2",
        "unpipe": "~1.0.0"
      },
      "engines": {
        "node": ">= 0.8"
      }
    },
    "node_modules/find-up": {
      "version": "4.1.0",
      "resolved": "https://registry.npmjs.org/find-up/-/find-up-4.1.0.tgz",
      "integrity": "sha512-PpOwAdQ/YlXQ2vj8a3h8IipDuYRi3wceVQQGYWxNINccq40Anw7BlsEXCMbt1Zt+OLA6Fq9suIpIWD0OsnISlw==",
      "license": "MIT",
      "dependencies": {
        "locate-path": "^5.0.0",
        "path-exists": "^4.0.0"
      },
      "engines": {
        "node": ">=8"
      }
    },
    "node_modules/forwarded": {
      "version": "0.2.0",
      "resolved": "https://registry.npmjs.org/forwarded/-/forwarded-0.2.0.tgz",
      "integrity": "sha512-buRG0fpBtRHSTCOASe6hD258tEubFoRLb4ZNA6NxMVHNw2gOcwHo9wyablzMzOA5z9xA9L1KNjk/Nt6MT9aYow==",
      "license": "MIT",
      "engines": {
        "node": ">= 0.6"
      }
    },
    "node_modules/fresh": {
      "version": "0.5.2",
      "resolved": "https://registry.npmjs.org/fresh/-/fresh-0.5.2.tgz",
      "integrity": "sha512-zJ2mQYM18rEFOudeV4GShTGIQ7RbzA7ozbU9I/XBpm7kqgMywgmylMwXHxZJmkVoYkna9d2pVXVXPdYTP9ej8Q==",
      "license": "MIT",
      "engines": {
        "node": ">= 0.6"
      }
    },
    "node_modules/function-bind": {
      "version": "1.1.2",
      "resolved": "https://registry.npmjs.org/function-bind/-/function-bind-1.1.2.tgz",
      "integrity": "sha512-7XHNxH7qX9xG5mIwxkhumTox/MIRNcOgDrxWsMt2pAr23WHp6MrRlN7FBSFpCpr+oVO0F744iUgR82nJMfG2SA==",
      "license": "MIT",
      "funding": {
        "url": "https://github.com/sponsors/ljharb"
      }
    },
    "node_modules/get-caller-file": {
      "version": "2.0.5",
      "resolved": "https://registry.npmjs.org/get-caller-file/-/get-caller-file-2.0.5.tgz",
      "integrity": "sha512-DyFP3BM/3YHTQOCUL/w0OZHR0lpKeGrxotcHWcqNEdnltqFwXVfhEBQ94eIo34AfQpo0rGki4cyIiftY06h2Fg==",
      "license": "ISC",
      "engines": {
        "node": "6.* || 8.* || >= 10.*"
      }
    },
    "node_modules/get-intrinsic": {
      "version": "1.3.0",
      "resolved": "https://registry.npmjs.org/get-intrinsic/-/get-intrinsic-1.3.0.tgz",
      "integrity": "sha512-9fSjSaos/fRIVIp+xSJlE6lfwhES7LNtKaCBIamHsjr2na1BiABJPo0mOjjz8GJDURarmCPGqaiVg5mfjb98CQ==",
      "license": "MIT",
      "dependencies": {
        "call-bind-apply-helpers": "^1.0.2",
        "es-define-property": "^1.0.1",
        "es-errors": "^1.3.0",
        "es-object-atoms": "^1.1.1",
        "function-bind": "^1.1.2",
        "get-proto": "^1.0.1",
        "gopd": "^1.2.0",
        "has-symbols": "^1.1.0",
        "hasown": "^2.0.2",
        "math-intrinsics": "^1.1.0"
      },
      "engines": {
        "node": ">= 0.4"
      },
      "funding": {
        "url": "https://github.com/sponsors/ljharb"
      }
    },
    "node_modules/get-proto": {
      "version": "1.0.1",
      "resolved": "https://registry.npmjs.org/get-proto/-/get-proto-1.0.1.tgz",
      "integrity": "sha512-sTSfBjoXBp89JvIKIefqw7U2CCebsc74kiY6awiGogKtoSGbgjYE/G/+l9sF3MWFPNc9IcoOC4ODfKHfxFmp0g==",
      "license": "MIT",
      "dependencies": {
        "dunder-proto": "^1.0.1",
        "es-object-atoms": "^1.0.0"
      },
      "engines": {
        "node": ">= 0.4"
      }
    },
    "node_modules/gopd": {
      "version": "1.2.0",
      "resolved": "https://registry.npmjs.org/gopd/-/gopd-1.2.0.tgz",
      "integrity": "sha512-ZUKRh6/kUFoAiTAtTYPZJ3hw9wNxx+BIBOijnlG9PnrJsCcSjs1wyyD6vJpaYtgnzDrKYRSqf3OO6Rfa93xsRg==",
      "license": "MIT",
      "engines": {
        "node": ">= 0.4"
      },
      "funding": {
        "url": "https://github.com/sponsors/ljharb"
      }
    },
    "node_modules/has-symbols": {
      "version": "1.1.0",
      "resolved": "https://registry.npmjs.org/has-symbols/-/has-symbols-1.1.0.tgz",
      "integrity": "sha512-1cDNdwJ2Jaohmb3sg4OmKaMBwuC48sYni5HUw2DvsC8LjGTLK9h+eb1X6RyuOHe4hT0ULCW68iomhjUoKUqlPQ==",
      "license": "MIT",
      "engines": {
        "node": ">= 0.4"
      },
      "funding": {
        "url": "https://github.com/sponsors/ljharb"
      }
    },
    "node_modules/hasown": {
      "version": "2.0.4",
      "resolved": "https://registry.npmjs.org/hasown/-/hasown-2.0.4.tgz",
      "integrity": "sha512-T2UbfbBEF32wiepXIsMlTW9+dDYC6wMh/t/vYA4tuOMKqWz/n3vr1NFSxQiyP+zk2mXsoMA/i/7qV6LKut1t1A==",
      "license": "MIT",
      "dependencies": {
        "function-bind": "^1.1.2"
      },
      "engines": {
        "node": ">= 0.4"
      }
    },
    "node_modules/http-errors": {
      "version": "2.0.1",
      "resolved": "https://registry.npmjs.org/http-errors/-/http-errors-2.0.1.tgz",
      "integrity": "sha512-4FbRdAX+bSdmo4AUFuS0WNiPz8NgFt+r8ThgNWmlrjQjt1Q7ZR9+zTlce2859x4KSXrwIsaeTqDoKQmtP8pLmQ==",
      "license": "MIT",
      "dependencies": {
        "depd": "~2.0.0",
        "inherits": "~2.0.4",
        "setprototypeof": "~1.2.0",
        "statuses": "~2.0.2",
        "toidentifier": "~1.0.1"
      },
      "engines": {
        "node": ">= 0.8"
      },
      "funding": {
        "type": "opencollective",
        "url": "https://opencollective.com/express"
      }
    },
    "node_modules/iconv-lite": {
      "version": "0.4.24",
      "resolved": "https://registry.npmjs.org/iconv-lite/-/iconv-lite-0.4.24.tgz",
      "integrity": "sha512-v3MXnZAcvnywkTUEZomIActle7RXXeedOR31wwl7VlyoXO4Qi9arvSenNQWne1TcRwhCL1HwLI21bEqdpj8/rA==",
      "license": "MIT",
      "dependencies": {
        "safer-buffer": ">= 2.1.2 < 3"
      },
      "engines": {
        "node": ">=0.10.0"
      }
    },
    "node_modules/inherits": {
      "version": "2.0.4",
      "resolved": "https://registry.npmjs.org/inherits/-/inherits-2.0.4.tgz",
      "integrity": "sha512-k/vGaX4/Yla3WzyMCvTQOXYeIHvqOKtnqBduzTHpzpQZzAskKMhZ2K+EnBiSM9zGSoIFeMpXKxa4dYeZIQqewQ==",
      "license": "ISC"
    },
    "node_modules/ipaddr.js": {
      "version": "1.9.1",
      "resolved": "https://registry.npmjs.org/ipaddr.js/-/ipaddr.js-1.9.1.tgz",
      "integrity": "sha512-0KI/607xoxSToH7GjN1FfSbLoU0+btTicjsQSWQlh/hZykN8KpmMf7uYwPW3R+akZ6R/w18ZlXSHBYXiYUPO3g==",
      "license": "MIT",
      "engines": {
        "node": ">= 0.10"
      }
    },
    "node_modules/is-fullwidth-code-point": {
      "version": "3.0.0",
      "resolved": "https://registry.npmjs.org/is-fullwidth-code-point/-/is-fullwidth-code-point-3.0.0.tgz",
      "integrity": "sha512-zymm5+u+sCsSWyD9qNaejV3DFvhCKclKdizYaJUuHA83RLjb7nSuGnddCHGv0hk+KY7BMAlsWeK4Ueg6EV6XQg==",
      "license": "MIT",
      "engines": {
        "node": ">=8"
      }
    },
    "node_modules/locate-path": {
      "version": "5.0.0",
      "resolved": "https://registry.npmjs.org/locate-path/-/locate-path-5.0.0.tgz",
      "integrity": "sha512-t7hw9pI+WvuwNJXwk5zVHpyhIqzg2qTlklJOf0mVxGSbe3Fp2VieZcduNYjaLDoy6p9uGpQEGWG87WpMKlNq8g==",
      "license": "MIT",
      "dependencies": {
        "p-locate": "^4.1.0"
      },
      "engines": {
        "node": ">=8"
      }
    },
    "node_modules/math-intrinsics": {
      "version": "1.1.0",
      "resolved": "https://registry.npmjs.org/math-intrinsics/-/math-intrinsics-1.1.0.tgz",
      "integrity": "sha512-/IXtbwEk5HTPyEwyKX6hGkYXxM9nbj64B+ilVJnC/R6B0pH5G4V3b0pVbL7DBj4tkhBAppbQUlf6F6Xl9LHu1g==",
      "license": "MIT",
      "engines": {
        "node": ">= 0.4"
      }
    },
    "node_modules/media-typer": {
      "version": "0.3.0",
      "resolved": "https://registry.npmjs.org/media-typer/-/media-typer-0.3.0.tgz",
      "integrity": "sha512-dq+qelQ9akHpcOl/gUVRTxVIOkAJ1wR3QAvb4RsVjS8oVoFjDGTc679wJYmUmknUF5HwMLOgb5O+a3KxfWapPQ==",
      "license": "MIT",
      "engines": {
        "node": ">= 0.6"
      }
    },
    "node_modules/merge-descriptors": {
      "version": "1.0.3",
      "resolved": "https://registry.npmjs.org/merge-descriptors/-/merge-descriptors-1.0.3.tgz",
      "integrity": "sha512-gaNvAS7TZ897/rVaZ0nMtAyxNyi/pdbjbAwUpFQpN70GqnVfOiXpeUUMKRBmzXaSQ8DdTX4/0ms62r2K+hE6mQ==",
      "license": "MIT",
      "funding": {
        "url": "https://github.com/sponsors/sindresorhus"
      }
    },
    "node_modules/methods": {
      "version": "1.1.2",
      "resolved": "https://registry.npmjs.org/methods/-/methods-1.1.2.tgz",
      "integrity": "sha512-iclAHeNqNm68zFtnZ0e+1L2yUIdvzNoauKU4WBA3VvH/vPFieF7qfRlwUZU+DA9P9bPXIS90ulxoUoCH23sV2w==",
      "license": "MIT",
      "engines": {
        "node": ">= 0.6"
      }
    },
    "node_modules/mime": {
      "version": "1.6.0",
      "resolved": "https://registry.npmjs.org/mime/-/mime-1.6.0.tgz",
      "integrity": "sha512-x0Vn8spI+wuJ1O6S7gnbaQg8Pxh4NNHb7KSINmEWKiPE4RKOplvijn+NkmYmmRgP68mc70j2EbeTFRsrswaQeg==",
      "license": "MIT",
      "bin": {
        "mime": "cli.js"
      },
      "engines": {
        "node": ">=4"
      }
    },
    "node_modules/mime-db": {
      "version": "1.52.0",
      "resolved": "https://registry.npmjs.org/mime-db/-/mime-db-1.52.0.tgz",
      "integrity": "sha512-sPU4uV7dYlvtWJxwwxHD0PuihVNiE7TyAbQ5SWxDCB9mUYvOgroQOwYQQOKPJ8CIbE+1ETVlOoK1UC2nU3gYvg==",
      "license": "MIT",
      "engines": {
        "node": ">= 0.6"
      }
    },
    "node_modules/mime-types": {
      "version": "2.1.35",
      "resolved": "https://registry.npmjs.org/mime-types/-/mime-types-2.1.35.tgz",
      "integrity": "sha512-ZDY+bPm5zTTF+YpCrAU9nK0UgICYPT0QtT1NZWFv4s++TNkcgVaT0g6+4R2uI4MjQjzysHB1zxuWL50hzaeXiw==",
      "license": "MIT",
      "dependencies": {
        "mime-db": "1.52.0"
      },
      "engines": {
        "node": ">= 0.6"
      }
    },
    "node_modules/ms": {
      "version": "2.0.0",
      "resolved": "https://registry.npmjs.org/ms/-/ms-2.0.0.tgz",
      "integrity": "sha512-Tpp60P6IUJDTuOq/5Z8cdskzJujfwqfOTkrwIwj7IRISpnkJnT6SyJ4PCPnGMoFjC9ddhal5KVIYtAt97ix05A==",
      "license": "MIT"
    },
    "node_modules/multicast-dns": {
      "version": "7.2.5",
      "resolved": "https://registry.npmjs.org/multicast-dns/-/multicast-dns-7.2.5.tgz",
      "integrity": "sha512-2eznPJP8z2BFLX50tf0LuODrpINqP1RVIm/CObbTcBRITQgmC/TjcREF1NeTBzIcR5XO/ukWo+YHOjBbFwIupg==",
      "license": "MIT",
      "dependencies": {
        "dns-packet": "^5.2.2",
        "thunky": "^1.0.2"
      },
      "bin": {
        "multicast-dns": "cli.js"
      }
    },
    "node_modules/negotiator": {
      "version": "0.6.3",
      "resolved": "https://registry.npmjs.org/negotiator/-/negotiator-0.6.3.tgz",
      "integrity": "sha512-+EUsqGPLsM+j/zdChZjsnX51g4XrHFOIXwfnCVPGlQk/k5giakcKsuxCObBRu6DSm9opw/O6slWbJdghQM4bBg==",
      "license": "MIT",
      "engines": {
        "node": ">= 0.6"
      }
    },
    "node_modules/node-forge": {
      "version": "1.4.0",
      "resolved": "https://registry.npmjs.org/node-forge/-/node-forge-1.4.0.tgz",
      "integrity": "sha512-LarFH0+6VfriEhqMMcLX2F7SwSXeWwnEAJEsYm5QKWchiVYVvJyV9v7UDvUv+w5HO23ZpQTXDv/GxdDdMyOuoQ==",
      "license": "(BSD-3-Clause OR GPL-2.0)",
      "engines": {
        "node": ">= 6.13.0"
      }
    },
    "node_modules/object-inspect": {
      "version": "1.13.4",
      "resolved": "https://registry.npmjs.org/object-inspect/-/object-inspect-1.13.4.tgz",
      "integrity": "sha512-W67iLl4J2EXEGTbfeHCffrjDfitvLANg0UlX3wFUUSTx92KXRFegMHUVgSqE+wvhAbi4WqjGg9czysTV2Epbew==",
      "license": "MIT",
      "engines": {
        "node": ">= 0.4"
      },
      "funding": {
        "url": "https://github.com/sponsors/ljharb"
      }
    },
    "node_modules/on-finished": {
      "version": "2.4.2",
      "resolved": "https://registry.npmjs.org/on-finished/-/on-finished-2.4.2.tgz",
      "integrity": "sha512-ATWUv387i+nLD49EiDoOft5scf32Vsdz3ktrQJG9bi7jRFmaXTssvyvj1EmeKVVgvesbqUtvQQvaS2TpTukAZQ==",
      "license": "MIT",
      "dependencies": {
        "ee-first": "1.1.1"
      },
      "engines": {
        "node": ">= 0.8"
      },
      "funding": {
        "type": "opencollective",
        "url": "https://opencollective.com/express"
      }
    },
    "node_modules/p-limit": {
      "version": "2.3.0",
      "resolved": "https://registry.npmjs.org/p-limit/-/p-limit-2.3.0.tgz",
      "integrity": "sha512-//88mFWSJx8lxCzwdAABTJL2MyWB12+eIY7MDL2SqLmAkeKU9qxRvWuSyTjm3FUmpBEMuFfckAIqEaVGUDxb6w==",
      "license": "MIT",
      "dependencies": {
        "p-try": "^2.0.0"
      },
      "engines": {
        "node": ">=6"
      },
      "funding": {
        "url": "https://github.com/sponsors/sindresorhus"
      }
    },
    "node_modules/p-locate": {
      "version": "4.1.0",
      "resolved": "https://registry.npmjs.org/p-locate/-/p-locate-4.1.0.tgz",
      "integrity": "sha512-R79ZZ/0wAxKGu3oYMlz8jy/kbhsNrS7SKZ7PxEHBgJ5+F2mtFW2fK2cOtBh1cHYkQsbzFV7I+EoRKe6Yt0oK7A==",
      "license": "MIT",
      "dependencies": {
        "p-limit": "^2.2.0"
      },
      "engines": {
        "node": ">=8"
      }
    },
    "node_modules/p-try": {
      "version": "2.2.0",
      "resolved": "https://registry.npmjs.org/p-try/-/p-try-2.2.0.tgz",
      "integrity": "sha512-R4nPAVTAU0B9D35/Gk3uJf/7XYbQcyohSKdvAxIRSNghFl4e71hVoGnBNQz9cWaXxO2I10KTC+3jMdvvoKw6dQ==",
      "license": "MIT",
      "engines": {
        "node": ">=6"
      }
    },
    "node_modules/parseurl": {
      "version": "1.3.3",
      "resolved": "https://registry.npmjs.org/parseurl/-/parseurl-1.3.3.tgz",
      "integrity": "sha512-CiyeOxFT/JZyN5m0z9PfXw4SCBJ6Sygz1Dpl0wqjlhDEGGBP1GnsUVEL0p63hoG1fcj3fHynXi9NYO4nWOL+qQ==",
      "license": "MIT",
      "engines": {
        "node": ">= 0.8"
      }
    },
    "node_modules/path-exists": {
      "version": "4.0.0",
      "resolved": "https://registry.npmjs.org/path-exists/-/path-exists-4.0.0.tgz",
      "integrity": "sha512-ak9Qy5Q7jYb2Wwcey5Fpvg2KoAc/ZIhLSLOSBmRmygPsGwkVVt0fZa0qrtMz+m6tJTAHfZQ8FnmB4MG4LWy7/w==",
      "license": "MIT",
      "engines": {
        "node": ">=8"
      }
    },
    "node_modules/path-to-regexp": {
      "version": "0.1.13",
      "resolved": "https://registry.npmjs.org/path-to-regexp/-/path-to-regexp-0.1.13.tgz",
      "integrity": "sha512-A/AGNMFN3c8bOlvV9RreMdrv7jsmF9XIfDeCd87+I8RNg6s78BhJxMu69NEMHBSJFxKidViTEdruRwEk/WIKqA==",
      "license": "MIT"
    },
    "node_modules/pngjs": {
      "version": "5.0.0",
      "resolved": "https://registry.npmjs.org/pngjs/-/pngjs-5.0.0.tgz",
      "integrity": "sha512-40QW5YalBNfQo5yRYmiw7Yz6TKKVr3h6970B2YE+3fQpsWcrbj1PzJgxeJ19DRQjhMbKPIuMY8rFaXc8moolVw==",
      "license": "MIT",
      "engines": {
        "node": ">=10.13.0"
      }
    },
    "node_modules/proxy-addr": {
      "version": "2.0.8",
      "resolved": "https://registry.npmjs.org/proxy-addr/-/proxy-addr-2.0.8.tgz",
      "integrity": "sha512-5nnx0yGyVUcY6t9RnWcARWtwT9F1D8O9rt08htPvnd49W1IgZtmLkhu9WfMzQj1cFxjHIO6connUNVW5k7AVyQ==",
      "license": "MIT",
      "dependencies": {
        "forwarded": "0.2.0",
        "ipaddr.js": "1.9.1"
      },
      "engines": {
        "node": ">= 0.10"
      },
      "funding": {
        "type": "opencollective",
        "url": "https://opencollective.com/express"
      }
    },
    "node_modules/qrcode": {
      "version": "1.5.4",
      "resolved": "https://registry.npmjs.org/qrcode/-/qrcode-1.5.4.tgz",
      "integrity": "sha512-1ca71Zgiu6ORjHqFBDpnSMTR2ReToX4l1Au1VFLyVeBTFavzQnv5JxMFr3ukHVKpSrSA2MCk0lNJSykjUfz7Zg==",
      "license": "MIT",
      "dependencies": {
        "dijkstrajs": "^1.0.1",
        "pngjs": "^5.0.0",
        "yargs": "^15.3.1"
      },
      "bin": {
        "qrcode": "bin/qrcode"
      },
      "engines": {
        "node": ">=10.13.0"
      }
    },
    "node_modules/qs": {
      "version": "6.16.1",
      "resolved": "https://registry.npmjs.org/qs/-/qs-6.16.1.tgz",
      "integrity": "sha512-5Px6J7rfHBAGUhNQ3es4YzcOdBBw0SDcVtThJml4RdnKw4tKs2epA0kPZ+K8X+WiHktT93RHVcQ/3ym5aTCgtg==",
      "license": "BSD-3-Clause",
      "dependencies": {
        "es-define-property": "^1.0.1",
        "side-channel": "^1.1.1"
      },
      "engines": {
        "node": ">=0.6"
      },
      "funding": {
        "url": "https://github.com/sponsors/ljharb"
      }
    },
    "node_modules/range-parser": {
      "version": "1.2.1",
      "resolved": "https://registry.npmjs.org/range-parser/-/range-parser-1.2.1.tgz",
      "integrity": "sha512-Hrgsx+orqoygnmhFbKaHE6c296J+HTAQXoxEF6gNupROmmGJRoyzfG3ccAveqCBrwr/2yxQ5BVd/GTl5agOwSg==",
      "license": "MIT",
      "engines": {
        "node": ">= 0.6"
      }
    },
    "node_modules/raw-body": {
      "version": "2.5.3",
      "resolved": "https://registry.npmjs.org/raw-body/-/raw-body-2.5.3.tgz",
      "integrity": "sha512-s4VSOf6yN0rvbRZGxs8Om5CWj6seneMwK3oDb4lWDH0UPhWcxwOWw5+qk24bxq87szX1ydrwylIOp2uG1ojUpA==",
      "license": "MIT",
      "dependencies": {
        "bytes": "~3.1.2",
        "http-errors": "~2.0.1",
        "iconv-lite": "~0.4.24",
        "unpipe": "~1.0.0"
      },
      "engines": {
        "node": ">= 0.8"
      }
    },
    "node_modules/require-directory": {
      "version": "2.1.1",
      "resolved": "https://registry.npmjs.org/require-directory/-/require-directory-2.1.1.tgz",
      "integrity": "sha512-fGxEI7+wsG9xrvdjsrlmL22OMTTiHRwAMroiEeMgq8gzoLC/PQr7RsRDSTLUg/bZAZtF+TVIkHc6/4RIKrui+Q==",
      "license": "MIT",
      "engines": {
        "node": ">=0.10.0"
      }
    },
    "node_modules/require-main-filename": {
      "version": "2.0.0",
      "resolved": "https://registry.npmjs.org/require-main-filename/-/require-main-filename-2.0.0.tgz",
      "integrity": "sha512-NKN5kMDylKuldxYLSUfrbo5Tuzh4hd+2E8NPPX02mZtn1VuREQToYe/ZdlJy+J3uCpfaiGF05e7B8W0iXbQHmg==",
      "license": "ISC"
    },
    "node_modules/safe-buffer": {
      "version": "5.2.1",
      "resolved": "https://registry.npmjs.org/safe-buffer/-/safe-buffer-5.2.1.tgz",
      "integrity": "sha512-rp3So07KcdmmKbGvgaNxQSJr7bGVSVk5S9Eq1F+ppbRo70+YeaDxkw5Dd8NPN+GD6bjnYm2VuPuCXmpuYvmCXQ==",
      "funding": [
        {
          "type": "github",
          "url": "https://github.com/sponsors/feross"
        },
        {
          "type": "patreon",
          "url": "https://www.patreon.com/feross"
        },
        {
          "type": "consulting",
          "url": "https://feross.org/support"
        }
      ],
      "license": "MIT"
    },
    "node_modules/safer-buffer": {
      "version": "2.1.2",
      "resolved": "https://registry.npmjs.org/safer-buffer/-/safer-buffer-2.1.2.tgz",
      "integrity": "sha512-YZo3K82SD7Riyi0E1EQPojLz7kpepnSQI9IyPbHHg1XXXevb5dJI7tpyN2ADxGcQbHG7vcyRHk0cbwqcQriUtg==",
      "license": "MIT"
    },
    "node_modules/send": {
      "version": "0.19.2",
      "resolved": "https://registry.npmjs.org/send/-/send-0.19.2.tgz",
      "integrity": "sha512-VMbMxbDeehAxpOtWJXlcUS5E8iXh6QmN+BkRX1GARS3wRaXEEgzCcB10gTQazO42tpNIya8xIyNx8fll1OFPrg==",
      "license": "MIT",
      "dependencies": {
        "debug": "2.6.9",
        "depd": "2.0.0",
        "destroy": "1.2.0",
        "encodeurl": "~2.0.0",
        "escape-html": "~1.0.3",
        "etag": "~1.8.1",
        "fresh": "~0.5.2",
        "http-errors": "~2.0.1",
        "mime": "1.6.0",
        "ms": "2.1.3",
        "on-finished": "~2.4.1",
        "range-parser": "~1.2.1",
        "statuses": "~2.0.2"
      },
      "engines": {
        "node": ">= 0.8.0"
      }
    },
    "node_modules/send/node_modules/ms": {
      "version": "2.1.3",
      "resolved": "https://registry.npmjs.org/ms/-/ms-2.1.3.tgz",
      "integrity": "sha512-6FlzubTLZG3J2a/NVCAleEhjzq5oxgHyaCU9yYXvcLsvoVaHJq/s5xXI6/XXP6tz7R9xAOtHnSO/tXtF3WRTlA==",
      "license": "MIT"
    },
    "node_modules/serve-static": {
      "version": "1.16.3",
      "resolved": "https://registry.npmjs.org/serve-static/-/serve-static-1.16.3.tgz",
      "integrity": "sha512-x0RTqQel6g5SY7Lg6ZreMmsOzncHFU7nhnRWkKgWuMTu5NN0DR5oruckMqRvacAN9d5w6ARnRBXl9xhDCgfMeA==",
      "license": "MIT",
      "dependencies": {
        "encodeurl": "~2.0.0",
        "escape-html": "~1.0.3",
        "parseurl": "~1.3.3",
        "send": "~0.19.1"
      },
      "engines": {
        "node": ">= 0.8.0"
      }
    },
    "node_modules/set-blocking": {
      "version": "2.0.0",
      "resolved": "https://registry.npmjs.org/set-blocking/-/set-blocking-2.0.0.tgz",
      "integrity": "sha512-KiKBS8AnWGEyLzofFfmvKwpdPzqiy16LvQfK3yv/fVH7Bj13/wl3JSR1J+rfgRE9q7xUJK4qvgS8raSOeLUehw==",
      "license": "ISC"
    },
    "node_modules/setprototypeof": {
      "version": "1.2.0",
      "resolved": "https://registry.npmjs.org/setprototypeof/-/setprototypeof-1.2.0.tgz",
      "integrity": "sha512-E5LDX7Wrp85Kil5bhZv46j8jOeboKq5JMmYM3gVGdGH8xFpPWXUMsNrlODCrkoxMEeNi/XZIwuRvY4XNwYMJpw==",
      "license": "ISC"
    },
    "node_modules/side-channel": {
      "version": "1.1.1",
      "resolved": "https://registry.npmjs.org/side-channel/-/side-channel-1.1.1.tgz",
      "integrity": "sha512-6x6dK6zJdpTzF4sQeNYxwtvBzf6Eg4GtlesS94HOvTudUeyK2WXAaIfmDgsyslYrRBeFIlsi54AYsFGUuhmvrQ==",
      "license": "MIT",
      "dependencies": {
        "es-errors": "^1.3.0",
        "object-inspect": "^1.13.4",
        "side-channel-list": "^1.0.1",
        "side-channel-map": "^1.0.1",
        "side-channel-weakmap": "^1.0.2"
      },
      "engines": {
        "node": ">= 0.4"
      },
      "funding": {
        "url": "https://github.com/sponsors/ljharb"
      }
    },
    "node_modules/side-channel-list": {
      "version": "1.0.1",
      "resolved": "https://registry.npmjs.org/side-channel-list/-/side-channel-list-1.0.1.tgz",
      "integrity": "sha512-mjn/0bi/oUURjc5Xl7IaWi/OJJJumuoJFQJfDDyO46+hBWsfaVM65TBHq2eoZBhzl9EchxOijpkbRC8SVBQU0w==",
      "license": "MIT",
      "dependencies": {
        "es-errors": "^1.3.0",
        "object-inspect": "^1.13.4"
      },
      "engines": {
        "node": ">= 0.4"
      },
      "funding": {
        "url": "https://github.com/sponsors/ljharb"
      }
    },
    "node_modules/side-channel-map": {
      "version": "1.0.1",
      "resolved": "https://registry.npmjs.org/side-channel-map/-/side-channel-map-1.0.1.tgz",
      "integrity": "sha512-VCjCNfgMsby3tTdo02nbjtM/ewra6jPHmpThenkTYh8pG9ucZ/1P8So4u4FGBek/BjpOVsDCMoLA/iuBKIFXRA==",
      "license": "MIT",
      "dependencies": {
        "call-bound": "^1.0.2",
        "es-errors": "^1.3.0",
        "get-intrinsic": "^1.2.5",
        "object-inspect": "^1.13.3"
      },
      "engines": {
        "node": ">= 0.4"
      },
      "funding": {
        "url": "https://github.com/sponsors/ljharb"
      }
    },
    "node_modules/side-channel-weakmap": {
      "version": "1.0.2",
      "resolved": "https://registry.npmjs.org/side-channel-weakmap/-/side-channel-weakmap-1.0.2.tgz",
      "integrity": "sha512-WPS/HvHQTYnHisLo9McqBHOJk2FkHO/tlpvldyrnem4aeQp4hai3gythswg6p01oSoTl58rcpiFAjF2br2Ak2A==",
      "license": "MIT",
      "dependencies": {
        "call-bound": "^1.0.2",
        "es-errors": "^1.3.0",
        "get-intrinsic": "^1.2.5",
        "object-inspect": "^1.13.3",
        "side-channel-map": "^1.0.1"
      },
      "engines": {
        "node": ">= 0.4"
      },
      "funding": {
        "url": "https://github.com/sponsors/ljharb"
      }
    },
    "node_modules/statuses": {
      "version": "2.0.2",
      "resolved": "https://registry.npmjs.org/statuses/-/statuses-2.0.2.tgz",
      "integrity": "sha512-DvEy55V3DB7uknRo+4iOGT5fP1slR8wQohVdknigZPMpMstaKJQWhwiYBACJE3Ul2pTnATihhBYnRhZQHGBiRw==",
      "license": "MIT",
      "engines": {
        "node": ">= 0.8"
      }
    },
    "node_modules/string-width": {
      "version": "4.2.3",
      "resolved": "https://registry.npmjs.org/string-width/-/string-width-4.2.3.tgz",
      "integrity": "sha512-wKyQRQpjJ0sIp62ErSZdGsjMJWsap5oRNihHhu6G7JVO/9jIB6UyevL+tXuOqrng8j/cxKTWyWUwvSTriiZz/g==",
      "license": "MIT",
      "dependencies": {
        "emoji-regex": "^8.0.0",
        "is-fullwidth-code-point": "^3.0.0",
        "strip-ansi": "^6.0.1"
      },
      "engines": {
        "node": ">=8"
      }
    },
    "node_modules/strip-ansi": {
      "version": "6.0.1",
      "resolved": "https://registry.npmjs.org/strip-ansi/-/strip-ansi-6.0.1.tgz",
      "integrity": "sha512-Y38VPSHcqkFrCpFnQ9vuSXmquuv5oXOKpGeT6aGrr3o3Gc9AlVa6JBfUSOCnbxGGZF+/0ooI7KrPuUSztUdU5A==",
      "license": "MIT",
      "dependencies": {
        "ansi-regex": "^5.0.1"
      },
      "engines": {
        "node": ">=8"
      }
    },
    "node_modules/thunky": {
      "version": "1.1.0",
      "resolved": "https://registry.npmjs.org/thunky/-/thunky-1.1.0.tgz",
      "integrity": "sha512-eHY7nBftgThBqOyHGVN+l8gF0BucP09fMo0oO/Lb0w1OF80dJv+lDVpXG60WMQvkcxAkNybKsrEIE3ZtKGmPrA==",
      "license": "MIT"
    },
    "node_modules/toidentifier": {
      "version": "1.0.1",
      "resolved": "https://registry.npmjs.org/toidentifier/-/toidentifier-1.0.1.tgz",
      "integrity": "sha512-o5sSPKEkg/DIQNmH43V0/uerLrpzVedkUh8tGNvaeXpfpuwjKenlSox/2O/BTlZUtEe+JG7s5YhEz608PlAHRA==",
      "license": "MIT",
      "engines": {
        "node": ">=0.6"
      }
    },
    "node_modules/type-is": {
      "version": "1.6.18",
      "resolved": "https://registry.npmjs.org/type-is/-/type-is-1.6.18.tgz",
      "integrity": "sha512-TkRKr9sUTxEH8MdfuCSP7VizJyzRNMjj2J2do2Jr3Kym598JVdEksuzPQCnlFPW4ky9Q+iA+ma9BGm06XQBy8g==",
      "license": "MIT",
      "dependencies": {
        "media-typer": "0.3.0",
        "mime-types": "~2.1.24"
      },
      "engines": {
        "node": ">= 0.6"
      }
    },
    "node_modules/unpipe": {
      "version": "1.0.0",
      "resolved": "https://registry.npmjs.org/unpipe/-/unpipe-1.0.0.tgz",
      "integrity": "sha512-pjy2bYhSsufwWlKwPc+l3cN7+wuJlK6uz0YdJEOlQDbl6jo/YlPi4mb8agUkVC8BF7V8NuzeyPNqRksA3hztKQ==",
      "license": "MIT",
      "engines": {
        "node": ">= 0.8"
      }
    },
    "node_modules/utils-merge": {
      "version": "1.0.1",
      "resolved": "https://registry.npmjs.org/utils-merge/-/utils-merge-1.0.1.tgz",
      "integrity": "sha512-pMZTvIkT1d+TFGvDOqodOclx0QWkkgi6Tdoa8gC8ffGAAqz9pzPTZWAybbsHHoED/ztMtkv/VoYTYyShUn81hA==",
      "license": "MIT",
      "engines": {
        "node": ">= 0.4.0"
      }
    },
    "node_modules/vary": {
      "version": "1.1.2",
      "resolved": "https://registry.npmjs.org/vary/-/vary-1.1.2.tgz",
      "integrity": "sha512-BNGbWLfd0eUPabhkXUVm0j8uuvREyTh5ovRa/dyow/BqAbZJyC+5fU+IzQOzmAKzYqYRAISoRhdQr3eIZ/PXqg==",
      "license": "MIT",
      "engines": {
        "node": ">= 0.8"
      }
    },
    "node_modules/which-module": {
      "version": "2.0.1",
      "resolved": "https://registry.npmjs.org/which-module/-/which-module-2.0.1.tgz",
      "integrity": "sha512-iBdZ57RDvnOR9AGBhML2vFZf7h8vmBjhoaZqODJBFWHVtKkDmKuHai3cx5PgVMrX5YDNp27AofYbAwctSS+vhQ==",
      "license": "ISC"
    },
    "node_modules/wrap-ansi": {
      "version": "6.2.0",
      "resolved": "https://registry.npmjs.org/wrap-ansi/-/wrap-ansi-6.2.0.tgz",
      "integrity": "sha512-r6lPcBGxZXlIcymEu7InxDMhdW0KDxpLgoFLcguasxCaJ/SOIZwINatK9KY/tf+ZrlywOKU0UDj3ATXUBfxJXA==",
      "license": "MIT",
      "dependencies": {
        "ansi-styles": "^4.0.0",
        "string-width": "^4.1.0",
        "strip-ansi": "^6.0.0"
      },
      "engines": {
        "node": ">=8"
      }
    },
    "node_modules/ws": {
      "version": "8.22.0",
      "resolved": "https://registry.npmjs.org/ws/-/ws-8.22.0.tgz",
      "integrity": "sha512-Ydggc987+RO0AnWtZ/7Wq9FtNvcrL1b/RO0ud9mWjUPgDrsAAwQSF51sm2hm1XofbU/4jkpGEsLFsZZxU+1DOg==",
      "license": "MIT",
      "engines": {
        "node": ">=10.0.0"
      },
      "peerDependencies": {
        "bufferutil": "^4.0.1",
        "utf-8-validate": ">=5.0.2"
      },
      "peerDependenciesMeta": {
        "bufferutil": {
          "optional": true
        },
        "utf-8-validate": {
          "optional": true
        }
      }
    },
    "node_modules/y18n": {
      "version": "4.0.3",
      "resolved": "https://registry.npmjs.org/y18n/-/y18n-4.0.3.tgz",
      "integrity": "sha512-JKhqTOwSrqNA1NY5lSztJ1GrBiUodLMmIZuLiDaMRJ+itFd+ABVE8XBjOvIWL+rSqNDC74LCSFmlb/U4UZ4hJQ==",
      "license": "ISC"
    },
    "node_modules/yargs": {
      "version": "15.4.1",
      "resolved": "https://registry.npmjs.org/yargs/-/yargs-15.4.1.tgz",
      "integrity": "sha512-aePbxDmcYW++PaqBsJ+HYUFwCdv4LVvdnhBy78E57PIor8/OVvhMrADFFEDh8DHDFRv/O9i3lPhsENjO7QX0+A==",
      "license": "MIT",
      "dependencies": {
        "cliui": "^6.0.0",
        "decamelize": "^1.2.0",
        "find-up": "^4.1.0",
        "get-caller-file": "^2.0.1",
        "require-directory": "^2.1.1",
        "require-main-filename": "^2.0.0",
        "set-blocking": "^2.0.0",
        "string-width": "^4.2.0",
        "which-module": "^2.0.0",
        "y18n": "^4.0.0",
        "yargs-parser": "^18.1.2"
      },
      "engines": {
        "node": ">=8"
      }
    },
    "node_modules/yargs-parser": {
      "version": "18.1.3",
      "resolved": "https://registry.npmjs.org/yargs-parser/-/yargs-parser-18.1.3.tgz",
      "integrity": "sha512-o50j0JeToy/4K6OZcaQmW6lyXXKhq7csREXcDwk2omFPJEwUNOVtJKvmDr9EI1fAJZUyZcRF7kxGBWmRXudrCQ==",
      "license": "ISC",
      "dependencies": {
        "camelcase": "^5.0.0",
        "decamelize": "^1.2.0"
      },
      "engines": {
        "node": ">=6"
      }
    }
  }
}
```

### campusguard/package.json

<!-- CAMPUSGUARD_SOURCE {"path": "package.json", "encoding": "utf-8", "sha256": "f2091187adb65d85d7bc4247e5e114cdfae7178c5c019d820fc94546ea4ecd54"} -->
```json
{
  "name": "campusguard",
  "version": "1.0.0",
  "private": true,
  "description": "Offline campus alert and response prototype",
  "type": "module",
  "engines": {
    "node": ">=20"
  },
  "scripts": {
    "start": "node server.js",
    "setup:https": "node scripts/setup-https.js",
    "udp:listen": "node agent/udp-listener.js",
    "seed": "node scripts/seed.js",
    "test": "node --test --test-concurrency=1 tests/protocol.test.js tests/integration.test.js tests/additions.test.js tests/hostels.test.js tests/ble-real.test.js tests/incidents.test.js",
    "check": "node scripts/check.js",
    "ble:probe": "powershell.exe -NoProfile -ExecutionPolicy Bypass -File bridge/ble_bridge.ps1 -Probe",
    "ble:smoke": "node scripts/ble-smoke.js"
  },
  "dependencies": {
    "bonjour-service": "^1.4.4",
    "express": "^4.21.2",
    "node-forge": "^1.3.1",
    "qrcode": "^1.5.4",
    "ws": "^8.18.0"
  }
}
```

### campusguard/public/admin.html

<!-- CAMPUSGUARD_SOURCE {"path": "public/admin.html", "encoding": "utf-8", "sha256": "4f9fcfd01bf597b9f610dbd09a3fbf7920e611aa944d5592c8c9940bd5128894"} -->
```html
<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta name="theme-color" content="#112b36"><title>CampusGuard · Operations center</title><link rel="icon" href="/icons/shield.svg" type="image/svg+xml"><link rel="stylesheet" href="/styles.css"><script type="module" src="/js/admin.js"></script></head>
<body class="admin-body">
<aside class="sidebar"><a class="brand" href="/admin.html"><img src="/icons/shield.svg" width="34" height="38" alt=""><span>Campus<span class="brand-light">Guard</span><small>OPERATIONS CENTER</small></span></a><div class="sidebar-caption">WORKSPACE</div><nav id="nav"><button class="nav-item active" data-page="overview"><span>▦</span>Overview<span class="nav-mark">01</span></button><button class="nav-item" data-page="history"><span>◷</span>Alert history<span class="nav-mark" id="history-nav-count">0</span></button><button class="nav-item" data-page="transport"><span>⇄</span>Transport lab</button><button class="nav-item" data-page="connect"><span>⌁</span>Connect devices</button></nav><div class="sidebar-network"><span class="dot green"></span><strong>Local by design.</strong><p>Your campus network is the connection. No cloud required.</p><span class="micro mono">LAN / OFFLINE READY</span></div><div class="sidebar-bottom"><span class="avatar">04</span><div><strong>404 FOUND</strong><small>Tech Elevate 2026</small></div><button id="logout" class="icon-button" title="Log out">↗</button></div></aside>
<div class="admin-shell"><header class="admin-header"><div class="breadcrumb">Campus safety <span>/</span> <strong id="breadcrumb-page">Overview</strong></div><div class="header-actions"><span id="server-status" class="status-pill"><span class="dot green"></span> Local server online</span><span id="admin-clock" class="mono"></span><a href="/" target="_blank" class="btn small outline">Student view ↗</a></div></header>
<main class="admin-main">
<div class="page-heading"><div><span class="eyebrow">CAMPUS SAFETY, CONNECTED</span><h1 id="page-title">Emergency operations</h1><p id="page-subtitle">One alert. Every connected student. A faster response.</p></div><span class="outline-stamp">⌁ <span>INTERNET OPTIONAL<br><strong>LOCAL NETWORK ACTIVE</strong></span></span></div>
<section class="stats-row" id="stats"></section>
<section id="page-overview" class="page-section">
<section class="incident-workspace"><div class="panel-heading"><h2>Incident overview</h2><div class="incident-tools"><select id="incident-select" aria-label="Select incident"></select><button id="edit-campus-contacts" class="btn small outline">Edit campus contacts</button></div></div><div id="incident-card" class="incident-card"></div></section>
<div class="workspace-grid"><section class="panel compose-panel"><div class="panel-heading"><div><span class="section-number">01</span><h2>Broadcast an alert</h2></div><span class="tag">ADMIN ONLY</span></div><p class="panel-description">Clear instructions. The right people. Right now.</p>
<form id="alert-form"><label>Emergency type</label><div id="type-grid" class="type-grid"></div><div class="form-two"><div class="hostel-target"><span>Target hostels</span><div id="alert-zones" class="hostel-picker" role="group" aria-label="Target hostels"></div></div><label>Severity<select id="alert-severity" name="severity"><option value="3">3 · Critical</option><option value="2">2 · Urgent</option><option value="1">1 · Advisory</option></select></label></div><label>Assembly point<select id="alert-assembly"><option value="football">Football ground</option><option value="cricket">Cricket ground</option></select></label><label>Safety instructions<textarea id="alert-message" rows="3" maxlength="400" required placeholder="Tell students what happened and what to do next."></textarea></label><div class="form-meta"><span id="message-count">0 / 400 bytes</span><label>Expires after <select id="alert-ttl"><option value="60">1 minute</option><option value="300" selected>5 minutes</option><option value="900">15 minutes</option><option value="1800">30 minutes</option><option value="3600">1 hour</option></select></label></div><button class="btn broadcast full" type="submit"><span>ϟ</span> Review & broadcast <span>→</span></button><p class="micro muted center">Sent over available transports. Receipt tracking starts instantly.</p></form></section>
<section class="panel campus-panel"><div class="panel-heading"><div><span class="section-number">02</span><h2>Campus coverage</h2></div><span class="live-label"><span class="dot green"></span> LIVE</span></div><div class="campus-map"><span class="map-label mono">CAMPUS / LOCAL NETWORK</span><div class="map-road horizontal"></div><div class="map-road vertical"></div><div class="map-road bottom"></div><div class="map-server"><span>◈</span><small>LOCAL SERVER</small><span class="map-pulse"></span></div><div id="map-zones"></div><span class="map-compass">N ↑</span><span class="map-note">SCHEMATIC · NOT A LIVE LOCATION MAP</span></div><div class="map-legend"><span><i class="dot green"></i>Connected client</span><span><i class="dot amber"></i>No client connected</span><span><i class="dot red"></i>Active alert</span></div><div id="zone-summary" class="zone-summary"></div><div class="coverage-footnote">Hostel status reflects LAN alerts and student responses. Bluetooth transmission is shown separately in Transport lab.</div></section></div>
<section class="panel"><div class="panel-heading"><h2>Student incident reports</h2><span class="tag">SAVED FOR ADMIN REVIEW</span></div><div id="incident-reports"></div></section><section class="panel recent-panel"><div class="panel-heading"><div><span class="section-number">03</span><h2>Latest broadcasts</h2></div><button class="text-link" data-go="history">View all activity ↗</button></div><div id="recent-alerts"></div></section>
</section>
<section id="page-history" class="page-section" hidden><div class="panel"><div class="panel-heading"><h2>Broadcast history</h2><span class="tag">PERSISTED ON THIS LAPTOP</span></div><p class="panel-description">Delivery receipts and safety responses are counted separately.</p><div id="history-table" class="table-wrap"></div></div></section>
<section id="page-transport" class="page-section" hidden><div class="info-banner"><span>⇄</span><div><strong>Make the failure visible.</strong><p>Disable WebSocket to demonstrate SSE. Disable SSE too to demonstrate polling. Existing peer connections can carry cached alerts when the server is unavailable.</p></div><button id="restore-transports" class="btn small outline">Restore connections</button></div><div id="transport-grid" class="transport-grid"></div><div class="panel ble-panel"><div class="panel-heading"><h2>Bluetooth radio</h2><span id="ble-mode-tag" class="tag amber-tag">REAL BLE</span></div><p id="ble-explanation" class="micro muted">Real advertisements use the laptop’s built-in Bluetooth. An OS advertising start does not confirm reception on a phone.</p><div id="ble-status" class="ble-zones"></div><div id="beacon-log" class="beacon-log"></div></div></section>
<section id="page-connect" class="page-section" hidden><div class="connect-grid"><div class="panel"><span class="eyebrow">YOUR DEMO STARTS HERE</span><h2 class="connect-title">One network.<br>A connected campus.</h2><ol class="setup-list"><li>Connect the laptop and phones to the same Wi-Fi or hotspot.</li><li>Keep the server running on the laptop. Allow Node.js through Windows Firewall on your trusted network.</li><li>Open a student link below. Choose a zone, then tap <strong>Enable alerts & test sound</strong>.</li><li>Keep each student page open. Use different zones to demonstrate targeting.</li></ol><div id="join-links"></div><div class="https-note"><strong>Want installable PWA & peer relay?</strong><p>Run <code>npm run setup:https</code>, restart the server, and trust the local CA on each device. See the included setup guide. The HTTP student view supports the core alert demo.</p><a href="/setup.html" class="text-link">Open local HTTPS setup guide ↗</a></div></div><div class="panel qr-panel"><span class="eyebrow">SCAN TO JOIN</span><div id="qr-wrap"></div><p id="qr-url" class="mono"></p><p class="micro muted">The QR opens a local address. It works only on the same network.</p></div></div></section>
<footer class="admin-footer"><span>CAMPUSGUARD / SOFTWARE PROTOTYPE</span><span id="server-id" class="mono"></span></footer>
</main></div>
<dialog id="login-modal" class="modal login-modal"><form id="login-form"><img src="/icons/shield.svg" width="52" height="58" alt="CampusGuard"><span class="eyebrow">CAMPUSGUARD OPERATIONS</span><h2>Ready to respond.</h2><p>Enter the admin PIN shown in your server window.</p><label>Admin PIN<input id="admin-pin" type="password" inputmode="numeric" autocomplete="current-password" required placeholder="••••••" maxlength="32"></label><button class="btn primary full" type="submit">Open operations center →</button><p id="login-error" class="form-error" role="alert"></p><span class="micro muted">Local access. Secure session. No cloud account.</span></form></dialog>
<dialog id="review-modal" class="modal"><span class="eyebrow">REVIEW BROADCAST</span><h2>Ready to send?</h2><div id="review-content"></div><div class="review-actions"><button id="cancel-send" class="btn outline">Keep editing</button><button id="confirm-send" class="btn broadcast">Broadcast alert →</button></div></dialog><dialog id="responses-dialog" class="modal response-modal"><div class="panel-heading"><h2 id="responses-title">Responses</h2><button id="close-responses" class="icon-button" aria-label="Close responses">×</button></div><label>Show<select id="response-filter"><option value="all">All recipients</option><option value="needs-help">Needs help</option><option value="safe">Marked safe</option><option value="awaiting">Awaiting response</option></select></label><div id="response-rows"></div></dialog>
<dialog id="all-clear-dialog" class="modal"><form id="all-clear-form"><h2>Issue an all-clear?</h2><p>This clears earlier alerts in the incident’s selected hostels. Confirm campus staff have authorized it.</p><label>Message<textarea id="all-clear-message" required maxlength="400" rows="3"></textarea></label><div class="review-actions"><button type="button" id="cancel-all-clear" class="btn outline">Cancel</button><button type="submit" class="btn dark">Confirm all-clear</button></div></form></dialog>
<dialog id="campus-contacts-dialog" class="modal"><form id="campus-contacts-form"><h2>Campus contacts</h2><label>Medical support<input id="medical-phone" inputmode="tel" maxlength="24"></label><label>Campus security<input id="security-phone" inputmode="tel" maxlength="24" placeholder="Not configured"></label><div class="review-actions"><button type="button" id="close-campus-contacts" class="btn outline">Cancel</button><button type="submit" class="btn dark">Save contacts</button></div></form></dialog>
<div id="toast" class="toast" role="status"></div>
</body></html>
```

### campusguard/public/icons/icon-192.png

<!-- CAMPUSGUARD_SOURCE {"path": "public/icons/icon-192.png", "encoding": "base64", "sha256": "9cf703b4a7f4ec26854f323c851f5b7fdb29160d896b7eaba99f50196f8ae522"} -->
```base64
iVBORw0KGgoAAAANSUhEUgAAAMAAAADACAIAAADdvvtQAAAFlElEQVR4nO3dTW4bSQyGYTvIBXyO7LMzcg6ekOcwcrEsBBiC7cjdzfohP77vajBIZlpdj1jVsoI8v/z6/UR0tR+XfycRgCgaE4hCAYhCAYhCAYhCAYhCAYhCAYhCAYhCAYhCAYhCAYhCAeib/O01dofFe+brHMfd2J+/0xekWgA6PXJgdB+Arm9VxkACUPyUY70Z9Z1Aw0/H1lJSO0Czn6qsGaNGgBY/kFsPSfqAtn+QY9KSZAFtd9OEkSCgbHS0JekASu5GlZECoFp0xCQVBlTajQyjeoCU3AhIqgRIm05RSQUAtXJTjlFqQJ3pVJGUERBuCjFKBAg3FSWlAASdupJ2AsKNAKMNgHCjJGkpIOjoMVoBCDfCkuYCgo48oymAcNNHEn+0mUIBiEIBiEIBiEIBKPvPCpL3c/cFFKBz+wceLb8MQEdHzvu/R9J9ADq9WzGQ7gPQxVMOA+lWa0BDDsjW+4TUFNDwZyvryqgXoNnP5NbvoN0F0OKPc6zNQBIHtPdjQGswkGQBpfoE2XQHkiCgVHTkGekASutGe19TAFSFjuRAKgyoqBuxgVQSkAYdjYHE94EoFIAoFIAoFIAoFIAoFIAoFIAoFIAoFIAoFIAoFIAoFIAoFIAm5tV+MnohAM3V4+qGADQlv3OjbQhA4/NPYoQNAWhR/vYqyQhAg/OHSvQMAWhkfsCHmCEADcuPyRD7QjeAxuQt9QBoTN5VD4AG5FpnmrOxhS3KFMcPgKJ5483rFhPoet5eD4DQE40JdCVmz3sAOh167gMQhQLQuRg/HwLQidDTHVDkU2P0dAcU+ZIyeroDinxJGT3dAUW+pIyex7UA9GXNf4qeGtDsnx2eWvsHv/jb/87i8eMzTU9aFPEJFCHC5qUMaODX17/8Zev1eM0tdRagDN+AOX4NH/7QFrNHfwIdXOZTjk99UDT2HeKTx8+893NhQAe7YIiOpw9o3l+xO6rSaicCWnAMOn7rB15MhuNdnlpMoOF/S3yt8WMzxZcHdGoB7M/fyN1k9qwGlPOOX7uqGa/FK59+RCbQtWU4qyHnOyHDlSsAutZ2E15//KwAtH2d4teW+SVsT2QCXX43f4tjkh6XGD86gCI9IFJ99tj8618BaM0yRN7Ttyu8v87gA3+T8VP1r/2e0WdD1G4LK/HO9goX2RQQ3bdmmi4CtGxrSP7+9tyXdyHBCZR2kTzrhUUSBJRzqTzfJRUDxAPOypbdbc0JlO0d75kupjCgxUMoybL58stYeZ9XT6Buhlxaj/IWlsGQ7+arCWj9aXrLQvqO/+n6e7tnAjUxtLgtz7nbtjBtQ65+9ElxBlL9ZMjb6Nl/iNZ7KPNOevYDWt/UBfYGJ610gGQOQ97jsSsdICVD3fRkASRgyJsdfdIBKm3Iu+rJBaioIW+sJx2gcrnEWUoKUKEh5C0fu7IDqmWouZ6kgEoYan70yQ4ouSH0FACU1hB6ygDa0mMfVU5Ly8oOaMvG/z8lPHbVA5T28LgmS//aCwBKchji6FMY0HZD6CkPaKMh9IgA2hKPXVKA8h8qu73GYoDK3V/5V1cPUMW7LPy6nl9+/X4qm8wBxQrSKTyBBO67zKuoPYGqjyKrTEdhApVeCSt4zbKAyq2Hlbpa/S2s0HZmKnTUJlCJFbLE13YtQUBp18lSXlUwwS0s4XZminSUJ1CqlbME1zAv8Qn0Ht9GnZT4BNo4Bkx68LQDtHhFrYeeRlvYsu3M2tBpN4EWrLE109N0As0YRdaPTt8JNHzVraue7oCGrL011tN9CwtuZ9abzq3uE+iyBvTcAtAVE+h5jy3s3HYGnQ8xgU4oQc/nmECHRhF0/hcT6FE3N+h5EBOIQjGBKBSAKBSAKBSAKBSAKBSAKBSAKBSAKBSAKBSAKBSAKBSAKBSA6CnSP1g0UfY+Jc4gAAAAAElFTkSuQmCC
```

### campusguard/public/icons/icon-512.png

<!-- CAMPUSGUARD_SOURCE {"path": "public/icons/icon-512.png", "encoding": "base64", "sha256": "42a7dc894af25de7d8b26e1c4eef7735b18e6b1faeb98893f0e111800d0c8005"} -->
```base64
iVBORw0KGgoAAAANSUhEUgAAAgAAAAIACAIAAAB7GkOtAAASEElEQVR4nO3dWY4cR5qFUUqoDWgdetcbwXXkCrkOghsrFCgksyJjcPPB7De75zw1uoCuGNzv5+4ZUv/x19//fAEgz5+jXwAAYwgAQCgBAAglAAChBAAglAAAhBIAgFACABBKAABCCQBAKAEACCUAAKEEACCUAACEEgCAUAIAEEoAAEIJAEAoAQAIJQAAoQQAIJQAAIQSAIBQAgAQSgAAQgkAQCgBAAglAAChBAAglAAAhBIAgFACABBKAABCCQBAKAEACCUAAKEEACCUABDt+4+vo18CDPPHX3//M+6/HQrt/tu3nyNeCwwjAGR5eckvA+QQACLseNSjBCxPAFjcwaf8MsDCBIA1nf7XXSVgPQLAai79YY8MsBIBYBGdf9CpBCxAAJjb2B/yywBTEwBmVeqf4VICZiQATKbU7n+mBExEAJhG8en/SAaYggBQ3US7/5kSUJkAUNfU0/+RDFCTAFDOMrv/mRJQigBQxcK7f0MGKEIAGC9n+m8oAWMJAMPE7v4NGWAUAWAA03+XEtCZANCP3d9ICehDAOjB9O8gA1xNALiQ3T+FEnARAeB8dv8KMsDpBIAzmf4OlICzCAAnsPv9yQDHCQCHmP7hlIDdBIA97H41MsAOAkAb01+cErCdALCJ3Z+OEvCSAPCM3Z+dDPCEAHCf6V+MEvCZAPB/7P7aZICPBIB/mf4oSoAAYPejyUA4dwC5XPLzTgkyCUAcu88jMpBGAFLYfbZTghACsD7Tz25KsDYBWJbd5ywysCoBWJDp5yJKsBgBWIfdpw8ZWIYArMD0M4QSzE4AJmb3qUAG5iUA87H71KQE0xGAmZh+pqAEsxCACdh9ZiQD9QlAaaafBShBWQJQkd1nPTJQkADUYvpZnhLUIQAl2H3SyEAFAjCS3QclGEgAxjD98JEMDCEAXdl9eE4JehKATkw/NFGCDgTgWnYfjpCBSwnAVUw/nEgJriAAJ7P7cB0ZOJcAnMPuQ09KcAoBOMr0wygycJAA7GT3oQ4l2EcAmpl+qEkGWgnAVnYfZqEEGwnAa6YfJqUEzwnAQ3Yf1iADjwjALbsPq1KCGwLwm+mHBDLwTgDsPoR6+/bzS7boALjkB96CM5AYALsPfBZYgqwAmH7gubekDEQEwO4Drd4CSvDn6BcAwBgCABBKAABCCQBAKAEACCUAAKEEACCUAACEEgCAUAIAEEoAAEIJAEAoASBawr/wCx75z8P/BDKm/9f/4F8ZSyABIMujS/73/70SkEMASLHxaY8bAnIIAIvb95TfDQEJBIBlnfIHXjcELEwAWM0VP+xxQ8CSBIB1dPhNpxsCViIATK//b/ndELAGAWBiw/8xLjcETE0AmM/w3b/hhoBJCQAzqTb9N9wQMBcBYALFd/+GGwJmIQCUNtf033BDQHECQFFTT/9HMkBZAkAty+z+Dc+FKEgAqGLV6b/hhoA6BIDBQnb/hhsCKhAAhsmc/htuCBhIAOjN7j/5TPy/o6EnAaAf07/xI5IB+hAALmf3d39iSsClBIALmf5TPkAZ4CICwPns/kWfpxJwLgHgTKb/Um4IOJcAcAK735MbAs4iABxi+gdyQ8BBAsBOpr8IGWA3AaCN3a/JcyF2EAC2Mv1TcEPAdgLAC3Z/Rm4I2EIAeMj0L8ANAU8IALfs/nrcEHCXAPCb6V+eGwI+EgDsfhw3BPwiANFc8odzQxBOABLZfe4eD/5lc2kEIIvp5+XhIQM5BCCC3WfH0aIEyxOAxZl+Dh48MrCwP0e/AADGEACAUAIAEEoAAEIJAEAoAQAIJQAAoQQAIJQAAIQSAIBQAgAQSgAAQgkAQCgBAAglAAChBAAglAAAhBIAgFACABBKAABCCQBAKAEACCUAAKEEACCUAACEEgCAUAIAEEoAAEIJAEAoAQAIJQAAoQQAIJQAAIQSAIBQAgAQSgCgru8/vo5+CaxMAKD0+msA1xEAqOjj7msAFxEAKOfz4n//8VUGOJ0AwDQ0gHMJAMy08hrAiQQACtmy7xrAWQQAqti+7P4kwCkEAErYcV3vVoCDBADG2z3lGsARAgCDHRxxDWA3AYDp+ZMA+wgAjHTi9btbAVoJAAxz+mRrAE0EAMa4aKw1gO0EAAa4dKb9SYCNBAB663OR7laAlwQAuuq5yxrAcwIAy3r79nP0S6A0AYAvS16SW39eEgDoxPpTjQBADx7HU5AAwGrr7+EPGwkAXMv6U5YAwIWsP5UJACzCkx9aCQBcxc9+KE4A4BLWn/oEAM7nR59MQQDgZP7wyywEAM5k/ZmIAMBprD9zEQCYkh99cpwAwDn87IfpCACcwPozIwGAo/zok0kJABziD7/MSwBgP+vP1AQAdrL+zE4AYAJ+9MkVBAD28LMfFiAA0Mz6swYBgDZ+9MkyBAAa+MMvKxEA2Mr6sxgBgIr87IcOBAA28Ydf1iMA8Jr1Z0kCAC9Yf1YlAPCMH32yMAGAh/zsh7UJAIOVvcS2/ixPABg/smUb0I0ffTKEADDMx92v1gB/+CWBAFBlYes0wPoTQgAotLAVGmD9ySEA1FrYsQ2oUCDoRgAot7CjVtjPfkgjAFRc2P4NsP4EEgCKLuzCT2P86JMiBIAe9q15twb4wy+ZBIDS89phmq0/sSICkHzHPfxByvEXcOlbsP51Pp9S3jJGIyIAzD4fF81Q7LrBLwKwvjV+VXn6u/Czn2ofEf0JANNsx4n/N60/BAUg5Ilekb277r9uxmvSSY+9GT/q8K9sh5QAsMxwFP+rcuyUMCMBSNFn9er/t1j/ap8SAwUFwLXYSqtR/J8sgykEBYBL1f+39/jDb80Pqpq3pKd2AhDk0hN7yGnz/cfXjW/K+sNnAsD0l04vx936w11ZAYi6uRsyhWUb0M3sx1idT3KIt8m/vlZZAaCDag3wsx94RADidBjEOg2w/gc/QNYWF4C0W7xRKjTAnMFzcQEgpAH+8Lvvc0v2lnd1KACJup3qFe4DOggcDtYgAFxr+XFc4w26/M+UGIA1ztiD/HX0FI4lppYYAPpbciiXeVMu/78s9G02EYBcHpQfkbkXLCY0AM5eHzsQGgBG3fuvkd413sUvnv98WesLbSIA9Db7yTb764d3AsAA827ovK/8Lpf/4XIDsNiZPN0EzPj5z/ia4YncADDcXHs616vdwuX/qt/sdgLAyCF4+/ZzitNvihcJraID4KwuwhfRn8t/0gNAnTmo3IDKrw2OEACqqLmzNV/V7L2v423F73c7AaCQamdjtdcD50oPgDO82lVhnW+kzitZ8oumgvQAUFCF5a3wGrjaW/y3LABUvDYce2baBUIIgLO9KCu8duOpQACoOxBDGiA85BAASus8x2uvf6m6D/e29He9kQBQfSa6nagWgTQC8D/O/OI6fEHLHwPVuk4FAsAcLh3o5dcf7hIAprlavGimE9a/5hc6UMKXvoUA/MsBMQVfE5xIAJjsmvHcBiQUpexXyXACwHzOWu2E9YcnBIAprxyPb7f1j+WrfycAvzksQr6vWf7/UC5fcYYTACa2Y8pzph9eEgCmv37cvulp6z/F19dZ2jHw3H9e/Odh3r79dM5MfVbf/fqc83CXAPDC9x9fJxrQiV7q1VzK8JJHQAChBIDXXEtOx1d2lxvEGwJwyyEChBAANnFFORFfFhsJAEAoAbjDU6C7XFdOwdf0iPP6MwGggXEpzhdEEwGgjYkpy1dDKwEA1uf5z10CcJ/D5QlXmgX5UthBANjD3JTi62AfAWAno1OEL4LdBOAhT4FeMj3D+Qq2cC4/IgAcYoAG8uFzkABwlBmCSQkATEl3OU4AnvHocCNj1JkPfDtn8RMCwDlMUjc+as4iAC+4fNjOMHXgQ27i/H1OAF5zDG1nni7l423izH1JADiZkYJZCMAmLiWaaMAVfKpNnLNbCMBWjicGsv5NnK0bCUADR9V2ButEPswmztPtBKCNY2s7s3UKH2MTZ2gTAWjmCNvOeB3kA2zi3GwlAFzLhPnoKEsA9nCh0UQD6MBZuYMA7ORoa6IBrXxiTZyP+wjAfo45LmL9mzgTdxOAQxx52xk1H9QVnINHCMBRjr/tNMBHdC5n30ECQFca4MOhDgE4gcuQJhrAKZx3xwnAORyLTTTAZ+KMq0AATqMBTTTAp+FcG04AzqQB7KCFzrJRBOBkGrCd4fMhOL/GEgBGCm9A+NtnOAE4n5uAJrEjGPvGd3NmnU4ALuFIbWIKcU4NIQBX0YAmaQ1Ie78HOZsuIgAXctQ2ydnEnHd6CufRdQTgWo5dblh/Z1AdAkAhy4/j8m+QuQjA5dwENFl4Ihd+axdx7lxNAHpwHDcxlDhr+hCATjQgvAHrvaNLOV/6EIB+HNOxi7nSe+nAmdKNAHTlyA5k/Zs4R3oSAOpaYDoXeAssTAB6c4GTM6BTv/ghnB2dCcAAjvImZjSE86I/ARjDsb58A2Z8zQM5I4YQgGEc8Qvv6VyvdjjnwigCMJLjfslVneV1FuEsGEgA4EzWn4kIwGAuf1aa1+IvryDH/1gCMJ5zYI2RLfvCynLkDycAJTgTmpjaBTjmKxCAKpwPUzeg2uspztFehAAU4qyYdHPrvJIpOM7rEAAmVmF5K7wG2EcAanFxNBfr38oRXooAlOMMmWWCrX8rx3Y1AlCR86SJIZ6Co7ogASjK2VK8AarTxPFckwDU5Zwpu8jWv4kjuSwBYB19dtn6swwBKM2lU7V1tv6tHMOVCUB1zp86rH8rR29xAjABZ1GFmbb+rRy39QnAHJxLTYz1cI7YKQjANJxRAxugKE0cq7MQAJZ11mpbf1YlADNxYdV/u61/K0fpRARgMs6ungtu/Vs5PuciAPNxjvVh/Vs5MqcjAFNypjUx5R04JmckALNyvl3aAM1wNCYQAFJs33TrTwgBmJibgFZblt36Ow5zCMDcNKDV8323/o7AKAIwPQ1o9Wjlrb9jL40ArEADcNSxgwAsQgOafL7Yd/nveAskAIT6uPjWn0wCsA43Aa1+7b71d6TF+uOvv/8Z/Ro4kznjUq4zVuIOYDXOTxxdbCQAC9IAHFdsIQBr0gAcUbwkAAChBGBZbgJwLPGcAKxMA3AU8YQALE4DcPzwiH8OIIV/PoAmLh0SuANI4XzG0cINAQiiAThO+MgjoEQeB3GXS4Q07gASOc9xVOAOIJ1bAVwQJHMHEM2tAI6BZAKQzvmfzLcfzh+B+ZfHQVFMP+4A+M0i5PBd84tHQPxmFxL4lnnnERB3eBy0JNPPDXcA3GEp1uM75TN3ADzjVmABpp9H3AHwjO2YnW+QJwSAFyzIvHx3POcREFt5HDQR088W7gDYyqbMwjfFRgJAA8tSn++I7TwCYg+Pgwoy/bRyB8AetqYa3wg7uAPgELcCw5l+dnMHwCHWZyyfP0cIAEfZoFF88hzkERCn8TioG9PPKdwBcBqr1IfPmbMIAGeyTVfzCXMij4C4hMdBpzP9nM4dAJewVj5P6nMHwLXcChwkpVzHHQDXsl8+PcoSAC6nAT43avIIiH48DtpIMunDHQD92DWfEqUIAF1pgM+HOjwCYgyPg25II/25A2AMe+fTYDgBYBgN8DkwlkdAjBf7OEgCGcsdAONl7mDmu6YUdwAUEnIrYPopwh0AhSQsY8J7ZBYCQC1r7+Pa747peAREUYs9DjL9FOQOgKJWWsyV3gsrEQDqWmM313gXLMkjICYw6eMg009x7gCYwIxLOuNrJo07AGYyxa2A6WcW7gCYSf1trf8K4Z0AMJnKC1v5tcFnHgExq1KPg0w/M3IHwKzqbG6dVwJNBICJVVjeCq8B9vEIiBUMeRxk+pmdOwBW0H+LrT8LcAfAUjrcCph+luEOgKVcvc7Wn5UIAKu5bqOtP4vxCIhlnfg4yPSzJHcALOus1bb+rEoAWNnx7bb+LMwjICLseBxk+lmeOwAitK659SeBOwCyvLwVMP3kcAdAluf7bv2JIgDEebTy1p80HgGR6/1xkOknkzsAcv3afetPLHcAAKHcAQCEEgCAUAIAEEoAAEIJAEAoAQAIJQAAoQQAIJQAAIQSAIBQAgAQSgAAQgkAQCgBAAglAAChBAAglAAAhBIAgFACABBKAABCCQBAKAEACCUAAKEEACCUAACEEgCAUAIAEEoAAEIJAEAoAQAIJQAAoQQAIJQAAIQSAIBQAgAQSgAAQgkAwJdM/wX/HyID/w3JmwAAAABJRU5ErkJggg==
```

### campusguard/public/icons/shield.svg

<!-- CAMPUSGUARD_SOURCE {"path": "public/icons/shield.svg", "encoding": "utf-8", "sha256": "6c53dc580f0558ee9060058562995de0126d7b076aec315b5abc536be8026e8c"} -->
```xml
<svg xmlns="http://www.w3.org/2000/svg" width="64" height="72" viewBox="0 0 64 72"><path fill="#b8ec73" d="M32 2 60 13v23c0 15-13 27-28 34C17 63 4 51 4 36V13Z"/><path fill="#112b36" d="m32 15 15 6v15c0 9-7 17-15 22-8-5-15-13-15-22V21Z"/><path fill="none" stroke="#b8ec73" stroke-width="5" stroke-linecap="round" stroke-linejoin="round" d="m24 36 6 6 12-14"/></svg>
```

### campusguard/public/index.html

<!-- CAMPUSGUARD_SOURCE {"path": "public/index.html", "encoding": "utf-8", "sha256": "169f967c0abb786c4b4b836e5fa040ab903085a027a740177602ce9f596beef2"} -->
```html
<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover"><meta name="theme-color" content="#112b36"><meta name="description" content="CampusGuard local campus safety alerts and acknowledgements"><title>CampusGuard · Your safety companion</title><link rel="icon" href="/icons/shield.svg" type="image/svg+xml"><link rel="manifest" href="/manifest.webmanifest"><link rel="stylesheet" href="/styles.css"><script type="module" src="/js/client.js"></script></head>
<body class="student-body">
<header class="student-header"><a class="brand" href="/"><img src="/icons/shield.svg" width="34" height="38" alt=""><span>Campus<span class="brand-light">Guard</span><small>CAMPUS SAFETY NETWORK</small></span></a><nav class="student-nav"><a href="#safe-state">⌂ My safety</a><a href="#assembly-card">⌖ Campus map</a><a href="#activity-heading">◷ Alert history</a></nav><a class="quiet-link" href="/admin.html">Admin ↗</a></header>
<main class="student-main">
  <div class="student-top"><span class="eyebrow">YOUR SAFETY COMPANION</span><span id="clock" class="mono"></span></div>
  <section class="welcome-row"><div><h1 id="greeting">Stay connected.<br>Stay informed.</h1><p id="welcome-status">Campus alerts, even without internet.</p></div><span class="round-shield">◈</span></section>
  <section class="connection-card"><div><span id="connection-dot" class="dot amber"></span><strong id="connection-title">Connecting</strong><span id="connection-mode" class="tag">LOCAL</span></div><p id="connection-detail">Join your campus Wi-Fi or hotspot to receive alerts.</p><div id="peer-info" class="micro">Peer relay starts on a secure connection.</div></section>
  <section class="student-tools"><label>Your zone<select id="student-zone" aria-label="Your campus zone"></select></label><span id="response-status" class="micro muted"></span></section>
  <p id="capability-note" class="micro muted"></p>
  <button id="peer-only" class="text-link peer-demo" type="button" hidden>Test peer-only alert reception</button>
  <section id="student-active" class="active-safety" hidden></section>
  <section id="safe-state" class="calm-card"><span class="calm-icon">✓</span><span class="eyebrow">CAMPUSGUARD IS LISTENING</span><h2>No active alerts<br>for your zone.</h2><p>Keep this page open. If an emergency is reported, instructions will appear here.</p><div class="calm-footer"><span class="dot green"></span> Ready when it matters</div></section>
  <section class="safety-grid">
    <article class="safety-card"><span class="eyebrow">YOUR SAFETY SETUP</span><h2>Ready when it matters.</h2><p id="live-readiness">Connecting to the campus network…</p><p id="sound-readiness">Alert sound needs your permission.</p><button id="enable-sound" class="btn dark full">♫ Enable alerts & test sound</button><p class="micro muted">Keep this page open. Background notifications are not guaranteed.</p></article>
    <article class="safety-card"><span class="eyebrow">HELP IS CLOSE</span><h2>Campus support</h2><div id="support-contacts"></div><button id="view-contacts" class="btn outline full">View emergency contacts</button></article>
    <article id="assembly-card" class="safety-card"><span class="eyebrow">YOUR DESIGNATED LOCATION</span><h2 id="assembly-name">Football ground</h2><div id="assembly-map" class="assembly-map"></div><p class="micro muted">Illustrative layout, not live GPS or a verified evacuation route. Follow posted campus directions.</p></article>
  </section>
  <div class="student-action-bar"><span>⌁ Local network alerts work without an internet uplink.</span><button id="report-incident" class="text-link">⚑ Report an incident →</button></div>
  <section id="activity-heading" class="section-row"><h2>Alert activity</h2><span id="activity-count" class="tag">0 alerts</span></section>
  <div id="student-alerts" class="student-alerts"></div>
  <div class="local-note"><span>⌁</span><p><strong>Built for the local network.</strong><br>Internet is optional. Campus Wi-Fi and the local server keep you connected.</p></div>
  <footer class="student-footer"><span>404 FOUND / TECH ELEVATE 2026</span><span id="queue-note">All responses synced</span></footer>
</main>
<dialog id="onboarding" class="modal onboarding"><form id="join-form"><span class="modal-emblem">◈</span><span class="eyebrow">WELCOME TO CAMPUSGUARD</span><h2>Your campus.<br>Your safety network.</h2><p>Choose your current zone to get the alerts that matter to you.</p><label>Your name <span class="muted">(optional)</span><input id="student-name" maxlength="40" placeholder="e.g. Pradish" autocomplete="given-name"></label><label>Campus zone<select id="join-zone" required></select></label><button class="btn primary full" type="submit">Join the safety network <span>→</span></button><p id="join-error" class="form-error" role="alert"></p><p class="micro muted">No account needed. Works on your campus network.</p></form></dialog>
<dialog id="emergency" class="emergency-modal"><div class="emergency-top"><span id="emergency-severity" class="eyebrow">CRITICAL · CAMPUS ALERT</span><button id="minimize-alert" class="icon-button" aria-label="Minimize alert">×</button></div><span id="emergency-icon" class="emergency-icon">!</span><span id="emergency-zone" class="eyebrow"></span><h2 id="emergency-type"></h2><p id="emergency-message"></p><div id="emergency-time" class="micro"></div><button id="safe-button" class="btn safe-button">✓ I'm safe — acknowledge</button><button id="help-button" class="btn help-button">⊕ I need help</button><button id="silence" class="text-button">Silence sound</button><p class="micro">Follow instructions from campus safety personnel.</p></dialog>
<dialog id="help-dialog" class="modal"><form id="help-form"><span class="eyebrow">REQUEST ASSISTANCE</span><h2>Tell us what you need.</h2><p>Your request is saved for campus admin review. If offline, it will wait on this device until reconnection.</p><label>Details (optional)<textarea id="help-message" maxlength="400" rows="3" placeholder="Where are you and what help do you need?"></textarea></label><div class="review-actions"><button type="button" class="btn outline" id="cancel-help">Cancel</button><button class="btn broadcast" type="submit">Send help request</button></div></form></dialog>
<dialog id="contacts-dialog" class="modal"><h2>Emergency contacts</h2><div id="contacts-content"></div><p class="micro muted">These are the campus contacts configured by your administrator.</p><button id="close-contacts" class="btn outline full">Close</button></dialog>
<dialog id="report-dialog" class="modal"><form id="report-form"><span class="eyebrow">REPORT AN INCIDENT</span><h2>Let campus staff know.</h2><label>Category<select id="report-category"><option value="security">Security</option><option value="medical">Medical</option><option value="fire">Fire</option><option value="other">Other</option></select></label><label>What happened?<textarea id="report-message" required maxlength="800" rows="4"></textarea></label><p class="micro muted">This report is saved for admin review. It does not automatically broadcast an emergency alert.</p><div class="review-actions"><button type="button" id="cancel-report" class="btn outline">Cancel</button><button class="btn dark" type="submit">Submit report</button></div></form></dialog>
<div id="toast" class="toast" role="status"></div>
</body></html>
```

### campusguard/public/js/admin-incidents.js

<!-- CAMPUSGUARD_SOURCE {"path": "public/js/admin-incidents.js", "encoding": "utf-8", "sha256": "a89586d5b20b3296a1063d93cd1722071766af57c1d3a1cfc9251eab8000293f"} -->
```javascript
import {$,h,api,title,time,toast} from './ui.js';
import {zoneLabel,zoneName} from '/src/zones.js';
import {assemblyName} from '/shared/campus.js';
let chosen=null,incidents=[],refreshPage,detailId=null,loading=false,pendingClear=null;
export function selectIncident(id){chosen=id;}
export function initIncidentControls(refresh){
  refreshPage=refresh;
  $('#incident-select').addEventListener('change',event=>{chosen=event.target.value;renderCurrent();});
  $('#close-responses').addEventListener('click',()=>{$('#responses-dialog').close();detailId=null;});
  $('#response-filter').addEventListener('change',loadResponses);
  $('#cancel-all-clear').addEventListener('click',()=>$('#all-clear-dialog').close());
  $('#all-clear-form').addEventListener('submit',async event=>{event.preventDefault();const button=event.submitter;button.disabled=true;try{
    await api(`/api/incidents/${encodeURIComponent(pendingClear)}/all-clear`,{method:'POST',body:JSON.stringify({message:$('#all-clear-message').value.trim()})});
    $('#all-clear-dialog').close();toast('All-clear saved and broadcast.');await refreshPage();
  }catch(error){toast(error.message,true);}finally{button.disabled=false;}});
  $('#edit-campus-contacts').addEventListener('click',()=>$('#campus-contacts-dialog').showModal());
  $('#close-campus-contacts').addEventListener('click',()=>$('#campus-contacts-dialog').close());
  $('#campus-contacts-form').addEventListener('submit',async event=>{event.preventDefault();try{
    await api('/api/campus',{method:'PATCH',body:JSON.stringify({medicalPhone:$('#medical-phone').value,securityPhone:$('#security-phone').value})});
    $('#campus-contacts-dialog').close();toast('Campus contacts saved. Student pages receive them on refresh.');await refreshPage();
  }catch(error){toast(error.message,true);}});
}
export function renderOperations(data,reports,campus){
  incidents=data;
  if(!chosen||!incidents.some(item=>item.alert.id===chosen))chosen=incidents.find(item=>item.active)?.alert.id||incidents[0]?.alert.id;
  if(document.activeElement!==$('#incident-select')){
    $('#incident-select').replaceChildren(...incidents.slice(0,100).map(item=>h('option',{value:item.alert.id},`#${item.alert.seq} · ${title(item.alert.type)} · ${zoneLabel(item.alert)} · ${item.status}`)));
    $('#incident-select').value=chosen||'';
  }
  renderCurrent();
  if(!$('#campus-contacts-dialog').open){$('#medical-phone').value=campus.medicalPhone||'';$('#security-phone').value=campus.securityPhone||'';}
  $('#incident-reports').replaceChildren(...(reports.length?reports.slice(0,30).map(report=>h('article',{class:'report-row'},
    h('div',{},h('strong',{},`${title(report.category)} · ${zoneName(report.zone)}`),h('p',{},report.message),h('small',{},`${report.name} · ${time(report.timestamp)} · ${report.status}`)),
    report.status==='open'?h('button',{class:'btn small outline',onClick:async()=>{try{await api(`/api/reports/${report.id}`,{method:'PATCH',body:'{"status":"resolved"}'});await refreshPage();}catch(error){toast(error.message,true);}}},'Mark resolved'):h('span',{class:'tag'},'Resolved'))):[h('p',{class:'micro muted'},'No student reports yet. Reports appear here when a student submits one.')]));
  if($('#responses-dialog').open)loadResponses();
}
function metric(label,count,percent,detail,kind){return h('div',{class:`incident-metric ${kind}`},h('span',{},label),h('strong',{},count.toLocaleString()),h('small',{},detail),h('progress',{max:100,value:percent,'aria-label':`${label} percentage`}),h('b',{},`${percent}%`));}
function renderCurrent(){
  const item=incidents.find(row=>row.alert.id===chosen),root=$('#incident-card');
  root.classList.toggle('incident-compact',!item?.active);
  if(!item){root.replaceChildren(h('span',{role:'status'},'No active incidents. Ready to broadcast.'));return;}
  const alert=item.alert;
  if(!item.active){
    root.replaceChildren(h('div',{},h('strong',{},`${item.status==='cleared'?'✓ Cleared':'Expired'} · ${title(alert.type)} · ${zoneLabel(alert)}`),h('small',{},`#${alert.seq} · ${item.delivered} delivered · ${item.safe} marked safe${item.help?` · ${item.help} requesting help`:''}`)),
      h('button',{class:'btn small outline',onClick:()=>{detailId=alert.id;$('#responses-dialog').showModal();loadResponses();}},'View responses'));
    return;
  }
  root.replaceChildren(h('div',{class:'incident-status'},h('span',{},h('i',{class:`dot ${item.active?'red':'green'}`}),item.active?' Active incident':` ${title(item.status)} incident`),h('span',{class:'incident-severity'},['','Advisory','Urgent','Critical'][alert.severity])),
    h('h2',{},`${title(alert.type)} · ${zoneLabel(alert)}`),h('p',{class:'incident-message'},alert.message),h('p',{class:'incident-assembly'},`Assembly point · ${assemblyName(item.assemblyPoint)}`),
    h('div',{class:'incident-metrics'},metric('Delivered',item.delivered,item.deliveredPercent,`of ${item.audience} registered recipients`,'delivery'),metric('Marked safe',item.safe,item.safePercent,'of confirmed deliveries','safety')),
    h('p',{class:item.help?'help-count':'micro'},`${item.help} student${item.help===1?'':'s'} requesting help`),
    h('h3',{},'Event timeline'),h('ol',{class:'incident-timeline'},...item.events.map(event=>h('li',{},h('time',{},time(event.timestamp)),h('div',{},h('strong',{},({issued:'Alert issued',delivered:'Delivery confirmed',responses:'Responses arriving','help-requested':'Help requested','staff-notified':'Staff notification recorded','all-clear':'All-clear issued'}[event.kind]||title(event.kind))),h('p',{},event.description))))),
    h('div',{class:'incident-actions'},h('button',{class:'btn view-response',onClick:()=>{detailId=alert.id;$('#responses-dialog').showModal();loadResponses();}},'♙ View responses'),
      h('button',{class:'btn incident-clear',disabled:!item.active,title:item.active?'Send an all-clear for this incident':item.status==='cleared'?'This incident is already cleared':'This alert has expired; select an active incident above',onClick:()=>{pendingClear=alert.id;$('#all-clear-message').value=`${alert.message.includes('DEMO')?'DEMO ONLY: ':''}All-clear for ${zoneLabel(alert)}. Follow campus guidance.`;$('#all-clear-dialog').showModal();}},'⚑ Issue all-clear')),
    ...(!item.active?[h('p',{class:'incident-assembly',role:'status'},item.status==='cleared'?'Already cleared. Select an active incident above to issue another all-clear.':'This alert has expired. Select an active incident above to issue an all-clear.')]:[]),
    h('button',{class:'staff-notify',disabled:item.notified||!item.active,onClick:async()=>{try{await api(`/api/incidents/${encodeURIComponent(alert.id)}/notify`,{method:'POST',body:'{}'});await refreshPage();}catch(error){toast(error.message,true);}}},item.notified?'✓ Staff notification recorded':'Record that hostel staff were notified'),
    h('small',{class:'incident-footnote'},'The last button records your manual confirmation. It does not send a message or place a call.'));
}
async function loadResponses(){
  if(!detailId||loading)return;loading=true;try{
    const data=await api(`/api/incidents/${encodeURIComponent(detailId)}`),filter=$('#response-filter').value;
    const rows=data.responses.filter(row=>filter==='all'||row.response===filter);
    $('#responses-title').textContent=`Responses · ${zoneLabel(data.alert)}`;
    $('#response-rows').replaceChildren(...(rows.length?rows.map(row=>h('article',{class:`response-row ${row.response==='needs-help'?'requires-help':''}`},
      h('strong',{},row.name||'Student'),h('span',{},zoneName(row.zone)),h('span',{class:'tag'},title(row.response)),h('small',{},row.delivered?'Delivery confirmed':'No delivery receipt'),row.message?h('p',{},row.message):null,row.responseAt?h('small',{},time(row.responseAt)):null)):[h('p',{class:'muted'},'No matching responses.')]));
  }catch(error){toast(error.message,true);}finally{loading=false;}
}
```

### campusguard/public/js/admin.js

<!-- CAMPUSGUARD_SOURCE {"path": "public/js/admin.js", "encoding": "utf-8", "sha256": "3f471a65a747a9ff8f4b0606422838994bb2e6f00b46be63e4cea511ba6c4d88"} -->
```javascript
import {initIncidentControls,renderOperations,selectIncident} from './admin-incidents.js';
import {assemblyName} from '/shared/campus.js';
import {$,h,toast,api,title,time,ago,icon} from './ui.js';
import { TYPES,INSTRUCTIONS } from '/shared/protocol.js';
import { zones,zoneName,zoneLabel,normalizeZones } from '/src/zones.js';
let config,health,history=[],selectedType='fire',currentPage='overview',pendingAlert=null,busy=false,joinedUrl='',dashboardSocket=null;
function connectDashboard(){if(dashboardSocket&&dashboardSocket.readyState<2)return;const socket=new WebSocket(`${location.protocol==='https:'?'wss':'ws'}://${location.host}/ws`);dashboardSocket=socket;socket.onopen=()=>socket.send(JSON.stringify({kind:'hello',role:'dashboard'}));socket.onmessage=e=>{try{if(JSON.parse(e.data).kind==='ble-beacon'&&currentPage==='transport')refresh();}catch{}};socket.onerror=()=>{};socket.onclose=()=>{if(dashboardSocket===socket)dashboardSocket=null;};}
const pageInfo={overview:['Emergency operations','One alert. Every connected student. A faster response.'],history:['A record of every response','Broadcasts, confirmed receipts, and safety acknowledgements.'],transport:['Built to keep the message moving','Inspect each channel. Introduce a failure. Watch the fallback.'],connect:['Bring your campus online','Connect devices over local Wi-Fi. No internet required.']};
const labels={ws:'WebSocket',sse:'Server-sent events',polling:'HTTP polling','udp-broadcast':'UDP broadcast','udp-multicast':'UDP multicast',webrtc:'WebRTC peer relay',ble:'Bluetooth LE'};
const descriptions={ws:'Primary live channel to registered student browsers.',sse:'Automatic one-way stream when WebSocket is unavailable.',polling:'Checks for missed alerts every 2 seconds as the final fallback.','udp-broadcast':'Datagrams to the local subnet. Requires the Node receiver.','udp-multicast':'One multicast group per hostel, plus campus-wide 239.255.42.99.',webrtc:'Direct browser DataChannels. Server provides signaling only.',ble:'Real Windows Bluetooth advertisements. Remote reception needs a native BLE receiver.'};
function go(page){currentPage=page;for(const el of document.querySelectorAll('.page-section'))el.hidden=el.id!==`page-${page}`;for(const b of document.querySelectorAll('[data-page]'))b.classList.toggle('active',b.dataset.page===page);$('#breadcrumb-page').textContent=page==='transport'?'Transport lab':page==='connect'?'Connect devices':title(page);$('#page-title').textContent=pageInfo[page][0];$('#page-subtitle').textContent=pageInfo[page][1];if(page==='connect')renderJoin();if(page==='transport')renderTransports();}
document.querySelectorAll('[data-page]').forEach(b=>b.addEventListener('click',()=>go(b.dataset.page)));
document.querySelectorAll('[data-go]').forEach(b=>b.addEventListener('click',()=>go(b.dataset.go)));
function chooseType(t){selectedType=t;for(const b of $('#type-grid').children)b.classList.toggle('selected',b.dataset.type===t);if(!$('#alert-message').value||Object.values(INSTRUCTIONS).includes($('#alert-message').value))$('#alert-message').value=INSTRUCTIONS[t];if(t==='all-clear')$('#alert-severity').value='1';updateLength();}
function updateLength(){const n=new TextEncoder().encode($('#alert-message').value).length;$('#message-count').textContent=`${n} / 400 bytes`;$('#message-count').style.color=n>400?'#c33':'';}
$('#alert-message').addEventListener('input',updateLength);
function renderStats(){const c=health.counts,ready=health.transports.filter(t=>t.enabled&&!['degraded','unsupported','starting','disabled'].includes(t.status)).length;$('#stats').replaceChildren(...[
  ['Connected students',c.connected,'Seen on the network in the last 16s','users'],['Active alerts',c.active,`${c.alerts} broadcasts in local history`,'bolt'],['Safety responses',c.safe,'Unique responses across all alerts','check'],['Channels enabled',`${health.transports.filter(t=>t.enabled).length} / 7`,`${ready} ready or waiting for receivers`,'transport']
].map(([caption,value,foot,i])=>h('div',{class:'stat-card'},h('div',{class:'stat-caption'},caption,icon(i,'stat-symbol')),h('div',{class:'stat-number'},value),h('div',{class:'stat-foot'},foot))));$('#history-nav-count').textContent=c.alerts;}
function renderMap(){const status=health.zoneStatus;$('#map-zones').replaceChildren(...status.map(z=>h('div',{class:`map-zone${z.active?' has-alert':''}`,'data-zone':z.id},h('span',{class:'building'}),h('span',{class:'zone-label'},h('strong',{},z.short),h('small',{},h('span',{class:`dot ${z.active?'red':z.connected?'green':'amber'}`}),z.active?`${z.active} active alert${z.active>1?'s':''}`:`${z.connected} connected`)))));$('#zone-summary').replaceChildren(...status.map(z=>h('div',{class:'zone-item'},h('div',{},h('strong',{},z.name),h('small',{},`${z.connected} connected · ${health.hostelStats?.find(row=>row.id===z.id)?.acksReceived||0} safe responses`)),h('span',{class:`tag${z.active?' amber-tag':''}`},z.active?'ALERT ACTIVE':z.connected?'LISTENING':'AWAITING CLIENT'))));}
function empty(message='No broadcasts yet',detail='Your next alert and its responses will appear here.'){return h('div',{class:'empty-state'},h('strong',{},message),detail);}
function activityRow(a){return h('div',{class:'activity-row'},h('span',{class:`type-icon${a.type==='all-clear'?' clear':''}`},icon(a.type)),h('div',{},h('strong',{},title(a.type),' · ',zoneLabel(a)),h('small',{},`#${a.seq} · ${a.expired?'Expired':a.type==='all-clear'?'Clear notice':'Severity '+a.severity}`)),h('div',{class:'activity-metrics'},h('b',{},a.safe),' safe',h('br'),h('small',{},`${a.received} confirmed receipt${a.received===1?'':'s'}`)),h('span',{class:'activity-time'},ago(a.timestamp)));}
function renderHistory(){const recent=$('#recent-alerts');recent.replaceChildren(...(history.length?history.slice(0,4).map(activityRow):[empty()]));const table=$('#history-table');if(!history.length){table.replaceChildren(empty());return;}table.replaceChildren(h('table',{},h('thead',{},h('tr',{},...['ALERT','TARGET','INSTRUCTIONS','RECEIVED','SAFE','TIME'].map(s=>h('th',{},s)))),h('tbody',{},...history.map(a=>h('tr',{},h('td',{},h('strong',{},title(a.type)),h('br'),h('small',{class:'muted'},`#${a.seq} · Severity ${a.severity}`)),h('td',{},zoneLabel(a)),h('td',{class:'message-cell'},a.message),h('td',{},a.received),h('td',{},h('strong',{class:'response-sent'},a.safe)),h('td',{},time(a.timestamp),h('br'),h('small',{class:'muted'},new Date(a.timestamp).toLocaleDateString())))))));}
async function changeTransport(name,body){try{await api(`/api/transports/${name}`,{method:'POST',body:JSON.stringify(body)});await refresh();}catch(e){toast(e.message,true);}}
function renderTransports(){if(!health)return;const cards=health.transports.map(t=>{const toggle=h('button',{class:'toggle',role:'switch','aria-label':`Enable ${labels[t.name]}`,'aria-checked':String(t.enabled),onClick:()=>changeTransport(t.name,{enabled:!t.enabled})});const connected=t.connected!==undefined?` · ${t.connected} connected`:'';return h('article',{class:'transport-card'},h('div',{class:'transport-card-top'},h('h3',{},labels[t.name]),toggle),h('span',{class:`tag${['unsupported','degraded','unavailable'].includes(t.status)?' amber-tag':''}`},h('span',{class:`dot ${t.enabled&&!['degraded','unsupported','starting','unavailable','disabled'].includes(t.status)?'green':'amber'}`}),`${title(t.status)}${connected}`),h('p',{class:'transport-description'},descriptions[t.name]),h('div',{class:'transport-stats'},...[['Writes',t.sent],['Receipts',t.confirmed],['Errors',t.errors]].map(([k,v])=>h('span',{},h('strong',{},v),h('small',{},k)))),t.lastError?h('p',{class:'transport-error'},t.lastError):null);});
  // Keep focused controls stable while the periodic health refresh runs.
  if(!$('#transport-grid').contains(document.activeElement))$('#transport-grid').replaceChildren(...cards);
  const b=health.transports.find(t=>t.name==='ble');
  $('#ble-mode-tag').textContent=b.nativeStatus==='advertising'?'REAL BLE · ADVERTISING':`REAL BLE · ${String(b.nativeStatus||b.status).toUpperCase()}`;
  $('#ble-explanation').textContent=b.lastError||'Windows Bluetooth broadcasts compact alert summaries. These are actual local transmissions; phone reception and physical coverage are not inferred.';
  $('#ble-status').replaceChildren(...[
    ['Adapter support',b.realAvailable===null?'Checking…':b.realAvailable?'Available':'Unavailable'],
    ['Queued packets',b.queueDepth||0],
    ['Active target',b.active?zoneName(b.active.zone):'None']
  ].map(([label,value])=>h('div',{class:'coverage-toggle'},h('span',{},label),h('strong',{},value))));
  $('#beacon-log').replaceChildren(...(b.events.length?b.events.slice(0,15).map(e=>h('div',{class:'beacon-row'},h('span',{class:'dot green'}),h('div',{},h('strong',{},`LOCAL TRANSMIT · ${e.zoneName} · ${e.alertId}`),h('br'),h('small',{},e.reason),h('div',{class:'mono'},`${e.manufacturerHex||e.hex} · ${e.manufacturerBytes||24} manufacturer bytes`)),h('small',{},time(e.time)))):[empty('No radio transmissions yet','Send a DEMO ONLY alert. Successful Windows advertising starts appear here.')]));
}
function renderJoin(){if(!health)return;const urls=health.urls.flatMap(u=>[u.http,u.https].filter(Boolean));if(!urls.length)urls.push(location.origin);$('#join-links').replaceChildren(...urls.map(url=>h('div',{class:'join-url'},h('a',{href:url,target:'_blank'},url),h('button',{class:'btn small outline',onClick:()=>selectQr(url)},'Show QR'))));selectQr(joinedUrl&&urls.includes(joinedUrl)?joinedUrl:urls[0]);}
function selectQr(url){if(joinedUrl===url&&$('#qr-wrap img'))return;joinedUrl=url;$('#qr-wrap').replaceChildren(h('img',{src:`/api/qr?url=${encodeURIComponent(url)}`,alt:'QR code to open the student interface'}));$('#qr-url').textContent=url;}
async function refresh(){if(busy||!config)return;busy=true;try{const [data,hist,zoneStats,incidents,reports,campus]=await Promise.all([api('/api/health'),api('/api/history'),api('/api/stats/zones'),api('/api/incidents'),api('/api/reports'),api('/api/campus')]);health={...data,hostelStats:zoneStats.zones};history=hist.alerts;$('#server-status').replaceChildren(h('span',{class:'dot green'}),' Local server online');$('#server-id').textContent=`SERVER ${data.serverId.toUpperCase()} · UP ${Math.floor(data.uptime/60)} MIN`;renderStats();renderMap();renderHistory();renderOperations(incidents.incidents,reports.reports,campus);if(currentPage==='transport')renderTransports();if(currentPage==='connect'&&!$('#join-links').children.length)renderJoin();if($('#login-modal').open)$('#login-modal').close();connectDashboard();}
  catch(e){if(e.status===401){dashboardSocket?.close();if(!$('#login-modal').open)$('#login-modal').showModal();}else $('#server-status').replaceChildren(h('span',{class:'dot red'}),' Server unreachable');}finally{busy=false;}}
$('#login-modal').addEventListener('cancel',e=>e.preventDefault());
$('#login-form').addEventListener('submit',async e=>{e.preventDefault();const button=$('#login-form button');button.disabled=true;try{await api('/api/login',{method:'POST',body:JSON.stringify({pin:$('#admin-pin').value})});$('#admin-pin').value='';$('#login-error').textContent='';await refresh();}catch(error){$('#login-error').textContent=error.message;}finally{button.disabled=false;}});
$('#logout').addEventListener('click',async()=>{try{await api('/api/logout',{method:'POST',body:'{}'});dashboardSocket?.close();$('#login-modal').showModal();}catch(e){toast(e.message,true);}});
$('#alert-form').addEventListener('submit',e=>{e.preventDefault();const message=$('#alert-message').value.trim();if(!message||new TextEncoder().encode(message).length>400)return toast('Enter instructions within 400 UTF-8 bytes.',true);pendingAlert={type:selectedType,severity:Number($('#alert-severity').value),zones:selectedHostels(),assemblyPoint:$('#alert-assembly').value,ttl:Number($('#alert-ttl').value),message};$('#review-content').replaceChildren(h('div',{class:'review-tags'},h('span',{class:'tag'},title(pendingAlert.type)),h('span',{class:'tag'},zoneLabel(pendingAlert)),h('span',{class:'tag'},assemblyName(pendingAlert.assemblyPoint)),h('span',{class:'tag'},`Severity ${pendingAlert.severity}`)),h('div',{class:'review-message'},message),h('p',{class:'micro'},`Expires after ${pendingAlert.ttl/60} minute${pendingAlert.ttl===60?'':'s'}. All-clear notices clear earlier alerts for the selected zone.`));$('#review-modal').showModal();});
$('#cancel-send').addEventListener('click',()=>$('#review-modal').close());
$('#confirm-send').addEventListener('click',async()=>{if(!pendingAlert)return;const b=$('#confirm-send');b.disabled=true;try{const data=await api('/api/alerts',{method:'POST',body:JSON.stringify(pendingAlert)});if(data.alert.type!=='all-clear')selectIncident(data.alert.id);$('#review-modal').close();pendingAlert=null;toast(`Alert #${data.alert.seq} saved and dispatched.`);await refresh();}catch(e){toast(e.message,true);}finally{b.disabled=false;}});

$('#restore-transports').addEventListener('click',async()=>{for(const name of ['ws','sse','polling','webrtc'])await api(`/api/transports/${name}`,{method:'POST',body:JSON.stringify({enabled:true})});toast('Browser connections restored. Clients reconnect automatically.');refresh();});
setInterval(()=>$('#admin-clock').textContent=new Date().toLocaleTimeString([],{hour:'2-digit',minute:'2-digit'}),1000);
function selectedHostels(){return normalizeZones([...document.querySelectorAll('#alert-zones input:checked')].map(input=>Number(input.value)));}
function renderHostelPicker(){
  $('#alert-zones').replaceChildren(...zones.map(zone=>h('label',{class:'hostel-choice'},h('input',{type:'checkbox',value:zone.id,checked:zone.id===0,onChange:event=>{
    const input=event.target,inputs=[...document.querySelectorAll('#alert-zones input')];
    if(input.checked)for(const other of inputs)if(other!==input&&(Number(input.value)===0||Number(other.value)===0))other.checked=false;
    if(!inputs.some(other=>other.checked))inputs.find(other=>Number(other.value)===0).checked=true;
  }}),h('span',{},zone.id===0?'ALL · Entire campus':zone.name))));
}
async function init(){try{config=await api('/api/config');config.zones=zones;renderHostelPicker();$('#type-grid').replaceChildren(...TYPES.map(t=>h('button',{type:'button',class:'type-button','data-type':t,onClick:()=>chooseType(t)},icon(t),title(t))));chooseType('fire');await refresh();setInterval(refresh,1800);}catch(e){toast('Cannot reach the local server. Keep its window open and reload.',true);}}
initIncidentControls(refresh);
init();
```

### campusguard/public/js/alarm.js

<!-- CAMPUSGUARD_SOURCE {"path": "public/js/alarm.js", "encoding": "utf-8", "sha256": "a45dac747f4d313d2c46d6049a5188cecc1ce1819076cab5e9be280c85696dea"} -->
```javascript
let context,oscillator,gain,timer;
export async function unlock(){context??=new(window.AudioContext||window.webkitAudioContext)();await context.resume();const o=context.createOscillator(),g=context.createGain();g.gain.value=0;o.connect(g).connect(context.destination);o.start();o.stop(context.currentTime+0.05);return context.state==='running';}
export function stop(){clearInterval(timer);try{oscillator?.stop();}catch{}oscillator=null;gain=null;navigator.vibrate?.(0);}
export function start(severity=3){stop();navigator.vibrate?.([400,200,400,200,700]);if(!context||context.state!=='running')return false;oscillator=context.createOscillator();gain=context.createGain();oscillator.type='sine';gain.gain.value=severity===3?0.35:0.22;oscillator.connect(gain).connect(context.destination);oscillator.start();let high=false;const sweep=()=>{if(oscillator){high=!high;oscillator.frequency.exponentialRampToValueAtTime(high?1000:480,context.currentTime+0.4);}};sweep();timer=setInterval(sweep,450);return true;}
export async function test(){await unlock();start(1);setTimeout(stop,1200);}
```

### campusguard/public/js/client.js

<!-- CAMPUSGUARD_SOURCE {"path": "public/js/client.js", "encoding": "utf-8", "sha256": "494576de872421bdf1bdb6ab27fd83bdb66d521f3724a6efa890ccedaf8e57c0"} -->
```javascript
import {$,h,toast,api,title,time} from './ui.js';
import {validAlert,applicable,cleared,expiresAt,signingPayload,alertZones} from '/shared/protocol.js';
import { zones, hostels, zoneName, zoneLabel } from '/src/zones.js';
import {Connection} from './connection.js';
import {PeerRelay} from './peer-relay.js';
import * as alarm from './alarm.js';
import { ASSEMBLY_POINTS,assemblyName } from '/shared/campus.js';

let identity=JSON.parse(localStorage.getItem('cg.identity')||'null'),config,connection,peer,verificationKey,db,offset=0,currentAlert=null,soundEnabled=false,flushing=false,renderTimer,peerOnly=false;
let state={alerts:[],seen:[],safe:{},pending:[],markers:{},responseStatus:{},serverId:null};
const inflight=new Map();
const now=()=>Date.now()+offset;
function openDB(){return new Promise((resolve,reject)=>{const req=indexedDB.open('campusguard',1);req.onupgradeneeded=()=>req.result.createObjectStore('cache');req.onsuccess=()=>resolve(req.result);req.onerror=()=>reject(req.error);});}
async function readCache(){try{db=await openDB();const saved=await new Promise((resolve,reject)=>{const r=db.transaction('cache').objectStore('cache').get('state');r.onsuccess=()=>resolve(r.result);r.onerror=()=>reject(r.error);});if(saved)state={...state,...saved};}catch{try{state={...state,...JSON.parse(localStorage.getItem('cg.cache')||'{}')};}catch{}}}
function save(){state.alerts=state.alerts.slice(-300);state.seen=state.seen.slice(-2000);if(db){try{const t=db.transaction('cache','readwrite');t.objectStore('cache').put(structuredClone(state),'state');}catch{}}else{try{localStorage.setItem('cg.cache',JSON.stringify(state));}catch{}}}
async function verify(a,via){if(!validAlert(a)||a.serverId!==config.serverId)return false;
  if(!globalThis.crypto?.subtle)return via!=='webrtc';
  if(typeof a.signature!=='string'||!verificationKey)return false;
  try{const raw=atob(a.signature.replace(/-/g,'+').replace(/_/g,'/'));return await crypto.subtle.verify({name:'ECDSA',hash:'SHA-256'},verificationKey,Uint8Array.from(raw,c=>c.charCodeAt(0)),new TextEncoder().encode(signingPayload(a)));}catch{return false;}
}
function isCurrent(a){return a.type!=='all-clear'&&expiresAt(a)>now()&&applicable(a,identity?.zone)&&!cleared(a,state.markers,identity?.zone);}
function queue(kind,a,via){const key=`${kind}:${a.id}`;if(!state.pending.some(p=>p.key===key))state.pending.push({key,kind,alertId:a.id,via});save();updateQueue();flush();}
const responseKind=p=>p.kind==='ack'||p.kind==='response';
async function flush(){if(flushing||!connection)return;flushing=true;try{for(const p of [...state.pending]){try{
  const route=p.kind==='report'?'/api/reports':responseKind(p)?'/api/responses':'/api/receipts';
  const body=p.kind==='report'?p.body:{alertId:p.alertId,status:p.status||'safe',message:p.message||'',via:p.via};
  await connection.request(route,body);state.pending=state.pending.filter(item=>item.key!==p.key);
  if(responseKind(p)&&!state.pending.some(item=>responseKind(item)&&item.alertId===p.alertId))state.safe[p.alertId]='sent';save();
}catch{break;}}}finally{flushing=false;updateQueue();render();}}
function updateQueue(){const n=state.pending.filter(p=>responseKind(p)||p.kind==='report').length;$('#queue-note').textContent=n?`${n} response/report${n===1?'':'s'} waiting to sync`:'All responses synced';}
async function ingest(a,via){if(inflight.has(a?.id))return inflight.get(a.id);const work=ingestOnce(a,via);if(a?.id)inflight.set(a.id,work);try{return await work;}finally{inflight.delete(a?.id);}}
async function ingestOnce(a,via){
  if(!await verify(a,via)||Date.parse(a.timestamp)>now()+60000||(a.type!=='all-clear'&&expiresAt(a)<=now()))return false;
  if(state.seen.includes(a.id))return false;
  state.seen.push(a.id);const stored={...a,receivedVia:via};state.alerts.push(stored);
  if(a.type==='all-clear'){for(const zone of alertZones(a))state.markers[zone]=Math.max(state.markers[zone]||0,a.seq);if(applicable(a,identity.zone)){queue('receipt',a,via);toast(`All clear · ${zoneLabel(a)}`);}}
  else if(expiresAt(a)>now()&&applicable(a,identity.zone)){queue('receipt',a,via);if(!cleared(a,state.markers,identity.zone)&&!state.safe[a.id])showEmergency(stored);}
  save();render();if(via!=='webrtc')peer?.gossip(a);return true;
}
async function onSync(data,via){
  offset=Date.parse(data.serverTime)-Date.now();
  if(data.campus){config.campus=data.campus;renderContacts();localStorage.setItem('cg.config',JSON.stringify(config));}
  state.responseStatus??={};for(const [id,response]of Object.entries(data.responses||{}))if(!state.pending.some(p=>responseKind(p)&&p.alertId===id)){state.responseStatus[id]=response.status;state.safe[id]='sent';}
  if(state.serverId&&state.serverId!==data.serverId){state.alerts=[];state.seen=[];state.safe={};state.responseStatus={};state.pending=[];state.markers={};currentAlert=null;alarm.stop();}
  state.serverId=data.serverId;
  for(const [zone,seq]of Object.entries(data.clearMarkers||{}))state.markers[zone]=Math.max(state.markers[zone]||0,seq);
  for(const a of data.alerts.sort((a,b)=>a.seq-b.seq))await ingest(a,via);
  save();render();flush();
}
function showEmergency(a){if(currentAlert&&isCurrent(currentAlert)&&currentAlert.severity>a.severity)return;currentAlert=a;
  $('#emergency').className=`emergency-modal ${a.severity===2?'urgent':a.severity===1?'advisory':''}`;
  $('#emergency-type').textContent=title(a.type)+' alert';$('#emergency-severity').textContent=`${['','ADVISORY','URGENT','CRITICAL'][a.severity]} · CAMPUS ALERT`;
  $('#emergency-zone').textContent=zoneLabel(a).toUpperCase();$('#emergency-message').textContent=a.message;$('#emergency-time').textContent=`${time(a.timestamp)} · #${a.seq} · via ${a.receivedVia||'local network'}`;
  $('#safe-button').disabled=false;$('#safe-button').textContent="✓ I'm safe — acknowledge";
  $('#silence').textContent='Silence sound';$('#help-button').disabled=false;
  if(!$('#emergency').open)$('#emergency').showModal();if(soundEnabled)alarm.start(a.severity);
}
function minimize(){alarm.stop();currentAlert=null;if($('#emergency').open)$('#emergency').close();}
function reenroll(){if(!identity)return;connection?.stop();peer?.stop();identity=null;localStorage.removeItem('cg.identity');minimize();$('#join-error').textContent='This server needs a new registration. Choose your zone to rejoin.';if(!$('#onboarding').open)$('#onboarding').showModal();}
function respond(a,status,message=''){
  if(!a||!applicable(a,identity.zone))return false;if(new TextEncoder().encode(message).length>400){toast('Keep the help message within 400 UTF-8 bytes.',true);return false;}state.responseStatus??={};state.responseStatus[a.id]=status;state.safe[a.id]='pending';
  state.pending=state.pending.filter(p=>!responseKind(p)||p.alertId!==a.id);
  state.pending.push({key:`response:${a.id}:${Date.now()}`,kind:'response',alertId:a.id,status,message,via:a.receivedVia||'polling'});
  save();minimize();render();flush();toast(status==='safe'?'Safety response saved.':'Help request saved. It will sync when connected.');return true;
}
function acknowledge(a){respond(a,'safe');}
let helpAlert=null;
function requestHelp(a){helpAlert=a;$('#help-message').value='';$('#help-dialog').showModal();}
function render(){if(!identity||!config)return;const active=state.alerts.filter(isCurrent);$('#safe-state').hidden=active.length>0;
  if(currentAlert&&!isCurrent(currentAlert))minimize();renderSafety(active);
  const relevant=state.alerts.filter(a=>applicable(a,identity.zone)).slice().sort((a,b)=>b.seq-a.seq).slice(0,30);$('#activity-count').textContent=`${relevant.length} alert${relevant.length===1?'':'s'}`;
  $('#student-alerts').replaceChildren(...(relevant.length?relevant.map(a=>{
    const live=isCurrent(a),response=state.safe[a.id];const detail=a.type==='all-clear'?'All clear':live?`Severity ${a.severity} · Active`:cleared(a,state.markers,identity.zone)?'Cleared':'Expired';
    const action=response?h('span',{class:'response-sent'},response==='sent'?(state.responseStatus?.[a.id]==='needs-help'?'⊕ Help requested':'✓ Safety response sent'):'✓ Saved · waiting to sync'):live?h('button',{class:'btn primary',onClick:()=>acknowledge(a)},"✓ I'm safe"):null;
    return h('article',{class:`student-alert ${live?(a.severity===3?'alert-critical':'alert-urgent'):''}`,'data-alert-id':a.id},h('div',{class:'student-alert-top'},h('div',{},h('span',{class:`type-icon${a.type==='all-clear'?' clear':''}`},a.type==='all-clear'?'✓':'!'),h('h3',{},title(a.type))),h('span',{class:'tag'},detail)),h('p',{},a.message),h('div',{class:'student-alert-meta'},h('span',{},`${zoneLabel(a)} · ${time(a.timestamp)} · #${a.seq}`),action));
  }):[h('div',{class:'empty-state'},'Your alert history will appear here.')]));updateQueue();
}
function connectionState(mode,note){const names={ws:'Connected to campus',sse:'Connected with fallback',polling:'Connected with fallback',offline:'Reconnecting to campus',connecting:'Connecting to campus'};$('#connection-title').textContent=names[mode]||'Connecting';$('#connection-detail').textContent=note;$('#connection-mode').textContent=mode==='ws'?'WEBSOCKET':mode.toUpperCase();$('#connection-dot').className=`dot ${['ws','sse','polling'].includes(mode)?'green':'amber'}`;$('#live-readiness').textContent=['ws','sse','polling'].includes(mode)?'✓ Live alerts connected on this device.':'○ Reconnecting to the campus network.';$('#welcome-status').textContent=['ws','sse','polling'].includes(mode)?'You’re connected. We’ve got you.':'We’re reconnecting. Saved instructions remain available.';if(mode!=='offline')flush();}
async function begin(){
  $('#greeting').replaceChildren(document.createTextNode(identity.name&&identity.name!=='Student'?`Hi, ${identity.name}.`:'Stay connected.'),h('br'),document.createTextNode('Your safety comes first.'));
  $('#student-zone').value=identity.zone;state.serverId??=config.serverId;
  connection?.stop();peer?.stop();
  connection=new Connection(identity,{onAlert:ingest,onSync,onState:connectionState,onUnauthorized:reenroll,onMessage:m=>{
    if(m.kind==='peers'){if(m.enabled===false)peer?.reset();else peer?.roster(m.peers).catch(()=>{});}if(m.kind==='signal')peer?.signal(m).catch(()=>{});
    if(m.serverTime)offset=Date.parse(m.serverTime)-Date.now();
  }});
  peer=new PeerRelay({id:identity.id,sendSignal:m=>connection.send(m),onAlert:ingest,getAlerts:()=>state.alerts.filter(a=>expiresAt(a)>now()||(a.type==='all-clear'&&alertZones(a).some(zone=>a.seq===(state.markers[zone]||0))))});
  try{const mine=await connection.request('/api/responses/me',null,'GET');state.responseStatus??={};for(const [id,response]of Object.entries(mine.responses)){if(!state.pending.some(p=>responseKind(p)&&p.alertId===id)){state.responseStatus[id]=response.status;state.safe[id]='sent';}}}catch{}
  peerOnly=false;connection.start();$('#peer-only').hidden=!globalThis.crypto?.subtle;clearInterval(renderTimer);renderTimer=setInterval(()=>{render();flush();const p=peer.health();$('#peer-info').textContent=globalThis.crypto?.subtle?`${p.connections} peer link${p.connections===1?'':'s'} · ${p.forwards} relayed messages`:'Peer relay requires trusted HTTPS or localhost.';connection.send({kind:'peer-health',...p});},5000);render();
}
$('#onboarding').addEventListener('cancel',e=>e.preventDefault());
$('#join-form').addEventListener('submit',async e=>{e.preventDefault();const b=$('#join-form button');b.disabled=true;try{const c=await api('/api/clients',{method:'POST',body:JSON.stringify({zone:Number($('#join-zone').value),name:$('#student-name').value.trim()})});identity=c;localStorage.setItem('cg.identity',JSON.stringify(c));$('#onboarding').close();await begin();}catch(error){$('#join-error').textContent=error.message;}finally{b.disabled=false;}});
$('#student-zone').addEventListener('change',async e=>{if(!identity)return;try{await flush();if(state.pending.some(responseKind))throw new Error('Wait for your safety response to sync before changing zone.');await connection.request('/api/clients/me',{zone:Number(e.target.value)},'PATCH');identity.zone=Number(e.target.value);localStorage.setItem('cg.identity',JSON.stringify(identity));minimize();connection.cursor=0;connection.stop();await begin();toast(`Now listening for ${zoneName(identity.zone)}.`);}catch(error){e.target.value=identity.zone;toast(error.message,true);}});
$('#enable-sound').addEventListener('click',async()=>{try{await alarm.test();soundEnabled=true;$('#sound-readiness').textContent='✓ Alert sound ready for this page session.';$('#enable-sound').textContent='✓ Alerts enabled · test again';toast('Sound enabled. Keep this page open to receive live alerts.');}catch{toast('Sound is unavailable in this browser. Visual alerts remain enabled.',true);}});
$('#safe-button').addEventListener('click',()=>acknowledge(currentAlert));$('#minimize-alert').addEventListener('click',minimize);$('#emergency').addEventListener('cancel',e=>{e.preventDefault();minimize();});$('#silence').addEventListener('click',()=>{alarm.stop();$('#silence').textContent='Sound silenced';});
$('#peer-only').addEventListener('click',()=>{if(!peerOnly){if(!peer?.health().connections)return toast('Connect a second student client and wait for a peer link first.',true);peerOnly=true;connection.stop();$('#peer-only').textContent='Restore automatic server connections';$('#connection-title').textContent='Peer-only reception demo';$('#connection-mode').textContent='WEBRTC';$('#connection-detail').textContent='Direct alert channels paused on this client. Existing peer links remain active.';}else{peerOnly=false;connection.mode=null;connection.start();$('#peer-only').textContent='Test peer-only alert reception';}});
setInterval(()=>$('#clock').textContent=new Date().toLocaleTimeString([],{hour:'2-digit',minute:'2-digit'}),1000);
window.addEventListener('online',()=>{if(connection?.running){connection.tryWs();connection.sync().catch(()=>{});}});
document.addEventListener('visibilitychange',()=>{if(document.visibilityState==='visible'&&connection?.running){connection.tryWs();connection.sync().catch(()=>{});}});
async function init(){await readCache();try{config=await api('/api/config',{signal:AbortSignal.timeout(5000)});localStorage.setItem('cg.config',JSON.stringify(config));}catch{config=JSON.parse(localStorage.getItem('cg.config')||'null');}
  if(config)config.zones=zones;
  if(!config){$('#connection-title').textContent='Server unreachable';$('#connection-detail').textContent='Join the same Wi-Fi as the laptop, keep its server running, and reload this page.';return;}
  if(state.serverId&&state.serverId!==config.serverId){identity=null;localStorage.removeItem('cg.identity');state={alerts:[],seen:[],safe:{},pending:[],markers:{},responseStatus:{},serverId:config.serverId};save();}
  if(globalThis.crypto?.subtle){try{verificationKey=await crypto.subtle.importKey('jwk',config.publicKey,{name:'ECDSA',namedCurve:'P-256'},false,['verify']);}catch{}}
  for(const id of ['#student-zone','#join-zone'])$(id).replaceChildren(...hostels.map(z=>h('option',{value:z.id},z.name)));
  $('#capability-note').textContent=`${isSecureContext?'Secure context · offline app caching available':'HTTP demo mode · installable PWA and peer relay need trusted HTTPS'}. ${'vibrate' in navigator?'Vibration requested when supported.':'Vibration unavailable on this browser.'} Keep the page open.`;
  if('serviceWorker'in navigator&&isSecureContext)navigator.serviceWorker.register('/sw.js').catch(()=>{});
  renderContacts();if(identity)await begin();else $('#onboarding').showModal();
}
function contactCard(label,number){return h('div',{class:'contact-row'},h('div',{},h('strong',{},label),h('small',{},number||'Not configured')),number?h('a',{href:`tel:${number.replace(/[^+0-9]/g,'')}`,class:'btn small outline'},'Call'):null);}
function renderContacts(){const campus=config?.campus||{};for(const selector of ['#support-contacts','#contacts-content'])$(selector).replaceChildren(contactCard('Campus security',campus.securityPhone),contactCard('Medical support',campus.medicalPhone));}
function renderSafety(active){
  const alert=[...active].sort((a,b)=>b.severity-a.severity||b.seq-a.seq)[0];
  const assembly=alert?.assemblyPoint||'football';$('#assembly-name').textContent=assemblyName(assembly);
  const positions=[[35,43],[135,33],[185,83],[45,110]],point=positions[Math.max(0,hostels.findIndex(z=>z.id===identity.zone))],ground=assembly==='cricket'?[170,125]:[117,113];
  $('#assembly-map').innerHTML=`<svg viewBox="0 0 240 160" role="img" aria-label="Illustrative hostel and assembly layout"><rect width="240" height="160" rx="12" fill="#e8eee4"/><path d="M0 65H240M95 0V160M0 143H240" stroke="#fafcf8" stroke-width="13"/><rect x="15" y="15" width="57" height="32" rx="4" fill="#cad5c4"/><rect x="115" y="8" width="70" height="39" rx="4" fill="#d2dbcd"/><rect x="172" y="65" width="52" height="33" rx="4" fill="#c6d3c0"/><rect x="15" y="92" width="55" height="36" rx="4" fill="#d3ddcd"/><ellipse cx="130" cy="112" rx="30" ry="24" fill="#b9d9a6"/><ellipse cx="190" cy="126" rx="26" ry="20" fill="#b3d3bc"/><path d="M${point[0]} ${point[1]}L95 ${point[1]}L95 ${ground[1]}L${ground[0]} ${ground[1]}" fill="none" stroke="#418c65" stroke-width="2" stroke-dasharray="4 3"/><circle cx="${point[0]}" cy="${point[1]}" r="6" fill="#d84f48"/><circle cx="${ground[0]}" cy="${ground[1]}" r="8" fill="#24724d"/><text x="9" y="155" font-size="8" fill="#526b5b">Illustrative layout · not GPS</text></svg>`;
  const panel=$('#student-active');panel.hidden=!alert;if(!alert)return;
  const status=state.responseStatus?.[alert.id]||(state.safe[alert.id]?'safe':null);
  panel.replaceChildren(h('div',{class:'active-alert-top'},h('span',{class:'eyebrow'},`ACTIVE ALERT · ${['','ADVISORY','URGENT','CRITICAL'][alert.severity]}`),h('span',{class:'tag'},zoneLabel(alert))),
    h('h2',{},`${title(alert.type)} · ${zoneLabel(alert)}`),h('p',{},alert.message),
    h('ol',{class:'evacuation-steps'},h('li',{},'Follow the instructions above.'),h('li',{},`Designated assembly point: ${assemblyName(assembly)}. Go there when instructed.`),h('li',{},'Follow campus staff guidance until an all-clear is issued.')),
    h('div',{class:'safety-actions'},h('button',{class:'btn dark',onClick:()=>acknowledge(alert)},status==='safe'?'✓ Marked safe — update':'✓ I’m safe'),h('button',{class:'btn request-help',onClick:()=>requestHelp(alert)},status==='needs-help'?'⊕ Help requested — update':'⊕ I need help')),
    h('small',{},state.safe[alert.id]==='pending'?'Saved on this device · waiting to sync':status==='needs-help'?'Help request saved for campus admin review.':status==='safe'?'Your safety response is recorded.':'Choose a response so staff can check on you.'));
}
$('#help-button').addEventListener('click',()=>requestHelp(currentAlert));
$('#cancel-help').addEventListener('click',()=>$('#help-dialog').close());
$('#help-form').addEventListener('submit',event=>{event.preventDefault();if(respond(helpAlert,'needs-help',$('#help-message').value.trim()))$('#help-dialog').close();});
$('#view-contacts').addEventListener('click',()=>$('#contacts-dialog').showModal());$('#close-contacts').addEventListener('click',()=>$('#contacts-dialog').close());
$('#report-incident').addEventListener('click',()=>$('#report-dialog').showModal());$('#cancel-report').addEventListener('click',()=>$('#report-dialog').close());
$('#report-form').addEventListener('submit',event=>{event.preventDefault();const message=$('#report-message').value.trim();if(!message)return;if(new TextEncoder().encode(message).length>800){toast('Keep the report within 800 bytes.');return;}const requestId=globalThis.crypto?.randomUUID?.()||`report-${Date.now()}-${Math.random().toString(16).slice(2)}`;state.pending.push({key:`report:${requestId}`,kind:'report',body:{requestId,message,category:$('#report-category').value}});save();$('#report-dialog').close();$('#report-message').value='';updateQueue();flush();toast('Report saved. It will reach admin when connected.');});

init();
```

### campusguard/public/js/connection.js

<!-- CAMPUSGUARD_SOURCE {"path": "public/js/connection.js", "encoding": "utf-8", "sha256": "edacbd88867693b681027b09b8a5c5850844fb5aad73b40edfcb233587b664d0"} -->
```javascript
import { api } from './ui.js';
export class Connection {
  constructor(identity,{onAlert,onSync,onState,onMessage,onUnauthorized}){Object.assign(this,{identity,onAlert,onSync,onState,onMessage,onUnauthorized});this.mode=null;this.running=false;this.ws=null;this.candidate=null;this.cursor=0;this.serverId='';this.syncing=null;this.sseAbort=null;this.poll=null;this.lastMessage=Date.now();}
  headers(){return {Authorization:`Bearer ${this.identity.id}.${this.identity.token}`};}
  async request(path,body,method='POST'){return api(path,{method,headers:this.headers(),...(body?{body:JSON.stringify(body)}:{})});}
  async sync(via=this.mode||'polling'){
    if(this.syncing)return this.syncing;
    this.syncing=(async()=>{const data=await api(`/api/sync?after=${this.cursor}&serverId=${encodeURIComponent(this.serverId)}&transport=${via}&zone=${this.identity.zone}`,{headers:this.headers(),signal:AbortSignal.timeout(5000)});await this.onSync(data,via);this.cursor=data.cursor;this.serverId=data.serverId;return data;})();
    try{return await this.syncing;}catch(e){if(e.status===401)this.onUnauthorized?.();throw e;}finally{this.syncing=null;}
  }
  setState(mode,note){this.mode=mode;this.onState(mode,note);}
  start(){this.running=true;this.onState('connecting','Connecting to the campus server…');this.tryWs();this.probe=setInterval(()=>{if(this.mode!=='ws')this.tryWs();if(['polling','offline'].includes(this.mode)&&!this.sseAbort)this.startSse();if(this.mode==='ws'&&Date.now()-this.lastMessage>15000)this.ws?.close();},5000);}
  tryWs(){
    if(!this.running||this.candidate||this.ws?.readyState===1)return;
    const ws=new WebSocket(`${location.protocol==='https:'?'wss':'ws'}://${location.host}/ws`);this.candidate=ws;
    const timeout=setTimeout(()=>ws.close(),3000);let ready=false;
    ws.onopen=()=>ws.send(JSON.stringify({kind:'hello',clientId:this.identity.id,token:this.identity.token,zone:this.identity.zone,lastSeq:this.cursor,serverId:this.serverId}));
    ws.onmessage=async event=>{this.lastMessage=Date.now();let m;try{m=JSON.parse(event.data);}catch{return;}
      if(m.kind==='ready'){ready=true;clearTimeout(timeout);this.candidate=null;this.sseAbort?.abort();clearInterval(this.poll);this.poll=null;this.ws=ws;this.setState('ws','Live connection');this.sync('ws').catch(()=>{});}
      if(m.kind==='alert'){try{await this.onAlert(m.alert,'ws');}catch{}this.scheduleSync();}
      this.onMessage?.(m);
    };
    ws.onerror=()=>{};
    ws.onclose=event=>{clearTimeout(timeout);if(this.candidate===ws)this.candidate=null;if(this.ws===ws)this.ws=null;if(!this.running)return;if(event.code===1008){this.onUnauthorized?.();return;}if(ready&&this.mode==='ws'){this.setState('connecting','WebSocket lost. Trying SSE…');this.startSse();}else if(!this.mode)this.startSse();};
  }
  scheduleSync(){clearTimeout(this.syncTimer);this.syncTimer=setTimeout(()=>this.sync().catch(()=>{}),150);}
  async startSse(){
    if(!this.running||this.sseAbort||this.mode==='ws')return;
    const controller=new AbortController();this.sseAbort=controller;let watchdog;
    const arm=()=>{clearTimeout(watchdog);watchdog=setTimeout(()=>controller.abort(),15000);};
    const timeout=setTimeout(()=>controller.abort(),3000);
    try{
      const res=await fetch(`/api/events?zone=${this.identity.zone}&since=${this.cursor}&serverId=${encodeURIComponent(this.serverId)}`,{headers:this.headers(),signal:controller.signal});if(res.status===401)this.onUnauthorized?.();if(!res.ok)throw new Error('SSE unavailable');clearTimeout(timeout);
      if(this.mode==='ws'){controller.abort();return;}
      clearInterval(this.poll);this.poll=null;this.setState('sse','WebSocket unavailable · SSE fallback active');this.sync('sse').catch(()=>{});arm();
      const reader=res.body.getReader(),decoder=new TextDecoder();let buffer='';
      while(this.running){const {done,value}=await reader.read();if(done)break;arm();buffer+=decoder.decode(value,{stream:true});let pos;while((pos=buffer.indexOf('\n\n'))>=0){const block=buffer.slice(0,pos);buffer=buffer.slice(pos+2);const event=block.split('\n').find(x=>x.startsWith('event:'))?.slice(6).trim();if(event==='alert'){const line=block.split('\n').find(x=>x.startsWith('data:'))?.slice(5).trim();try{await this.onAlert(JSON.parse(line).alert,'sse');this.scheduleSync();}catch{}}}}
    }catch{}finally{clearTimeout(timeout);clearTimeout(watchdog);if(this.sseAbort===controller)this.sseAbort=null;if(this.running&&this.mode!=='ws')this.startPolling();}
  }
  startPolling(){
    if(this.poll||!this.running||this.mode==='ws')return;
    const tick=async()=>{try{await this.sync('polling');if(this.mode!=='ws'&&this.mode!=='sse')this.setState('polling','WebSocket and SSE unavailable · polling every 2 seconds');}catch(e){if(this.mode!=='ws'&&this.mode!=='sse')this.setState('offline','Server unreachable · reconnecting automatically');}};
    this.setState('polling','Trying polling fallback…');tick();this.poll=setInterval(tick,2000);
  }
  send(message){if(this.ws?.readyState===1){this.ws.send(JSON.stringify(message));return true;}return false;}
  stop(){this.running=false;clearInterval(this.probe);clearInterval(this.poll);clearTimeout(this.syncTimer);this.sseAbort?.abort();this.ws?.close();this.candidate?.close();}
}
```

### campusguard/public/js/peer-relay.js

<!-- CAMPUSGUARD_SOURCE {"path": "public/js/peer-relay.js", "encoding": "utf-8", "sha256": "06f72b7384ecddb3fd4b165fddbde621ac44682bdc332b8cb21c8f060896ad64"} -->
```javascript
export class PeerRelay {
  constructor({id,sendSignal,onAlert,getAlerts}){Object.assign(this,{id,sendSignal,onAlert,getAlerts});this.peers=new Map();this.forwarded=new Set();this.forwards=0;this.enabled=!!globalThis.crypto?.subtle;this.inventoryTimer=setInterval(()=>this.inventory(),5000);}
  make(id){if(this.peers.has(id))return this.peers.get(id);const pc=new RTCPeerConnection({iceServers:[]}),entry={pc,channel:null,ice:[]};this.peers.set(id,entry);
    pc.onicecandidate=e=>{if(e.candidate)this.sendSignal({kind:'signal',to:id,type:'ice',data:e.candidate.toJSON()});};
    pc.ondatachannel=e=>this.attach(id,e.channel);
    pc.onconnectionstatechange=()=>{if(['failed','closed'].includes(pc.connectionState)){pc.close();if(this.peers.get(id)===entry)this.peers.delete(id);}};
    return entry;
  }
  attach(id,channel){const p=this.peers.get(id);if(!p)return;p.channel=channel;channel.onopen=()=>this.inventory();channel.onmessage=event=>this.receive(id,event.data).catch(()=>{});channel.onerror=()=>{};}
  async roster(peers){this.lastRoster=peers;if(!this.enabled||typeof RTCPeerConnection==='undefined')return;
    const ids=peers.filter(p=>p.id!==this.id).map(p=>p.id).sort();
    // Deterministic ring: each node connects to its successor and second successor.
    const all=[this.id,...ids].sort(), index=all.indexOf(this.id), wanted=new Set([all[(index+1)%all.length],all[(index+2)%all.length]].filter(x=>x&&x!==this.id));
    // Keep established DataChannels even when a peer loses its signaling connection.
    // Closing them on roster removal would defeat relay during a server-path failure.
    for(const id of wanted){if(this.peers.has(id))continue;const p=this.make(id);this.attach(id,p.pc.createDataChannel('campus-alerts',{ordered:true}));try{await p.pc.setLocalDescription(await p.pc.createOffer());if(!this.sendSignal({kind:'signal',to:id,type:'offer',data:p.pc.localDescription})){p.pc.close();if(this.peers.get(id)===p)this.peers.delete(id);}}catch{p.pc.close();if(this.peers.get(id)===p)this.peers.delete(id);}}
  }
  async signal(m){if(!this.enabled)return;
    let p=this.peers.get(m.from);
    if(m.type==='offer'){
      if(p?.pc.signalingState==='have-local-offer') {if(this.id<m.from)return; p.pc.close();this.peers.delete(m.from);p=null;}
      else if(p?.pc.remoteDescription){p.pc.close();this.peers.delete(m.from);p=null;}
      p??=this.make(m.from);await p.pc.setRemoteDescription(m.data);for(const ice of p.ice.splice(0))await p.pc.addIceCandidate(ice);await p.pc.setLocalDescription(await p.pc.createAnswer());this.sendSignal({kind:'signal',to:m.from,type:'answer',data:p.pc.localDescription});
    }else if(m.type==='answer'&&p){await p.pc.setRemoteDescription(m.data);for(const ice of p.ice.splice(0))await p.pc.addIceCandidate(ice);}
    else if(m.type==='ice'){p??=this.make(m.from);if(p.pc.remoteDescription)await p.pc.addIceCandidate(m.data);else p.ice.push(m.data);}
  }
  transmit(id,msg){const c=this.peers.get(id)?.channel;if(c?.readyState==='open'&&c.bufferedAmount<65536)c.send(JSON.stringify(msg));}
  inventory(){const ids=this.getAlerts().map(a=>a.id).slice(-100);for(const id of this.peers.keys())this.transmit(id,{kind:'inventory',ids});if(this.lastRoster)this.roster(this.lastRoster).catch(()=>{});}
  async receive(id,raw){if(raw.length>16384)return;const m=JSON.parse(raw);
    if(m.kind==='inventory'&&Array.isArray(m.ids)){const have=new Set(this.getAlerts().map(a=>a.id));this.transmit(id,{kind:'want',ids:m.ids.filter(x=>typeof x==='string'&&!have.has(x)).slice(0,100)});}
    if(m.kind==='want'&&Array.isArray(m.ids)){for(const a of this.getAlerts().filter(a=>m.ids.includes(a.id)))this.transmit(id,{kind:'relay',alert:a,hops:1});}
    if(m.kind==='relay'&&Number.isInteger(m.hops)&&m.hops>=1&&m.hops<=4){const accepted=await this.onAlert(m.alert,'webrtc');if(accepted)this.gossip(m.alert,m.hops,id);}
  }
  gossip(alert,hops=0,exclude=null){if(hops>=4||this.forwarded.has(alert.id))return;this.forwarded.add(alert.id);for(const id of this.peers.keys())if(id!==exclude){this.transmit(id,{kind:'relay',alert,hops:hops+1});if(this.peers.get(id)?.channel?.readyState==='open')this.forwards++;}}
  health(){return {connections:[...this.peers.values()].filter(p=>p.channel?.readyState==='open').length,forwards:this.forwards};}
  reset(){this.lastRoster=[];for(const p of this.peers.values())p.pc.close();this.peers.clear();}
  stop(){clearInterval(this.inventoryTimer);for(const p of this.peers.values())p.pc.close();this.peers.clear();}
}
```

### campusguard/public/js/ui.js

<!-- CAMPUSGUARD_SOURCE {"path": "public/js/ui.js", "encoding": "utf-8", "sha256": "74fd8203d87d8d6fd4a504b7a038c2d528b7a4f7fd4722d960349bf5e6891d31"} -->
```javascript
export const $=(s,root=document)=>root.querySelector(s);
export function h(tag,attrs={},...children){const e=document.createElement(tag);for(const [k,v]of Object.entries(attrs)){if(k==='class')e.className=v;else if(k.startsWith('on'))e.addEventListener(k.slice(2).toLowerCase(),v);else if(k==='text')e.textContent=v;else if(v!==false&&v!=null)e.setAttribute(k,v===true?'':String(v));}for(const c of children.flat(Infinity)){if(c!=null)e.append(c instanceof Node?c:document.createTextNode(String(c)));}return e;}
export function toast(message,error=false){let box=$('#toast');if(!box){box=h('div',{id:'toast',role:'status'});document.body.append(box);}box.textContent=message;box.className='toast show'+(error?' error':'');clearTimeout(box.timer);box.timer=setTimeout(()=>box.classList.remove('show'),4500);}
export const title=s=>String(s).replace(/(^|[-\s])\S/g,x=>x.toUpperCase()).replace('-',' ');
export const time=s=>new Date(s).toLocaleTimeString([],{hour:'2-digit',minute:'2-digit',second:'2-digit'});
export const ago=s=>{const n=Math.max(0,Math.round((Date.now()-Date.parse(s))/1000));return n<60?`${n}s ago`:n<3600?`${Math.floor(n/60)}m ago`:`${Math.floor(n/3600)}h ago`;};
export async function api(path,options={}){const res=await fetch(path,{cache:'no-store',...options,headers:{'Content-Type':'application/json',...options.headers}});const data=await res.json().catch(()=>({error:`Request failed (${res.status})`}));if(!res.ok){const e=new Error(data.error||'Request failed');e.status=res.status;throw e;}return data;}
export const icon=(name,cls='')=>h('span',{class:`symbol ${cls}`,'aria-hidden':'true'},({shield:'◈',overview:'▦',history:'◷',transport:'⇄',connect:'⌁',fire:'♨',earthquake:'⌁',medical:'✚',security:'◈',drill:'⚑','all-clear':'✓',arrow:'↗',bolt:'ϟ',check:'✓',close:'×',sound:'♫',safe:'✓',users:'♙'}[name]||'◉'));
```

### campusguard/public/manifest.webmanifest

<!-- CAMPUSGUARD_SOURCE {"path": "public/manifest.webmanifest", "encoding": "utf-8", "sha256": "bdd63bf23be3b8c45e16c209ac54c5c087ee65dea0e81a1c809fdf92ba94c89d"} -->
```json
{"id":"/","name":"CampusGuard Safety","short_name":"CampusGuard","start_url":"/","scope":"/","display":"standalone","background_color":"#f4f5ef","theme_color":"#112b36","icons":[{"src":"/icons/icon-192.png","sizes":"192x192","type":"image/png","purpose":"any maskable"},{"src":"/icons/icon-512.png","sizes":"512x512","type":"image/png","purpose":"any maskable"}]}
```

### campusguard/public/setup.html

<!-- CAMPUSGUARD_SOURCE {"path": "public/setup.html", "encoding": "utf-8", "sha256": "74a58f06968d8d7b72ee21749af7e640f7d505a62998866df4f212500c52af5a"} -->
```html
<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>CampusGuard · Device setup</title><link rel="stylesheet" href="/styles.css"><link rel="icon" href="/icons/shield.svg"></head>
<body><main class="setup-page"><img src="/icons/shield.svg" width="50" height="56" alt="CampusGuard"><span class="eyebrow">LOCAL DEVICE SETUP</span><h1>Your phones.<br>Your local safety network.</h1>
<p>Student browser alerts use the local network. Trusted HTTPS enables installation, offline app caching and browser peer relay. Real Bluetooth advertising is a separate radio channel for native receivers.</p>
<h2>Start the alert demo</h2><ol><li>Connect the laptop and phones to one Wi-Fi network or hotspot.</li><li>Open the HTTP student address printed by the laptop server.</li><li>Select Visvesvaraya, Abdul Kalam, Ramanujan or Kalpana Chawla.</li><li>Tap <strong>Enable alerts &amp; test sound</strong> and keep the page open.</li><li>Send a clearly labelled drill from the admin dashboard and acknowledge it on the targeted phones.</li></ol>
<h2>Enable the full PWA</h2><ol><li>Run <code>START-CAMPUSGUARD.cmd</code> on the laptop. It prepares certificates for the current network.</li><li>Download this laptop’s public CA certificate below from each phone.</li><li><strong>Android:</strong> search Settings for Install CA certificate or Encryption &amp; credentials. Select the downloaded file and complete the device prompts.</li><li><strong>iPhone/iPad:</strong> install the certificate profile in Settings, then enable trust for CampusGuard Local Demo CA under General → About → Certificate Trust Settings.</li><li><strong>Windows:</strong> open <code>certs/root-ca.cer</code> and install for Current User → Trusted Root Certification Authorities.</li><li>Open the printed HTTPS student address with no certificate warning. Enroll and enable sound again; HTTP and HTTPS use separate browser storage.</li><li>Use Install or Add to Home Screen if offered. First load must happen while the server is reachable.</li></ol>
<a class="btn primary" href="/root-ca.cer">Download this laptop’s public CA certificate ↓</a><p>Trust the certificate from your own demo laptop only. Remove the demo CA after the event. Private keys stay on the laptop. Restart the launcher after changing networks to refresh its IP certificate.</p>
<h2>Real Bluetooth</h2><p>Enable Bluetooth on the Windows laptop. Transport lab reports adapter availability, actual advertising starts, queued packets and failures. Manufacturer ID is 0xFFFF and each manufacturer payload contains 24 bytes, including the 21-byte alert core. Native BLE scanners can inspect these packets. The student browser page does not scan these advertisements; it receives over LAN. An advertising start does not prove that another device received it.</p>
<h2>Troubleshooting</h2><ol><li><strong>Old hostel list:</strong> reload the admin/student page. The shared zone table is used throughout, with cache revalidation and an updated offline cache.</li><li><strong>Phone cannot open the link:</strong> verify the current laptop IP and trusted-network firewall permission. Guest Wi-Fi/hotspot isolation can block local devices.</li><li><strong>No sound:</strong> tap the sound button after each page load, raise media volume and keep the page active. Vibration depends on device/browser support.</li><li><strong>No peer link:</strong> both clients need trusted HTTPS and a working WebSocket during setup. Local network isolation can block direct connections.</li><li><strong>Bluetooth unavailable:</strong> inspect the error in Transport lab. LAN delivery continues; fake Bluetooth coverage is never shown.</li></ol>
<a class="btn outline" href="/">Student view →</a><a class="btn outline" href="/admin.html">Operations center →</a></main></body></html>
```

### campusguard/public/styles.css

<!-- CAMPUSGUARD_SOURCE {"path": "public/styles.css", "encoding": "utf-8", "sha256": "0340578a3b2a4a3e13a1daba4985cb6b160b9a0689b9e7d0971954b7bb98696e"} -->
```css
@charset "UTF-8";
:root{--ink:#112b36;--muted:#708084;--paper:#f4f5ef;--panel:#fff;--line:#e4e8e1;--lime:#b8ec73;--green:#247557;--red:#d74735;--amber:#d89e38;--nav:#102c37;--radius:16px;font-family:Inter,"Segoe UI",Arial,sans-serif;color:var(--ink);background:var(--paper);font-synthesis:none}
*{box-sizing:border-box}body{margin:0}button,input,select,textarea{font:inherit}button,a,input,select,textarea{-webkit-tap-highlight-color:transparent}button,a{touch-action:manipulation}button{cursor:pointer}button:disabled{opacity:.5;cursor:wait}a{color:inherit;text-decoration:none}p{line-height:1.6;margin:0}h1,h2,h3{margin:0;letter-spacing:-.04em}h1{font-size:36px;line-height:1.15;font-weight:650}h2{font-size:18px;font-weight:650}h3{font-size:16px}small,.micro{font-size:11px;line-height:1.6}.muted{color:var(--muted)}.mono{font-family:Consolas,"SFMono-Regular",monospace;font-size:11px}.eyebrow{font-size:10px;letter-spacing:.15em;font-weight:750;display:block}.center{text-align:center}.full{width:100%}.dot{width:7px;height:7px;border-radius:100%;display:inline-block;flex-shrink:0;vertical-align:middle}.green{background:#50a87c}.amber{background:var(--amber)}.red{background:var(--red)}.btn{min-height:42px;display:inline-flex;align-items:center;justify-content:center;gap:12px;border-radius:8px;border:1px solid transparent;padding:10px 17px;font-size:12px;font-weight:650;transition:background .15s,transform .15s}.btn:hover{filter:brightness(.96);transform:translateY(-1px)}.btn.primary{background:var(--lime);color:var(--ink)}.btn.dark{background:var(--ink);color:white}.btn.outline{background:transparent;border-color:#d8dfd8;color:var(--ink)}.btn.small{min-height:32px;padding:7px 12px;font-size:11px}.btn.broadcast{background:var(--red);color:white;min-height:46px;justify-content:space-between}.btn.broadcast:hover{background:#bd3a2b}.tag{font-size:9px;font-weight:750;letter-spacing:.06em;display:inline-flex;align-items:center;gap:5px;padding:5px 8px;border:1px solid var(--line);border-radius:5px;background:#f7f8f5;white-space:nowrap}.amber-tag{color:#936614;background:#fff6dd;border-color:#eed9a8}.icon-button{border:0;background:transparent;font-size:22px;color:inherit;padding:4px 8px}.text-link,.text-button{border:0;background:transparent;padding:0;font-size:11px;font-weight:650;color:var(--green)}.text-button{padding:14px}.symbol{display:inline-flex;align-items:center;justify-content:center}.brand{display:flex;align-items:center;gap:10px;font-size:21px;letter-spacing:-.06em;font-weight:750;color:white}.brand-light{font-weight:350}.brand small{display:block;font-size:7px;letter-spacing:.2em;font-weight:500;margin-top:3px;color:#a7bec4}.sidebar{position:fixed;left:0;top:0;bottom:0;width:232px;background:var(--nav);color:#d6e0e3;padding:32px 22px 0;display:flex;flex-direction:column;z-index:5}.sidebar-caption{font-size:8px;font-weight:600;letter-spacing:.2em;color:#729099;margin:48px 15px 17px}nav{display:flex;flex-direction:column;gap:8px}.nav-item{background:transparent;border:0;color:#a7bec4;width:100%;display:flex;align-items:center;gap:14px;text-align:left;padding:14px 15px;font-size:12px;border-radius:8px}.nav-item>span:first-child{font-size:20px;line-height:15px;width:18px}.nav-item.active{background:var(--lime);color:var(--ink);font-weight:700}.nav-item:hover:not(.active){background:#1d3c47}.nav-mark{margin-left:auto;font-size:9px;opacity:.55}.sidebar-network{border:1px solid #2d4c56;border-radius:10px;padding:17px 14px;margin-top:auto}.sidebar-network strong{font-size:12px;margin-left:6px;color:#e0e9e4}.sidebar-network p{font-size:11px;color:#92aab2;margin:10px 0}.sidebar-network .micro{font-size:8px;color:#b8ec73}.sidebar-bottom{display:flex;gap:10px;align-items:center;padding:24px 0;color:#e6eff0}.avatar{background:#2b4650;color:var(--lime);font-size:11px;width:32px;height:32px;border-radius:8px;display:grid;place-items:center}.sidebar-bottom strong{font-size:10px}.sidebar-bottom small{display:block;font-size:9px;color:#8fa7af}.sidebar-bottom button{margin-left:auto;font-size:18px}.admin-shell{margin-left:232px}.admin-header{height:76px;background:#fafbf7;border-bottom:1px solid var(--line);display:flex;align-items:center;justify-content:space-between;padding:0 38px;gap:14px}.breadcrumb{font-size:11px;color:#8b9693;white-space:nowrap}.breadcrumb span{margin:0 12px}.breadcrumb strong{font-weight:500;color:var(--ink)}.header-actions{display:flex;gap:22px;align-items:center}.status-pill{font-size:10px;display:flex;gap:6px;align-items:center;white-space:nowrap}.admin-main{max-width:1640px;padding:34px 38px 0;margin:auto}.page-heading{display:flex;align-items:center;justify-content:space-between;gap:15px;margin-bottom:26px}.page-heading .eyebrow{color:#688679;margin-bottom:10px}.page-heading p{font-size:12px;color:var(--muted);margin-top:10px}.outline-stamp{display:flex;gap:10px;align-items:center;border:1px solid #d5ddcf;background:#f8faf3;padding:13px;border-radius:7px;font-size:26px;color:#5c7d5d}.outline-stamp span{font-size:7px;letter-spacing:.12em;line-height:1.8}.outline-stamp strong{font-size:8px}.stats-row{display:grid;grid-template-columns:repeat(4,1fr);gap:15px;margin-bottom:24px}.stat-card{border:1px solid var(--line);border-radius:11px;padding:18px 19px;background:var(--panel);position:relative;overflow:hidden}.stat-card:first-child{background:var(--ink);color:white;border-color:var(--ink)}.stat-caption{font-size:10px;display:flex;justify-content:space-between;align-items:center;color:#7b8b8f}.stat-card:first-child .stat-caption{color:#b6ced4}.stat-symbol{font-size:18px;color:#668278}.stat-number{font-size:34px;letter-spacing:-.06em;font-weight:600;margin:9px 0 6px}.stat-foot{font-size:9px;color:#8c999b}.stat-card:first-child .stat-number{color:var(--lime)}.stat-card:first-child .stat-foot{color:#a2bec6}.workspace-grid{display:grid;grid-template-columns:minmax(320px,.95fr) minmax(350px,1.15fr);gap:22px}.panel{background:var(--panel);border:1px solid var(--line);border-radius:var(--radius);padding:23px;overflow:hidden}.panel-heading{display:flex;align-items:center;justify-content:space-between;gap:10px}.panel-heading>div{display:flex;align-items:center;gap:9px}.section-number{font-size:9px;color:#81938a;border:1px solid var(--line);padding:4px;border-radius:4px}.panel-description{font-size:11px;color:var(--muted);margin:9px 0 22px}label{display:block;font-size:11px;font-weight:600;margin:16px 0 7px}input,select,textarea{display:block;border:1px solid #dce2db;border-radius:7px;background:#fcfdf9;color:var(--ink);padding:11px 12px;font-size:12px;width:100%;margin-top:8px;outline:0;min-height:40px}textarea{resize:vertical;min-height:91px;line-height:1.5}input:focus,select:focus,textarea:focus{border-color:#749c63;box-shadow:0 0 0 3px #b8ec7328}button:focus-visible,a:focus-visible{outline:3px solid #8dba65;outline-offset:3px}.type-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:8px}.type-button{display:flex;align-items:center;justify-content:center;gap:6px;background:white;border:1px solid #e1e6de;border-radius:7px;color:#697b80;padding:12px 5px;font-size:10px}.type-button .symbol{font-size:17px}.type-button.selected{border-color:#d74735;background:#fff4ee;color:#c1402f;box-shadow:0 0 0 1px #d7473518}.type-button[data-type="all-clear"].selected{border-color:var(--green);background:#edf7e8;color:var(--green)}.form-two{display:grid;grid-template-columns:1fr 1fr;gap:14px}.form-meta{display:flex;justify-content:space-between;align-items:center;margin:4px 0 20px;gap:10px;font-size:9px;color:var(--muted)}.form-meta label{display:flex;align-items:center;gap:6px;margin:0;font-weight:400;font-size:9px;white-space:nowrap}.form-meta select{padding:4px 20px 4px 5px;min-height:25px;width:auto;margin:0;font-size:9px;border:0;background:transparent}.compose-panel .center{margin-top:11px;font-size:9px}.campus-panel{display:flex;flex-direction:column}.live-label{font-size:8px;letter-spacing:.1em;display:flex;gap:5px;align-items:center;color:var(--green)}.campus-map{background-color:#f0f3e9;background-image:linear-gradient(#dfe6d83b 1px,transparent 1px),linear-gradient(90deg,#dfe6d83b 1px,transparent 1px);background-size:20px 20px;border:1px solid #e3e9dd;border-radius:10px;position:relative;min-height:255px;margin-top:21px;overflow:hidden}.map-label{position:absolute;left:14px;top:12px;font-size:7px;color:#8d9b85;letter-spacing:.08em}.map-road{background:#d6decd;position:absolute;border:4px solid #e3e9db;border-radius:10px}.map-road.horizontal{top:48%;left:-5px;right:-5px;height:18px}.map-road.vertical{top:-5px;bottom:-5px;left:46%;width:18px}.map-road.bottom{bottom:20%;left:5%;right:5%;height:9px;opacity:.6}.map-server{position:absolute;top:42%;left:calc(48% - 21px);background:#173b36;color:var(--lime);width:43px;height:43px;border:5px solid white;border-radius:11px;display:grid;place-items:center;box-shadow:0 0 0 14px #b8ec7335;z-index:2}.map-server>span:first-child{font-size:24px}.map-server small{position:absolute;top:47px;white-space:nowrap;left:-11px;font-size:6px;font-weight:700;color:#446049}.map-zone{position:absolute;width:31%;display:flex;align-items:center;gap:8px}.map-zone[data-zone="1"]{left:8%;top:25%}.map-zone[data-zone="2"]{left:62%;top:23%}.map-zone[data-zone="3"]{left:9%;bottom:18%}.map-zone[data-zone="4"]{left:62%;bottom:18%}.building{width:39px;height:31px;background:#d8e3cb;border:1px solid #bccbaa;border-radius:4px;position:relative;box-shadow:4px 4px 0 #c6d2ba55;flex-shrink:0}.building:before{content:"";position:absolute;left:7px;right:7px;top:8px;bottom:7px;background:repeating-linear-gradient(90deg,#97af88 0,#97af88 3px,transparent 3px,transparent 7px)}.map-zone[data-zone="4"] .building{border-radius:50%;background:#c1d9a7;border:3px solid #dce9d0}.map-zone .zone-label strong{display:block;font-size:8px;white-space:nowrap}.map-zone .zone-label small{font-size:7px;color:#7b8f73;white-space:nowrap}.map-zone .dot{width:5px;height:5px;margin-right:3px}.map-zone.has-alert .building{background:#f0c3b7;border-color:#d74735}.map-zone.has-alert .zone-label strong{color:#b13b2c}.map-compass{position:absolute;right:14px;top:12px;font-size:9px;color:#829578}.map-note{font-size:5px;color:#8d9d84;letter-spacing:.1em;position:absolute;bottom:8px;left:14px}.map-legend{display:flex;justify-content:center;gap:16px;padding:11px 0;color:#7c8d7e;font-size:7px}.map-legend .dot{width:5px;height:5px;margin-right:3px}.zone-summary{display:grid;grid-template-columns:repeat(2,1fr);gap:10px;margin-top:5px}.zone-item{border:1px solid var(--line);padding:11px 12px;border-radius:7px;display:flex;justify-content:space-between;align-items:center;gap:5px}.zone-item strong{display:block;font-size:9px}.zone-item small{font-size:8px;color:var(--muted)}.zone-item>.tag{font-size:7px;padding:4px 5px}.coverage-footnote{font-size:8px;color:#8c9a91;margin-top:auto;padding-top:16px}.recent-panel{margin-top:22px}.recent-panel .panel-heading{margin-bottom:12px}.empty-state{padding:30px;text-align:center;color:var(--muted);font-size:12px;background:#fafbf7;border:1px dashed #dce3d5;border-radius:8px;margin-top:15px}.empty-state strong{display:block;font-size:13px;color:#657874;margin-bottom:5px}.activity-row{display:grid;grid-template-columns:34px minmax(100px,1fr) 1fr auto;align-items:center;gap:12px;padding:15px 0;border-bottom:1px solid #edf0e8}.activity-row:last-child{border:0}.type-icon{width:34px;height:34px;border-radius:8px;background:#fff0e9;color:var(--red);display:grid;place-items:center;font-size:20px}.type-icon.clear{color:var(--green);background:#ecf6e6}.activity-row strong{font-size:11px;display:block}.activity-row small{color:var(--muted);font-size:9px}.activity-metrics{font-size:10px;text-align:right}.activity-metrics b{color:var(--green)}.activity-time{font-size:9px;color:#8d9d96}.admin-footer{display:flex;justify-content:space-between;gap:10px;font-size:7px;color:#8f9d91;letter-spacing:.1em;padding:23px 1px}.admin-footer .mono{font-size:7px}.transport-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:16px;margin:22px 0}.transport-card{background:white;border:1px solid var(--line);border-radius:12px;padding:20px}.transport-card-top{display:flex;justify-content:space-between;align-items:center}.transport-card h3{font-size:15px;margin-bottom:8px}.transport-card .tag{font-size:7px}.transport-description{font-size:10px;line-height:1.5;color:var(--muted);min-height:42px;margin-top:9px}.transport-stats{display:flex;gap:18px;padding-top:15px;margin-top:12px;border-top:1px solid var(--line)}.transport-stats strong{font-size:19px;display:block}.transport-stats small{font-size:8px;color:var(--muted)}.transport-error{color:#c34833;font-size:9px;margin-top:10px;overflow-wrap:anywhere}.toggle{appearance:none;border:0;width:32px;height:18px;background:#dce2d6;border-radius:20px;padding:0;position:relative;flex-shrink:0}.toggle:after{content:"";width:12px;height:12px;border-radius:50%;background:white;position:absolute;left:3px;top:3px;transition:left .15s}.toggle[aria-checked="true"]{background:var(--green)}.toggle[aria-checked="true"]:after{left:17px}.info-banner{display:flex;gap:15px;align-items:center;border:1px solid #d5dfce;background:#eaf0e0;border-radius:12px;padding:20px}.info-banner>span{font-size:29px}.info-banner strong{font-size:12px}.info-banner p{font-size:10px;color:#72836b;max-width:700px}.info-banner button{white-space:nowrap;margin-left:auto}.ble-controls{display:flex;gap:15px}.ble-controls label{flex:1}.ble-zones{display:flex;gap:10px;flex-wrap:wrap;padding:16px 0}.coverage-toggle{display:flex;align-items:center;gap:10px;padding:10px 12px;border:1px solid var(--line);border-radius:7px;font-size:11px}.beacon-log{max-height:320px;overflow:auto;border:1px solid var(--line);border-radius:8px;background:#fafcf7}.beacon-row{padding:12px 15px;border-bottom:1px solid var(--line);display:flex;gap:12px;align-items:start}.beacon-row .mono{font-size:9px;overflow-wrap:anywhere;word-break:break-all;color:#7c8c7b;margin-top:4px}.beacon-row strong{font-size:10px}.beacon-row small{font-size:9px;color:var(--muted)}.beacon-row>div{flex:1}.table-wrap{overflow:auto}table{border-collapse:collapse;width:100%;font-size:11px;white-space:nowrap}th{text-align:left;color:var(--muted);font-size:8px;letter-spacing:.08em;padding:13px 12px;background:#f8faf4;border-bottom:1px solid var(--line)}td{padding:16px 12px;border-bottom:1px solid var(--line)}td.message-cell{white-space:normal;min-width:220px;max-width:380px;font-size:10px;color:var(--muted)}.connect-grid{display:grid;grid-template-columns:1.4fr 1fr;gap:22px}.connect-title{font-size:34px;margin-top:15px}.setup-list{font-size:12px;line-height:1.8;padding-left:20px;color:#5e7479;margin:24px 0}.setup-list li{padding-left:7px;margin-bottom:12px}.join-url{background:#f1f5e9;border:1px solid #dce6cf;border-radius:7px;padding:14px;display:flex;align-items:center;justify-content:space-between;gap:10px;margin:10px 0}.join-url a{font-family:Consolas,monospace;font-size:11px;overflow-wrap:anywhere}.https-note{border-top:1px solid var(--line);margin-top:24px;padding-top:20px;font-size:11px}.https-note p{font-size:10px;color:var(--muted);margin:6px 0 10px}.qr-panel{text-align:center;display:flex;flex-direction:column;align-items:center;justify-content:center;min-height:420px}.qr-panel img{width:210px;height:210px;max-width:100%;margin:25px 0}.qr-panel p{overflow-wrap:anywhere;max-width:100%}.modal{border:1px solid #dce4d3;border-radius:18px;background:#fffef9;color:var(--ink);padding:34px;width:440px;max-width:calc(100vw - 30px);box-shadow:0 25px 100px #102c3725}.modal::backdrop{background:#102c3790;backdrop-filter:blur(5px)}.modal .eyebrow{margin-top:20px;color:#708969}.modal h2{font-size:30px;line-height:1.15;margin:14px 0}.modal p{font-size:12px;color:var(--muted);margin:10px 0}.modal form>.btn{margin-top:20px}.modal label{margin-top:20px}.modal .form-error{font-size:11px;color:var(--red);min-height:16px}.modal .micro{font-size:10px}.review-actions{display:flex;gap:10px;justify-content:flex-end;margin-top:25px}.review-message{white-space:pre-wrap;background:#f4f6ef;border-radius:8px;padding:17px;margin:15px 0;font-size:13px;line-height:1.7}.review-tags{display:flex;gap:7px;flex-wrap:wrap}.toast{position:fixed;left:50%;bottom:25px;transform:translate(-50%,20px);background:var(--ink);color:white;border:1px solid #587d75;border-radius:10px;padding:14px 22px;box-shadow:0 6px 25px #102c3733;font-size:12px;max-width:calc(100vw - 35px);z-index:100;opacity:0;pointer-events:none;transition:opacity .2s,transform .2s}.toast.show{opacity:1;transform:translate(-50%,0)}.toast.error{background:#863625;border-color:#bd5844}[hidden]{display:none!important}
/* Student surface */
.student-body{background:#f4f5ef}.student-header{background:var(--ink);color:white;display:flex;align-items:center;justify-content:space-between;padding:20px max(24px,calc((100vw - 760px)/2));gap:20px}.student-header .quiet-link{font-size:11px;color:#a7bec4;white-space:nowrap}.student-main{max-width:760px;margin:auto;padding:28px 24px}.student-top{display:flex;align-items:center;justify-content:space-between;color:#6e877b}.student-top .eyebrow{font-size:8px}.student-top .mono{font-size:10px}.welcome-row{display:flex;align-items:center;justify-content:space-between;margin:28px 0;gap:15px}.welcome-row h1{font-size:42px}.welcome-row p{font-size:12px;color:var(--muted);margin-top:10px}.round-shield{background:#e5edda;border:1px solid #d4dfc8;border-radius:50%;width:90px;height:90px;display:grid;place-items:center;font-size:50px;color:#5b8254;flex-shrink:0}.connection-card{background:white;border:1px solid #dee5d8;border-radius:11px;padding:18px 20px}.connection-card>div:first-child{display:flex;align-items:center;gap:8px}.connection-card strong{font-size:12px}.connection-card .tag{margin-left:auto;font-size:8px;background:#eef5e7;color:#47744f;border-color:#dae8d0}.connection-card p{color:var(--muted);font-size:10px;margin:6px 0 3px}.connection-card .micro{font-size:9px;color:#8b9b8a}.student-tools{display:flex;align-items:end;gap:15px;margin:9px 0}.student-tools label{flex:1;margin:14px 0 0;font-size:10px}.student-tools select{margin-top:6px;background:transparent;font-size:11px}.student-tools .btn{font-size:10px;min-height:40px;flex:1;white-space:nowrap}.calm-card{margin-top:25px;border:1px solid #d4e1c5;border-radius:17px;background:#eaf1e1;min-height:260px;padding:30px;position:relative;overflow:hidden}.calm-card:after{content:"";position:absolute;width:260px;height:260px;border:1px solid #d3e1c3;border-radius:50%;right:-100px;top:-30px;box-shadow:0 0 0 35px #e1ead633,0 0 0 70px #e1ead633}.calm-icon{position:absolute;right:40px;top:65px;display:grid;place-items:center;width:90px;height:90px;border-radius:50%;border:1px solid #cbdcba;background:#dcebcf;font-size:45px;color:#5d8350;z-index:1}.calm-card .eyebrow{color:#718c5e;font-size:8px}.calm-card h2{font-size:30px;line-height:1.18;margin:16px 0;max-width:75%}.calm-card p{font-size:11px;color:#7b8f6c;max-width:65%}.calm-footer{display:flex;align-items:center;gap:6px;border-top:1px solid #d8e4cb;padding-top:16px;margin-top:25px;font-size:9px;color:#6c865d}.section-row{display:flex;align-items:center;justify-content:space-between;margin:30px 0 15px}.section-row h2{font-size:15px}.student-alert{border:1px solid #e3e7dc;background:white;border-radius:12px;padding:20px;margin-bottom:13px}.student-alert.alert-critical{border-left:4px solid var(--red)}.student-alert.alert-urgent{border-left:4px solid var(--amber)}.student-alert-top{display:flex;align-items:center;justify-content:space-between;gap:10px;margin-bottom:12px}.student-alert-top>div{display:flex;align-items:center;gap:9px}.student-alert-top h3{font-size:15px}.student-alert p{font-size:12px;color:#647b7d;white-space:pre-wrap;overflow-wrap:anywhere}.student-alert-meta{display:flex;gap:10px;justify-content:space-between;align-items:center;margin-top:14px;font-size:9px;color:#8a9b8e;flex-wrap:wrap}.student-alert .btn{font-size:10px;min-height:32px;padding:6px 12px}.response-sent{color:var(--green);font-size:10px;font-weight:600}.local-note{display:flex;gap:14px;padding:24px 0;border-top:1px solid #dfe6d8;margin-top:28px;color:#7c8e80}.local-note>span{font-size:35px}.local-note p{font-size:10px}.local-note strong{font-weight:600;color:#5d756c}.student-footer{font-size:7px;letter-spacing:.08em;color:#8c9d8e;display:flex;gap:12px;justify-content:space-between;padding:10px 0 20px}.onboarding h2{font-size:34px}.modal-emblem{display:grid;place-items:center;width:48px;height:48px;border-radius:12px;background:#e4efd7;color:#5c844b;font-size:35px}.emergency-modal{background:#ce412d;color:white;min-height:580px;width:520px;border:0;border-radius:18px;padding:28px;text-align:center;max-width:calc(100vw - 24px);box-shadow:0 30px 100px #6f201b66}.emergency-modal::backdrop{background:#1c0909b8;backdrop-filter:blur(7px)}.emergency-modal.urgent{background:#9a6515}.emergency-modal.advisory{background:#234d58}.emergency-top{display:flex;align-items:center;justify-content:space-between;text-align:left}.emergency-top .eyebrow{font-size:9px;color:#ffe2cd}.emergency-icon{display:grid;place-items:center;width:85px;height:85px;border:2px solid #ffd3bb;border-radius:24px;font-size:55px;margin:35px auto 23px;background:#ffffff0c}.emergency-modal>.eyebrow{font-size:9px;color:#ffd9c2}.emergency-modal h2{font-size:43px;margin:12px 0 20px}.emergency-modal>p{font-size:17px;line-height:1.6;white-space:pre-wrap;overflow-wrap:anywhere;margin:15px 0 20px}.emergency-modal .micro{font-size:10px;color:#ffdbcd}.emergency-modal .safe-button{background:white;color:#a32d1c;width:100%;min-height:54px;font-size:14px;margin-top:30px}.emergency-modal .text-button{color:white}.emergency-modal>p.micro{font-size:9px;margin-top:0}.form-error:empty{display:none}.setup-page{max-width:780px;margin:40px auto;padding:25px}.setup-page h1{margin:20px 0}.setup-page h2{margin:25px 0 10px}.setup-page ol{font-size:14px;line-height:1.8}.setup-page p{font-size:13px;color:var(--muted)}.setup-page code{background:#e6eddd;padding:3px 6px;border-radius:4px}.setup-page .btn{margin:15px 0}
.peer-demo{font-size:10px;margin-top:12px}
@media(min-width:1500px){.admin-main{padding:42px 46px 0}.panel{padding:28px}.campus-map{min-height:285px}.type-button{font-size:11px;padding:14px}.workspace-grid{gap:25px}.coverage-footnote{font-size:9px}}
@media(max-width:1200px){.sidebar{width:205px;padding-left:17px;padding-right:17px}.brand{font-size:19px}.admin-shell{margin-left:205px}.admin-main{padding:25px 24px 0}.admin-header{padding:0 24px}.header-actions{gap:12px}.header-actions .mono{display:none}.outline-stamp{display:none}.workspace-grid{grid-template-columns:minmax(290px,1fr) minmax(310px,1fr);gap:16px}.panel{padding:19px}.map-zone{gap:4px}.building{width:29px}.map-zone .zone-label strong{font-size:7px}.map-legend{gap:8px;font-size:6px}.transport-grid{grid-template-columns:repeat(2,1fr)}.stat-card{padding:15px}.stat-caption{font-size:9px}.stat-foot{font-size:8px}.zone-item{padding:9px}.zone-item>.tag{display:none}}
@media(max-width:980px){.workspace-grid{grid-template-columns:1fr}.campus-map{min-height:280px}.map-zone .zone-label strong{font-size:10px}.building{width:45px;height:35px}.map-legend{font-size:9px;gap:18px}.campus-panel .coverage-footnote{padding-top:15px}.zone-item>.tag{display:flex}.stats-row{grid-template-columns:repeat(2,1fr)}.connect-grid{grid-template-columns:1fr}.header-actions .status-pill{display:none}.activity-row{grid-template-columns:34px 1fr auto}.activity-time{display:none}.info-banner{flex-wrap:wrap}.info-banner button{margin-left:0}}
@media(max-width:680px){.sidebar{position:relative;top:auto;bottom:auto;width:auto;padding:19px 18px 0}.sidebar .brand{font-size:22px}.sidebar-caption,.sidebar-network,.sidebar-bottom{display:none}.sidebar nav{flex-direction:row;gap:3px;margin-top:20px}.nav-item{font-size:9px;padding:11px 8px;justify-content:center;gap:4px;flex:1;white-space:nowrap;border-radius:7px 7px 0 0}.nav-item>span:first-child{font-size:14px;width:14px}.nav-mark{display:none}.admin-shell{margin:0}.admin-header{height:54px;padding:0 18px}.breadcrumb{font-size:10px}.header-actions .btn{font-size:9px}.admin-main{padding:24px 16px 0}.page-heading{margin-bottom:22px}h1{font-size:29px}.page-heading p{font-size:11px}.page-heading .eyebrow{font-size:8px}.stats-row{gap:10px;margin-bottom:18px}.stat-card{padding:14px 15px}.stat-number{font-size:30px}.panel{padding:19px}.panel h2{font-size:16px}.panel .tag{font-size:7px}.panel-description{font-size:10px}.workspace-grid{gap:18px}.form-meta{margin-bottom:17px}.campus-map{min-height:245px}.map-zone .zone-label strong{font-size:8px}.building{width:32px;height:30px}.map-legend{font-size:7px;gap:12px}.zone-summary{gap:8px}.zone-item>.tag{font-size:6px;padding:3px}.coverage-footnote{font-size:7px}.transport-grid{gap:12px}.transport-card{padding:15px}.transport-card h3{font-size:13px}.transport-description{font-size:9px;min-height:50px}.transport-stats{gap:14px}.transport-stats strong{font-size:16px}.transport-stats small{font-size:7px}.ble-controls{flex-direction:column;gap:0}.coverage-toggle{font-size:10px;flex:1}.beacon-row .mono{font-size:8px}.info-banner{padding:16px}.info-banner p{font-size:10px}.connect-title{font-size:29px}.setup-list{font-size:11px}.admin-footer{font-size:6px}.admin-footer .mono{font-size:6px}.activity-row{gap:9px}.activity-metrics{font-size:9px}.activity-row strong{font-size:10px}.activity-row small{font-size:8px}.student-header{padding:18px 20px}.student-main{padding:25px 20px}.welcome-row h1{font-size:35px}.round-shield{width:65px;height:65px;font-size:38px}.student-tools{gap:9px;align-items:end}.student-tools .btn{font-size:9px;padding:9px 8px;gap:5px}.student-tools select{font-size:10px;padding:11px 7px}.calm-card{padding:25px;min-height:260px}.calm-card h2{font-size:28px;max-width:78%}.calm-card p{max-width:75%;font-size:10px}.calm-icon{right:22px;top:70px;width:60px;height:60px;font-size:35px}.calm-card:after{right:-160px}.connection-card{padding:16px}.connection-card p{font-size:9px}.emergency-modal{padding:23px;min-height:560px}.emergency-modal h2{font-size:35px}.emergency-modal>p{font-size:16px}.modal{padding:27px}.modal h2{font-size:28px}.student-footer{font-size:6px}.onboarding h2{font-size:32px}}
@media(prefers-reduced-motion:reduce){*{animation:none!important;transition:none!important;scroll-behavior:auto!important}}

/* Hostel controls use the shared zone table and work without Ctrl-click. */
.hostel-target>span{font-size:11px;font-weight:600;color:var(--ink)}
.hostel-picker{display:grid;gap:6px;margin-top:8px}
.hostel-picker .hostel-choice{display:flex;align-items:center;gap:9px;margin:0;padding:9px 10px;border:1px solid var(--line);border-radius:7px;background:#fcfdf9;cursor:pointer;font-size:11px}
.hostel-choice input{width:16px;height:16px;min-height:0;margin:0;padding:0;accent-color:#507b43;flex-shrink:0}
.hostel-choice:has(input:checked){border-color:#749c63;background:#edf5e5}
.map-zone .zone-label strong{white-space:normal;max-width:100px;line-height:1.3}
.coverage-toggle{flex-wrap:wrap;gap:6px}

/* Student safety dashboard and real incident operations. */
.student-header{background:#fbfcf8;color:#163d32;border-bottom:1px solid #e0e6dc;padding:18px max(24px,calc((100vw - 1180px)/2));flex-wrap:wrap}.student-header .quiet-link{color:#466255}.student-nav{display:flex;gap:24px;margin-left:auto;font-size:12px}.student-nav a{padding:8px 0;border-bottom:2px solid transparent}.student-nav a:hover{border-color:#418a61}.student-main{max-width:1180px}.welcome-row h1{font-size:43px;max-width:760px}.welcome-row p{font-size:18px;color:#3d6d51}.round-shield{display:none}.student-tools{max-width:480px}.calm-card{min-height:205px;background:linear-gradient(110deg,#e9f2e1,#dfeedd);margin-bottom:22px}.calm-card h2{font-size:31px}.calm-card p{font-size:13px}.safety-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:17px;margin:22px 0}.safety-card{padding:23px;background:#fff;border:1px solid #e0e6da;border-radius:13px;box-shadow:0 3px 14px #18382305}.safety-card h2{margin:10px 0 15px;font-size:20px}.safety-card>p{font-size:12px;color:#5d7668;margin-bottom:12px}.safety-card .btn{margin-top:9px}.safety-card .eyebrow{font-size:8px;color:#718570}.contact-row{display:flex;align-items:center;justify-content:space-between;gap:10px;background:#f5f8f1;border-radius:7px;padding:12px;margin:9px 0}.contact-row strong,.contact-row small{display:block;font-size:11px}.contact-row small{color:#738173;margin-top:3px}.assembly-map svg{display:block;width:100%;height:auto;border-radius:10px}.student-action-bar{display:flex;justify-content:space-between;align-items:center;gap:20px;background:#fff;border:1px solid #e0e6da;border-radius:10px;padding:18px;font-size:11px;color:#607867}.active-safety{border:1px solid #efb5b5;border-left:5px solid #d84b45;background:#fff2f2;border-radius:10px;padding:25px;margin:22px 0}.active-alert-top{display:flex;align-items:center;justify-content:space-between;gap:12px}.active-safety h2{font-size:29px;margin:15px 0}.active-safety>p{font-size:16px;white-space:pre-wrap;overflow-wrap:anywhere}.active-safety>small{display:block;margin-top:12px;color:#607466}.evacuation-steps{padding-left:24px;font-size:13px;line-height:1.7}.evacuation-steps li{background:#ffffffa8;padding:9px;margin:7px 0}.safety-actions{display:flex;gap:12px}.safety-actions .btn{flex:1}.request-help{color:#b92c2c;background:white;border-color:#d84b45}.help-button{background:transparent;border:1px solid #ffdbcd;color:white;width:100%;margin-top:10px}.student-body .emergency-modal,.student-body .emergency-modal.urgent,.student-body .emergency-modal.advisory{background:#fff5f4;color:#172d27;border:2px solid #df6f65}.student-body .emergency-modal .eyebrow,.student-body .emergency-modal .micro{color:#9b3c32}.student-body .emergency-modal .safe-button{background:#165a40;color:white}.student-body .emergency-modal .help-button{background:white;color:#b73e35;border-color:#ca4b42}.student-body .emergency-modal .text-button{color:#754f48}.student-body .emergency-icon{color:#d8433a;border-color:#f0b7af;background:#ffe4df}.incident-workspace{margin-bottom:25px}.incident-tools{display:flex;gap:10px;align-items:center}.incident-tools select{font-size:11px;max-width:360px;margin:0}.incident-card{background:linear-gradient(135deg,#102630,#0c1e2b);border:1px solid #193a42;border-radius:15px;color:#edf6f8;padding:29px}.incident-card h2{font-size:30px;margin:22px 0 12px;letter-spacing:-.025em}.incident-card h3{margin:22px 0 15px}.incident-message{font-size:16px;white-space:pre-wrap;overflow-wrap:anywhere}.incident-assembly{font-size:11px;color:#9ac9bd;margin-top:10px}.incident-status{display:flex;justify-content:space-between;align-items:center;font-size:13px}.incident-severity{background:#d85757;color:white;padding:8px 13px;border-radius:8px;font-weight:700}.incident-metrics{display:grid;grid-template-columns:1fr 1fr;background:#ffffff06;border:1px solid #ffffff0b;border-radius:11px;margin:24px 0 15px;padding:20px;gap:25px}.incident-metric{display:grid;gap:8px}.incident-metric>span{font-size:12px;color:#c4d8de}.incident-metric>strong{font-size:34px}.incident-metric>small{color:#98b1b9}.incident-metric>progress{appearance:none;width:100%;height:10px;border:0;border-radius:8px;overflow:hidden;background:#2b4250}.incident-metric>progress::-webkit-progress-bar{background:#2b4250}.incident-metric>progress::-webkit-progress-value{background:#87e2ef}.incident-metric.safety>progress::-webkit-progress-value{background:#6edc98}.incident-metric>b{text-align:right;font-size:11px;color:#c3d8dd}.incident-timeline{list-style:none;padding:0 0 0 16px;margin:0 0 20px 8px;border-left:2px solid #35515d}.incident-timeline li{display:flex;gap:20px;padding:10px 0;position:relative;font-size:12px}.incident-timeline li:before{content:'';width:10px;height:10px;border-radius:50%;background:#7de0c5;position:absolute;left:-22px;top:15px;border:2px solid #28494d}.incident-timeline time{color:#a3bcc7;white-space:nowrap}.incident-timeline p{color:#9cb1bc;font-size:11px;margin-top:5px}.incident-actions{display:flex;gap:15px}.incident-actions .btn{flex:1}.view-response{background:#83dfee;color:#0c2932}.incident-clear{border-color:#83dfee;color:#a0e9ed;background:transparent}.help-count{color:#ffc2a6;font-size:13px}.staff-notify{display:block;background:transparent;color:#b3d6cb;border:0;font-size:11px;padding:15px 0 8px;text-decoration:underline}.incident-footnote{display:block;color:#87a5ad;font-size:10px}.response-modal{width:720px;max-height:85vh;overflow:auto}.response-row{display:grid;grid-template-columns:1fr 1fr auto;gap:7px;padding:15px;border:1px solid #e1e7dc;border-radius:8px;margin-top:10px;font-size:12px}.response-row p{grid-column:1/-1;white-space:pre-wrap;overflow-wrap:anywhere}.response-row.requires-help{background:#fff0ed;border-color:#e7aaa2}.report-row{display:flex;align-items:center;gap:18px;justify-content:space-between;padding:15px 0;border-bottom:1px solid #e5eadd}.report-row p{font-size:12px;white-space:pre-wrap;overflow-wrap:anywhere}.report-row small{color:#7b8c80;font-size:10px}
@media(max-width:900px){.safety-grid{grid-template-columns:1fr 1fr}.safety-card:last-child{grid-column:1/-1}.assembly-map{max-width:400px;margin:auto}.incident-tools{flex-wrap:wrap}.welcome-row h1{font-size:34px}}
@media(max-width:600px){.student-nav{width:100%;order:3;justify-content:space-between;gap:10px;font-size:10px}.student-main{padding:22px 16px}.welcome-row h1{font-size:29px}.welcome-row p{font-size:15px}.safety-grid{grid-template-columns:1fr}.safety-card:last-child{grid-column:auto}.calm-card h2{font-size:25px}.active-safety{padding:19px}.active-safety h2{font-size:24px}.active-alert-top{flex-wrap:wrap}.safety-actions,.student-action-bar{flex-direction:column}.safety-actions .btn{width:100%}.incident-card{padding:20px}.incident-card h2{font-size:24px}.incident-tools select{max-width:100%}.incident-metrics{padding:15px;gap:15px}.incident-metric>strong{font-size:28px}.incident-timeline li{gap:12px}.incident-actions{flex-direction:column}.response-row{grid-template-columns:1fr auto}.incident-workspace>.panel-heading{align-items:flex-start;gap:12px;flex-direction:column}}
.student-header .brand{color:#163d32}.student-header .brand small{color:#658574}.student-header .student-nav{flex-direction:row;align-items:center}
.incident-card.incident-compact{display:flex;align-items:center;justify-content:space-between;gap:14px;padding:14px 18px;background:#f4f8ef;border:1px solid #dce6d4;border-radius:9px;color:#254b38}.incident-compact strong{display:block;font-size:13px}.incident-compact small{display:block;margin-top:5px;font-size:11px;color:#657a6b}.incident-compact .btn{flex-shrink:0}@media(max-width:600px){.incident-card.incident-compact{flex-wrap:wrap}}
```

### campusguard/public/sw.js

<!-- CAMPUSGUARD_SOURCE {"path": "public/sw.js", "encoding": "utf-8", "sha256": "bbfdffd8ec44246887998d31b882886e6bc09edf8027741a6d89ac070ac90149"} -->
```javascript
const CACHE='campusguard-safety-response-v4';
const ASSETS=['/','/index.html','/styles.css','/manifest.webmanifest','/icons/shield.svg','/icons/icon-192.png','/icons/icon-512.png','/js/client.js','/js/ui.js','/js/connection.js','/js/peer-relay.js','/js/alarm.js','/shared/protocol.js','/shared/campus.js','/src/zones.js'];
self.addEventListener('install',event=>{event.waitUntil(caches.open(CACHE).then(c=>c.addAll(ASSETS)).then(()=>self.skipWaiting()));});
self.addEventListener('activate',event=>{event.waitUntil(caches.keys().then(keys=>Promise.all(keys.filter(k=>k!==CACHE).map(k=>caches.delete(k)))).then(()=>self.clients.claim()));});
self.addEventListener('fetch',event=>{
  const url=new URL(event.request.url);if(event.request.method!=='GET'||url.origin!==location.origin||url.pathname.startsWith('/api/')||url.pathname==='/ws'||!ASSETS.includes(url.pathname))return;
  event.respondWith(fetch(event.request).then(response=>{if(response.ok){const copy=response.clone();caches.open(CACHE).then(c=>c.put(event.request,copy));}return response;}).catch(()=>caches.match(event.request).then(cached=>cached||Response.error())));
});
```

### campusguard/scripts/ble-smoke.js

<!-- CAMPUSGUARD_SOURCE {"path": "scripts/ble-smoke.js", "encoding": "utf-8", "sha256": "1b9ca6af2345983d1c9c65d7fe53c5b93c3db9a00a441563d1924e8973d49ecb"} -->
```javascript
import { BleRealTransport } from '../src/transports/ble-real.js';
process.env.BLE_ENABLED='true';
const transport=new BleRealTransport();
const wait=ms=>new Promise(r=>setTimeout(r,ms));
async function until(check,label){for(let n=0;n<160;n++){if(check())return;await wait(50);}throw new Error(`${label}: ${transport.health().lastError||transport.health().nativeStatus}`);}
try{
  await until(()=>transport.realAvailable===true,'Bluetooth startup');
  console.log('Windows adapter supports BLE advertising.');
  const alert={v:1,serverId:'de000001',id:'de000001:1',seq:1,type:'drill',severity:1,zone:0,zones:[0],ttl:10,timestamp:new Date().toISOString(),message:'DEMO ONLY: BLE radio smoke test'};
  await transport.send(alert);
  await until(()=>transport.sent>0,'Advertising start');
  const event=transport.events[0];
  console.log(JSON.stringify({nativeStatus:transport.nativeStatus,simulated:event.simulated,zone:event.zone,manufacturerHex:event.manufacturerHex,sent:transport.sent,remoteReceptionVerified:false}));
  console.log('Windows reported advertising started. Confirm physical reception with a separate BLE scanner.');
}finally{await transport.stop();}
```

### campusguard/scripts/check.js

<!-- CAMPUSGUARD_SOURCE {"path": "scripts/check.js", "encoding": "utf-8", "sha256": "311b3b2dcb23ad58b1c898c05d698166fc436843f63027b11bc967b3b62a72d2"} -->
```javascript
import fs from 'node:fs';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
const folders=['src','server','public/js','shared','scripts','bridges','agent','tests'];
let count=0;const entry=spawnSync(process.execPath,['--check','server.js'],{encoding:'utf8'});if(entry.status!==0){console.error(entry.stderr);process.exit(1);}count++; for(const dir of folders)for(const name of fs.readdirSync(dir,{recursive:true})){const file=path.join(dir,name);if(!file.endsWith('.js'))continue;const r=spawnSync(process.execPath,['--check',file],{encoding:'utf8'});if(r.status!==0){console.error(r.stderr);process.exit(1);}count++;}console.log(`${count} JavaScript files passed syntax checks.`);
```

### campusguard/scripts/curl-tests.ps1

<!-- CAMPUSGUARD_SOURCE {"path": "scripts/curl-tests.ps1", "encoding": "utf-8", "sha256": "01d385e13a17bd5b29ff3647c0fdd55bf828229cb1099cdfb1af99b47080b7a3"} -->
```powershell
param([string]$Base = 'http://localhost:8080', [string]$Pin = '404404')
$ErrorActionPreference = 'Stop'
if (-not (Get-Command curl.exe -ErrorAction SilentlyContinue)) { throw 'curl.exe is required (included with Windows 10/11).' }
$scratch = Join-Path ([IO.Path]::GetTempPath()) ('campusguard-curl-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $scratch | Out-Null
$utf8 = New-Object System.Text.UTF8Encoding($false)
$counter = 0
function Invoke-Curl {
    param([string]$Route, [string]$Method = 'GET', [object]$Body = $null,
          [hashtable]$Headers = @{}, [int]$Expected = 200, [switch]$Stream, [switch]$Raw)
    $script:counter++
    $outFile = Join-Path $scratch ('response-' + $script:counter + '.txt')
    $errFile = Join-Path $scratch ('error-' + $script:counter + '.txt')
    $headerFile = Join-Path $scratch ('headers-' + $script:counter + '.txt')
    $callArgs = @('--silent', '--show-error', '--http1.1', '--max-time', $(if ($Stream) { '2' } else { '8' }),
                  '--request', $Method, '--output', $outFile, '--stderr', $errFile, '--dump-header', $headerFile, '--write-out', '%{http_code}')
    foreach ($entry in $Headers.GetEnumerator()) { $callArgs += @('--header', ($entry.Key + ': ' + $entry.Value)) }
    if ($null -ne $Body) {
        $inputFile = Join-Path $scratch ('request-' + $script:counter + '.json')
        [IO.File]::WriteAllText($inputFile, ($Body | ConvertTo-Json -Compress -Depth 10), $utf8)
        $callArgs += @('--header', 'Content-Type: application/json', '--data-binary', ('@' + $inputFile))
    }
    $callArgs += ($Base.TrimEnd('/') + $Route)
    $statusText = & curl.exe @callArgs
    $exitCode = $LASTEXITCODE
    if ($exitCode -ne 0 -and -not ($Stream -and $exitCode -eq 28)) { throw (Get-Content -LiteralPath $errFile -Raw) }
    if ([int]$statusText -ne $Expected) { throw "$Method $Route returned $statusText; expected $Expected. $(Get-Content -LiteralPath $outFile -Raw)" }
    Write-Host ("PASS {0} {1} -> {2}" -f $Method, $Route, $statusText)
    $text = if (Test-Path -LiteralPath $outFile) { [IO.File]::ReadAllText($outFile) } else { '' }
    $script:LastResponseHeaders = [IO.File]::ReadAllText($headerFile)
    if ($Stream -or $Raw) { return $text }
    return ($text | ConvertFrom-Json)
}
try {
    $adminHeaders = @{ 'x-admin-pin' = $Pin }
    $null = Invoke-Curl -Route '/api/config'
    $null = Invoke-Curl -Route '/api/health' -Headers $adminHeaders
    $null = Invoke-Curl -Route '/api/stats' -Headers $adminHeaders
    $zoneStatsBefore = Invoke-Curl -Route '/api/stats/zones' -Headers $adminHeaders
    $null = Invoke-Curl -Route '/api/stats/zones' -Expected 401
    $null = Invoke-Curl -Route '/api/login' -Method POST -Expected 401 -Body @{pin='incorrect-pin'}
    $null = Invoke-Curl -Route '/api/login' -Method POST -Body @{pin=$Pin}
    if ($script:LastResponseHeaders -notmatch '(?im)^Set-Cookie:\s*(cg_admin=[^;\r\n]+)') { throw 'Login did not set an admin session.' }
    $sessionHeaders = @{Cookie=$Matches[1]}
    $null = Invoke-Curl -Route '/api/history' -Headers $sessionHeaders
    $qrUrl = [uri]::EscapeDataString($Base.TrimEnd('/') + '/')
    $qr = Invoke-Curl -Route ("/api/qr?url=" + $qrUrl) -Headers $sessionHeaders -Raw
    if (-not $qr.Contains('<svg')) { throw 'Local QR endpoint did not return SVG.' }
    $null = Invoke-Curl -Route '/api/transports' -Headers $adminHeaders
    $client = Invoke-Curl -Route '/api/clients' -Method POST -Expected 201 -Body @{ name = 'Curl test client'; zone = 1 }
    $clientHeaders = @{ Authorization = ('Bearer ' + $client.id + '.' + $client.token) }
    $null = Invoke-Curl -Route '/api/clients/me' -Method PATCH -Headers $clientHeaders -Body @{zone=1}
    $second = Invoke-Curl -Route '/api/clients' -Method POST -Expected 201 -Body @{ name = 'Curl second hostel'; zone = 2 }
    $secondHeaders = @{ Authorization = ('Bearer ' + $second.id + '.' + $second.token) }
    $targeted = Invoke-Curl -Route '/api/alerts' -Method POST -Headers $adminHeaders -Expected 201 -Body @{
        type = 'drill'; severity = 1; zones = @(1,2); ttl = 60; message = 'DEMO ONLY: two-hostel route test.'
    }
    if (($targeted.alert.zones -join ',') -ne '1,2' -or $null -ne $targeted.alert.zone) { throw 'Multi-hostel envelope mismatch.' }
    $since = $targeted.alert.seq - 1
    $matching = Invoke-Curl -Route "/api/alerts?zone=2&since=$since" -Headers $secondHeaders
    if (-not (@($matching.alerts.id) -contains $targeted.alert.id)) { throw 'Selected hostel missed the multi-target alert.' }
    $other = Invoke-Curl -Route "/api/alerts?zone=3&since=$since" -Headers $adminHeaders
    if (@($other.alerts.id) -contains $targeted.alert.id) { throw 'Alert leaked to another hostel.' }
    $null = Invoke-Curl -Route '/api/alerts?zone=2' -Headers $clientHeaders -Expected 403
    $null = Invoke-Curl -Route '/api/stream?zone=2' -Headers $clientHeaders -Expected 403
    $null = Invoke-Curl -Route '/api/sync?zone=2' -Headers $clientHeaders -Expected 403
    $null = Invoke-Curl -Route '/api/alerts' -Method POST -Headers $adminHeaders -Expected 400 -Body @{
        type = 'drill'; severity = 1; zones = @(0,1); message = 'Invalid ALL combination'
    }
    $null = Invoke-Curl -Route '/api/alerts' -Method POST -Headers $adminHeaders -Expected 400 -Body @{
        type = 'drill'; severity = 1; zones = @(5); message = 'Unknown hostel'
    }
    $single = Invoke-Curl -Route '/api/alerts' -Method POST -Headers $adminHeaders -Expected 201 -Body @{
        type = 'drill'; severity = 1; zones = @(3); ttl = 60; message = 'DEMO ONLY: Ramanujan route test.'
    }
    $excluded = Invoke-Curl -Route "/api/alerts?zone=1&since=$($single.alert.seq - 1)" -Headers $clientHeaders
    if (@($excluded.alerts.id) -contains $single.alert.id) { throw 'Ramanujan alert leaked to Visvesvaraya.' }
    $created = Invoke-Curl -Route '/api/alerts' -Method POST -Headers $adminHeaders -Expected 201 -Body @{
        type = 'drill'; severity = 1; zones = @(0); ttl = 60; message = 'DEMO ONLY: curl endpoint test.'
    }
    $null = Invoke-Curl -Route '/api/alerts?since=0' -Headers $clientHeaders
    $latest = Invoke-Curl -Route '/api/latest' -Headers $clientHeaders
    if ($latest.alert.id -ne $created.alert.id) { throw 'Latest alert mismatch.' }
    $null = Invoke-Curl -Route '/api/receipts' -Method POST -Headers $clientHeaders -Body @{ alertId = $created.alert.id; via = 'polling' }
    $ack = @{ alertId = $created.alert.id; status = 'safe' }
    $null = Invoke-Curl -Route '/api/ack' -Method POST -Headers $clientHeaders -Body $ack
    $duplicate = Invoke-Curl -Route '/api/acks' -Method POST -Headers $clientHeaders -Body $ack
    if (-not $duplicate.duplicate) { throw 'Duplicate acknowledgement was counted twice.' }
    $zoneStats = Invoke-Curl -Route '/api/stats/zones' -Headers $adminHeaders
    if (@($zoneStats.zones).Count -ne 4) { throw 'Expected four hostel statistics rows.' }
    $before = @($zoneStatsBefore.zones | Where-Object { $_.id -eq 1 })[0]
    $after = @($zoneStats.zones | Where-Object { $_.id -eq 1 })[0]
    # Other connected students may acknowledge concurrently during a live demo.
    if ($after.acksReceived -lt ($before.acksReceived + 1)) { throw 'Per-hostel acknowledgement total did not increase.' }
    if ($after.connectedClients -lt 1 -or -not $after.lastAlertTime) { throw 'Hostel presence/time statistics missing.' }
    $null = Invoke-Curl -Route '/api/sync?zone=1&after=0&transport=polling' -Headers $clientHeaders
    foreach ($route in @('/api/stream?zone=1&since=0', '/api/events?zone=1&since=0')) {
        $events = Invoke-Curl -Route $route -Headers $clientHeaders -Stream
        if (-not $events.Contains('event: ready') -or -not $events.Contains($created.alert.id)) { throw "SSE replay missing from $route" }
    }
    $null = Invoke-Curl -Route '/api/alerts' -Method POST -Headers @{ 'x-admin-pin' = 'incorrect-pin' } -Expected 401 -Body @{ type = 'drill' }
    $fallback = Invoke-Curl -Route '/api/transports/ble' -Method POST -Headers $adminHeaders -Body @{ mode = 'native' }
    if ($fallback.mode -ne 'native' -or $fallback.automaticSimulationFallback) { throw 'Expected real Bluetooth without simulated delivery.' }
    $null = Invoke-Curl -Route '/api/transports/ble' -Method POST -Headers $adminHeaders -Expected 400 -Body @{ mode = 'simulator' }
    $upgrade = @{ Connection = 'Upgrade'; Upgrade = 'websocket'; 'Sec-WebSocket-Version' = '13'; 'Sec-WebSocket-Key' = 'dGhlIHNhbXBsZSBub25jZQ==' }
    $null = Invoke-Curl -Route '/ws' -Headers $upgrade -Expected 101 -Stream
    $campus = Invoke-Curl -Route '/api/campus'
    $null = Invoke-Curl -Route '/api/campus' -Method PATCH -Headers $adminHeaders -Body @{medicalPhone=$campus.medicalPhone;securityPhone=$campus.securityPhone}
    $null = Invoke-Curl -Route '/api/incidents' -Headers $adminHeaders
    $incidentId = $created.alert.id
    $null = Invoke-Curl -Route "/api/incidents/$incidentId" -Headers $adminHeaders
    $null = Invoke-Curl -Route '/api/responses' -Method POST -Headers $clientHeaders -Body @{alertId=$incidentId;status='needs-help';message='DEMO ONLY: no assistance needed.'}
    $null = Invoke-Curl -Route '/api/responses' -Method POST -Headers $clientHeaders -Body @{alertId=$incidentId;status='safe'}
    $null = Invoke-Curl -Route '/api/responses/me' -Headers $clientHeaders
    $null = Invoke-Curl -Route "/api/incidents/$incidentId/notify" -Method POST -Headers $adminHeaders -Body @{note='DEMO ONLY: manual confirmation endpoint check.'}
    $report = Invoke-Curl -Route '/api/reports' -Method POST -Headers $clientHeaders -Expected 201 -Body @{requestId=[Guid]::NewGuid().ToString();category='other';message='DEMO ONLY: report endpoint check.'}
    $null = Invoke-Curl -Route '/api/reports' -Headers $adminHeaders
    $null = Invoke-Curl -Route ("/api/reports/" + $report.report.id) -Method PATCH -Headers $adminHeaders -Body @{status='resolved'}
    $null = Invoke-Curl -Route "/api/incidents/$incidentId/all-clear" -Method POST -Headers $adminHeaders -Expected 201 -Body @{message='DEMO ONLY: curl checks complete.'}
    $null = Invoke-Curl -Route '/api/logout' -Method POST -Headers $sessionHeaders -Body @{}
    $null = Invoke-Curl -Route '/api/history' -Headers $sessionHeaders -Expected 401
    Write-Host 'All curl checks passed. Full authenticated WebSocket replay/signaling is tested by npm test.'
} finally {
    Get-ChildItem -LiteralPath $scratch -File | ForEach-Object { Remove-Item -LiteralPath $_.FullName }
    Remove-Item -LiteralPath $scratch
}
```

### campusguard/scripts/running.js

<!-- CAMPUSGUARD_SOURCE {"path": "scripts/running.js", "encoding": "utf-8", "sha256": "2768731aa21f54f340d8eaa8b285e20b6beb27a7be1c4dc9a8cac9b03403420f"} -->
```javascript
import '../server/config.js';
try {const r=await fetch(`http://127.0.0.1:${process.env.HTTP_PORT||8080}/api/config`,{signal:AbortSignal.timeout(1200)});const c=await r.json();process.exit(r.ok&&c.v===1&&Array.isArray(c.zones)?0:1);}catch{process.exit(1);}
```

### campusguard/scripts/seed.js

<!-- CAMPUSGUARD_SOURCE {"path": "scripts/seed.js", "encoding": "utf-8", "sha256": "304855bcff9f3bd271898b11e5678501346e228e655343f21b8da039f67af331"} -->
```javascript
import '../server/config.js';
import { hostels, zoneName } from '../src/zones.js';
const base=process.env.SERVER_URL||`http://127.0.0.1:${process.env.HTTP_PORT||8080}`;
const types=['fire','earthquake','medical','security'];
const samples=[...hostels.map((hostel,i)=>({type:types[i],zones:[hostel.id],
  message:`DEMO ONLY: safety exercise at ${hostel.name} hostel. Follow the announced safe route.`})),
  {type:'drill',zones:[0],message:'DEMO ONLY: campus-wide communication drill. Please acknowledge.'}];
for(const sample of samples){
  const response=await fetch(`${base}/api/alerts`,{method:'POST',headers:{'Content-Type':'application/json','x-admin-pin':process.env.ADMIN_PIN||'404404'},body:JSON.stringify({...sample,severity:1,ttl:60}),signal:AbortSignal.timeout(5000)});
  const body=await response.json();if(!response.ok)throw new Error(body.error||`HTTP ${response.status}`);
  console.log(`Seeded #${body.alert.seq}: ${body.alert.type}, ${body.alert.zones.map(zoneName).join(', ')}`);
}
```

### campusguard/scripts/setup-https.js

<!-- CAMPUSGUARD_SOURCE {"path": "scripts/setup-https.js", "encoding": "utf-8", "sha256": "e56831fc360816a15c26436bf38949c855fbe3709bdde2a96866608d23b9ae6e"} -->
```javascript
import forge from 'node-forge';
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..'),dir=path.join(root,'certs');fs.mkdirSync(dir,{recursive:true});
const pki=forge.pki,rootKeyPath=path.join(dir,'root-key.pem'),rootCertPath=path.join(dir,'root-ca.pem');
const serial=()=> '01'+crypto.randomBytes(15).toString('hex');
let caKey,caCert;
if(fs.existsSync(rootKeyPath)&&fs.existsSync(rootCertPath)){caKey=pki.privateKeyFromPem(fs.readFileSync(rootKeyPath,'utf8'));caCert=pki.certificateFromPem(fs.readFileSync(rootCertPath,'utf8'));}
else{
  console.log('Creating this installation’s local certificate authority…');const keys=pki.rsa.generateKeyPair(2048);caKey=keys.privateKey;caCert=pki.createCertificate();caCert.publicKey=keys.publicKey;caCert.serialNumber=serial();caCert.validity.notBefore=new Date(Date.now()-86400000);caCert.validity.notAfter=new Date(Date.now()+365*86400000);const attrs=[{name:'commonName',value:'CampusGuard Local Demo CA'},{name:'organizationName',value:'CampusGuard 404 Found'}];caCert.setSubject(attrs);caCert.setIssuer(attrs);caCert.setExtensions([{name:'basicConstraints',cA:true,critical:true},{name:'keyUsage',keyCertSign:true,cRLSign:true,critical:true},{name:'subjectKeyIdentifier'}]);caCert.sign(caKey,forge.md.sha256.create());fs.writeFileSync(rootKeyPath,pki.privateKeyToPem(caKey),{mode:0o600});fs.writeFileSync(rootCertPath,pki.certificateToPem(caCert));
}
const ips=[...new Set(['127.0.0.1',...Object.values(os.networkInterfaces()).flat().filter(x=>x.family==='IPv4'&&!x.internal).map(x=>x.address)])];
const keys=pki.rsa.generateKeyPair(2048),cert=pki.createCertificate();cert.publicKey=keys.publicKey;cert.serialNumber=serial();cert.validity.notBefore=new Date(Date.now()-86400000);cert.validity.notAfter=new Date(Date.now()+90*86400000);cert.setSubject([{name:'commonName',value:'CampusGuard Local Server'}]);cert.setIssuer(caCert.subject.attributes);cert.setExtensions([{name:'basicConstraints',cA:false,critical:true},{name:'keyUsage',digitalSignature:true,keyEncipherment:true,critical:true},{name:'extKeyUsage',serverAuth:true},{name:'subjectAltName',altNames:[{type:2,value:'localhost'},{type:2,value:'campus-alert.local'},...ips.map(ip=>({type:7,ip}))]}]);cert.sign(caKey,forge.md.sha256.create());fs.writeFileSync(path.join(dir,'server-key.pem'),pki.privateKeyToPem(keys.privateKey),{mode:0o600});fs.writeFileSync(path.join(dir,'server-cert.pem'),pki.certificateToPem(cert)+pki.certificateToPem(caCert));fs.writeFileSync(path.join(dir,'root-ca.cer'),Buffer.from(forge.asn1.toDer(pki.certificateToAsn1(caCert)).getBytes(),'binary'));
console.log(`HTTPS certificates ready for localhost and ${ips.join(', ')}.\nTrust certs/root-ca.cer on each demo device for PWA/peer relay.\nNo trust settings were changed automatically. Re-run after changing Wi-Fi/hotspot IP.`);
```

### campusguard/scripts/stop.ps1

<!-- CAMPUSGUARD_SOURCE {"path": "scripts/stop.ps1", "encoding": "utf-8", "sha256": "7f595a999f74142df9fc24eb1d3ab518168d787c3f19fe9a88c61a1fdca6e963"} -->
```powershell
$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent $PSScriptRoot
$pidFile = Join-Path $project 'data\server.pid'
if (-not (Test-Path -LiteralPath $pidFile)) { Write-Host 'No CampusGuard server record found.'; exit 0 }
$record = Get-Content -LiteralPath $pidFile -Raw | ConvertFrom-Json
$target = Get-CimInstance Win32_Process -Filter ('ProcessId = ' + [int]$record.pid)
if (-not $target) { Write-Host 'CampusGuard is already stopped.'; exit 0 }
$started = [DateTimeOffset]::FromUnixTimeMilliseconds([long]$record.started).UtcDateTime
if ($target.ExecutablePath -ne $record.executable -or [Math]::Abs(($target.CreationDate.ToUniversalTime() - $started).TotalSeconds) -gt 15 -or $target.CommandLine -notmatch '(?:server[\\/]index|server)\.js') {
    Write-Host 'The saved process record does not match a running CampusGuard server. Nothing was stopped.'
    exit 1
}
Stop-Process -Id ([int]$record.pid)
Write-Host 'CampusGuard stopped. Alert history remains saved.'
```

### campusguard/server.js

<!-- CAMPUSGUARD_SOURCE {"path": "server.js", "encoding": "utf-8", "sha256": "76514f5cdbc48393c41c0a31965921a73619083404f62d248d38967ab559ef9d"} -->
```javascript
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import { fileURLToPath } from 'node:url';
import { ROOT } from './src/config.js';
import { createCampusGuard } from './src/app.js';
export { createCampusGuard } from './src/app.js';

export function startServer(){
  const instance=createCampusGuard();let stopping=false;
  const stop=()=>{if(stopping)return;stopping=true;instance.close().finally(()=>process.exit(0));};
  for(const signal of ['SIGINT','SIGTERM'])process.on(signal,stop);
  instance.listen().then(()=>{
    fs.writeFileSync(path.join(ROOT,'data/server.pid'),JSON.stringify({pid:process.pid,executable:process.execPath,started:Date.now()}));
    const port=instance.servers.http.address().port,securePort=instance.servers.https?.address().port;
    console.log('\n  CAMPUSGUARD • Local emergency alerts + real BLE\n');
    console.log(`  Admin: http://localhost:${port}/admin.html`);
    console.log(`  Student: http://localhost:${port}/`);
    console.log(`  mDNS: http://campus-alert.local:${port}/ (LAN IP is the fallback)`);
    console.log(`  Admin PIN: ${process.env.ADMIN_PIN||'404404'}`);
    for(const network of Object.values(os.networkInterfaces()).flat().filter(n=>n.family==='IPv4'&&!n.internal)){
      console.log(`  Phone: http://${network.address}:${port}/`);
      if(securePort)console.log(`  PWA: https://${network.address}:${securePort}/`);
    }
    console.log('  BLE: real Windows advertiser; inspect Transport lab for hardware status.');
    console.log('  Phone browsers receive over LAN. Native BLE receivers/scanners use the radio packets.');
    console.log('\n  Keep this window open. Ctrl+C stops the server.\n');
  }).catch(error=>{console.error(error.message);instance.close().finally(()=>process.exit(1));});
  return instance;
}
if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url))startServer();
```

### campusguard/server/additions.js

<!-- CAMPUSGUARD_SOURCE {"path": "server/additions.js", "encoding": "utf-8", "sha256": "856f67e6b89bc3d294546231e9e2c597ecd448fc115c3635b5873e3c91f26713"} -->
```javascript
export { clientZone, createDiscovery, installReadRoutes, replaySnapshot } from '../src/additions.js';
```

### campusguard/server/config.js

<!-- CAMPUSGUARD_SOURCE {"path": "server/config.js", "encoding": "utf-8", "sha256": "56cede2c56495eabe52fd423bcd4b115d54b602a8da29a9ce2c3aabbedf85c81"} -->
```javascript
export { ROOT, zones } from '../src/config.js';
```

### campusguard/server/index.js

<!-- CAMPUSGUARD_SOURCE {"path": "server/index.js", "encoding": "utf-8", "sha256": "4cbb37192daca21cb8fdef87822e402f2eeb9e074787aa8e599dbd1207671c25"} -->
```javascript
// Legacy entry point and import compatibility.
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { startServer } from '../server.js';
export { createCampusGuard } from '../src/app.js';
if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url))startServer();
```

### campusguard/server/store.js

<!-- CAMPUSGUARD_SOURCE {"path": "server/store.js", "encoding": "utf-8", "sha256": "b18dfc2a9798f8ef3bd71799bae180c852184524e5f14011b2cf756609c00793"} -->
```javascript
export { Store } from '../src/store.js';
```

### campusguard/server/transports/index.js

<!-- CAMPUSGUARD_SOURCE {"path": "server/transports/index.js", "encoding": "utf-8", "sha256": "611d7cb670af7f1490b823bbd3ba1e00f5d165f2b56ccc828e888446ac5af034"} -->
```javascript
// Backward-compatible import path; the implementation lives in src.
export { TransportManager } from '../../src/transportManager.js';
```

### campusguard/shared/campus.js

<!-- CAMPUSGUARD_SOURCE {"path": "shared/campus.js", "encoding": "utf-8", "sha256": "2cf61804e2a4f9d3e9bc3e18d0f7b1d68455a222493cf9021ef506caa21b4935"} -->
```javascript
export const ASSEMBLY_POINTS=Object.freeze([
  Object.freeze({id:'football',name:'Football ground'}),
  Object.freeze({id:'cricket',name:'Cricket ground'})
]);
export const isAssemblyPoint=id=>ASSEMBLY_POINTS.some(point=>point.id===id);
export const assemblyName=id=>ASSEMBLY_POINTS.find(point=>point.id===id)?.name||'Assembly point not configured';
```

### campusguard/shared/protocol.js

<!-- CAMPUSGUARD_SOURCE {"path": "shared/protocol.js", "encoding": "utf-8", "sha256": "56c592c0d5f6726af77fa241fcccfe4b20ecff680e397c8081a0a69d628afcec"} -->
```javascript
import { alertZones, isZone, normalizeZones } from '../src/zones.js';
import { isAssemblyPoint } from './campus.js';
export { alertZones } from '../src/zones.js';
export const TYPES = ['fire','earthquake','medical','security','drill','all-clear'];
export const TYPE_CODES = Object.fromEntries(TYPES.map((t,i)=>[t,i+1]));
export const INSTRUCTIONS = {
  fire: 'Leave the building by the nearest safe exit. Do not use lifts.',
  earthquake: 'Drop, cover and hold on. Stay clear of windows.',
  medical: 'Keep the area clear and contact campus medical support.',
  security: 'Move to a safe location and follow campus security instructions.',
  drill: 'This is a practice drill. Follow the announced evacuation route.',
  'all-clear': 'The earlier emergency has been cleared. Follow campus guidance.'
};
export function expiresAt(a) { return Date.parse(a.timestamp) + a.ttl * 1000; }
export function validAlert(a) {
  return a && a.v === 1 && /^[a-f0-9]{8}$/.test(a.serverId) && Number.isInteger(a.seq) && a.seq > 0 && a.seq <= 0xffffffff &&
    a.id === `${a.serverId}:${a.seq}` && TYPES.includes(a.type) && [1,2,3].includes(a.severity) &&
    validTargets(a) && (a.assemblyPoint===undefined||isAssemblyPoint(a.assemblyPoint)) && Number.isInteger(a.ttl) && a.ttl >= 1 && a.ttl <= 3600 &&
    Number.isFinite(Date.parse(a.timestamp)) && typeof a.message === 'string' && new TextEncoder().encode(a.message).length <= 400;
}
function validTargets(a) {
  if (a.zones === undefined) return isZone(a.zone);
  try {
    const ids = normalizeZones(a.zones);
    return JSON.stringify(ids) === JSON.stringify(a.zones) && a.zone === (ids.length === 1 ? ids[0] : null);
  } catch { return false; }
}
export function applicable(a, zone) { const ids=alertZones(a); return ids.includes(0) || ids.includes(zone); }
// Preserve verification of saved single-zone alerts; new envelopes sign every target.
export function signingPayload(a) { const fields=[a.v,a.id,a.serverId,a.seq,a.timestamp,a.ttl,a.type,a.severity,a.zone,a.message];if(a.zones!==undefined)fields.push(a.zones);if(a.assemblyPoint!==undefined)fields.push(a.assemblyPoint);return JSON.stringify(fields); }
export function cleared(a, markers, zone) { return a.seq <= Math.max(markers[0] || 0, markers[zone] || 0); }
// CRC-16/CCITT-FALSE: poly 0x1021, init 0xffff, refin/refout false, xorout 0.
export function crc16(bytes) {
  let crc = 0xffff;
  for (const b of bytes) { crc ^= b << 8; for(let i=0;i<8;i++) crc = ((crc << 1) ^ ((crc & 0x8000) ? 0x1021 : 0)) & 0xffff; }
  return crc;
}
export function encodeBeacon(a) {
  const targets=normalizeZones(alertZones(a));
  if(targets.length!==1)throw new Error('Encode one BLE summary per target hostel');
  const b = new Uint8Array(21), d = new DataView(b.buffer);
  d.setUint8(0,1); d.setUint32(1,parseInt(a.serverId,16),true); d.setUint32(5,a.seq,true);
  d.setUint8(9,TYPE_CODES[a.type]); d.setUint8(10,a.severity); d.setUint8(11,targets[0]); d.setUint8(12,0);
  d.setUint16(13,a.ttl,true); d.setUint32(15,Math.floor(Date.parse(a.timestamp)/1000),true);
  d.setUint16(19,crc16(b.subarray(0,19)),true); return b;
}
export function decodeBeacon(bytes) {
  if(bytes.length!==21) throw new Error('Beacon must be 21 bytes');
  const d = new DataView(bytes.buffer,bytes.byteOffset,bytes.byteLength);
  if(d.getUint8(0)!==1 || d.getUint16(19,true)!==crc16(bytes.subarray(0,19))) throw new Error('Invalid beacon version or checksum');
  const serverId=d.getUint32(1,true).toString(16).padStart(8,'0'), seq=d.getUint32(5,true), type=TYPES[d.getUint8(9)-1];
  if(d.getUint8(12)!==0)throw new Error('Invalid reserved zone byte');
  const a={v:1,id:`${serverId}:${seq}`,serverId,seq,type,severity:d.getUint8(10),zone:d.getUint8(11),ttl:d.getUint16(13,true),timestamp:new Date(d.getUint32(15,true)*1000).toISOString(),message:INSTRUCTIONS[type]||''};
  if(!validAlert(a)) throw new Error('Invalid beacon fields'); return a;
}
// Software-only 31-byte legacy advertising-data envelope: 3-byte Flags AD,
// 28-byte Manufacturer AD (4-byte header + 21-byte core + 3 reserved zeros).
export function encodeAdvertisement(a) {
  const bytes=new Uint8Array(31);
  bytes.set([2,1,6,27,255,255,255]);
  bytes.set(encodeBeacon(a),7);
  return bytes;
}
export function decodeAdvertisement(bytes) {
  if(bytes.length!==31||![2,1,6,27,255,255,255].every((v,i)=>bytes[i]===v)||bytes.subarray(28).some(v=>v!==0))throw new Error('Invalid 31-byte simulated advertisement');
  return decodeBeacon(bytes.subarray(7,28));
}
```

### campusguard/src/additions.js

<!-- CAMPUSGUARD_SOURCE {"path": "src/additions.js", "encoding": "utf-8", "sha256": "67cd67c95ecb20708570fe049de54a48d9ab64cd0a17b620cfacd974ebe31fc1"} -->
```javascript
import { Bonjour } from 'bonjour-service';
import { alertZones, applicable, cleared, expiresAt } from '../shared/protocol.js';
import { zones } from './config.js';
import { isHostel } from './zones.js';

export function clientZone(req) {
  if (req.query.zone === undefined) return req.client.zone;
  const requested=Number(req.query.zone);
  if (!isHostel(requested)) throw Object.assign(new Error('Select a valid hostel zone'),{status:400});
  if (requested!==req.client.zone) throw Object.assign(new Error('Zone must match client registration; update /api/clients/me to move hostels'),{status:403});
  return requested;
}

export function replaySnapshot(store, zone, since = 0, serverId) {
  since = Number(since);
  if (!Number.isSafeInteger(since) || since < 0) {
    throw Object.assign(new Error('since/lastSeq must be a non-negative integer'), { status: 400 });
  }
  const s = store.state;
  const reset = Boolean(serverId && serverId !== s.serverId) || since > s.seq;
  const targets = zone == null ? zones.filter(z => z.id).map(z => z.id) : [zone];
  const matches = a => targets.some(z => applicable(a, z));
  const alerts = s.alerts.filter(a => {
    if (!matches(a) || (!reset && a.seq <= since)) return false;
    if (a.type === 'all-clear') return since === 0 || reset ? alertZones(a).some(id=>a.seq===s.clearMarkers[id]) : true;
    return expiresAt(a) > Date.now() && targets.some(z => applicable(a, z) && !cleared(a, s.clearMarkers, z));
  });
  return { serverId: s.serverId, serverTime: new Date().toISOString(), cursor: s.seq,
    lastSeq: s.seq, reset, clearMarkers: s.clearMarkers, alerts };
}

export function installReadRoutes(app, { store, manager, admin, client, onlineClients }) {
  const read = (req, res, next) => req.headers.authorization ? client(req, res, next) : admin(req, res, next);
  const target = req => {
    if (req.headers.authorization) return clientZone(req);
    if (req.query.zone === undefined || req.query.zone === 'ALL' || req.query.zone === '0') return null;
    const zone = Number(req.query.zone);
    if (!zones.some(z => z.id === zone)) throw Object.assign(new Error('Unknown zone'), { status: 400 });
    return zone;
  };
  app.get('/api/alerts', read, (req, res) => res.json(replaySnapshot(store, target(req), req.query.since ?? 0, req.query.serverId)));
  app.get('/api/latest', read, (req, res) => {
    const zone = target(req);
    const alert = [...store.state.alerts].reverse().find(a => zone == null || applicable(a, zone)) || null;
    res.json({ serverId: store.state.serverId, lastSeq: store.state.seq, alert,
      expired: alert ? expiresAt(alert) <= Date.now() : false, clearMarkers: store.state.clearMarkers });
  });
  app.get('/api/transports', admin, (req, res) => res.json({ transports: manager.health() }));
  app.get('/api/stats/zones', admin, (req,res)=>{
    const online=onlineClients();
    res.json({zones:zones.filter(z=>z.id).map(z=>({id:z.id,name:z.name,
      connectedClients:online.filter(c=>c.zone===z.id).length,
      acksReceived:Object.values(store.state.acksByZone[z.id]||{}).reduce((n,entries)=>n+Object.keys(entries).length,0),
      lastAlertTime:[...store.state.alerts].reverse().find(a=>applicable(a,z.id))?.timestamp||null
    }))});
  });
  app.get('/api/stats', admin, (req, res) => {
    const s = store.state;
    const count = table => Object.values(table).reduce((n, entries) => n + Object.keys(entries).length, 0);
    res.json({ serverId: s.serverId, lastSeq: s.seq, alerts: s.alerts.length,
      registeredClients: Object.keys(s.clients).length, receipts: count(s.receipts), acknowledgements: count(s.acks),
      byAlert: s.alerts.map(a => ({ id: a.id, seq: a.seq,
        receipts: Object.keys(s.receipts[a.id] || {}).length, safe: Object.keys(s.acks[a.id] || {}).length })),
      transports: manager.health() });
  });
}

export function createDiscovery() {
  let instance, service, timer;
  const state = { host: 'campus-alert.local', status: 'not started', lastError: null, port: null };
  const fail = error => { state.status = 'unavailable'; state.lastError = String(error.message || error); };
  return {
    state,
    start(port) {
      state.port = port;
      if (process.env.MDNS_ENABLED === 'false') { state.status = 'disabled'; return; }
      try {
        state.status = 'starting';
        instance = new Bonjour({}, fail);
        service = instance.publish({ name: 'CampusGuard', host: state.host, type: 'http', port,
          disableIPv6: true, txt: { path: '/', app: 'campusguard', version: '1' } });
        service.on('up', () => { state.status = 'advertised'; state.lastError = null; clearTimeout(timer); });
        service.on('error', fail);
        timer = setTimeout(() => { if (state.status === 'starting') fail('mDNS announcement unavailable or name conflict; use the LAN IP'); }, 5000);
        timer.unref();
      } catch (error) { fail(error); }
    },
    async close() {
      clearTimeout(timer);
      if (!instance) return;
      await new Promise(resolve => {
        let finished = false;
        const finish = () => { if (finished) return; finished = true; clearTimeout(timeout); instance.destroy(); state.status = 'stopped'; resolve(); };
        const timeout = setTimeout(finish, 500);
        try { instance.unpublishAll(finish); } catch { finish(); }
      });
    }
  };
}
```

### campusguard/src/alerts.js

<!-- CAMPUSGUARD_SOURCE {"path": "src/alerts.js", "encoding": "utf-8", "sha256": "07ac2fc67088161d4e9869e4099b2257e8fbf5f3f432d0a4e1592ec486d5cc8b"} -->
```javascript
import crypto from 'node:crypto';
import { TYPES, signingPayload, applicable } from '../shared/protocol.js';
import { normalizeZones } from './zones.js';
import { isAssemblyPoint } from '../shared/campus.js';

export function createAlert(body,store,privateKey){
  const {type}=body,severity=Number(body.severity),ttl=Number(body.ttl??300),message=String(body.message||'').trim();
  const targets=normalizeZones(body.zones!==undefined?body.zones:(Array.isArray(body.zone)?body.zone:[body.zone==='ALL'?0:body.zone]));
  const assemblyPoint=body.assemblyPoint||'football';if(!isAssemblyPoint(assemblyPoint))throw new Error('Choose Football ground or Cricket ground');
  if(!TYPES.includes(type)||![1,2,3].includes(severity)||!Number.isInteger(ttl)||ttl<1||ttl>3600||!message||Buffer.byteLength(message)>400)throw new Error('Check type, severity, zones, TTL (1–3600s), and message (1–400 UTF-8 bytes)');
  if(store.state.seq>=0xffffffff)throw Object.assign(new Error('Sequence space exhausted; archive this installation before rotating its identity'),{status:409});
  const seq=store.state.seq+1,alert={v:1,id:`${store.state.serverId}:${seq}`,serverId:store.state.serverId,seq,
    timestamp:new Date(Math.floor(Date.now()/1000)*1000).toISOString(),ttl,type,severity,zone:targets.length===1?targets[0]:null,zones:targets,assemblyPoint,message};
  alert.signature=crypto.sign('sha256',Buffer.from(signingPayload(alert)),{key:privateKey,dsaEncoding:'ieee-p1363'}).toString('base64url');
  if(Buffer.byteLength(JSON.stringify({v:1,kind:'alert',transport:'udp-multicast',zone:targets,alert}))>1200)throw new Error('Encoded alert is too large for one UDP packet. Shorten the message.');
  store.transaction(state=>{state.seq=seq;state.alerts.push(alert);if(type==='all-clear')for(const zone of targets)state.clearMarkers[zone]=seq;
    if(type!=='all-clear'&&state.incidents)state.incidents[alert.id]={audience:Object.values(state.clients).filter(c=>applicable(alert,c.zone)).map(c=>({id:c.id,name:c.name,zone:c.zone})),basis:'registered recipients',events:[]};
  });
  return alert;
}

export function recordReceipt(store,manager,client,body,safeAck=false){
  const alert=store.state.alerts.find(a=>a.id===body.alertId);if(!alert||!applicable(alert,client.zone))throw new Error('Alert is not available for this client');
  const via=['ws','sse','polling','webrtc','udp-broadcast','udp-multicast'].includes(body.via)?body.via:'polling';
  const table=safeAck?store.state.acks:store.state.receipts;
  if(table[alert.id]?.[client.id])return {recorded:true,duplicate:true};
  store.transaction(state=>{
    const target=safeAck?state.acks:state.receipts;target[alert.id]??={};
    target[alert.id][client.id]={timestamp:new Date().toISOString(),via,zone:client.zone};
    if(safeAck){state.acksByZone[client.zone]??={};state.acksByZone[client.zone][alert.id]??={};state.acksByZone[client.zone][alert.id][client.id]={...target[alert.id][client.id]};}
  });
  if(!safeAck)manager.get(via).confirmed++;
  return {recorded:true,duplicate:false};
}
```

### campusguard/src/app.js

<!-- CAMPUSGUARD_SOURCE {"path": "src/app.js", "encoding": "utf-8", "sha256": "3bae954a5ceca39b304efbdf342ff4fc1e29b5f661588bd8b2b16d1e236a7ae1"} -->
```javascript
import express from 'express';
import http from 'node:http';
import https from 'node:https';
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import crypto from 'node:crypto';
import { WebSocketServer } from 'ws';
import QRCode from 'qrcode';
import { ROOT, zones } from './config.js';
import { Store } from './store.js';
import { TransportManager } from './transportManager.js';
import { TYPES, alertZones, applicable, cleared, expiresAt, signingPayload } from '../shared/protocol.js';
import { clientZone, createDiscovery, installReadRoutes, replaySnapshot } from './additions.js';

import { isHostel } from './zones.js';
import { hash, safe, createClientRegistry } from './clients.js';
import { createAlert, recordReceipt as saveReceipt } from './alerts.js';
import { createIncidentService } from './incidents.js';

export function createCampusGuard({dataFile=process.env.DATA_FILE||path.join(ROOT,'data/alerts.json'),bleTransport}={}){
  const migrate=dataFile===path.join(ROOT,'data/alerts.json')&&!fs.existsSync(dataFile)&&fs.existsSync(path.join(ROOT,'data/state.json'));
  const store=new Store(dataFile), manager=new TransportManager({bleTransport}), sessions=new Map(), attempts=new Map(), seenClients=new Map();
  if(migrate){const legacy=new Store(path.join(ROOT,'data/state.json'));store.transaction(s=>Object.assign(s,legacy.state));}
  // Preserve old signatures/history; materialize a durable hostel index for old acks once.
  if(!store.state.acksByZone)store.transaction(s=>{
    s.acksByZone=Object.fromEntries(zones.filter(z=>z.id).map(z=>[z.id,{}]));
    for(const [alertId,entries] of Object.entries(s.acks))for(const [clientId,ack] of Object.entries(entries)){
      const zone=ack.zone??s.clients[clientId]?.zone;
      if(isHostel(zone)){ack.zone=zone;s.acksByZone[zone][alertId]??={};s.acksByZone[zone][alertId][clientId]={...ack};}
    }
  });
  const operations=createIncidentService(store);
  const discovery=createDiscovery(), dashboards=new Set();
  manager.get('ble').onBeacon=beacon=>{for(const socket of dashboards)if(socket.readyState===1&&socket.bufferedAmount<262144)socket.send(JSON.stringify({kind:'ble-beacon',zone:beacon.zone,beacon}));};
  if(!store.state.signingKey){const key=crypto.generateKeyPairSync('ec',{namedCurve:'prime256v1'}).privateKey.export({type:'pkcs8',format:'pem'});store.transaction(s=>{s.signingKey=key;});}
  const privateKey=crypto.createPrivateKey(store.state.signingKey), publicKey=crypto.createPublicKey(privateKey).export({format:'jwk'});
  const app=express();app.disable('x-powered-by');app.use(express.json({limit:'8kb'}));
  app.use((req,res,next)=>{res.set('X-Content-Type-Options','nosniff');res.set('Referrer-Policy','no-referrer');res.set('X-Frame-Options','DENY');res.set('Content-Security-Policy',"default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self' ws: wss:; media-src 'self'; object-src 'none'; base-uri 'self'; frame-ancestors 'none'");if(req.path.startsWith('/api/'))res.set('Cache-Control','no-store');
    if(['POST','PUT','PATCH','DELETE'].includes(req.method)&&req.get('Origin')&&req.get('Origin')!==`${req.protocol}://${req.get('host')}`)return res.status(403).json({error:'Origin rejected'});next();});
  const registry=createClientRegistry(store);
  const sessionValid=req=>{const token=(req.headers.cookie||'').split(';').map(s=>s.trim()).find(s=>s.startsWith('cg_admin='))?.slice(9);return Boolean(token&&sessions.has(token)&&sessions.get(token)>Date.now());};
  const pinValid=pin=>typeof pin==='string'&&safe(hash(pin),hash(process.env.ADMIN_PIN||'404404'));
  const pinFailures=new Map();
  const admin=(req,res,next)=>{const key=req.ip,old=pinFailures.get(key);if(old&&Date.now()-old.start<60000&&old.count>=10)return res.status(429).json({error:'Too many PIN attempts. Wait one minute.'});if(sessionValid(req)||pinValid(req.get('x-admin-pin'))){pinFailures.delete(key);return next();}if(req.get('x-admin-pin')){const item=old&&Date.now()-old.start<60000?old:{count:0,start:Date.now()};item.count++;pinFailures.set(key,item);}return res.status(401).json({error:'Admin session or x-admin-pin required'});};
  const getClient=(id,token)=>registry.authenticate(id,token);
  const client=(req,res,next)=>{const raw=(req.headers.authorization||'').replace(/^Bearer /,'');const [id,token]=raw.split('.');req.client=getClient(id,token);if(!req.client)return res.status(401).json({error:'Client registration required'});seenClients.set(id,Date.now());next();};
  const activeFor=zone=>store.state.alerts.filter(a=>a.type!=='all-clear'&&expiresAt(a)>Date.now()&&applicable(a,zone)&&!cleared(a,store.state.clearMarkers,zone));
  const liveAlerts=()=>store.state.alerts.filter(a=>expiresAt(a)>Date.now()&&(a.type==='all-clear'||zones.some(z=>z.id&&applicable(a,z.id)&&!cleared(a,store.state.clearMarkers,z.id))));
  const countFor=(table,id)=>Object.keys(table[id]||{}).length;
  const sendRoster=()=>{const peers=[...manager.get('ws').clients].map(s=>({id:s.client.id,zone:s.client.zone}));for(const s of manager.get('ws').clients)if(s.readyState===1)s.send(JSON.stringify({kind:'peers',enabled:manager.get('webrtc').enabled,peers:manager.get('webrtc').enabled?peers:[]}));};
  const recordReceipt=(client,body,safeAck=false)=>{if(safeAck)return operations.respond(client,{...body,status:'safe'});const result=saveReceipt(store,manager,client,body);operations.delivery(body.alertId,result.duplicate);return result;};
  app.get('/api/config',(req,res)=>res.json({v:1,zones,serverId:store.state.serverId,serverTime:new Date().toISOString(),publicKey,https:!!servers.https,types:TYPES,campus:operations.campus()}));
  app.post('/api/login',(req,res)=>{
    const key=req.ip, prev=attempts.get(key);const entry=prev&&Date.now()-prev.start<60000?prev:{count:0,start:Date.now()};attempts.set(key,entry);
    if(++entry.count>10)return res.status(429).json({error:'Too many attempts. Wait one minute.'});
    if(!safe(hash(String(req.body.pin||'')),hash(process.env.ADMIN_PIN||'404404')))return res.status(401).json({error:'Incorrect PIN'});
    const token=crypto.randomBytes(32).toString('hex');sessions.set(token,Date.now()+8*3600000);attempts.delete(key);
    res.setHeader('Set-Cookie',`cg_admin=${token}; HttpOnly; SameSite=Strict; Path=/; Max-Age=28800${req.secure?'; Secure':''}`);res.json({ok:true});
  });
  app.post('/api/logout',admin,(req,res)=>{const token=(req.headers.cookie||'').split(';').map(s=>s.trim()).find(s=>s.startsWith('cg_admin='))?.slice(9);sessions.delete(token);res.setHeader('Set-Cookie',`cg_admin=; HttpOnly; SameSite=Strict; Path=/; Max-Age=0${req.secure?'; Secure':''}`);res.json({ok:true});});
  app.post('/api/clients',(req,res)=>{try{res.status(201).json(registry.register(req.body));}catch(error){res.status(400).json({error:error.message});}});
  app.patch('/api/clients/me',client,(req,res)=>{let zone;try{zone=registry.move(req.client.id,req.body.zone);}catch(error){return res.status(400).json({error:error.message});}for(const s of [...manager.get('ws').clients])if(s.client.id===req.client.id){s.client=store.state.clients[req.client.id];manager.get('ws').join(s,zone);}for(const c of manager.get('sse').clients)if(c.id===req.client.id)c.res.end();sendRoster();res.json({ok:true,zone});});
  app.post('/api/alerts',admin,async(req,res)=>{
    let a;try{a=createAlert(req.body,store,privateKey);}catch(error){return res.status(error.status||400).json({error:error.message});}
    const results=await manager.send(a);res.status(201).json({alert:a,transports:results.map((r,i)=>({name:manager.transports.filter(t=>t.enabled)[i]?.name,result:r.status==='fulfilled'?r.value:{accepted:false}}))});
  });
  app.get('/api/sync',client,(req,res)=>{
    if(!manager.get('polling').enabled&&req.query.transport==='polling')return res.status(503).json({error:'Polling disabled for demo'});
    const zone=clientZone(req);
    const after=Number(req.query.after||req.query.since||0);if(!Number.isSafeInteger(after)||after<0)return res.status(400).json({error:'Invalid cursor'});
    const reset=req.query.serverId!==store.state.serverId||after>store.state.seq;
    const snapshot=replaySnapshot(store,zone,after,req.query.serverId);
    const alerts=(reset||after===0)?snapshot.alerts.filter(a=>a.type!=='all-clear'):snapshot.alerts;
    if(req.query.transport==='polling')manager.get('polling').ok(alerts.length);
    res.json({serverId:store.state.serverId,serverTime:new Date().toISOString(),cursor:store.state.seq,alerts,clearMarkers:store.state.clearMarkers,reset,zone:req.client.zone,campus:operations.campus(),responses:operations.myResponses(req.client)});
  });
  app.post('/api/receipts',client,(req,res)=>{res.json(recordReceipt(req.client,req.body));});
  app.post(['/api/acks','/api/ack'],client,(req,res)=>{if(req.body.status!=='safe')return res.status(400).json({error:'Acknowledgement status must be safe'});res.json(recordReceipt(req.client,req.body,true));});
  app.get(['/api/events','/api/stream'],client,(req,res)=>{
    const zone=clientZone(req),t=manager.get('sse');if(!t.enabled)return res.status(503).json({error:'SSE disabled for demo'});
    const eventId=req.get('Last-Event-ID'),parts=eventId?.split(':');
    const snapshot=replaySnapshot(store,zone,req.query.since??(parts?parts.at(-1):0),req.query.serverId??(parts?.length===2?parts[0]:undefined));
    res.writeHead(200,{'Content-Type':'text/event-stream','Cache-Control':'no-cache, no-transform','Connection':'keep-alive','X-Accel-Buffering':'no'});res.flushHeaders();res.write(`event: ready\ndata: ${JSON.stringify({...snapshot,alerts:undefined})}\n\n`);
    for(const alert of snapshot.alerts)res.write(`id: ${alert.id}\nevent: alert\ndata: ${JSON.stringify({v:1,kind:'alert',alert,replay:true})}\n\n`);
    const c={res,zone,id:req.client.id};t.clients.add(c);const timer=setInterval(()=>{seenClients.set(c.id,Date.now());res.write('event: heartbeat\ndata: {}\n\n');},5000);req.on('close',()=>{clearInterval(timer);t.clients.delete(c);});
  });
  app.get('/api/campus',(req,res)=>res.json(operations.campus()));
  app.patch('/api/campus',admin,(req,res)=>res.json(operations.updateCampus(req.body)));
  app.get('/api/incidents',admin,(req,res)=>res.json({incidents:operations.list()}));
  app.get('/api/incidents/:id',admin,(req,res)=>res.json(operations.detail(req.params.id)));
  app.get('/api/responses/me',client,(req,res)=>res.json({responses:operations.myResponses(req.client)}));
  app.post('/api/responses',client,(req,res)=>res.json(operations.respond(req.client,req.body)));
  app.post('/api/incidents/:id/notify',admin,(req,res)=>res.json(operations.notify(req.params.id,req.body.note)));
  app.post('/api/incidents/:id/all-clear',admin,async(req,res)=>{
    try{const incident=operations.detail(req.params.id);if(!incident.active)return res.status(409).json({error:'Incident is no longer active'});
      const alert=createAlert({type:'all-clear',severity:1,zones:alertZones(incident.alert),assemblyPoint:incident.assemblyPoint,ttl:300,message:String(req.body.message||'All-clear issued by campus administration.')},store,privateKey);
      await manager.send(alert);res.status(201).json({alert});
    }catch(error){res.status(error.status||400).json({error:error.message});}
  });
  app.post('/api/reports',client,(req,res)=>{const result=operations.report(req.client,req.body);res.status(result.duplicate?200:201).json(result);});
  app.get('/api/reports',admin,(req,res)=>res.json({reports:operations.reports()}));
  app.patch('/api/reports/:id',admin,(req,res)=>{if(req.body.status!=='resolved')return res.status(400).json({error:'Status must be resolved'});res.json({report:operations.closeReport(req.params.id)});});
  app.get('/api/history',admin,(req,res)=>{res.json({alerts:[...store.state.alerts].reverse().slice(0,200).map(a=>({...a,safe:countFor(store.state.acks,a.id),received:countFor(store.state.receipts,a.id),expired:expiresAt(a)<=Date.now()}))});});
  const ipAddresses=()=>Object.values(os.networkInterfaces()).flat().filter(x=>x.family==='IPv4'&&!x.internal).map(x=>x.address);
  const onlineClients=()=>[...seenClients].filter(([id,t])=>Date.now()-t<16000&&store.state.clients[id]).map(([id])=>store.state.clients[id]);
  app.get('/api/health',admin,(req,res)=>{
    const alerts=store.state.alerts,active=alerts.filter(a=>a.type!=='all-clear'&&zones.some(z=>z.id&&activeFor(z.id).some(x=>x.id===a.id)));
    const urls=ipAddresses().map(ip=>({ip,http:`http://${ip}:${servers.http?.address()?.port||process.env.HTTP_PORT||8080}`,https:servers.https?`https://${ip}:${servers.https.address()?.port||process.env.HTTPS_PORT||8443}`:null}));
    const online=onlineClients();
    res.json({serverId:store.state.serverId,uptime:process.uptime(),serverTime:new Date().toISOString(),cursor:store.state.seq,mdns:discovery.state,transports:manager.health(),counts:{alerts:alerts.length,active:active.length,registered:Object.keys(store.state.clients).length,connected:online.length,safe:Object.values(store.state.acks).reduce((n,x)=>n+Object.keys(x).length,0)},zoneStatus:zones.filter(z=>z.id).map(z=>({...z,connected:online.filter(c=>c.zone===z.id).length,active:activeFor(z.id).length})),urls,zones,publicKey});
  });
  app.post('/api/transports/:name',admin,(req,res)=>{
    const t=manager.get(req.params.name);if(!t)return res.status(404).json({error:'Unknown transport'});
    if(typeof req.body.enabled==='boolean'){if(t.setEnabled)t.setEnabled(req.body.enabled);else t.enabled=req.body.enabled;}
    if(t.name==='ble'){
      if(req.body.mode!==undefined)t.setMode(req.body.mode);
      if(typeof req.body.loss==='number'&&req.body.loss>=0&&req.body.loss<=1)t.loss=req.body.loss;
      if(req.body.coverage&&typeof req.body.coverage==='object')for(const z of zones.filter(z=>z.id))if(typeof req.body.coverage[z.id]==='boolean')t.coverage[z.id]=req.body.coverage[z.id];
    }
    if(t.name==='webrtc')sendRoster();res.json(t.health());
  });
  app.get('/api/qr',admin,async(req,res)=>{
    const url=String(req.query.url||'');let u;try{u=new URL(url);}catch{return res.status(400).end();}
    if(!['http:','https:'].includes(u.protocol)||!['localhost','127.0.0.1',...ipAddresses()].includes(u.hostname))return res.status(400).end();
    res.type('svg').send(await QRCode.toString(url,{type:'svg',margin:1,color:{dark:'#112b36',light:'#ffffff'}}));
  });
  app.get('/root-ca.cer',(req,res)=>{const f=path.join(ROOT,'certs/root-ca.cer');if(!fs.existsSync(f))return res.status(404).send('Run npm run setup:https first.');res.download(f,'CampusGuard-Local-CA.cer');});
  installReadRoutes(app,{store,manager,admin,client,onlineClients});
  app.get('/src/zones.js',(req,res)=>res.sendFile(path.join(ROOT,'src/zones.js'),{headers:{'Cache-Control':'no-cache'}}));
  app.use('/shared',express.static(path.join(ROOT,'shared'),{setHeaders:res=>res.set('Cache-Control','no-cache')}));app.use(express.static(path.join(ROOT,'public'),{etag:true,setHeaders:res=>res.set('Cache-Control','no-cache')}));
  app.use((err,req,res,next)=>{console.error('Request error:',err.message);if(!res.headersSent)res.status(err.status||400).json({error:err.message||'Request failed'});});
  const wss=new WebSocketServer({noServer:true,maxPayload:8192});
  wss.on('connection',(socket,request)=>{
    let authorized=false;const authTimeout=setTimeout(()=>socket.close(1008,'Registration required'),5000);socket.isAlive=true;
    socket.on('pong',()=>{socket.isAlive=true;});socket.on('error',()=>{});
    socket.on('message',raw=>{try{
      const m=JSON.parse(raw.toString());
      if(!authorized){if(m.kind!=='hello')return socket.close(1008,'Hello required');if(m.role==='dashboard'){if(!sessionValid(request))return socket.close(1008,'Admin session required');authorized=true;socket.dashboard=true;clearTimeout(authTimeout);dashboards.add(socket);socket.send(JSON.stringify({kind:'ready',role:'dashboard',softwareOnly:false,bleMode:manager.get('ble').mode}));return;}const c=getClient(m.clientId,m.token);if(!c)return socket.close(1008,'Invalid client');if(m.zone!==undefined&&(!isHostel(m.zone)||m.zone!==c.zone))return socket.close(1008,'Zone must match client registration');const snapshot=replaySnapshot(store,c.zone,m.lastSeq??m.cursor??0,m.serverId);authorized=true;clearTimeout(authTimeout);socket.client=c;seenClients.set(c.id,Date.now());manager.get('ws').join(socket,c.zone);socket.send(JSON.stringify({kind:'ready',...snapshot,alerts:undefined}));for(const alert of snapshot.alerts)socket.send(JSON.stringify({v:1,kind:'alert',alert,replay:true}));socket.send(JSON.stringify({kind:'replay-complete',cursor:snapshot.cursor,clearMarkers:snapshot.clearMarkers,reset:snapshot.reset}));sendRoster();return;}
      if(socket.dashboard){if(!sessionValid(request)){socket.close(1008,'Admin session expired');return;}if(m.kind==='ping')socket.send(JSON.stringify({kind:'pong'}));return;}
      if(m.kind==='receipt'||m.kind==='ack'){if(m.kind==='ack'&&m.status!=='safe')throw new Error('Acknowledgement status must be safe');const result=recordReceipt(socket.client,m,m.kind==='ack');socket.send(JSON.stringify({kind:m.kind+'-recorded',alertId:m.alertId,...result}));}
      if(m.kind==='ping')socket.send(JSON.stringify({kind:'pong',timestamp:Date.now()}));
      if(m.kind==='signal'&&manager.get('webrtc').enabled){if(!['offer','answer','ice'].includes(m.type))return;for(const peer of manager.get('ws').clients)if(peer.client.id===m.to&&peer!==socket)peer.send(JSON.stringify({kind:'signal',from:socket.client.id,type:m.type,data:m.data}));}
      if(m.kind==='peer-health')manager.get('webrtc').peers.set(socket.client.id,{time:Date.now(),connections:Math.max(0,Math.min(6,Number(m.connections)||0)),forwards:Math.max(0,Number(m.forwards)||0)});
    }catch(e){socket.send(JSON.stringify({kind:'error',error:e.message}));}});
    socket.on('close',()=>{clearTimeout(authTimeout);dashboards.delete(socket);manager.get('ws').leave(socket);if(socket.client)manager.get('webrtc').peers.delete(socket.client.id);sendRoster();});
  });
  function upgrade(req,socket,head){if(req.url!=='/ws'||!manager.get('ws').enabled){socket.end('HTTP/1.1 503 Service Unavailable\r\n\r\n');return;}if(req.headers.origin&&req.headers.origin!==`${req.socket.encrypted?'https':'http'}://${req.headers.host}`){socket.destroy();return;}wss.handleUpgrade(req,socket,head,s=>wss.emit('connection',s,req));}
  const servers={};
  const heartbeat=setInterval(()=>{for(const s of wss.clients){if(!s.isAlive){s.terminate();continue;}s.isAlive=false;s.ping();if(s.readyState===1&&s.client){seenClients.set(s.client.id,Date.now());s.send(JSON.stringify({kind:'heartbeat',serverTime:new Date().toISOString()}));}}},5000);
  const repeat=setInterval(()=>{for(const a of liveAlerts())manager.send(a,{repeat:true});},5000);
  const cleanup=setInterval(()=>{for(const [k,v]of sessions)if(v<Date.now())sessions.delete(k);for(const [k,v]of attempts)if(Date.now()-v.start>60000)attempts.delete(k);for(const [k,v]of pinFailures)if(Date.now()-v.start>60000)pinFailures.delete(k);},60000);
  return {app,store,manager,operations,servers,wss,async listen({httpPort=Number(process.env.HTTP_PORT||8080),httpsPort=Number(process.env.HTTPS_PORT||8443),host='0.0.0.0',tls=true}={}){
    servers.http=http.createServer(app);servers.http.on('upgrade',upgrade);await new Promise((resolve,reject)=>{servers.http.once('error',reject);servers.http.listen(httpPort,host,resolve);});
    if(tls&&fs.existsSync(path.join(ROOT,'certs/server-key.pem'))){servers.https=https.createServer({key:fs.readFileSync(path.join(ROOT,'certs/server-key.pem')),cert:fs.readFileSync(path.join(ROOT,'certs/server-cert.pem'))},app);servers.https.on('upgrade',upgrade);await new Promise((resolve,reject)=>{servers.https.once('error',reject);servers.https.listen(httpsPort,host,resolve);});}
    discovery.start(servers.http.address().port);return servers;
  },async close(){clearInterval(heartbeat);clearInterval(repeat);clearInterval(cleanup);for(const s of wss.clients)s.terminate();for(const c of manager.get('sse').clients)c.res.end();await manager.stop();wss.close();await discovery.close();await Promise.all(Object.values(servers).map(s=>new Promise(r=>{s.close(r);s.closeAllConnections?.();})));}};
}

```

### campusguard/src/clients.js

<!-- CAMPUSGUARD_SOURCE {"path": "src/clients.js", "encoding": "utf-8", "sha256": "1cbc782033183be3997d4a7e585d802d23fc9971969631e1cd093d109752b1d4"} -->
```javascript
import crypto from 'node:crypto';
import { isHostel } from './zones.js';
export const hash=value=>crypto.createHash('sha256').update(value).digest('hex');
export const safe=(a,b)=>typeof a==='string'&&typeof b==='string'&&a.length===b.length&&crypto.timingSafeEqual(Buffer.from(a),Buffer.from(b));
export function createClientRegistry(store){
  return {
    authenticate(id,token){const client=store.state.clients[id];return client&&safe(client.tokenHash,hash(token||''))?client:null;},
    register({zone,name}){
      zone=Number(zone);if(!isHostel(zone))throw new Error('Select a hostel');
      const client={id:crypto.randomUUID(),zone,name:String(name||'Student').trim().slice(0,40),createdAt:new Date().toISOString()};
      const token=crypto.randomBytes(24).toString('hex');
      store.transaction(state=>{state.clients[client.id]={...client,tokenHash:hash(token)};});
      return {...client,token};
    },
    move(id,zone){zone=Number(zone);if(!isHostel(zone))throw new Error('Unknown hostel');store.transaction(state=>{state.clients[id].zone=zone;});return zone;}
  };
}
```

### campusguard/src/config.js

<!-- CAMPUSGUARD_SOURCE {"path": "src/config.js", "encoding": "utf-8", "sha256": "b2eec1a65185e50e77bf32cce5264b75e55b15a136a1bcfd83ba466bdc7a7277"} -->
```javascript
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
export const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const envPath=path.join(ROOT,'.env');
if(!fs.existsSync(envPath)) fs.writeFileSync(envPath, 'ADMIN_PIN=404404\nHTTP_PORT=8080\nHTTPS_PORT=8443\nMDNS_ENABLED=true\nUDP_ENABLED=true\nBLE_ENABLED=true\nBLE_DWELL_MS=1200\n');
for(const line of fs.readFileSync(envPath,'utf8').split(/\r?\n/)) {
  const m=line.match(/^([A-Z_]+)=(.*)$/); if(m && process.env[m[1]]===undefined) process.env[m[1]]=m[2].trim();
}
export { zones } from './zones.js';
```

### campusguard/src/incidents.js

<!-- CAMPUSGUARD_SOURCE {"path": "src/incidents.js", "encoding": "utf-8", "sha256": "b8ad571ac190876067ebfabf92d767543e98839c210ccd8a3fad703525a7ea52"} -->
```javascript
import crypto from 'node:crypto';
import { applicable, cleared, expiresAt, alertZones } from '../shared/protocol.js';
import { hostels, isHostel } from './zones.js';
import { ASSEMBLY_POINTS } from '../shared/campus.js';

export function createIncidentService(store){
  store.transaction(state=>{
    state.incidents??={};state.responses??={};state.reports??=[];
    state.campus??={securityPhone:'',medicalPhone:'8571893627'};
    for(const alert of state.alerts)if(alert.type!=='all-clear'&&!state.incidents[alert.id]){
      state.incidents[alert.id]={audience:Object.values(state.clients).filter(c=>applicable(alert,c.zone)).map(c=>({id:c.id,name:c.name,zone:c.zone})),basis:'registered devices at migration',events:[]};
    }
  });
  const find=id=>{const alert=store.state.alerts.find(a=>a.id===id&&a.type!=='all-clear');if(!alert)throw Object.assign(new Error('Incident not found'),{status:404});return alert;};
  const event=(state,id,kind,description)=>{const entries=state.incidents[id]?.events;if(entries&&!entries.some(item=>item.kind===kind))entries.push({id:crypto.randomUUID(),kind,description,timestamp:new Date().toISOString()});};
  const currentResponses=id=>{
    const result={...store.state.responses[id]};
    for(const [clientId,ack]of Object.entries(store.state.acks[id]||{}))result[clientId]??={...ack,status:'safe',message:''};
    return result;
  };
  function summary(alert){
    const state=store.state,meta=state.incidents[alert.id]||{audience:[],events:[],basis:'registered recipients'},responses=currentResponses(alert.id);
    const roster=new Map(meta.audience.map(c=>[c.id,c]));
    for(const id of new Set([...Object.keys(state.receipts[alert.id]||{}),...Object.keys(responses)])){
      if(!roster.has(id)){const c=state.clients[id];if(c)roster.set(id,{id:c.id,name:c.name,zone:responses[id]?.zone??c.zone});}
    }
    const rows=[...roster.values()].map(c=>({...c,zone:responses[c.id]?.zone??c.zone,delivered:Boolean(state.receipts[alert.id]?.[c.id]),response:responses[c.id]?.status||'awaiting',responseAt:responses[c.id]?.timestamp||null,message:responses[c.id]?.message||''}));
    const delivered=rows.filter(row=>row.delivered).length,safe=rows.filter(row=>row.response==='safe').length,help=rows.filter(row=>row.response==='needs-help').length;
    const active=expiresAt(alert)>Date.now()&&hostels.some(z=>applicable(alert,z.id)&&!cleared(alert,state.clearMarkers,z.id));
    const fullyCleared=hostels.filter(z=>applicable(alert,z.id)).every(z=>cleared(alert,state.clearMarkers,z.id));
    const clear=fullyCleared?[...state.alerts].reverse().find(a=>a.type==='all-clear'&&a.seq>alert.seq&&hostels.some(z=>applicable(alert,z.id)&&applicable(a,z.id))):null;
    const events=[{id:'issued',kind:'issued',timestamp:alert.timestamp,description:'Alert issued to selected hostels'},...meta.events];
    if(clear)events.push({id:clear.id,kind:'all-clear',timestamp:clear.timestamp,description:'All-clear issued'});
    return {alert,active,status:clear?'cleared':active?'active':'expired',assemblyPoint:alert.assemblyPoint||'football',
      audience:rows.length,audienceBasis:meta.basis,delivered,safe,help,
      deliveredPercent:rows.length?Math.round(delivered/rows.length*100):0,
      safePercent:delivered?Math.min(100,Math.round(rows.filter(row=>row.delivered&&row.response==='safe').length/delivered*100)):0,
      notified:meta.events.some(e=>e.kind==='staff-notified'),events:events.sort((a,b)=>Date.parse(a.timestamp)-Date.parse(b.timestamp)),responses:rows};
  }
  return {
    campus(){return {...store.state.campus,assemblyPoints:ASSEMBLY_POINTS};},
    updateCampus(body){
      const next={...store.state.campus};
      for(const key of ['securityPhone','medicalPhone'])if(body[key]!==undefined){
        const value=String(body[key]).trim();if(value&&!/^\+?[0-9 ()-]{5,24}$/.test(value))throw new Error('Contact number must contain phone digits only');next[key]=value;
      }
      store.transaction(state=>{state.campus=next;});return this.campus();
    },
    capture(alert){if(alert.type==='all-clear')return;store.transaction(state=>{
      state.incidents[alert.id]={audience:Object.values(state.clients).filter(c=>applicable(alert,c.zone)).map(c=>({id:c.id,name:c.name,zone:c.zone})),basis:'registered recipients',events:[]};
    });},
    delivery(id,duplicate){if(duplicate||!store.state.incidents[id])return;store.transaction(state=>event(state,id,'delivered','First student device confirmed delivery'));},
    list(){return [...store.state.alerts].reverse().filter(a=>a.type!=='all-clear').map(summary).map(({responses,...item})=>item);},
    detail(id){return summary(find(id));},
    respond(client,body){
      const alert=find(body.alertId);if(!applicable(alert,client.zone))throw new Error('Incident is not available for your hostel');
      if(!['safe','needs-help'].includes(body.status))throw new Error('Response must be safe or needs-help');
      const message=String(body.message||'').trim();if(new TextEncoder().encode(message).length>400)throw new Error('Help message must be at most 400 UTF-8 bytes');
      const old=currentResponses(alert.id)[client.id];if(old?.status===body.status&&(old.message||'')===message)return {recorded:true,duplicate:true,status:old.status};
      const via=['ws','sse','polling','webrtc','udp-broadcast','udp-multicast'].includes(body.via)?body.via:'polling';
      const response={status:body.status,message,zone:client.zone,timestamp:new Date().toISOString(),via};
      store.transaction(state=>{
        state.responses[alert.id]??={};state.responses[alert.id][client.id]=response;
        const prior=state.acks[alert.id]?.[client.id];
        if(prior){delete state.acks[alert.id][client.id];delete state.acksByZone[prior.zone??client.zone]?.[alert.id]?.[client.id];}
        if(body.status==='safe'){
          state.acks[alert.id]??={};state.acks[alert.id][client.id]={timestamp:response.timestamp,via,zone:client.zone};
          state.acksByZone[client.zone]??={};state.acksByZone[client.zone][alert.id]??={};state.acksByZone[client.zone][alert.id][client.id]={...state.acks[alert.id][client.id]};
        }
        event(state,alert.id,'responses','Students started responding');
        if(body.status==='needs-help')event(state,alert.id,'help-requested','A student requested assistance');
      });
      return {recorded:true,duplicate:false,status:response.status};
    },
    notify(id,note){find(id);store.transaction(state=>event(state,id,'staff-notified',String(note||'Admin recorded that hostel staff were notified').trim().slice(0,240)));return this.detail(id);},
    myResponses(client){return Object.fromEntries(store.state.alerts.filter(a=>applicable(a,client.zone)).map(a=>[a.id,currentResponses(a.id)[client.id]]).filter(([,response])=>response));},
    report(client,body){
      if(!isHostel(client.zone))throw new Error('Select a hostel');
      const message=String(body.message||'').trim(),category=String(body.category||'other');
      if(!message||new TextEncoder().encode(message).length>800||!['security','medical','fire','other'].includes(category))throw new Error('Choose a category and enter 1–800 UTF-8 bytes');
      const requestId=String(body.requestId||'');if(!/^[a-zA-Z0-9-]{8,80}$/.test(requestId))throw new Error('A requestId is required');
      const old=store.state.reports.find(r=>r.clientId===client.id&&r.requestId===requestId);if(old)return {report:old,duplicate:true};
      const report={id:crypto.randomUUID(),requestId,clientId:client.id,name:client.name,zone:client.zone,category,message,status:'open',timestamp:new Date().toISOString()};
      store.transaction(state=>state.reports.push(report));return {report,duplicate:false};
    },
    reports(){return [...store.state.reports].reverse();},
    closeReport(id){const report=store.state.reports.find(r=>r.id===id);if(!report)throw Object.assign(new Error('Report not found'),{status:404});store.transaction(()=>{report.status='resolved';report.resolvedAt=new Date().toISOString();});return report;}
  };
}
```

### campusguard/src/store.js

<!-- CAMPUSGUARD_SOURCE {"path": "src/store.js", "encoding": "utf-8", "sha256": "e2f046c706bf481488ff367daa852fb3465aebcf0c69899cde212d392e4f9942"} -->
```javascript
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
export class Store {
  constructor(file) {
    this.file=file; fs.mkdirSync(path.dirname(file),{recursive:true});
    if(fs.existsSync(file)) {
      try { this.state=JSON.parse(fs.readFileSync(file,'utf8')); }
      catch { if(!fs.existsSync(file+'.bak')) throw new Error('State damaged and no backup is available'); this.state=JSON.parse(fs.readFileSync(file+'.bak','utf8')); }
    } else this.state={serverId:crypto.randomBytes(4).toString('hex'),seq:0,alerts:[],clients:{},acks:{},receipts:{},clearMarkers:{}};
    if(!/^[a-f0-9]{8}$/.test(this.state.serverId) || !Array.isArray(this.state.alerts) || !Number.isInteger(this.state.seq)) throw new Error('Invalid state file');
  }
  save() {
    const tmp=this.file+'.tmp', fd=fs.openSync(tmp,'w');
    try { fs.writeFileSync(fd,JSON.stringify(this.state,null,2)); fs.fsyncSync(fd); } finally {fs.closeSync(fd);}
    if(fs.existsSync(this.file)) fs.copyFileSync(this.file,this.file+'.bak');
    fs.renameSync(tmp,this.file);
  }
  transaction(fn) {
    const previous=structuredClone(this.state);
    try { const result=fn(this.state); this.save(); return result; }
    catch(e) { this.state=previous; throw e; }
  }
}
```

### campusguard/src/transportManager.js

<!-- CAMPUSGUARD_SOURCE {"path": "src/transportManager.js", "encoding": "utf-8", "sha256": "54b25b503e5eb326c9a94adab3bb87a1f69024951a3abc82af0c61357cbfcaa7"} -->
```javascript
import { WebSocketTransport } from './transports/ws.js';
import { SseTransport } from './transports/sse.js';
import { PollTransport } from './transports/polling.js';
import { UdpTransport } from './transports/udp.js';
import { MulticastTransport } from './transports/multicast.js';
import { PeerTransport } from './transports/webrtc-signal.js';
import { BleRealTransport } from './transports/ble-real.js';
export class TransportManager {
  constructor({bleTransport}={}){this.transports=[new WebSocketTransport(),new SseTransport(),new PollTransport(),new UdpTransport(false),new MulticastTransport(),new PeerTransport(),bleTransport||new BleRealTransport()];this.inflight=new Set();}
  get(name){return this.transports.find(t=>t.name===name);}
  async send(alert,{repeat=false}={}){
    const selected=this.transports.filter(t=>t.enabled&&(!repeat||['udp-broadcast','udp-multicast','ble'].includes(t.name)));
    return Promise.allSettled(selected.map(async t=>{t.attempts++;let timer;try{return await Promise.race([t.send(alert),new Promise((_,reject)=>{timer=setTimeout(()=>reject(new Error('Transport timeout')),1500);})]);}catch(e){t.fail(e);return {accepted:false,error:e.message};}finally{clearTimeout(timer);}}));
  }
  health(){return this.transports.map(t=>t.health());}
  async stop(){await Promise.allSettled(this.transports.map(async t=>t.stop?.()));}
}
```

### campusguard/src/transports/base.js

<!-- CAMPUSGUARD_SOURCE {"path": "src/transports/base.js", "encoding": "utf-8", "sha256": "1e328bde680e478cee8431951574d8159343545d4cc6d33cd2f32338c4d8756b"} -->
```javascript
export class Transport {
  constructor(name,mode='real') { this.name=name; this.enabled=true; this.mode=mode; this.status='ready'; this.attempts=0; this.errors=0; this.sent=0; this.confirmed=0; this.lastSuccess=null; this.lastError=null; }
  async send() { return {accepted:false}; }
  ok(count=1) {this.sent+=count; this.lastSuccess=new Date().toISOString(); this.lastError=null; if(this.status!=='unsupported') this.status='ready';}
  fail(e) {this.errors++; this.lastError=String(e.message||e); this.status='degraded';}
  health() {return {name:this.name,mode:this.mode,enabled:this.enabled,status:this.enabled?this.status:'disabled',attempts:this.attempts,sent:this.sent,failed:this.errors,confirmed:this.confirmed,errors:this.errors,lastSuccess:this.lastSuccess,lastError:this.lastError};}
}
```

### campusguard/src/transports/ble-real.js

<!-- CAMPUSGUARD_SOURCE {"path": "src/transports/ble-real.js", "encoding": "utf-8", "sha256": "b8e77ee9a70cfe00fce06981e15cd4be74fe5a023762c4ff160e98f73dcb946b"} -->
```javascript
import { spawn } from 'node:child_process';
import path from 'node:path';
import { ROOT } from '../config.js';
import { Transport } from './base.js';
import { alertZones, zoneName } from '../zones.js';
import { encodeBeacon, encodeAdvertisement, expiresAt } from '../../shared/protocol.js';

export class BleBridge {
  constructor({spawnProcess=spawn,onEvent=()=>{}}={}){
    this.spawnProcess=spawnProcess;this.onEvent=onEvent;this.pending=new Map();this.serial=0;this.child=null;this.buffer='';this.stopping=false;
  }
  start(){
    if(this.starting)return this.starting;
    this.starting=new Promise((resolve,reject)=>{
      if(process.platform!=='win32'){reject(new Error('The real BLE bridge currently supports Windows 10/11'));return;}
      const child=this.spawnProcess('powershell.exe',['-NoProfile','-ExecutionPolicy','Bypass','-File',path.join(ROOT,'bridge/ble_bridge.ps1')],{windowsHide:true,stdio:['pipe','pipe','pipe']});
      this.child=child;let ready=false;
      const timeout=setTimeout(()=>reject(new Error('Bluetooth bridge startup timed out')),7000);
      const failed=error=>{clearTimeout(timeout);if(!ready)reject(error);for(const item of this.pending.values()){clearTimeout(item.timer);item.reject(error);}this.pending.clear();if(!this.stopping)this.onEvent({kind:'error',status:'unavailable',error:error.message});};
      child.on('error',failed);child.on('exit',code=>failed(new Error(`Bluetooth bridge exited (${code})`)));
      child.stdin.on('error',()=>{});
      child.stderr.on('data',data=>{this.lastStderr=data.toString().trim().slice(-600);});
      child.stdout.on('data',chunk=>{
        this.buffer+=chunk.toString();if(this.buffer.length>32768){failed(new Error('Bluetooth bridge output overflow'));child.kill();return;}
        let end;while((end=this.buffer.indexOf('\n'))>=0){
          const line=this.buffer.slice(0,end).trim();this.buffer=this.buffer.slice(end+1);if(!line)continue;
          let message;try{message=JSON.parse(line);}catch{failed(new Error('Invalid Bluetooth bridge response'));continue;}
          if(message.kind==='ready'){ready=true;clearTimeout(timeout);resolve(message);}
          if(message.kind==='error'&&!ready){clearTimeout(timeout);reject(new Error(message.error));}
          const item=this.pending.get(message.id);
          if(item&&['published','stopped','error'].includes(message.kind)){
            clearTimeout(item.timer);this.pending.delete(message.id);
            if(message.kind==='error')item.reject(new Error(message.error));else item.resolve(message);
          }
          this.onEvent(message);
        }
      });
    });
    return this.starting;
  }
  request(command,timeoutMs=5000){
    return new Promise((resolve,reject)=>{
      if(!this.child||this.child.exitCode!==null||this.stopping){reject(new Error('Bluetooth bridge is not running'));return;}
      const id=String(++this.serial),timer=setTimeout(()=>{this.pending.delete(id);reject(new Error('Bluetooth advertising request timed out'));},timeoutMs);
      this.pending.set(id,{resolve,reject,timer});
      this.child.stdin.write(JSON.stringify({...command,id})+'\n',error=>{if(error){clearTimeout(timer);this.pending.delete(id);reject(error);}});
    });
  }
  async stop(){
    if(this.stopping)return;this.stopping=true;
    for(const item of this.pending.values()){clearTimeout(item.timer);item.reject(new Error('Bluetooth bridge stopped'));}this.pending.clear();
    const child=this.child;if(!child)return;
    await new Promise(resolve=>{
      let finished=false;const finish=()=>{if(finished)return;finished=true;clearTimeout(timer);resolve();};
      const timer=setTimeout(()=>{child.kill();finish();},1000);
      child.once('exit',finish);child.stdin.end();if(child.exitCode!==null)finish();
    });
  }
}

export class BleRealTransport extends Transport {
  constructor({bridgeFactory=options=>new BleBridge(options),dwellMs=Number(process.env.BLE_DWELL_MS||1200)}={}){
    super('ble','native');this.enabled=process.env.BLE_ENABLED!=='false';this.status=this.enabled?'starting':'disabled';
    this.bridgeFactory=bridgeFactory;this.dwellMs=Math.max(500,Math.min(5000,dwellMs||1200));
    this.jobs=new Map();this.events=[];this.onBeacon=()=>{};this.realAvailable=null;this.nativeStatus=this.status;
    this.bridge=null;this.timer=null;this.busy=false;this.connecting=false;this.closed=false;this.cursor=0;this.active=null;
    if(this.enabled)this.connect();
  }
  observe(message){
    if(message.kind==='ready'){this.realAvailable=true;this.nativeStatus='ready';this.status='ready';this.lastError=null;}
    if(message.kind==='expired'){this.nativeStatus='ready';this.status='ready';this.active=null;}
    if(message.kind==='error'){
      this.realAvailable=false;this.nativeStatus=message.status||'unavailable';this.lastError=message.error;this.status='unavailable';
      if(!this.busy&&!this.connecting&&!this.closed){this.fail(new Error(message.error));this.status='unavailable';const old=this.bridge;this.bridge=null;old?.stop();this.schedule(10000);}
    }
  }
  async connect(){
    if(this.bridge||this.closed||!this.enabled)return;
    this.connecting=true;const bridge=this.bridgeFactory({onEvent:m=>this.observe(m)});this.bridge=bridge;
    try{await bridge.start();if(!this.closed&&this.enabled){this.realAvailable=true;this.nativeStatus='ready';this.status='ready';this.schedule(0);}}
    catch(error){if(this.bridge===bridge)this.bridge=null;await bridge.stop();if(this.closed||!this.enabled)return;this.realAvailable=false;this.nativeStatus='unavailable';this.fail(error);this.status='unavailable';this.schedule(10000);}
    finally{this.connecting=false;}
  }
  schedule(ms){clearTimeout(this.timer);if(!this.closed&&this.enabled)this.timer=setTimeout(()=>this.pump(),ms);}
  async pump(){
    if(this.busy||this.closed||!this.enabled)return;
    if(!this.bridge){this.connect();return;}
    if(this.realAvailable!==true){this.schedule(1000);return;}
    for(const [key,job] of this.jobs)if(expiresAt(job.alert)<=Date.now())this.jobs.delete(key);
    const jobs=[...this.jobs.values()];if(!jobs.length){this.active=null;this.schedule(1000);return;}
    const job=jobs[this.cursor++%jobs.length];this.busy=true;
    try{
      const projected={...job.alert,zone:job.zone,zones:[job.zone]};
      const packet=encodeAdvertisement(projected),core=encodeBeacon(projected);
      await this.bridge.request({op:'publish',hex:Buffer.from(core).toString('hex'),expiresAt:expiresAt(job.alert)});
      if(this.closed||!this.enabled)return;
      this.ok();this.realAvailable=true;this.nativeStatus='advertising';this.status='advertising';this.active={alertId:job.alert.id,zone:job.zone};
      const event={time:new Date().toISOString(),alertId:job.alert.id,zone:job.zone,zoneName:zoneName(job.zone),
        hex:Buffer.from(packet).toString('hex'),manufacturerHex:Buffer.from(packet.subarray(7)).toString('hex'),
        bytes:31,applicationBytes:21,manufacturerBytes:24,simulated:false,transmitted:true,received:false,
        reason:'Windows confirmed advertising started; remote reception has not been confirmed'};
      this.events.unshift(event);this.events=this.events.slice(0,30);this.onBeacon(event);
    }catch(error){
      this.fail(error);this.nativeStatus='unavailable';this.status='unavailable';this.realAvailable=false;
      const old=this.bridge;this.bridge=null;await old?.stop();
    }finally{this.busy=false;this.schedule(this.realAvailable?this.dwellMs:10000);}
  }
  async send(alert){
    if(this.closed||!this.enabled)throw new Error('Real Bluetooth advertising is disabled');
    for(const [key,job] of this.jobs)if(expiresAt(job.alert)<=Date.now())this.jobs.delete(key);
    if(alert.type==='all-clear')for(const [key,job] of this.jobs){
      if(job.alert.seq<alert.seq&&(alertZones(alert).includes(0)||alertZones(alert).includes(job.zone)))this.jobs.delete(key);
    }
    const targets=alertZones(alert);
    if(this.jobs.size+targets.filter(zone=>!this.jobs.has(`${alert.id}:${zone}`)).length>128)throw new Error('Bluetooth advertisement queue is full');
    for(const zone of targets)this.jobs.set(`${alert.id}:${zone}`,{alert,zone});
    if(!this.bridge)this.connect();else if(!this.busy)this.schedule(0);
    return {accepted:true,queued:true,simulated:false,targets,remoteDeliveryConfirmed:false};
  }
  setMode(mode){if(!['native','real'].includes(mode))throw new Error('This build uses real Bluetooth. Simulation is not an automatic fallback.');}
  setEnabled(value){this.enabled=value;if(value){this.closed=false;this.status='starting';this.connect();}else{clearTimeout(this.timer);this.jobs.clear();this.active=null;const old=this.bridge;this.bridge=null;old?.stop();this.nativeStatus='disabled';this.status='disabled';}}
  health(){return {...super.health(),softwareOnly:false,realAvailable:this.realAvailable,nativeStatus:this.nativeStatus,
    automaticSimulationFallback:false,queueDepth:[...this.jobs.values()].filter(job=>expiresAt(job.alert)>Date.now()).length,active:this.active,companyId:'0xFFFF',events:this.events,
    deliveryMeaning:'sent counts Windows advertising starts; it does not prove phone reception'};}
  async stop(){this.closed=true;clearTimeout(this.timer);this.jobs.clear();const old=this.bridge;this.bridge=null;await old?.stop();}
}
```

### campusguard/src/transports/ble-sim.js

<!-- CAMPUSGUARD_SOURCE {"path": "src/transports/ble-sim.js", "encoding": "utf-8", "sha256": "678548de4ec4cde6e774f1aa4c3e93156f2931845d9bc6b69edb9de82756fe56"} -->
```javascript
// Explicit test/development adapter. Never selected or used as an automatic fallback.
import { Transport } from './base.js';
import { zones, alertZones } from '../zones.js';
import { applicable, encodeAdvertisement, decodeAdvertisement } from '../../shared/protocol.js';
export class BleSimulator extends Transport {
  constructor(){super('ble','simulator');this.coverage=Object.fromEntries(zones.filter(z=>z.id).map(z=>[z.id,true]));this.loss=0;this.events=[];this.onBeacon=()=>{};this.fallbackReason=null;}
  record(e){const event={time:new Date().toISOString(),...e};this.events.unshift(event);this.events=this.events.slice(0,30);this.onBeacon(event);}
  setEnabled(value){this.enabled=value;}
  async send(a){
    for(const z of zones.filter(z=>z.id && applicable(a,z.id))){
      const zone=alertZones(a).includes(0)?0:z.id;
      const packet=encodeAdvertisement({...a,zone,zones:[zone]}),hex=Buffer.from(packet).toString('hex'),decoded=decodeAdvertisement(packet);
      const covered=this.coverage[z.id], received=covered && Math.random()>=this.loss;
      this.record({alertId:decoded.id,zone:z.id,zoneName:z.name,hex,bytes:packet.length,applicationBytes:21,decoded,received,reason:!covered?'Coverage disabled':received?'Virtual receiver decoded packet':'Simulated packet loss',simulated:true});
    }
    this.ok();return {accepted:true,simulated:true};
  }
  setMode(mode){
    this.mode='simulator';this.status='ready';this.lastError=null;
    this.fallbackReason=mode==='native'?'Test fixture only: no real radio is used by this isolated software-only fixture.':null;
  }
  health(){return {...super.health(),softwareOnly:true,realAvailable:false,nativeStatus:'unavailable (software-only)',fallbackReason:this.fallbackReason,coverage:this.coverage,loss:this.loss,events:this.events};}
}
```

### campusguard/src/transports/multicast.js

<!-- CAMPUSGUARD_SOURCE {"path": "src/transports/multicast.js", "encoding": "utf-8", "sha256": "646bd76f99b769a2fe3cdd42ba96dc16a445c6d682f67e374db2fd481389d1ae"} -->
```javascript
import { UdpTransport } from './udp.js';
export class MulticastTransport extends UdpTransport { constructor(){super(true);} }
```

### campusguard/src/transports/polling.js

<!-- CAMPUSGUARD_SOURCE {"path": "src/transports/polling.js", "encoding": "utf-8", "sha256": "e1e995e4c7b3d40cd478f86ccb2f925b791b59c762c9453fe6ac43fb80113d46"} -->
```javascript
import { Transport } from './base.js';
export class PollTransport extends Transport {
  constructor(){super('polling');}
  async send(){this.ok(0);return {accepted:true,note:'Available on next sync request'};}
}
```

### campusguard/src/transports/sse.js

<!-- CAMPUSGUARD_SOURCE {"path": "src/transports/sse.js", "encoding": "utf-8", "sha256": "2bd9eebe6257cd24e7c39afb55b1e361644d7064ccd35d369cb480a4ae77ab64"} -->
```javascript
import { Transport } from './base.js';
import { applicable } from '../../shared/protocol.js';
export class SseTransport extends Transport {
  constructor() {super('sse');this.clients=new Set();}
  async send(a) {let count=0;for(const c of this.clients) if(applicable(a,c.zone)){c.res.write(`id: ${a.id}\nevent: alert\ndata: ${JSON.stringify({v:1,kind:'alert',alert:a})}\n\n`);count++;}this.ok(count);return {accepted:true,recipients:count};}
  health() {return {...super.health(),connected:this.clients.size};}
  setEnabled(value){this.enabled=value;if(!value) for(const c of this.clients)c.res.end();}
}
```

### campusguard/src/transports/udp.js

<!-- CAMPUSGUARD_SOURCE {"path": "src/transports/udp.js", "encoding": "utf-8", "sha256": "893ed8dac678d8043d91de41b18ecb56e4fdadac8d9823802b55c8d13b058fce"} -->
```javascript
import { Transport } from './base.js';
import dgram from 'node:dgram';
import { alertZones, multicastGroup } from '../zones.js';
import { expiresAt } from '../../shared/protocol.js';
export class UdpTransport extends Transport {
  constructor(multicast=false) {
    super(multicast?'udp-multicast':'udp-broadcast');this.multicast=multicast;
    this.enabled=process.env.UDP_ENABLED!=='false';this.socket=dgram.createSocket({type:'udp4',reuseAddr:true});this.ready=false;this.timers=new Set();
    this.socket.on('error',e=>this.fail(e));
    this.socket.bind(0,()=>{try{if(multicast){this.socket.setMulticastTTL(1);if(process.env.UDP_INTERFACE)this.socket.setMulticastInterface(process.env.UDP_INTERFACE);}else this.socket.setBroadcast(true);this.ready=true;}catch(e){this.fail(e);}});
  }
  async send(a) {
    if(!this.ready) throw new Error('UDP socket is not ready');
    const packet=Buffer.from(JSON.stringify({v:1,kind:'alert',transport:this.name,zone:alertZones(a),alert:a}));
    if(packet.length>1200)throw new Error('UDP packet exceeds 1200 bytes');
    const targets=this.multicast?alertZones(a).map(multicastGroup):[process.env.UDP_BROADCAST||'255.255.255.255'];
    const emit=()=>{if(!this.enabled||Date.now()>=expiresAt(a))return;for(const target of targets)this.socket.send(packet,Number(process.env.UDP_PORT||41234),target,e=>e?this.fail(e):this.ok());};
    emit();for(const ms of [250,750]){const t=setTimeout(()=>{this.timers.delete(t);emit();},ms);this.timers.add(t);}return {accepted:true};
  }
  stop(){for(const t of this.timers)clearTimeout(t);this.socket.close();}
}
```

### campusguard/src/transports/webrtc-signal.js

<!-- CAMPUSGUARD_SOURCE {"path": "src/transports/webrtc-signal.js", "encoding": "utf-8", "sha256": "5982616a84486936ff0576a9ade7bad2758a9e330dbddf79caf1b2e8bdfbbcf3"} -->
```javascript
import { Transport } from './base.js';
export class PeerTransport extends Transport {
  constructor(){super('webrtc');this.peers=new Map();}
  async send(){this.ok(0);return {accepted:true,note:'Browser seeds relay via established DataChannels'};}
  health(){const reports=[...this.peers.values()].filter(p=>Date.now()-p.time<20000);return {...super.health(),connected:reports.filter(p=>p.connections>0).length,reportedForwards:reports.reduce((n,p)=>n+p.forwards,0),status:this.enabled?(reports.some(p=>p.connections)?'ready':'waiting for peers'):'disabled'};}
}
```

### campusguard/src/transports/ws.js

<!-- CAMPUSGUARD_SOURCE {"path": "src/transports/ws.js", "encoding": "utf-8", "sha256": "b52fe724371a069fa38b58c1ab15e84c49816ea1b7f8b5a1abda367a25372284"} -->
```javascript
import { Transport } from './base.js';
import { zones, alertZones } from '../zones.js';
import { applicable } from '../../shared/protocol.js';
export class WebSocketTransport extends Transport {
  constructor() {super('ws');this.clients=new Set();this.rooms=new Map(zones.map(z=>[z.id,new Set()]));}
  join(socket,zone){this.leave(socket);socket.roomZone=zone;this.clients.add(socket);this.rooms.get(0).add(socket);this.rooms.get(zone).add(socket);}
  leave(socket){this.clients.delete(socket);for(const room of this.rooms.values())room.delete(socket);}
  async send(a) {const recipients=new Set(alertZones(a).flatMap(zone=>[...(this.rooms.get(zone)||[])]));let count=0;for(const s of recipients)if(s.readyState===1&&applicable(a,s.roomZone)){s.send(JSON.stringify({v:1,kind:'alert',alert:a}));count++;}this.ok(count);return {accepted:true,recipients:count};}
  health() {return {...super.health(),connected:this.clients.size,rooms:Object.fromEntries([...this.rooms].map(([zone,clients])=>[zone,clients.size]))};}
  setEnabled(value) {this.enabled=value;if(!value) for(const s of this.clients) s.close(1012,'WebSocket disabled for demo');}
}
```

### campusguard/src/zones.js

<!-- CAMPUSGUARD_SOURCE {"path": "src/zones.js", "encoding": "utf-8", "sha256": "b9b468080e1407c25c25528e5b33fa1ba0da1df0830424a9515b4e00cf5cbb76"} -->
```javascript
// Shared by Node and the PWA. This is the only campus-zone lookup table.
export const zones = Object.freeze([
  { id: 0, name: 'ALL', short: 'ALL' },
  { id: 1, name: 'Visvesvaraya', short: 'Visvesvaraya' },
  { id: 2, name: 'Abdul Kalam', short: 'Abdul Kalam' },
  { id: 3, name: 'Ramanujan', short: 'Ramanujan' },
  { id: 4, name: 'Kalpana Chawla', short: 'Kalpana Chawla' }
].map(Object.freeze));
export const hostels = Object.freeze(zones.filter(z => z.id !== 0));
export const isZone = id => Number.isInteger(id) && zones.some(z => z.id === id);
export const isHostel = id => hostels.some(z => z.id === id);
export const zoneName = id => zones.find(z => z.id === id)?.name || 'Unknown hostel';
export function normalizeZones(value) {
  if (!Array.isArray(value) || !value.length || value.some(id => !isZone(id))) {
    throw new Error('zones must be a non-empty array of valid numeric zone IDs');
  }
  const result = [...new Set(value)].sort((a, b) => a - b);
  if (result.includes(0) && result.length !== 1) throw new Error('Use [0] alone for ALL');
  return result;
}
export const alertZones = alert => Array.isArray(alert.zones) ? alert.zones : [alert.zone];
export const zoneLabel = alert => alertZones(alert).map(zoneName).join(', ');
export const multicastGroup = id => {
  if (!isZone(id)) throw new Error('Unknown multicast zone');
  return id === 0 ? '239.255.42.99' : `239.255.42.${id}`;
};
```

### campusguard/tests/additions.test.js

<!-- CAMPUSGUARD_SOURCE {"path": "tests/additions.test.js", "encoding": "utf-8", "sha256": "f36c4435f2dddb3c6d3e6293624f542a5c6f306c3ad9a1a7be6c0a3f3a40da1c"} -->
```javascript
import { BleSimulator } from '../src/transports/ble-sim.js';
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { WebSocket } from 'ws';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { createCampusGuard } from '../server/index.js';
import { encodeAdvertisement, decodeAdvertisement } from '../shared/protocol.js';
process.env.MDNS_ENABLED='false';
process.env.UDP_ENABLED='false';
process.env.ADMIN_PIN='404404';
const wait=ms=>new Promise(resolve=>setTimeout(resolve,ms));
async function until(check){for(let i=0;i<200;i++){if(check())return;await wait(15);}throw new Error('Timed out waiting for a protocol message');}

test('31-byte software envelope preserves the existing core and detects damage',()=>{
  const a={v:1,serverId:'1234abcd',id:'1234abcd:9',seq:9,type:'drill',severity:1,zone:3,ttl:60,timestamp:'2026-10-24T10:00:00.000Z',message:'DEMO ONLY'};
  const b=encodeAdvertisement(a);assert.equal(b.length,31);assert.equal(decodeAdvertisement(b).id,a.id);
  b[17]^=1;assert.throws(()=>decodeAdvertisement(b),/checksum/);
});

test('compatible APIs, WS/SSE replay, dashboard simulation events, signaling and isolation',{timeout:20000},async()=>{
  const dir=fs.mkdtempSync(path.join(os.tmpdir(),'cg-additions-'));
  const instance=createCampusGuard({bleTransport:new BleSimulator(),dataFile:path.join(dir,'alerts.json')});
  const sockets=[],streamAbort=new AbortController();
  try{
    await instance.listen({httpPort:0,host:'127.0.0.1',tls:false});
    const base=`http://127.0.0.1:${instance.servers.http.address().port}`,pin={'x-admin-pin':'404404'};
    const request=async(route,body,headers=pin)=>{
      const r=await fetch(base+route,{method:body?'POST':'GET',headers:{'Content-Type':'application/json',...headers},...(body?{body:JSON.stringify(body)}:{}),signal:AbortSignal.timeout(5000)});
      return {status:r.status,data:await r.json(),headers:r.headers};
    };
    assert.equal((await request('/api/latest')).data.alert,null);
    assert.equal((await request('/api/stats')).data.alerts,0);
    assert.equal((await request('/api/transports')).data.transports.length,7);
    assert.equal((await request('/api/health')).data.mdns.status,'disabled');
    const c1=(await request('/api/clients',{zone:1,name:'Replay client'})).data;
    const c2=(await request('/api/clients',{zone:3,name:'Signal peer'})).data;
    const auth=c=>({Authorization:`Bearer ${c.id}.${c.token}`});
    const send=(type,zone)=>request('/api/alerts',{type,severity:1,zone,ttl:60,message:'DEMO ONLY: compatibility test'});
    assert.equal((await request('/api/alerts',{type:'drill'},{'x-admin-pin':'bad'})).status,401);
    const a1=(await send('drill',1)).data.alert,a2=(await send('medical',3)).data.alert;
    assert.equal((await request('/api/alerts?since=0',null,auth(c1))).data.alerts.length,1);
    assert.equal((await request('/api/alerts?since=-1')).status,400);
    assert.equal((await request('/api/latest',null,auth(c1))).data.alert.id,a1.id);
    assert.equal((await request('/api/latest')).data.alert.id,a2.id);
    const login=await request('/api/login',{pin:'404404'});
    const cookie=login.headers.get('set-cookie').split(';')[0];
    const connect=(hello,headers={})=>{
      const messages=[],ws=new WebSocket(base.replace('http','ws')+'/ws',{headers});sockets.push(ws);
      ws.on('open',()=>ws.send(JSON.stringify(hello)));ws.on('message',b=>messages.push(JSON.parse(b)));
      return {ws,messages};
    };
    const one=connect({kind:'hello',clientId:c1.id,token:c1.token,lastSeq:0});
    const two=connect({kind:'hello',clientId:c2.id,token:c2.token,lastSeq:0});
    const dashboard=connect({kind:'hello',role:'dashboard'},{Cookie:cookie});
    await until(()=>one.messages.some(m=>m.kind==='replay-complete')&&two.messages.some(m=>m.kind==='ready')&&dashboard.messages.some(m=>m.kind==='ready'));
    assert.equal(one.messages.filter(m=>m.kind==='alert').length,1);
    assert.equal(one.messages.find(m=>m.kind==='alert').alert.id,a1.id);
    one.ws.send(JSON.stringify({kind:'ping'}));await until(()=>one.messages.some(m=>m.kind==='pong'));
    one.ws.send(JSON.stringify({kind:'signal',to:c2.id,type:'offer',data:{sdp:'test signaling payload'}}));
    await until(()=>two.messages.some(m=>m.kind==='signal'));
    assert.equal(two.messages.find(m=>m.kind==='signal').from,c1.id);
    for(let i=0;i<2;i++)assert.equal((await request('/api/ack',{alertId:a1.id,status:'safe'},auth(c1))).status,200);
    assert.equal((await request('/api/stats')).data.acknowledgements,1);
    const sse=await fetch(base+'/api/stream?since=0',{headers:auth(c1),signal:streamAbort.signal});
    assert.equal(sse.status,200);const reader=sse.body.getReader();let text='';
    while(!text.includes(a1.id)){const chunk=await reader.read();text+=new TextDecoder().decode(chunk.value);}
    assert.match(text,/event: ready/);assert.match(text,/event: alert/);
    const a3=(await send('security',0)).data.alert;
    await until(()=>dashboard.messages.some(m=>m.kind==='ble-beacon'&&m.beacon.alertId===a3.id));
    const beacon=dashboard.messages.find(m=>m.kind==='ble-beacon'&&m.beacon.alertId===a3.id);
    assert.equal(beacon.beacon.bytes,31);assert.equal(beacon.beacon.simulated,true);
    assert.equal(Buffer.from(beacon.beacon.hex,'hex').length,31);
    const fallback=await request('/api/transports/ble',{mode:'native'});
    assert.equal(fallback.data.mode,'simulator');assert.equal(fallback.data.realAvailable,false);assert.match(fallback.data.fallbackReason,/software-only/);
    await send('all-clear',1);
    const snapshot=(await request('/api/alerts?since=0',null,auth(c1))).data;
    assert.equal(snapshot.alerts.every(a=>a.type==='all-clear'),true);
    const poll=instance.manager.get('polling'),original=poll.send;
    poll.send=()=>new Promise(()=>{});
    const posting=send('drill',0);
    await until(()=>one.messages.some(m=>m.kind==='alert'&&m.alert.seq===5));
    assert.equal((await posting).status,201);poll.send=original;
    assert.equal((await request('/api/transports')).data.transports.find(t=>t.name==='polling').failed,1);
    const {stdout}=await promisify(execFile)(process.execPath,[path.resolve('scripts/seed.js')],{env:{...process.env,SERVER_URL:base}});
    assert.equal((stdout.match(/Seeded #/g)||[]).length,5);
    assert.equal((await request('/api/stats')).data.alerts,10);
    streamAbort.abort();
  }finally{
    streamAbort.abort();for(const ws of sockets)ws.terminate();await instance.close();fs.rmSync(dir,{recursive:true,force:true});
  }
});
```

### campusguard/tests/ble-real.test.js

<!-- CAMPUSGUARD_SOURCE {"path": "tests/ble-real.test.js", "encoding": "utf-8", "sha256": "59f3495c7d3a8b79b18c015556664bd3a431aa7361dd43612667caa1d6450df4"} -->
```javascript
import test from 'node:test';
import assert from 'node:assert/strict';
import { BleRealTransport } from '../src/transports/ble-real.js';
import { TransportManager } from '../src/transportManager.js';
import { decodeBeacon } from '../shared/protocol.js';
process.env.UDP_ENABLED='false';process.env.BLE_ENABLED='true';
const wait=ms=>new Promise(r=>setTimeout(r,ms));
async function until(check){for(let n=0;n<200;n++){if(check())return;await wait(10);}throw new Error('BLE adapter check timed out');}
const alert=(zones=[1,2])=>({v:1,serverId:'1234abcd',id:'1234abcd:1',seq:1,type:'drill',severity:1,zone:zones.length===1?zones[0]:null,zones,ttl:60,timestamp:new Date().toISOString(),message:'DEMO ONLY: adapter test'});

test('real BLE rotates hostel packets, confirms OS starts, and never claims remote receipt',async()=>{
  const commands=[];let stopped=false;
  const transport=new BleRealTransport({dwellMs:500,bridgeFactory:({onEvent})=>({
    async start(){onEvent({kind:'ready'});return {kind:'ready'};},
    async request(command){commands.push(command);return {kind:'published'};},
    async stop(){stopped=true;}
  })});
  try{
    await until(()=>transport.realAvailable===true);
    await transport.send(alert());await until(()=>transport.sent>=2);
    assert.deepEqual(commands.slice(0,2).map(c=>decodeBeacon(Buffer.from(c.hex,'hex')).zone),[1,2]);
    assert.ok(transport.events.every(e=>e.transmitted&&!e.simulated&&!e.received));
    assert.equal(transport.health().automaticSimulationFallback,false);
    assert.equal(transport.confirmed,0);assert.throws(()=>transport.setMode('simulator'),/real Bluetooth/);
  }finally{await transport.stop();}assert.equal(stopped,true);
});

test('unavailable Bluetooth leaves LAN dispatch working and cannot report simulated sends',async()=>{
  const ble=new BleRealTransport({bridgeFactory:()=>({async start(){throw new Error('No peripheral adapter');},async stop(){}})});
  const manager=new TransportManager({bleTransport:ble});let delivered=0;
  manager.get('ws').send=async()=>{delivered++;return {accepted:true};};
  try{
    await until(()=>ble.status==='unavailable');
    await manager.send(alert([0]));
    assert.equal(delivered,1);assert.equal(ble.mode,'native');assert.equal(ble.sent,0);assert.equal(ble.events.length,0);
    assert.equal(ble.realAvailable,false);assert.ok(ble.errors>=1);
  }finally{await manager.stop();}
});
```

### campusguard/tests/hostels.test.js

<!-- CAMPUSGUARD_SOURCE {"path": "tests/hostels.test.js", "encoding": "utf-8", "sha256": "3e4b36e2f490f7b98f7bf7f8c1abfd66f8703a70b8b8fe906043f2844973f53a"} -->
```javascript
import { BleSimulator } from '../src/transports/ble-sim.js';
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import crypto from 'node:crypto';
import { WebSocket } from 'ws';
import { createCampusGuard } from '../server/index.js';
import { zones, multicastGroup } from '../src/zones.js';
import { decodeAdvertisement, signingPayload } from '../shared/protocol.js';
process.env.UDP_ENABLED='false';process.env.MDNS_ENABLED='false';process.env.ADMIN_PIN='404404';
const wait=ms=>new Promise(r=>setTimeout(r,ms));
async function until(check){for(let n=0;n<200;n++){if(check())return;await wait(10);}throw new Error('Hostel test timed out');}

test('hostel rooms, multi-target replay, query filtering, BLE/UDP routing and durable ack attribution',{timeout:20000},async()=>{
  const dir=fs.mkdtempSync(path.join(os.tmpdir(),'cg-hostels-')),file=path.join(dir,'alerts.json');
  let instance=createCampusGuard({bleTransport:new BleSimulator(),dataFile:file});const sockets=[],controllers=[];
  try{
    await instance.listen({httpPort:0,host:'127.0.0.1',tls:false});
    let base=`http://127.0.0.1:${instance.servers.http.address().port}`;
    const admin={'x-admin-pin':'404404'},auth=c=>({Authorization:`Bearer ${c.id}.${c.token}`});
    const request=async(route,body,headers=admin,method=body?'POST':'GET')=>{
      const r=await fetch(base+route,{method,headers:{'Content-Type':'application/json',...headers},...(body?{body:JSON.stringify(body)}:{})});
      return {status:r.status,data:await r.json()};
    };
    const clients=[];
    const moduleResponse=await fetch(base+'/src/zones.js');assert.equal(moduleResponse.status,200);assert.match(await moduleResponse.text(),/Visvesvaraya/);
    for(const zone of zones.filter(z=>z.id))clients.push((await request('/api/clients',{zone:zone.id,name:zone.name})).data);
    const connect=(c,zone=c.zone,lastSeq=0)=>{
      const ws=new WebSocket(base.replace('http','ws')+'/ws'),messages=[];sockets.push(ws);
      ws.on('open',()=>ws.send(JSON.stringify({kind:'hello',clientId:c.id,token:c.token,zone,lastSeq})));
      ws.on('message',b=>messages.push(JSON.parse(b)));
      return {ws,messages};
    };
    const peers=clients.map(c=>connect(c));
    await until(()=>peers.every(p=>p.messages.some(m=>m.kind==='replay-complete')));
    const wrong=connect(clients[0],2);await until(()=>wrong.ws.readyState===3);
    assert.equal(wrong.messages.some(m=>m.kind==='alert'),false);
    const send=async(targets,type='drill')=>request('/api/alerts',{type,severity:1,zones:targets,ttl:300,message:'DEMO ONLY: hostel route test'});
    for(const invalid of [[],[5],['1'],[0,1],null,'1']){
      assert.equal((await request('/api/alerts',{type:'drill',severity:1,zones:invalid,ttl:60,message:'Invalid target test'})).status,400);
    }
    const multi=(await send([2,1,1])).data.alert;
    assert.deepEqual(multi.zones,[1,2]);assert.equal(multi.zone,null);
    await until(()=>peers[0].messages.some(m=>m.alert?.id===multi.id)&&peers[1].messages.some(m=>m.alert?.id===multi.id));
    assert.equal(peers[2].messages.some(m=>m.alert?.id===multi.id),false);
    assert.equal(peers[3].messages.some(m=>m.alert?.id===multi.id),false);
    const all=(await send([0])).data.alert,third=(await send([3])).data.alert;
    await until(()=>peers.every(p=>p.messages.some(m=>m.alert?.id===all.id))&&peers[2].messages.some(m=>m.alert?.id===third.id));
    assert.deepEqual(instance.manager.get('ws').health().rooms,{'0':4,'1':1,'2':1,'3':1,'4':1});
    const reconnect=connect(clients[2],3,multi.seq);
    await until(()=>reconnect.messages.some(m=>m.kind==='replay-complete'));
    assert.deepEqual(reconnect.messages.filter(m=>m.kind==='alert').map(m=>m.alert.id),[all.id,third.id]);
    assert.deepEqual((await request('/api/alerts?zone=3&since=0',null,auth(clients[2]))).data.alerts.map(a=>a.id),[all.id,third.id]);
    assert.deepEqual((await request('/api/alerts?zone=3&since=0')).data.alerts.map(a=>a.id),[all.id,third.id]);
    assert.equal((await request('/api/alerts?zone=2',null,auth(clients[2]))).status,403);
    assert.equal((await request('/api/sync?zone=2&after=0',null,auth(clients[2]))).status,403);
    assert.equal((await request('/api/sync?zone=0',null,auth(clients[2]))).status,400);
    const poll=(await request('/api/sync?zone=4&after=0&transport=polling',null,auth(clients[3]))).data;
    assert.deepEqual(poll.alerts.map(a=>a.id),[all.id]);
    for(const route of ['/api/events','/api/stream']){
      const controller=new AbortController();controllers.push(controller);
      const stream=await fetch(base+route+'?zone=4&since=0',{headers:auth(clients[3]),signal:controller.signal});
      const reader=stream.body.getReader();let text='';
      while(!text.includes('event: alert'))text+=new TextDecoder().decode((await reader.read()).value);
      assert.ok(text.includes(all.id));assert.ok(!text.includes(multi.id));assert.ok(!text.includes(third.id));
      controller.abort();
    }
    const forbidden=await fetch(base+'/api/stream?zone=1',{headers:auth(clients[3])});assert.equal(forbidden.status,403);await forbidden.json();
    const beacons=instance.manager.get('ble').events.filter(e=>e.alertId===multi.id);
    assert.deepEqual(beacons.map(e=>e.zone).sort(),[1,2]);
    for(const event of beacons){const bytes=Buffer.from(event.hex,'hex');assert.equal(bytes.length,31);assert.equal(bytes[18],event.zone);assert.equal(bytes[19],0);assert.equal(decodeAdvertisement(bytes).zone,event.zone);}
    for(const multicast of [false,true]){
      const transport=instance.manager.get(multicast?'udp-multicast':'udp-broadcast');
      const calls=[],original=transport.socket.send;transport.enabled=true;
      transport.socket.send=(packet,port,target,done)=>{calls.push({body:JSON.parse(packet.toString()),port,target});done(null);};
      await until(()=>transport.ready);
      try{
        await transport.send(multi);await transport.send(all);
        assert.deepEqual(calls.map(c=>c.target),multicast?[multicastGroup(1),multicastGroup(2),multicastGroup(0)]:[process.env.UDP_BROADCAST||'255.255.255.255',process.env.UDP_BROADCAST||'255.255.255.255']);
        assert.ok(calls.every(c=>c.port===41234));assert.deepEqual(calls[0].body.zone,[1,2]);assert.deepEqual(calls[0].body.alert.zones,[1,2]);assert.equal(calls[0].body.alert.zone,null);
      }finally{transport.enabled=false;transport.socket.send=original;}
    }
    const key=crypto.createPublicKey({key:(await request('/api/config')).data.publicKey,format:'jwk'});
    const verify=a=>crypto.verify('sha256',Buffer.from(signingPayload(a)),{key,dsaEncoding:'ieee-p1363'},Buffer.from(a.signature,'base64url'));
    assert.ok(verify(multi));assert.equal(verify({...multi,zones:[3,4]}),false);
    assert.equal((await request('/api/ack',{alertId:multi.id,status:'safe'},auth(clients[2]))).status,400);
    for(const c of clients.slice(0,2))for(let i=0;i<2;i++)await request('/api/ack',{alertId:multi.id,status:'safe'},auth(c));
    let stats=(await request('/api/stats/zones')).data.zones;
    assert.deepEqual(stats.map(z=>z.acksReceived),[1,1,0,0]);assert.deepEqual(stats.map(z=>z.connectedClients),[1,1,1,1]);
    assert.ok(stats.every(z=>z.lastAlertTime!==null));
    await request('/api/clients/me',{zone:4},auth(clients[0]),'PATCH');
    stats=(await request('/api/stats/zones')).data.zones;
    assert.deepEqual(stats.map(z=>z.acksReceived),[1,1,0,0]);
    assert.deepEqual(stats.map(z=>z.connectedClients),[0,1,1,2]);
    await send([1],'all-clear');
    assert.ok((await request('/api/alerts?zone=2&since=0',null,auth(clients[1]))).data.alerts.some(a=>a.id===multi.id));
    assert.ok(!(await request('/api/alerts?zone=1&since=0')).data.alerts.some(a=>a.id===multi.id));
    const clear=(await send([1,2],'all-clear')).data.alert;
    assert.equal(instance.store.state.clearMarkers[1],clear.seq);assert.equal(instance.store.state.clearMarkers[2],clear.seq);
    assert.ok((await request('/api/alerts?since=0',null,auth(clients[1]))).data.alerts.every(a=>a.type==='all-clear'));
    assert.ok((await request('/api/alerts?since=0',null,auth(clients[2]))).data.alerts.some(a=>a.id===third.id));
    for(const ws of sockets)ws.terminate();await instance.close();
    instance=createCampusGuard({bleTransport:new BleSimulator(),dataFile:file});
    assert.equal(Object.keys(instance.store.state.acksByZone[1][multi.id]).length,1);
    assert.equal(instance.store.state.acks[multi.id][clients[0].id].zone,1);
    assert.equal(Object.keys(instance.store.state.acksByZone[2][multi.id]).length,1);
  }finally{
    for(const controller of controllers)controller.abort();for(const ws of sockets)ws.terminate();
    await instance.close();fs.rmSync(dir,{recursive:true,force:true});
  }
});
```

### campusguard/tests/incidents.test.js

<!-- CAMPUSGUARD_SOURCE {"path": "tests/incidents.test.js", "encoding": "utf-8", "sha256": "96733b76c5db8b2607855da08a6e072fec90dcf42cd87493a4084a58a70fc015"} -->
```javascript
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import crypto from 'node:crypto';
import {createCampusGuard} from '../src/app.js';
import {signingPayload} from '../shared/protocol.js';
process.env.BLE_ENABLED='false';process.env.UDP_ENABLED='false';process.env.MDNS_ENABLED='false';process.env.ADMIN_PIN='404404';
test('persistent incident metrics, safe/help transitions, real timelines, assembly integrity and reports',async()=>{
  const dir=fs.mkdtempSync(path.join(os.tmpdir(),'cg-incidents-')),file=path.join(dir,'alerts.json');let app=createCampusGuard({dataFile:file});
  try{
    await app.listen({httpPort:0,host:'127.0.0.1',tls:false});const base=`http://127.0.0.1:${app.servers.http.address().port}`;
    const admin={'x-admin-pin':'404404'},auth=c=>({Authorization:`Bearer ${c.id}.${c.token}`});
    const req=async(url,body,headers=admin,method=body?'POST':'GET')=>{const response=await fetch(base+url,{method,headers:{'Content-Type':'application/json',...headers},...(body?{body:JSON.stringify(body)}:{})});return {status:response.status,data:await response.json()};};
    const c=(await req('/api/clients',{zone:3,name:'Incident student'})).data,other=(await req('/api/clients',{zone:1,name:'Other hostel'})).data;
    const posted=await req('/api/alerts',{type:'fire',severity:3,zones:[3],assemblyPoint:'cricket',ttl:300,message:'DEMO ONLY: incident test'}),alert=posted.data.alert;
    assert.equal(posted.status,201);assert.equal(alert.assemblyPoint,'cricket');
    const config=(await req('/api/config')).data,key=crypto.createPublicKey({key:config.publicKey,format:'jwk'});
    const verify=a=>crypto.verify('sha256',Buffer.from(signingPayload(a)),{key,dsaEncoding:'ieee-p1363'},Buffer.from(a.signature,'base64url'));
    assert.equal(verify(alert),true);assert.equal(verify({...alert,assemblyPoint:'football'}),false);
    const route=`/api/incidents/${alert.id}`;
    assert.equal((await req(route,null,auth(c))).status,401);
    let detail=(await req(route)).data;assert.equal(detail.audience,1);assert.equal(detail.delivered,0);assert.equal(detail.safe,0);assert.equal(detail.notified,false);assert.equal(detail.events.length,1);
    assert.equal((await req('/api/responses',{alertId:alert.id,status:'needs-help'},auth(other))).status,400);
    await req('/api/receipts',{alertId:alert.id,via:'ws'},auth(c));
    await req('/api/responses',{alertId:alert.id,status:'needs-help',message:'DEMO ONLY: assistance requested'},auth(c));
    detail=(await req(route)).data;assert.equal(detail.delivered,1);assert.equal(detail.help,1);assert.equal(detail.safe,0);assert.equal(detail.deliveredPercent,100);
    assert.equal((await req('/api/responses',{alertId:alert.id,status:'needs-help',message:'DEMO ONLY: assistance requested'},auth(c))).data.duplicate,true);
    await req('/api/responses',{alertId:alert.id,status:'safe'},auth(c));
    detail=(await req(route)).data;assert.equal(detail.help,0);assert.equal(detail.safe,1);assert.equal(detail.safePercent,100);
    assert.equal((await req('/api/acks',{alertId:alert.id,status:'safe'},auth(c))).data.duplicate,true);
    await req('/api/responses',{alertId:alert.id,status:'needs-help'},auth(c));
    assert.equal(Object.keys(app.store.state.acks[alert.id]).length,0);assert.equal(Object.keys(app.store.state.acksByZone[3][alert.id]).length,0);
    assert.equal((await req('/api/responses/me',null,auth(c))).data.responses[alert.id].status,'needs-help');
    await req(route+'/notify',{});await req(route+'/notify',{});detail=(await req(route)).data;
    assert.equal(detail.events.filter(e=>e.kind==='staff-notified').length,1);
    const body={requestId:'demo-report-request',category:'medical',message:'DEMO ONLY: medical report'};
    const report=(await req('/api/reports',body,auth(c))).data.report;
    assert.equal((await req('/api/reports',body,auth(c))).data.duplicate,true);
    assert.equal((await req('/api/reports',null,auth(other))).status,401);
    assert.equal((await req(`/api/reports/${report.id}`,{status:'resolved'},admin,'PATCH')).data.report.status,'resolved');
    assert.equal((await req('/api/campus')).data.medicalPhone,'8571893627');
    assert.equal((await req('/api/campus',{securityPhone:'1234567890'},auth(c),'PATCH')).status,401);
    await req('/api/campus',{securityPhone:'1234567890'},admin,'PATCH');
    const clear=await req(route+'/all-clear',{message:'DEMO ONLY: all-clear'});assert.equal(clear.status,201);
    detail=(await req(route)).data;assert.equal(detail.active,false);assert.equal(detail.status,'cleared');
    assert.equal((await req(route+'/all-clear',{})).status,409);
    await app.close();app=createCampusGuard({dataFile:file});
    assert.equal(app.operations.detail(alert.id).help,1);assert.equal(app.operations.reports()[0].status,'resolved');assert.equal(app.operations.campus().securityPhone,'1234567890');
  }finally{await app.close();fs.rmSync(dir,{recursive:true,force:true});}
});
```

### campusguard/tests/integration.test.js

<!-- CAMPUSGUARD_SOURCE {"path": "tests/integration.test.js", "encoding": "utf-8", "sha256": "7cf43a5a7ce1b5781931a1789a531f0cc053fa562e53eb0b3fe292982ebe4b95"} -->
```javascript
process.env.BLE_ENABLED='false';
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import crypto from 'node:crypto';
import {WebSocket} from 'ws';
import {createCampusGuard} from '../server/index.js';
import {signingPayload,encodeBeacon,decodeBeacon} from '../shared/protocol.js';
process.env.UDP_ENABLED='false';process.env.MDNS_ENABLED='false';process.env.ADMIN_PIN='404404';
const wait=ms=>new Promise(r=>setTimeout(r,ms));
async function until(fn,ms=3000){const end=Date.now()+ms;while(Date.now()<end){if(fn())return;await wait(20);}throw new Error('Condition timed out');}
test('end-to-end: auth, zones, WS/SSE/polling, dedupe, clearing, persistence and signature',{timeout:20000},async()=>{
  const dir=fs.mkdtempSync(path.join(os.tmpdir(),'cg-integration-'));let instance=createCampusGuard({dataFile:path.join(dir,'state.json')}),sseAbort=new AbortController(),ws1,ws2;
  try{
    await instance.listen({httpPort:0,host:'127.0.0.1',tls:false});let base=`http://127.0.0.1:${instance.servers.http.address().port}`;
    const request=async(route,body,auth={},method=body?'POST':'GET')=>{const r=await fetch(base+route,{method,headers:{'Content-Type':'application/json',...auth},...(body?{body:JSON.stringify(body)}:{})});return {status:r.status,data:await r.json(),headers:r.headers};};
    assert.equal((await request('/api/alerts',{type:'fire'})).status,401);
    assert.equal((await request('/api/login',{pin:'wrong'})).status,401);
    const login=await request('/api/login',{pin:'404404'}),admin={Cookie:login.headers.get('set-cookie').split(';')[0]};assert.equal(login.status,200);
    const c1=(await request('/api/clients',{name:'Test Hostel',zone:1})).data,c2=(await request('/api/clients',{name:'Test Labs',zone:3})).data;
    const auth=c=>({Authorization:`Bearer ${c.id}.${c.token}`});
    const m1=[],m2=[];
    function connect(c,m){const ws=new WebSocket(base.replace('http','ws')+'/ws');ws.on('open',()=>ws.send(JSON.stringify({kind:'hello',clientId:c.id,token:c.token})));ws.on('message',b=>m.push(JSON.parse(b)));return ws;}
    ws1=connect(c1,m1);ws2=connect(c2,m2);await until(()=>m1.some(m=>m.kind==='ready')&&m2.some(m=>m.kind==='ready'));
    const sse=await fetch(base+'/api/events',{headers:auth(c1),signal:sseAbort.signal});assert.equal(sse.status,200);const reader=sse.body.getReader();await reader.read();
    const posted=await request('/api/alerts',{type:'fire',severity:3,zone:1,ttl:300,message:'TEST ONLY: use east exit.'},admin);assert.equal(posted.status,201);const a=posted.data.alert;assert.equal(a.seq,1);
    await until(()=>m1.some(m=>m.kind==='alert'&&m.alert.id===a.id));await wait(100);assert.equal(m2.some(m=>m.kind==='alert'),false);
    const chunk=await reader.read();assert.match(new TextDecoder().decode(chunk.value),new RegExp(a.id));
    const sync=(await request('/api/sync?after=0',null,auth(c1))).data;assert.equal(sync.alerts[0].id,a.id);assert.equal((await request('/api/sync?after=0',null,auth(c2))).data.alerts.length,0);
    const config=(await request('/api/config')).data,key=crypto.createPublicKey({key:config.publicKey,format:'jwk'});
    assert.equal(crypto.verify('sha256',Buffer.from(signingPayload(a)),{key,dsaEncoding:'ieee-p1363'},Buffer.from(a.signature,'base64url')),true);
    assert.equal(crypto.verify('sha256',Buffer.from(signingPayload({...a,message:'tampered'})),{key,dsaEncoding:'ieee-p1363'},Buffer.from(a.signature,'base64url')),false);
    assert.equal(decodeBeacon(encodeBeacon(a)).id,a.id);
    assert.equal((await request('/api/acks',{alertId:a.id},auth(c2))).status,400);
    for(let i=0;i<2;i++){await request('/api/receipts',{alertId:a.id,via:'ws'},auth(c1));await request('/api/acks',{alertId:a.id,status:'safe'},auth(c1));}
    const history=(await request('/api/history',null,admin)).data.alerts;assert.equal(history[0].received,1);assert.equal(history[0].safe,1);
    // Adapter failure does not undo durable alert creation.
    instance.manager.get('udp-broadcast').enabled=true;instance.manager.get('udp-broadcast').send=async()=>{throw new Error('Injected adapter failure');};
    await request('/api/transports/ws',{enabled:false},admin);await until(()=>ws1.readyState===3);assert.equal((await request('/api/sync?after=0',null,auth(c1))).status,200);
    await request('/api/transports/sse',{enabled:false},admin);const disabled=await fetch(base+'/api/events',{headers:auth(c1)});assert.equal(disabled.status,503);
    const clear=await request('/api/alerts',{type:'all-clear',severity:1,zone:1,ttl:300,message:'TEST ONLY: drill complete.'},admin);assert.equal(clear.status,201);assert.equal(clear.data.alert.seq,2);
    const afterClear=(await request('/api/sync?after=0',null,auth(c1))).data;assert.equal(afterClear.alerts.length,0);assert.equal(afterClear.clearMarkers[1],2);
    const all=await request('/api/alerts',{type:'drill',severity:1,zone:0,ttl:300,message:'TEST ONLY: campus drill.'},admin);assert.equal(all.status,201);
    assert.equal((await request('/api/sync?after=0',null,auth(c2))).data.alerts.length,1);
    const state=instance.store.state;assert.equal(instance.manager.get('udp-broadcast').errors>=1,true);
    assert.equal((await request('/api/alerts',{type:'fire',severity:4,zone:1,ttl:1,message:'bad'},admin)).status,400);
    sseAbort.abort();ws1.close();ws2.close();await instance.close();instance=createCampusGuard({dataFile:path.join(dir,'state.json')});await instance.listen({httpPort:0,host:'127.0.0.1',tls:false});base=`http://127.0.0.1:${instance.servers.http.address().port}`;
    assert.equal(instance.store.state.serverId,state.serverId);assert.equal(instance.store.state.seq,3);assert.equal(Object.keys(instance.store.state.acks[a.id]).length,1);
    const login2=await request('/api/login',{pin:'404404'}),admin2={Cookie:login2.headers.get('set-cookie').split(';')[0]};const next=await request('/api/alerts',{type:'medical',severity:2,zone:3,ttl:1,message:'TEST ONLY: medical exercise.'},admin2);assert.equal(next.data.alert.seq,4);await wait(1200);assert.equal((await request('/api/sync?after=0',null,auth(c2))).data.alerts.some(a=>a.seq===4),false);
  }finally{sseAbort.abort();ws1?.terminate();ws2?.terminate();await instance.close();fs.rmSync(dir,{recursive:true,force:true});}
});
```

### campusguard/tests/protocol.test.js

<!-- CAMPUSGUARD_SOURCE {"path": "tests/protocol.test.js", "encoding": "utf-8", "sha256": "ded328f90676f04a314a27c2bb5fab9ff15df0d5369d6cc706e8f1a8f6e5f008"} -->
```javascript
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {crc16,encodeBeacon,decodeBeacon,validAlert,cleared,applicable} from '../shared/protocol.js';
import {Store} from '../server/store.js';
const alert={v:1,id:'1234abcd:42',serverId:'1234abcd',seq:42,timestamp:'2026-10-24T10:00:00.000Z',ttl:300,type:'fire',severity:3,zone:1,message:'Evacuate.'};
test('BLE codec uses 21 bytes and preserves identity, time, TTL and routing',()=>{const b=encodeBeacon(alert),a=decodeBeacon(b);assert.equal(b.length,21);for(const key of ['id','serverId','seq','timestamp','ttl','type','severity','zone'])assert.equal(a[key],alert[key]);assert.equal(crc16(new TextEncoder().encode('123456789')),0x29b1);assert.equal(validAlert(a),true);});
test('BLE rejects corrupted packets and unknown fields',()=>{const b=encodeBeacon(alert);b[10]^=1;assert.throws(()=>decodeBeacon(b),/checksum/);assert.throws(()=>decodeBeacon(new Uint8Array(31)),/21 bytes/);});
test('zone and clear markers prevent late alerts from reopening',()=>{assert.equal(applicable(alert,1),true);assert.equal(applicable(alert,3),false);assert.equal(cleared(alert,{1:43},1),true);assert.equal(cleared(alert,{3:43},1),false);assert.equal(cleared(alert,{0:50},1),true);assert.equal(cleared({...alert,seq:51},{0:50},1),false);});
test('JSON store persists sequence and rolls back failed writes',()=>{const dir=fs.mkdtempSync(path.join(os.tmpdir(),'cg-store-')),file=path.join(dir,'state.json');try{const s=new Store(file);s.transaction(state=>{state.seq=7;});assert.equal(new Store(file).state.seq,7);s.file=path.join(dir,'missing','state.json');assert.throws(()=>s.transaction(state=>{state.seq=8;}));assert.equal(s.state.seq,7);}finally{fs.rmSync(dir,{recursive:true,force:true});}});
```
