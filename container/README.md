# JavaCard Express :: Container

Run JavaCard sessions against a jCardSim instance in a Docker container. Same `SmartCardSession` API as embedded mode, with full process isolation. Built on Testcontainers. Part of the [JavaCard Express](../README.md) toolkit.

## Installation

```xml
<dependency>
    <groupId>name.velikodniy</groupId>
    <artifactId>javacard-express-container</artifactId>
    <version>0.1.0</version>
    <scope>test</scope>
</dependency>
```

Depends on `javacard-express-core` and `org.testcontainers:testcontainers` (both pulled transitively).

## Table of Contents

- [Prerequisites](#prerequisites)
- [Quick Start — Annotation-Based](#quick-start--annotation-based)
- [Programmatic Container Management](#programmatic-container-management)
- [Using a Pre-Built Image](#using-a-pre-built-image)
- [Embedded vs Container](#embedded-vs-container)
- [See Also](#see-also)

## Prerequisites

1. **Docker** must be installed and running
2. **Build the simulator server** JAR first:

```bash
cd docker && mvn package && cd ..
```

The container image is built automatically on first use from the `docker/` directory (cached for subsequent runs).

## Quick Start — Annotation-Based

The simplest way to use container mode — just change the annotation:

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

The `JavaCardExtension` detects container mode and automatically:
1. Builds the Docker image from `docker/` (if not cached)
2. Starts the container
3. Connects via TCP on port 9876
4. Provides the `SmartCardSession` instance

All core features (assertions, APDU builder, TLV parser, PIN helper, etc.) work identically in container mode.

## Programmatic Container Management

For advanced scenarios — manual container lifecycle, shared container across tests, or custom configuration:

```java
import name.velikodniy.jcexpress.*;
import name.velikodniy.jcexpress.container.*;

import java.nio.file.Path;

class AdvancedContainerTest {

    private static SmartCardContainer container;

    @BeforeAll
    static void startContainer() {
        // Build and start the container from local docker/ directory
        container = new SmartCardContainer(Path.of("docker"));
        container.start();
    }

    @AfterAll
    static void stopContainer() {
        if (container != null) {
            container.stop();
        }
    }

    @Test
    void shouldUseSharedContainer() throws Exception {
        // Create a session connected to the running container
        ContainerSession session = new ContainerSession(
            container.getHost(),
            container.getPort(),
            null,    // container lifecycle managed externally
            false
        );

        session.install(MyApplet.class);
        APDUResponse response = session.send(0x80, 0x01);
        assertThat(response).isSuccess();

        // Reset between tests to get a clean card state
        session.reset();

        session.close();
    }

    @Test
    void shouldInstallWithExplicitAid() throws Exception {
        ContainerSession session = new ContainerSession(
            container.getHost(),
            container.getPort(),
            null,
            false
        );

        AID aid = AID.of(0xF0, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06);
        session.install(MyApplet.class, aid);
        session.select(aid);

        APDUResponse response = session.send(0x80, 0x01);
        assertThat(response).isSuccess();

        session.close();
    }
}
```

## Using a Pre-Built Image

If you have a pre-built Docker image (e.g., from a CI registry), pass the image name to `@SmartCard`:

```java
@SmartCard(mode = Mode.CONTAINER, image = "myregistry/jcx-sim:latest")
SmartCardSession card;
```

This skips the local build step and pulls the image directly.

## Embedded vs Container

| Aspect | Embedded | Container |
|--------|----------|-----------|
| **Startup** | ~50ms | ~5s (first run), ~1s (cached image) |
| **Isolation** | In-process (shared JVM) | Full Docker isolation |
| **Dependencies** | jCardSim JAR only | Docker + simulator server JAR |
| **Debugging** | Direct stack traces | TCP boundary (remote debugging) |
| **CI/CD** | No Docker needed | Docker required on CI runner |
| **Determinism** | Shared JVM state possible | Clean environment per container |
| **Use case** | Unit tests, fast feedback | Integration tests, isolation |

**Recommendation:** Use embedded mode for most tests (fast, simple). Use container mode when you need process isolation or want to test closer to real card deployment scenarios.

## See Also

- [Core module](../core/README.md) — SmartCardSession interface, all features work in both modes
- [Project root](../README.md) — overview, modules, configuration
