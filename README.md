# StreamBid

StreamBid is a marketplace for booking ad space on live streams. Streamers connect their Twitch channels, set prices and available hours, and can mark where ads may appear. Advertisers choose a time, draw an ad area on the stream layout, and send an offer. If the streamer accepts, StreamBid reserves that area for the agreed time.

Advertisers can also publish campaigns and invite streamers. Streamers can browse published campaigns and apply with a connected channel.

## Tech stack

- React, TypeScript, Vite, and Tailwind CSS for the frontend
- Java 21, Spring Boot, and Spring Security for the backend
- PostgreSQL, jOOQ, and Flyway for data storage and migrations
- Twitch OAuth for connecting channels and S3-compatible storage for ad creatives
- Docker Compose for running PostgreSQL and the backend locally

StreamBid is still in development.
