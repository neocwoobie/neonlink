<p align="center">
  <img src="./docs/logo_doc.svg" height="50" />
</p>

## Overview

- [Introduction](#introduction)
- [Features](#features)
- [Screenshots](#screenshots)
- [Installation](#installation)
- [Development](#development)

## Introduction

NeonLink is a simple and open-source self-hosted bookmark service. It is lightweight, uses minimal dependencies, and is easy to install via Docker. Due to the low system requirements, this application is ideal for deployment on the RaspberryPI.

## Features

- Tags
- Search
- Auto icon, title, description
- Customizable background
- Lightweight
- Private
- Dashboard
- Android PWA share target for saving links from the Android share menu

## Installation

### With Docker

[DockerHub](https://hub.docker.com/r/alexscifier/neonlink)

You can easily install an application using Docker. The images are also optimized for RaspberryPi.

Then run the command which will install the Docker container.

```sh
docker run -p {80}:3333 -v {/path/to/data}:/app/data -v {/path/to/backgrounds}:/app/public/static/media/background alexscifier/neonlink:latest
```

- Replace {80} with any port you like.
- Replace {/path/to/data} with the path to the data folder with private data
- Replace {/path/to/backgrounds} with the path to the background images folder

Or you can install with `docker-compose.yml` file

```sh
#clone repo
git clone https://github.com/neocwoobie/neonlink.git
cd neonlink

#edit docker-compose.yml and run docker compose
docker-compose up -d
```

## Android PWA Share Target

This fork adds an Android-friendly PWA share target. After NeonLink is installed as a PWA on Android, it can appear in the Android share menu. Shared links open a small `/share` page where you can save the link to an existing group or create a new group during the save flow.

### What changed

- Added a web manifest `share_target` pointing to `/share`.
- Added a minimal service worker so Android Chrome can install NeonLink as a PWA.
- Added a `/share` page for incoming shared links.
- Added `POST /api/share` on the server to save a shared URL, create a new group when requested, attach tags, and handle duplicate URLs gracefully.

### Deploying this fork on Unraid

The official `alexscifier/neonlink` Docker image does not include this PWA sharing feature. To use Android sharing, run an image built from this fork.

One simple approach is to build the image on your Unraid host:

```sh
git clone https://github.com/neocwoobie/neonlink.git
cd neonlink
docker build -t neonlink-pwa:latest .
```

Then update your Unraid Docker container:

- Repository/image: `neonlink-pwa:latest`
- Container port: `3333`
- Host port: any port you prefer, for example `3333` or `80`
- Data volume: keep your existing NeonLink data mapping to `/app/data`
- Background volume: keep your existing background mapping to `/app/public/static/media/background`

Keeping the same `/app/data` volume preserves your existing bookmarks and settings.

If you use the included `docker-compose.yml`, it builds `neonlink-pwa:latest` from this repository instead of pulling the official image:

```sh
docker compose up -d --build
```

### Android setup

1. Connect your Android phone to Tailscale.
2. Open your NeonLink URL in Chrome. HTTPS is recommended for reliable PWA installation and share-target behavior.
3. Log in to NeonLink.
4. In Chrome, open the menu and choose `Install app` or `Add to Home screen`.
5. Open the installed NeonLink app from your Android home screen once.
6. Open another app or webpage, tap Android share, then choose `NeonLink`.
7. Confirm the URL, choose an existing group or type a new group name, then tap `Save`.

If NeonLink does not appear in the Android share menu:

- Make sure you are running this fork, not the official Docker image.
- Make sure NeonLink was installed as a PWA from Chrome.
- Prefer an HTTPS URL, especially when using Tailscale.
- Remove the home-screen app and install it again after updating the container.
- Reopen Chrome or reboot Android if the share menu cache is stale.

## Development

This project is open source, so you can change it or contribute. The application consists of two parts. The frontend is based on the [React](https://reactjs.org/) framework. The server part is based on the [Fastify](https://www.fastify.io/) framework. [Sqlite](https://www.sqlite.org/index.html) is used as a data base and its implementation for Nodejs is [better-sqlite3](https://github.com/WiseLibs/better-sqlite3).

This project requires Nodejs and npm.

For Windows development, it is recommended to use git bash.

### Setup

```sh
# Clone project
git clone https://github.com/AlexSciFier/neonlink.git
cd neonlink

# Install fastify-cli
npm install fastify-cli --global

# Run once to install dependencies
npm run dev-init

# Run dev server
npm run dev-start
```

## Build

Neonlink uses multiarch build. That means you need to use [buildx](https://docs.docker.com/engine/reference/commandline/buildx_build/). Or you can use [BuildKit](https://docs.docker.com/build/buildkit/) to build image for one platform.
To build your own docker container run in root folder

### Build multiarch image
```sh
docker buildx build --platform linux/arm/v7,linux/amd64 --push --tag alexscifier/neonlink:latest .
```

### Build for one platform
```sh
# Linux shell
DOCKER_BUILDKIT=1 docker build --tag alexscifier/neonlink:latest .
```
```sh
# Windows PowerShell
$env:DOCKER_BUILDKIT=1; docker build -t alexscifier/neonlink .
```

## Screenshots

![Dashboard](https://raw.githubusercontent.com/AlexSciFier/neonlink/master/docs/Dashboard.png)
![Links dark](https://raw.githubusercontent.com/AlexSciFier/neonlink/master/docs/Links%20dark.png)
![Links light](https://raw.githubusercontent.com/AlexSciFier/neonlink/master/docs/Links%20light.png)
