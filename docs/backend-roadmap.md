# Backend completion plan

Source: original StreamBid technical description supplied on November 2, 2026.

## Implemented foundation
- Java 21, Spring Boot, PostgreSQL, jOOQ, Flyway, security and roles.
- Twitch OAuth, encrypted credentials, refresh, validation and statistics.
- Discovery campaigns, two catalogs, applications, invitations and participation history.
- Owner-managed inventory, availability exclusion constraints, activation and advertising category policies.
- Validated static images, private S3-compatible uploads, administrator moderation and private review history.
- Durable upload intents, upload/cleanup fencing and retryable orphan cleanup, tested with PostgreSQL and S3-compatible storage.

## Creative cleanup operation
Cleanup is disabled by default. Enable `creative.storage.cleanup-enabled=true` with configured private storage.
The worker claims up to five expired uploads per minute, with a five-minute retry lease.
Successful orphan deletion retains a durable tombstone for daily rechecks, so a delayed PUT is collected later.
Tombstones are not automatically purged: this trades retained database rows and daily storage requests for delayed-upload recovery.
A committed creative is preserved and its recovery intent is removed. Storage failures retain retry state.
Local S3-compatible tests do not establish production AWS or real Twitch integration readiness.

## Required before declaring the original backend complete
1. Streamer advertising inventory: dimensions, position, duration, prices, capacity and availability.
2. Validated PNG/JPG/WebP uploads, S3-compatible storage and moderation.
3. Offers with immutable price/duration revisions, counteroffers and version-safe acceptance.
4. Transactional bookings with interval/capacity protection and cancellation rules.
5. Delivery campaigns, durable scheduling and idempotent execution.
6. Protected OBS Browser Source, WebSocket state synchronization, reconnect and manual stop.
7. Delivery events, overlay activity and honest viewer-minute reports.
8. Full end-to-end placement verification, recovery and access checks.

Discovery campaign publication is not an executed advertising placement.
Accepted participation is not agreement on price, booking or proof of delivery.
Real Twitch verification is deferred by the user; YouTube and Kick follow the initial Twitch scenario.
Redis may follow the basic working scenario, as allowed in the original description.

## Frontend after backend
React + TypeScript + Vite, Tailwind CSS + shadcn/ui, Recharts.
Preserve the approved light blue visual direction. The OBS overlay is a separate web page.
