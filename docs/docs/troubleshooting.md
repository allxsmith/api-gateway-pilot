---
sidebar_position: 6
title: Troubleshooting
---

# Troubleshooting

Real issues hit while bringing the prototype up, with their fixes. Each was a
gotcha worth recording.

## `make up` fails with "unknown shorthand flag: 'd' in -d"

**Symptom**

```
$ make up
docker compose up -d
unknown shorthand flag: 'd' in -d
make: *** [up] Error 125
```

**Cause**

Docker Desktop is installed but has never been launched. The `docker compose`
plugin only gets symlinked into place on first launch — until then, Docker
treats `compose up -d` as raw arguments and chokes on `-d`.

**Fix**

```sh
open -a Docker
```

Wait for the menu-bar whale icon to stop animating, then re-run `make up`.
Confirm with `docker compose version` — it should print `Docker Compose version
v2.x`.

## Maven Docker build fails with "Child module does not exist"

**Symptom**

The `auth-server` or `resource-api` image build fails at the Maven step:

```
[ERROR] Child module /workspace/resource-api of /workspace/pom.xml does not exist
[ERROR] The build could not read 1 project
```

**Cause**

The parent POM lists both `auth-server` and `resource-api` in `<modules>`. The
service's Dockerfile only copied its own module's `pom.xml` into the build
context, so Maven sees the sibling module declared but missing and refuses to
start the reactor. `-pl <one> -am` controls what Maven *builds*, but every
module listed in the parent still has to be *readable*.

**Fix** *(already in the committed Dockerfiles)*

Copy every module's POM into the build context; copy only the chosen service's
`src/`, so only it gets built:

```dockerfile
COPY pom.xml ./
COPY auth-server/pom.xml auth-server/pom.xml
COPY resource-api/pom.xml resource-api/pom.xml
COPY auth-server/src auth-server/src
RUN mvn -pl auth-server -am -DskipTests clean package
```

## Sign-in redirects to `http://localhost/login` and ERR_CONNECTION_REFUSED

**Symptom**

Clicking **Sign in** on the SPA redirects the browser to
`http://localhost/login` (port 80) instead of the nginx-fronted
`http://localhost:8088/login`. Port 80 isn't served, so the browser shows
`ERR_CONNECTION_REFUSED`.

**Cause**

The nginx config was passing `Host: $host` and `X-Forwarded-Host: $host` —
`$host` is the hostname only, with the port stripped. Spring is configured for
`forward-headers-strategy: framework`, so it rebuilds request URLs from the
forwarded headers. With no port indicator, it defaulted to `80` for `http`.

**Fix** *(already in the committed `nginx/nginx.conf`)*

Use `$http_host` (the original `Host` header verbatim, port included) and add
an explicit `X-Forwarded-Port`:

```nginx
proxy_set_header Host             $http_host;
proxy_set_header X-Forwarded-Host $http_host;
proxy_set_header X-Forwarded-Port $server_port;
```

If you ever put another Spring app behind a reverse proxy on a non-default
port, this is the trio of headers that has to be right or OAuth redirects will
break in confusing ways.

## Homebrew AWS CLI fails with a pyexpat symbol error

**Symptom**

```
aws: [ERROR]: dlopen(.../pyexpat.cpython-314-darwin.so):
  Symbol not found: _XML_SetAllocTrackerActivationThreshold
```

**Cause**

Homebrew's Python 3.14 was built against a newer expat (2.8+) that has this
symbol, but its `pyexpat.so` is linked to `/usr/lib/libexpat.1.dylib` — macOS's
system libexpat, which is older. Anything depending on brew Python's XML parser
breaks, AWS CLI included.

**Fix**

Don't install AWS CLI through Homebrew on macOS. Use AWS's official installer
— it bundles its own Python and is what AWS docs recommend:

```sh
brew uninstall awscli
curl -L https://awscli.amazonaws.com/AWSCLIV2.pkg -o /tmp/AWSCLIV2.pkg
sudo installer -pkg /tmp/AWSCLIV2.pkg -target /
# or: open /tmp/AWSCLIV2.pkg   # GUI installer
```

Brew stays the right choice for Terraform, Node, JDK, Docker Desktop, etc. —
AWS CLI is the outlier worth handling specially.
