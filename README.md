![maven](https://github.com/pvanassen/netscaler-autologin/actions/workflows/maven.yml/badge.svg)

# Netscaler autologin

A simple reverse-proxy which forwards GET requests to a server behind a NetScaler and handle the login for you. 

## Docker image availability

Image is available through GitHub packages (`ghcr.io/pvanassen/netscaler-autologin:latest`) for both `amd64` as well as 
`arm64` architecture. 

## Example use: maven

Start a docker(compose) with the `NETSCALER_AUTOLOGIN_URL` environment variable set to the server to forward requests to 
and `NETSCALER_KEEPALIVE_PATH` to a path that will return `302` when logged out. 

```yaml
version: "3.6"
services:
  netscaler-proxy:
    image: "ghcr.io/pvanassen/netscaler-autologin:latest"
    ports:
      - "12345:8080/tcp"
    environment:
      - NETSCALER_AUTOLOGIN_URL=https://nexus-behind-netscaler.local
      - NETSCALER_KEEPALIVE_PATH=/does-not-redirect
    restart: "unless-stopped"
```

Configure `settings.xml`, create a server with your username/password and either create a full mirror or define just your repositories as one would do normally
```xml

<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 http://maven.apache.org/xsd/settings-1.0.0.xsd">
   <servers>
      <server>
         <id>nexus-behind-netscaler</id>
         <username>username</username>
         <password>password</password>
      </server>
   </servers>
   <mirrors>
      <mirror>
          <id>nexus-behind-netscaler</id>
         <name>Local proxy of repo behind netscaler</name>
         <url>http://localhost:12345/repository/maven-public</url>
         <mirrorOf>*</mirrorOf>
      </mirror>
   </mirrors>
</settings>
```

All requests are now passed through the proxy which performs the login for you. 

