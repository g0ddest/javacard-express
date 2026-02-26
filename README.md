# JavaCard Express

A Java toolkit for JavaCard development — APDU encoding, BER-TLV parsing, GlobalPlatform secure channels, ISO 7816-4 Secure Messaging, PACE, and more. Ships with JUnit 5 integration for zero-config applet testing.

## Why JavaCard Express

JavaCard development traditionally involves proprietary tools, physical card readers, and verbose boilerplate. JavaCard Express gives you:

- **One dependency, zero setup** — add `javacard-express-core` and start writing code
- **Fluent, expressive API** — `card.send(0x80, 0x01).requireSuccess().data()` instead of manual byte wrangling
- **Composable decorators** — `card.logged().send(...)`, `card.pin().verify(1, "1234")`
- **Production-grade protocols** — SCP02/SCP03, ISO 7816-4 SM, PACE are fully implemented, not stubbed
- **JUnit 5 integration** — `@SmartCard` annotation manages session lifecycle, or use the API directly without JUnit
- **Dual backend** — embedded jCardSim (~50ms startup) or Docker container (full isolation)

## Quick Start

Add the dependency:

```xml
<dependency>
    <groupId>name.velikodniy</groupId>
    <artifactId>javacard-express-core</artifactId>
    <version>0.1.0</version>
    <scope>test</scope>
</dependency>
```

Write a test:

```java
@ExtendWith(JavaCardExtension.class)
class MyAppletTest {

    @SmartCard
    SmartCardSession card;

    @Test
    void shouldReturnHello() {
        card.install(HelloWorldApplet.class);

        card.send(0x80, 0x01).requireSuccess();

        assertThat(card.send(0x80, 0x01))
            .isSuccess()
            .dataAsString().isEqualTo("Hello");
    }
}
```

Or use the toolkit directly — no JUnit required:

```java
SmartCardSession card = new EmbeddedSession();
card.install(MyApplet.class);

// Fluent APDU construction
byte[] apdu = APDUBuilder.command()
    .cla(0x00).ins(0xA4).p1(0x04).p2(0x00)
    .data(WellKnownAIDs.MRTD.toBytes())
    .build();
APDUResponse response = new APDUResponse(card.transmit(apdu));

// BER-TLV parsing with path navigation
byte[] aid = response.tlv().at(0x6F, 0x84).orElseThrow().value();

// Decorator chaining
LoggingSession logged = card.logged();
PinSession pin = card.pin();
```

## Modules

Five Maven modules — add only what you need:

| Module | Artifact | Purpose | Docs |
|--------|----------|---------|------|
| **Core** | `javacard-express-core` | Sessions, APDU builder, TLV parser, assertions, PIN, logging, AID | [README](core/README.md) |
| **GlobalPlatform** | `javacard-express-gp` | SCP02/SCP03, card management, CAP loading, key diversification | [README](gp/README.md) |
| **Secure Messaging** | `javacard-express-sm` | ISO 7816-4 SM with DES3 (BAC) and AES (PACE/EAC) | [README](sm/README.md) |
| **PACE** | `javacard-express-pace` | PACE (BSI TR-03110 / ICAO 9303) with Generic Mapping | [README](pace/README.md) |
| **Container** | `javacard-express-container` | Docker-based sessions via Testcontainers | [README](container/README.md) |

**Dependency graph:**

```
core ← gp
core ← sm
core ← container
core + sm ← pace
```

Most users only need `core`. Add `gp` for GlobalPlatform, `sm` + `pace` for ePassport/eID, or `container` for Docker isolation.

```xml
<!-- GlobalPlatform support -->
<dependency>
    <groupId>name.velikodniy</groupId>
    <artifactId>javacard-express-gp</artifactId>
    <version>0.1.0</version>
    <scope>test</scope>
</dependency>

<!-- PACE (pulls in sm transitively) -->
<dependency>
    <groupId>name.velikodniy</groupId>
    <artifactId>javacard-express-pace</artifactId>
    <version>0.1.0</version>
    <scope>test</scope>
</dependency>

<!-- Container mode (pulls in Testcontainers transitively) -->
<dependency>
    <groupId>name.velikodniy</groupId>
    <artifactId>javacard-express-container</artifactId>
    <version>0.1.0</version>
    <scope>test</scope>
</dependency>
```

## Features

| Category | What it does |
|----------|-------------|
| **APDU Builder** | Fluent API for ISO 7816-4 commands — short + extended, up to 65535 bytes |
| **APDU Sequence** | Automatic GET RESPONSE chaining (SW=61XX) and Le correction (SW=6CXX) |
| **TLV Parser** | Full BER-TLV: multi-byte tags, constructed elements, path navigation (`at(0x6F, 0x84)`) |
| **Fluent Assertions** | `assertThat(response).isSuccess()` with human-readable SW descriptions |
| **TLV Assertions** | `assertThat(response).tlv().containsTag(0x6F)` with nested navigation |
| **PIN Helper** | Verify, change, unblock — ASCII, BCD, ISO 9564 Format 2 encoding |
| **Logical Channels** | ISO 7816-4 channels 0-3, MANAGE CHANNEL open/close |
| **APDU Logging** | Capture, inspect, and dump APDU exchanges programmatically |
| **Memory Probing** | Measure applet memory footprint (persistent, transient reset, transient deselect) |
| **Well-known AIDs** | Constants for MRTD, Visa, Mastercard, GP ISD, and more |
| **GlobalPlatform** | SCP02/SCP03 secure channel — C-MAC, C-ENC, R-MAC, R-ENC |
| **GP Card Management** | CAP loading, applet install/delete, lifecycle, Security Domains |
| **Key Management** | PUT KEY (3DES / AES), diversification (VISA2, EMV CPS 1.1, KDF3) |
| **Secure Messaging** | ISO 7816-4 SM — DES3 (ePassport BAC) and AES (PACE/EAC) |
| **PACE** | Password Authenticated Connection Establishment with Generic Mapping |
| **Dual Backend** | Embedded (jCardSim, ~50ms) or container (Docker, full isolation) |
| **JUnit 5** | `@SmartCard` annotation + `JavaCardExtension` — or use the API standalone |

## Configuration

### @SmartCard annotation

| Parameter | Default | Description |
|-----------|---------|-------------|
| `mode` | `EMBEDDED` | Backend: `EMBEDDED` (jCardSim in-process) or `CONTAINER` (Docker) |
| `persistentMemory` | `32768` | EEPROM size in bytes (embedded mode) |
| `image` | `""` | Docker image name (empty = build from local `docker/` directory) |
| `log` | `false` | Enable APDU logging (wraps session with `LoggingSession`) |

### System properties

| Property | Description |
|----------|-------------|
| `jcx.log` | Enable APDU logging globally (`true`/`false`) |
| `jcx.docker.dir` | Path to Docker build directory (overrides automatic detection) |

## License

Apache License 2.0
