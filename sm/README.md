# JavaCard Express :: Secure Messaging

[![Maven Central](https://img.shields.io/maven-central/v/name.velikodniy/javacard-express-sm)](https://search.maven.org/artifact/name.velikodniy/javacard-express-sm)
[![javadoc](https://javadoc.io/badge2/name.velikodniy/javacard-express-sm/javadoc.svg)](https://javadoc.io/doc/name.velikodniy/javacard-express-sm)

ISO/IEC 7816-4 Secure Messaging as profiled by ICAO Doc 9303-11, Section 9.8 — the secure channel of ePassports and
eID cards after Basic Access Control (3DES) or PACE (AES). Commands are wrapped with DO'87'/DO'85', DO'97' and
DO'8E'; responses are MAC-verified and decrypted, and the Send Sequence Counter (SSC) is kept in step automatically.
Part of the [JavaCard Express](../README.md) toolkit.

> **Note:** ISO 7816-4 Secure Messaging is a different protocol from GlobalPlatform SCP. For GP secure channels, see
> the [GlobalPlatform module](../gp/README.md). BAC and PACE themselves live in the [PACE module](../pace/README.md).

## Installation

```xml
<dependency>
    <groupId>name.velikodniy</groupId>
    <artifactId>javacard-express-sm</artifactId>
    <version>0.4.0</version>
    <scope>test</scope>
</dependency>
```

Depends on `javacard-express-core` (pulled transitively).

## Table of Contents

- [Scope](#scope)
- [Algorithm Suites](#algorithm-suites)
- [Basic Usage](#basic-usage)
- [Session Lifecycle and Errors](#session-lifecycle-and-errors)
- [Low-Level Codec](#low-level-codec)
- [SM Data Objects](#sm-data-objects)
- [After BAC or PACE](#after-bac-or-pace)
- [See Also](#see-also)

## Scope

Implemented (ICAO Doc 9303-11, 9.8, on top of ISO/IEC 7816-4):

- SM class byte with the logical channel and chaining bits kept (ISO/IEC 7816-4 5.4.1); the header is always MACed
- DO'87' for even INS, DO'85' for odd INS (9.8.4), DO'97' with one or two bytes, DO'8E' with an 8-byte MAC
- protected responses with any status word (e.g. `6282` at end of file), DO'99' required
- short and extended APDUs, chosen per command (extended when the protected body exceeds 255 bytes or Ne exceeds 256)
- 3DES with zero IV (9.8.6) and AES with `IV = E(KSEnc, SSC)` (9.8.7)

Not implemented: other ISO/IEC 7816-4 SM formats (e.g. responses without DO'99', control reference templates) and
GlobalPlatform SCP. When new session keys are agreed under Secure Messaging (e.g. Chip Authentication, 9.8.2), wrap
the plain session again with a new `SMContext`.

Every rule above is checked against the ICAO worked example (App. D.4) and against vectors from an independent
reference of ICAO 9303-11 (`src/test/python/icao_ref.py`).

## Algorithm Suites

| Suite | Encryption | MAC | Block / SSC size | Initial SSC | Established by |
|-------|------------|-----|------------------|-------------|----------------|
| `SMAlgorithm.DES3` | two-key 3DES-CBC, zero IV | ISO/IEC 9797-1 MAC algorithm 3 (retail MAC) | 8 bytes | `RND.IC[4..7] ‖ RND.IFD[4..7]` | BAC |
| `SMAlgorithm.AES` | AES-CBC, `IV = E(KSEnc, SSC)` | AES-CMAC, 8 bytes | 16 bytes | zero | PACE |

## Basic Usage

Wrap a `SmartCardSession`; every `send()`/`transmit()` is then protected:

```java
import name.velikodniy.jcexpress.sm.*;

SMContext ctx = new SMContext(SMAlgorithm.DES3, new SMKeys(ksEnc, ksMac), ssc);
SMSession secure = SMSession.wrap(card, ctx);

secure.send(0x00, 0xA4, 0x02, 0x0C, Hex.decode("011E"));             // SELECT EF.COM
APDUResponse head = secure.send(0x00, 0xB0, 0x00, 0x00, null, 4);    // READ BINARY, Ne = 4
```

`le` is the expected response length Ne of the plain command: `-1` for none, `256` for Le `'00'`. A protected
response must also fit DO'87', DO'99' and DO'8E', so with short APDUs read at most **223 bytes** (AES) or **231
bytes** (3DES) per command; ask for more than 256 bytes to get an extended protected command instead.

## Session Lifecycle and Errors

The chip ends Secure Messaging when it detects an SM error or receives a plain APDU, and deletes its session keys
(ICAO 9303-11, 9.8.3 and 9.8.5). `SMSession` mirrors this:

| Event | Result |
|-------|--------|
| Protected response with a non-9000 status (e.g. `6282`, `6A82`) | MAC verified, data decrypted, status from DO'99' returned |
| Response MAC wrong or response malformed | `SMException`; the SSC stays counted, in step with the chip |
| Bare status word (an SM error reported without SM) | context terminated; the status word is returned once |
| Bare `9000` | context terminated and `SMException` (never accepted as authenticated) |
| `select()`, `install()`, `reset()`, `close()` on the `SMSession` | delegated in plain, then the context is terminated |

After termination every `send()` fails with an `SMException` that names the cause; start a new session (BAC, PACE)
and a new context. To select a file or application while staying protected, send SELECT through `send()`.

## Low-Level Codec

`SMCodec` wraps and unwraps single APDUs. The values below are the 3DES example of ICAO Doc 9303-11 App. D.4
(session keys and SSC from App. D.3):

```java
SMContext ctx = new SMContext(SMAlgorithm.DES3,
        new SMKeys(Hex.decode("979EC13B1CBFE9DCD01AB0FED307EAE5"),    // KSEnc
                   Hex.decode("F1CB1F1FB5ADF208806B89DC579DC1F8")),   // KSMAC
        Hex.decode("887022120C06C226"));                              // SSC

byte[] wrapped = SMCodec.wrapCommand(ctx, Hex.decode("00A4020C02011E"));   // SELECT EF.COM
// 0CA4020C 15 8709016375432908C044F6 8E08BF8B92D635FF24F8 00

APDUResponse response = SMCodec.unwrapResponse(ctx, Hex.decode("990290008E08FA855A5D4C50A8ED9000"));
// response.sw() == 0x9000, ctx.ssc() == 887022120C06C228
```

## SM Data Objects

| Tag | In | Content |
|-----|----|---------|
| `0x87` | command (even INS), response | `01` (padding-content indicator) ‖ encrypted padded data |
| `0x85` | command (odd INS), response | encrypted padded data (the plain data field is BER-TLV) |
| `0x97` | command | Ne: one byte for 1–256 (`00` = 256), two bytes for 257–65536 |
| `0x99` | response | status word SW1-SW2, covered by the MAC |
| `0x8E` | both | 8-byte MAC over SSC ‖ (padded header) ‖ the data objects, padded |

The class byte gets the SM indication of ISO/IEC 7816-4 5.4.1 without losing the channel and chaining bits:
`00 → 0C`, `01 → 0D`, `10 → 1C` (first interindustry), `40 → 60`, `4F → 6F` (further interindustry). The reserved
classes `20`–`3F` and `FF` are rejected.

### Anatomy of a Wrapped Command

```
Plain:    00 B0 00 00 04                      READ BINARY, Ne = 4
Wrapped:  0C B0 00 00 0D 97 01 04 8E 08 <MAC> 00
          │           │  └──┬───┘ └────┬────┘ └─ Le' = '00'
          │           │     │          └──────── DO'8E': MAC over SSC ‖ pad(0C B0 00 00) ‖ DO'97'
          │           │     └─────────────────── DO'97': Ne of the plain command
          │           └───────────────────────── Lc' = length of the data objects
          └───────────────────────────────────── CLA' = 0C (SM, header authenticated)
```

## After BAC or PACE

`BacResult` and `PaceResult` from the [PACE module](../pace/README.md) create the matching session:

```java
SMSession passport = BacSession.builder()
        .mrz("L898902C", "690806", "940623")      // document number, date of birth, date of expiry
        .build()
        .perform(card)
        .toSMSession(card);                       // 3DES, SSC from the BAC nonces

SMSession eid = PaceSession.builder()
        .algorithm(PaceAlgorithm.ECDH_GM_AES_CBC_CMAC_128)
        .parameterId(PaceParameterId.BRAINPOOL_P256R1)
        .canPassword("123456")
        .build()
        .perform(card)
        .toSMSession(card);                       // AES, SSC = 0
```

## See Also

- [PACE module](../pace/README.md) — BAC and PACE, which establish the session keys
- [Core module](../core/README.md) — SmartCardSession, APDU encoding, assertions
- [Project root](../README.md) — overview, modules, configuration
