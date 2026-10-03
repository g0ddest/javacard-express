# JavaCard Express :: Container

Run JavaCard sessions against jCardSim in a Docker container, with the same `SmartCardSession` API as embedded mode.
The applet runs in a separate JVM inside the container; the test talks to it over a small TCP protocol. Built on
Testcontainers. Part of the [JavaCard Express](../README.md) toolkit.

> Container mode is **not** a PC/SC stack: there is no `pcscd`, no `vpcd` and no `javax.smartcardio` reader inside
> the container. The server executes commands directly on jCardSim. To test through a real PC/SC reader, use the
> PC/SC backend of the core module.

## Installation

```xml
<dependency>
    <groupId>name.velikodniy</groupId>
    <artifactId>javacard-express-container</artifactId>
    <version>0.4.0</version>
    <scope>test</scope>
</dependency>
```

Depends on `javacard-express-core` and `org.testcontainers:testcontainers` (both pulled transitively). The simulator
server (server classes and jCardSim, see `META-INF/THIRD-PARTY-NOTICES.txt` inside it) is bundled in this artifact.

## Table of Contents

- [Prerequisites](#prerequisites)
- [Quick Start — Annotation-Based](#quick-start--annotation-based)
- [How a Session Behaves](#how-a-session-behaves)
- [Choosing the Simulator](#choosing-the-simulator)
- [Programmatic Container Management](#programmatic-container-management)
- [Configuration](#configuration)
- [Security and Trust Model](#security-and-trust-model)
- [Embedded vs Container](#embedded-vs-container)
- [See Also](#see-also)

## Prerequisites

**Docker** must be installed and running. Nothing else: on first use the simulator image is built from the server
jar bundled in this artifact on top of an `eclipse-temurin` JRE image (`localhost/jcx-simulator:<hash>`), and reused
afterwards. No checkout of javacard-express, no `docker/` directory and no registry login are needed.

## Quick Start — Annotation-Based

Change the annotation of an embedded-mode test:

```java
import name.velikodniy.jcexpress.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import static name.velikodniy.jcexpress.assertions.JCXAssertions.assertThat;

@ExtendWith(JavaCardExtension.class)
class MyContainerTest {

    @SmartCard(mode = Mode.CONTAINER)
    SmartCardSession card;

    @Test
    void shouldRunInDocker() {
        card.install(MyApplet.class);
        APDUResponse response = card.send(0x80, 0x01);
        assertThat(response).isSuccess();
    }
}
```

For every session `JavaCardExtension`:
1. builds the simulator image if it is not cached yet,
2. starts a container that requires a random access token,
3. connects over TCP (the port is published on `127.0.0.1` when Docker runs locally),
4. injects the `SmartCardSession`, and stops the container when the session is closed.

## How a Session Behaves

- **Each session is one card.** Every connection to the simulator gets its own blank card; sessions sharing a
  container never see each other's applets.
- **Applet classes are shipped automatically.** `install(MyApplet.class)` sends the applet class and every class it
  references that the simulator does not provide itself: superclasses, interfaces, helper classes, nested and
  anonymous classes at any depth (JDK, `javacard.*`, `javacardx.*` and jCardSim classes come from the simulator).
  The simulator JVM must be able to load the class files: the bundled image uses the Java version of the test JVM
  (at least 25).
- **Install parameters** follow the Java Card API (`Applet.install`): the applet receives
  `[Li][instance AID][Lc=0][La][installParams]`, at most 127 bytes in total; longer parameters are rejected with
  `IllegalArgumentException`. Applets that register with `register(bArray, (short) (bOffset + 1), bArray[bOffset])`
  get the requested AID.
- **`reset()` is a card reset**: applets and their persistent state survive, `CLEAR_ON_RESET` transient memory is
  cleared and no applet is selected until the next `select(...)`. Installing a second applet under an AID that is in
  use throws `IllegalStateException`.
- **Commands** are encoded with `APDUCodec` exactly as in embedded mode (`le = SmartCardSession.NO_LE` (-1): no
  Le field, `256`: short `Le = '00'`).
- **SELECT** behaves as in embedded mode: `select(...)` and the install methods throw `SelectException` when the
  card answers an error status (e.g. `6999` from an applet whose `select()` returns false) or when no applet
  installed in the session matches the AID (jCardSim then hands the SELECT to the applet that is already selected).
- **Logical channels**: jCardSim has only the basic channel, so commands whose CLA codes another channel and
  MANAGE CHANNEL throw `UnsupportedOperationException` instead of reaching the applet on the basic channel (same as
  embedded mode).
- **Failures inside the simulator are rethrown locally**: a `javacard.framework` exception (`ISOException`,
  `SystemException`, ...) with its reason code, or a `java.*` runtime exception with its message, each with a
  `SimulatorException` cause that names the remote exception, its cause chain and any classes the applet needed
  but that were not sent. Other failures (for example a `LinkageError` while defining the applet classes) are thrown
  as `SimulatorException`. Transport problems are `UncheckedIOException`s.
- **Timeouts**: every request waits at most 60 seconds for its reply (`-Djcx.container.timeout=<seconds>`). An applet
  stuck in an endless loop fails the call instead of hanging the build; the session is closed afterwards. The stuck
  applet keeps one server thread busy until the container stops, other sessions are not affected.

## Choosing the Simulator

The simulator a container session uses is resolved in this order:

| Source | How | Notes |
|--------|-----|-------|
| Image in the annotation | `@SmartCard(mode = CONTAINER, image = "...")` | Any image running this project's simulator server |
| Image system property | `-Djcx.simulator.image=...` | Same, without changing the code |
| Local `docker/` project | `-Djcx.docker.dir=/path/to/javacard-express/docker` | Uses `target/jcx-simulator.jar` of a built checkout (`cd docker && mvn package`); fails if it is not built |
| Bundled server (default) | nothing | Works in any project |

The release workflow publishes the image as `ghcr.io/g0ddest/jcx-simulator:<version>` (and `latest`); use the image
of the version you depend on (the [changelog](../CHANGELOG.md) names the old images that contain no server and must not
be used). Because the bundled server always matches the client version, the default is the recommended choice.

## Programmatic Container Management

To share one container between tests (each session still gets its own card):

```java
import name.velikodniy.jcexpress.*;
import name.velikodniy.jcexpress.container.*;

class SharedContainerTest {

    private static SmartCardContainer container;

    @BeforeAll
    static void startContainer() {
        container = new SmartCardContainer().withAccessToken(); // bundled simulator, random access token
        container.start();
    }

    @AfterAll
    static void stopContainer() {
        container.stop();
    }

    @Test
    void shouldInstallAndSend() throws Exception {
        try (ContainerSession session = new ContainerSession(container)) { // the container keeps running
            session.install(MyApplet.class, AID.of(0xF0, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06));
            assertThat(session.send(0x80, 0x01)).isSuccess();
        }
    }
}
```

`new SmartCardContainer("image:tag")` runs a pre-built image and `new SmartCardContainer(Path.of(".../docker"))` the
server built by a `docker/` checkout. `new ContainerSession(host, port, container)` connects to a server without an
access token and closes `container` (if not `null`) together with the session.

## Configuration

Client side (system properties):

| Property | Default | Meaning |
|----------|---------|---------|
| `jcx.container.timeout` | `60` | Seconds to wait for the reply to a request |
| `jcx.simulator.image` | — | Pre-built simulator image to use instead of the bundled server |
| `jcx.docker.dir` | — | `docker/` project whose `target/` holds a built server jar |
| `jcx.simulator.baseImage` | `eclipse-temurin:<max(25, test JVM)>-jre` | Base image of the image built from the bundled server |

Server side (environment variables of the container; `java -jar server.jar [port [bindAddress]]` outside Docker):

| Variable | Default | Meaning |
|----------|---------|---------|
| `JCX_BIND_ADDRESS` | loopback (`0.0.0.0` in the images) | Listen address |
| `JCX_TOKEN` | none | Access token clients must present first |
| `JCX_MAX_SESSIONS` | `16` | Concurrent sessions; a further client waits 2 s, then gets "Simulator busy" |

## Security and Trust Model

The simulator executes the class files its clients send — that is its purpose — so whoever can talk to its port can
run code in the container. Container mode limits who that is and what the code can do:

- `@SmartCard(mode = CONTAINER)` (and `SmartCardContainer.withAccessToken()`) gives every container a random access
  token; connections that do not present it are refused. The token is visible only to those who can inspect the
  container, i.e. who control Docker anyway.
- With a local Docker daemon the port is published on `127.0.0.1` only. Inside the container the server listens on
  all interfaces of the container, which is required for the published port to reach it; run outside Docker it
  listens on the loopback interface unless told otherwise.
- The server runs as an unprivileged user (`65534`, nobody), with all Linux capabilities dropped and
  `no-new-privileges`.

The container is a test fixture, not a security boundary: run only applets you would run in your test JVM.

## Embedded vs Container

| Aspect | Embedded | Container |
|--------|----------|-----------|
| **Startup** | ~50 ms | ~1-2 s per container (image cached), more on the first build |
| **Process** | Applet runs in the test JVM | Applet runs in a separate JVM inside a container |
| **Transport** | Direct calls into jCardSim | Small TCP protocol to jCardSim (no PC/SC) |
| **Dependencies** | jCardSim | Docker |
| **Debugging** | Direct stack traces | Exceptions rethrown locally with the remote cause chain |
| **Card state** | One card per session | One card per session |
| **Use case** | Unit tests, fast feedback | Applet code kept out of the test JVM (own class path; a crash or hang does not take the test JVM down) |

**Recommendation:** use embedded mode for most tests. Use container mode when the applet must not share the test
JVM, for example because of class path conflicts or code that may crash or hang a JVM.

## See Also

- [Core module](../core/README.md) — SmartCardSession interface, all features work in both modes
- [Project root](../README.md) — overview, modules, configuration
