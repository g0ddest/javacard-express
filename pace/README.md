# JavaCard Express :: PACE

[![Maven Central](https://img.shields.io/maven-central/v/name.velikodniy/javacard-express-pace)](https://search.maven.org/artifact/name.velikodniy/javacard-express-pace)
[![javadoc](https://javadoc.io/badge2/name.velikodniy/javacard-express-pace/javadoc.svg)](https://javadoc.io/doc/name.velikodniy/javacard-express-pace)

PACE (Password Authenticated Connection Establishment) per BSI TR-03110 / ICAO 9303 Part 11. Supports ECDH with Generic Mapping on Brainpool and NIST curves, with AES-CBC-CMAC key agreement. After authentication, the result integrates directly with the [Secure Messaging module](../sm/README.md) for subsequent protected communication. Part of the [JavaCard Express](../README.md) toolkit.

## Installation

```xml
<dependency>
    <groupId>name.velikodniy</groupId>
    <artifactId>javacard-express-pace</artifactId>
    <version>0.1.0</version>
    <scope>test</scope>
</dependency>
```

Depends on `javacard-express-core` and `javacard-express-sm` (both pulled transitively).

## Table of Contents

- [Supported Algorithms](#supported-algorithms)
- [Supported Curves](#supported-curves)
- [Quick Start — MRZ Password](#quick-start--mrz-password)
  - [Complete ePassport Session](#complete-epassport-session)
- [CAN/PIN Password](#canpin-password)
- [MRZ Key Derivation](#mrz-key-derivation)
- [Low-Level EC Operations](#low-level-ec-operations)
- [Protocol Steps](#protocol-steps)
- [See Also](#see-also)

## Supported Algorithms

| Algorithm | Key Length | OID |
|-----------|----------|-----|
| `ECDH_GM_AES_CBC_CMAC_128` | 16 bytes | 0.4.0.127.0.7.2.2.4.2.2 |
| `ECDH_GM_AES_CBC_CMAC_192` | 24 bytes | 0.4.0.127.0.7.2.2.4.2.3 |
| `ECDH_GM_AES_CBC_CMAC_256` | 32 bytes | 0.4.0.127.0.7.2.2.4.2.4 |

## Supported Curves

| Parameter ID | Curve | Bit Size |
|-------------|-------|----------|
| `BRAINPOOL_P256R1` | brainpoolP256r1 | 256 |
| `BRAINPOOL_P384R1` | brainpoolP384r1 | 384 |
| `BRAINPOOL_P512R1` | brainpoolP512r1 | 512 |
| `NIST_P256` | secp256r1 | 256 |
| `NIST_P384` | secp384r1 | 384 |
| `NIST_P521` | secp521r1 | 521 |

## Quick Start — MRZ Password

```java
import name.velikodniy.jcexpress.pace.*;

// Perform PACE with MRZ-derived password
PaceResult result = PaceSession.builder()
    .algorithm(PaceAlgorithm.ECDH_GM_AES_CBC_CMAC_128)
    .parameterId(PaceParameterId.BRAINPOOL_P256R1)
    .mrzPassword("L898902C<", "690806", "940623")  // docNumber, DOB, DOE
    .build()
    .perform(card);

// Use the result for Secure Messaging
SMSession secure = result.toSMSession(card);
APDUResponse response = secure.send(0x00, 0xB0, 0x00, 0x00);
```

### Complete ePassport Session

A full ePassport interaction — PACE authentication followed by reading data groups via Secure Messaging:

```java
import name.velikodniy.jcexpress.*;
import name.velikodniy.jcexpress.pace.*;
import name.velikodniy.jcexpress.sm.*;

import static name.velikodniy.jcexpress.assertions.JCXAssertions.assertThat;

@Test
void shouldReadPassportDataGroups() {
    // Step 1: Perform PACE with MRZ data
    PaceResult result = PaceSession.builder()
        .algorithm(PaceAlgorithm.ECDH_GM_AES_CBC_CMAC_128)
        .parameterId(PaceParameterId.BRAINPOOL_P256R1)
        .mrzPassword("L898902C<", "690806", "940623")
        .build()
        .perform(card);

    // Step 2: Create SM session with derived keys
    SMSession passport = result.toSMSession(card);

    // Step 3: Select the ePassport application (eMRTD)
    passport.send(0x00, 0xA4, 0x04, 0x00,
        Hex.decode("A0000002471001"));

    // Step 4: Read EF.COM (SFI 0x1E) — lists available data groups
    APDUResponse efCom = passport.send(
        0x00, 0xB0, 0x80 | 0x1E, 0x00, null, 256);
    assertThat(efCom).isSuccess();

    // Step 5: Read DG1 (SFI 0x01) — MRZ data
    APDUResponse dg1 = passport.send(
        0x00, 0xB0, 0x80 | 0x01, 0x00, null, 256);
    assertThat(dg1).isSuccess();

    // Step 6: Read DG2 (SFI 0x02) — facial image
    APDUResponse dg2 = passport.send(
        0x00, 0xB0, 0x80 | 0x02, 0x00, null, 256);
    assertThat(dg2).isSuccess();
}
```

## CAN/PIN Password

For eID cards that use CAN (Card Access Number) or PIN instead of MRZ:

```java
// Derive password key externally and provide directly
byte[] canKey = deriveCanKey("123456");

PaceResult result = PaceSession.builder()
    .algorithm(PaceAlgorithm.ECDH_GM_AES_CBC_CMAC_128)
    .parameterId(PaceParameterId.NIST_P256)
    .password(PasswordRef.CAN, canKey)
    .build()
    .perform(card);
```

Available password references:

| Reference | Usage |
|-----------|-------|
| `PasswordRef.MRZ` | Machine Readable Zone (ePassports) |
| `PasswordRef.CAN` | Card Access Number (eID cards) |
| `PasswordRef.PIN` | Personal Identification Number |
| `PasswordRef.PUK` | PIN Unblocking Key |

## MRZ Key Derivation

The `PaceMrz` utility handles ICAO 9303 MRZ key derivation:

```java
// Compute K_seed from MRZ fields
byte[] kSeed = PaceMrz.computeKSeed("L898902C<", "690806", "940623");

// Derive session keys
SMKeys keys = PaceMrz.deriveKeys(kSeed, 16);  // 16 bytes for AES-128

// Check digit computation (ICAO 9303 Part 3)
int cd = PaceMrz.checkDigit("L898902C<");  // -> 3
```

### MRZ Check Digit Validation

```java
// ICAO 9303 check digit computation uses weighted sum (7-3-1 cycle)
assertThat(PaceMrz.checkDigit("L898902C<")).isEqualTo(3);
assertThat(PaceMrz.checkDigit("690806")).isEqualTo(7);
assertThat(PaceMrz.checkDigit("940623")).isEqualTo(6);

// Full MRZ info string: docNumber + checkDigit + DOB + checkDigit + DOE + checkDigit
String mrzInfo = "L898902C<3" + "6908067" + "9406236";
```

## Low-Level EC Operations

The `PaceCrypto` utility provides elliptic curve operations:

```java
// EC point arithmetic (BigInteger-based)
ECPoint sum = PaceCrypto.pointAdd(p, q, curve);
ECPoint product = PaceCrypto.scalarMultiply(k, p, curve);

// Generic Mapping: G' = s * G + H
ECPoint mappedG = PaceCrypto.genericMapping(nonce, generator, sharedPoint, curve);

// ECDH shared secret
byte[] secret = PaceCrypto.ecdh(privateKey, remotePublicPoint, params);

// Authentication token (AES-CMAC, truncated to 8 bytes)
byte[] token = PaceCrypto.authToken(macKey, oidBytes, publicKeyEncoded);
```

## Protocol Steps

The PACE protocol executes five APDU commands:

| Step | Command | What Happens |
|------|---------|-------------|
| 0 | MSE:Set AT | Select algorithm, curve, and password reference |
| 1 | GENERAL AUTHENTICATE | Get encrypted nonce from card, decrypt with K_&pi; |
| 2 | GENERAL AUTHENTICATE | Generic Mapping: ECDH + nonce &rarr; new generator G' |
| 3 | GENERAL AUTHENTICATE | Key agreement on G' &rarr; KDF &rarr; session keys |
| 4 | GENERAL AUTHENTICATE | Mutual authentication via AES-CMAC tokens |

After step 4, `PaceResult.toSMSession()` creates an `SMSession` for ISO 7816-4 Secure Messaging with AES.

### Step-by-Step Breakdown

```
Step 0: MSE:Set AT
  >> 00 22 C1 A4 0F 80 0A <OID> 83 01 <PasswordRef>
  << 90 00

Step 1: Get Encrypted Nonce
  >> 10 86 00 00 02 7C 00 00
  << 7C XX 80 XX <encrypted nonce> 90 00
  -> Decrypt nonce with K_pi (password-derived key)

Step 2: Generic Mapping
  >> 10 86 00 00 XX 7C XX 81 XX <terminal ephemeral public key>
  << 7C XX 82 XX <card ephemeral public key> 90 00
  -> ECDH shared secret H, map generator: G' = s*G + H

Step 3: Key Agreement
  >> 10 86 00 00 XX 7C XX 83 XX <terminal public key on G'>
  << 7C XX 84 XX <card public key on G'> 90 00
  -> ECDH on G', derive K_enc and K_mac via KDF

Step 4: Mutual Authentication
  >> 00 86 00 00 XX 7C XX 85 XX <terminal auth token>
  << 7C XX 86 XX <card auth token> 90 00
  -> Verify card's token, establish SM session
```

## See Also

- [Secure Messaging module](../sm/README.md) — SM session used after PACE authentication
- [Core module](../core/README.md) — SmartCardSession, TLV, assertions
- [Project root](../README.md) — overview, modules, configuration
