# JavaCard Express :: Secure Messaging

ISO 7816-4 Secure Messaging with DES3 (ePassport BAC) and AES (PACE/EAC) algorithm suites. Wraps and unwraps APDUs with DO87/DO97/DO8E/DO99 data objects, handles Send Sequence Counter (SSC) incrementing automatically. Part of the [JavaCard Express](../README.md) toolkit.

> **Note:** ISO 7816-4 Secure Messaging is a different protocol from GlobalPlatform SCP. SM is used by ePassports (ICAO 9303, BAC/PACE), national ID cards, and other non-GP applications. For GP secure channels, see the [GlobalPlatform module](../gp/README.md).

## Installation

```xml
<dependency>
    <groupId>name.velikodniy</groupId>
    <artifactId>javacard-express-sm</artifactId>
    <version>0.1.0</version>
    <scope>test</scope>
</dependency>
```

Depends on `javacard-express-core` (pulled transitively).

## Table of Contents

- [Algorithm Suites](#algorithm-suites)
- [Basic Usage](#basic-usage)
- [Low-Level Codec](#low-level-codec)
- [SM Data Objects](#sm-data-objects)
- [ePassport BAC Example](#epassport-bac-example)
- [Integration with PACE](#integration-with-pace)
- [See Also](#see-also)

## Algorithm Suites

Two algorithm suites are supported:

| Suite | Encryption | MAC | Block Size | Use Case |
|-------|-----------|-----|-----------|----------|
| `SMAlgorithm.DES3` | 3DES-CBC | Retail MAC (ISO 9797-1 Alg 3) | 8 bytes | ePassport BAC |
| `SMAlgorithm.AES` | AES-CBC | AES-CMAC (truncated to 8 bytes) | 16 bytes | PACE / EAC |

## Basic Usage

Wrap an existing `SmartCardSession` with Secure Messaging — all `send()` calls are automatically protected:

```java
import name.velikodniy.jcexpress.sm.*;

// Set up SM context with keys and initial SSC
SMKeys keys = new SMKeys(encKey, macKey);
SMContext ctx = new SMContext(SMAlgorithm.DES3, keys, initialSsc);

// Wrap a session — all send() calls are now SM-protected
SMSession secure = SMSession.wrap(card, ctx);

// All commands are automatically wrapped with DO87/DO97/DO8E
// All responses are automatically unwrapped: MAC verified, data decrypted
APDUResponse response = secure.send(0x00, 0xB0, 0x00, 0x00);
```

The SSC is incremented automatically on each command and response. You never need to manage it manually.

## Low-Level Codec

For fine-grained control, use `SMCodec` directly:

```java
// Wrap a single command
byte[] plainApdu = Hex.decode("00A4040007A0000002471001");
byte[] wrappedApdu = SMCodec.wrapCommand(ctx, plainApdu);
// -> 0C A4 04 00 Lc DO87 DO8E 00

// Unwrap a single response
APDUResponse unwrapped = SMCodec.unwrapResponse(ctx, rawResponseBytes);
// MAC verified, data decrypted, SW extracted from DO99
```

### Wrap/Unwrap Cycle

Manual wrap and unwrap with hex output at each step:

```java
// Set up context
byte[] encKey = Hex.decode("979EC13B1CBFE9DCD01AB0FED307EAE5");
byte[] macKey = Hex.decode("F1CB1F1FB5ADF208806B89DC579DC1F8");
byte[] ssc    = Hex.decode("887022120C06C226");

SMContext ctx = new SMContext(SMAlgorithm.DES3, new SMKeys(encKey, macKey), ssc);

// Wrap a READ BINARY command: 00 B0 00 00 (Le=04)
byte[] plain = Hex.decode("00B0000004");
byte[] wrapped = SMCodec.wrapCommand(ctx, plain);
// Result: 0C B0 00 00 0D 97 01 04 8E 08 <mac bytes> 00
//  - CLA changed:  0x00 -> 0x0C (SM indicator)
//  - DO97:         Le byte (04) wrapped in tag 97
//  - DO8E:         8-byte MAC over SSC + padded header + DO97
//  - Le:           0x00 appended

// Send the wrapped command, get raw response bytes
byte[] rawResponse = card.transmit(wrapped);

// Unwrap the response — verifies MAC, decrypts data, extracts SW
APDUResponse response = SMCodec.unwrapResponse(ctx, rawResponse);
```

## SM Data Objects

The wrapped APDU contains these ISO 7816-4 data objects:

| Tag | Name | Description |
|-----|------|-------------|
| `0x87` | DO87 | Encrypted data: `0x01` (padding indicator) + encrypted(padded(data)) |
| `0x97` | DO97 | Le byte from the original command |
| `0x8E` | DO8E | 8-byte MAC over SSC + padded header + DOs |
| `0x99` | DO99 | Status word (SW1 SW2) from the card response |

The CLA byte is modified to indicate SM: `CLA' = (CLA & 0xF0) | 0x0C`.

### Anatomy of a Wrapped Command

```
Original:  00 B0 00 04 00              (READ BINARY, offset=4, Le=0)
Wrapped:   0C B0 00 04 0D 97 01 00 8E 08 [MAC] 00
           ── ── ── ── ── ── ── ── ── ── ────── ──
           │  │  │  │  │  │  │  │  │  │  │      └─ Le (always 0x00)
           │  │  │  │  │  │  │  │  │  │  └──────── 8-byte MAC
           │  │  │  │  │  │  │  │  │  └─────────── DO8E tag
           │  │  │  │  │  │  │  │  └────────────── 8 (MAC length)
           │  │  │  │  │  │  │  └───────────────── Le value (0x00 = 256)
           │  │  │  │  │  │  └──────────────────── 1 (Le length)
           │  │  │  │  │  └─────────────────────── DO97 tag
           │  │  │  │  └────────────────────────── Lc (total DO length)
           │  │  │  └───────────────────────────── P2
           │  │  └──────────────────────────────── P1
           │  └─────────────────────────────────── INS (preserved)
           └────────────────────────────────────── CLA' = (0x00 & 0xF0) | 0x0C
```

## ePassport BAC Example

After performing Basic Access Control (BAC) key agreement, you have session keys and an initial SSC:

```java
// BAC session keys derived from MRZ data
byte[] ksEnc = deriveKsEnc(kSeed);  // 16-byte 3DES encryption key
byte[] ksMac = deriveKsMac(kSeed);  // 16-byte 3DES MAC key
byte[] ssc = computeInitialSSC(rndIcc, rndIfd);  // 8-byte SSC

SMSession passport = SMSession.wrap(card,
    new SMContext(SMAlgorithm.DES3, new SMKeys(ksEnc, ksMac), ssc));

// Read EF.COM
passport.send(0x00, 0xB0, 0x00, 0x00, null, 256);
```

### Complete BAC Flow

```java
// 1. Derive K_seed from MRZ fields (document number, date of birth, date of expiry)
byte[] kSeed = PaceMrz.computeKSeed("L898902C<", "690806", "940623");

// 2. Derive BAC session keys
SMKeys bacKeys = PaceMrz.deriveKeys(kSeed, 16);  // 16-byte 3DES keys

// 3. Compute initial SSC from mutual authentication nonces
//    SSC = last 4 bytes of RND.ICC || last 4 bytes of RND.IFD
byte[] ssc = computeInitialSSC(rndIcc, rndIfd);

// 4. Create SM session with DES3 algorithm
SMSession passport = SMSession.wrap(card,
    new SMContext(SMAlgorithm.DES3, bacKeys, ssc));

// 5. Read EF.COM (master file listing available data groups)
APDUResponse efCom = passport.send(0x00, 0xB0, 0x80 | 0x1E, 0x00, null, 256);

// 6. Read DG1 (MRZ data group)
APDUResponse dg1 = passport.send(0x00, 0xB0, 0x80 | 0x01, 0x00, null, 256);
```

## Integration with PACE

After PACE authentication (see the [PACE module](../pace/README.md)), `PaceResult.toSMSession()` creates an `SMSession` with AES keys:

```java
import name.velikodniy.jcexpress.pace.*;

// Perform PACE (see pace/README.md for full details)
PaceResult result = PaceSession.builder()
    .algorithm(PaceAlgorithm.ECDH_GM_AES_CBC_CMAC_128)
    .parameterId(PaceParameterId.BRAINPOOL_P256R1)
    .mrzPassword("L898902C<", "690806", "940623")
    .build()
    .perform(card);

// Convert to SM session — keys and SSC are set up automatically
SMSession secure = result.toSMSession(card);

// All commands now use AES Secure Messaging
APDUResponse r = secure.send(0x00, 0xB0, 0x00, 0x00, null, 256);
assertThat(r).isSuccess();
```

## See Also

- [PACE module](../pace/README.md) — PACE authentication that produces SM session keys
- [Core module](../core/README.md) — SmartCardSession, APDU builder, assertions
- [Project root](../README.md) — overview, modules, configuration
