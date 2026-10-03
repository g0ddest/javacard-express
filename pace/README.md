# JavaCard Express :: PACE

[![Maven Central](https://img.shields.io/maven-central/v/name.velikodniy/javacard-express-pace)](https://search.maven.org/artifact/name.velikodniy/javacard-express-pace)
[![javadoc](https://javadoc.io/badge2/name.velikodniy/javacard-express-pace/javadoc.svg)](https://javadoc.io/doc/name.velikodniy/javacard-express-pace)

The inspection-system (terminal) side of the ICAO Doc 9303-11 access control protocols: **PACE** with ECDH Generic
Mapping and AES (Section 4.4) and **Basic Access Control** (Section 4.3). Both return the session keys for the
[Secure Messaging module](../sm/README.md), so you can test ePassport and eID applets and cards end to end. Part of
the [JavaCard Express](../README.md) toolkit.

> Version 0.3.0 on Maven Central behaves differently in several places; see the [changelog](../CHANGELOG.md).

## Installation

```xml
<dependency>
    <groupId>name.velikodniy</groupId>
    <artifactId>javacard-express-pace</artifactId>
    <version>0.4.0</version>
    <scope>test</scope>
</dependency>
```

Depends on `javacard-express-core` and `javacard-express-sm` (both pulled transitively).

## Table of Contents

- [Scope](#scope)
- [PACE](#pace)
  - [Passwords](#passwords)
  - [Algorithms](#algorithms)
  - [Domain Parameters](#domain-parameters)
  - [Reading Data Under Secure Messaging](#reading-data-under-secure-messaging)
  - [Protocol Steps](#protocol-steps)
- [Basic Access Control](#basic-access-control)
- [MRZ Helpers](#mrz-helpers)
- [Errors and Key Handling](#errors-and-key-handling)
- [See Also](#see-also)

## Scope

| Implemented | Not implemented |
|-------------|-----------------|
| PACE with ECDH Generic Mapping, AES-128/192/256 (`id-PACE-ECDH-GM-AES-CBC-CMAC-*`) | PACE with DH, with 3DES, Integrated Mapping, Chip Authentication Mapping |
| All 11 elliptic curves of Table 12 (NIST and Brainpool), built in — no JCA provider support needed | Explicit domain parameters, the DH groups of Table 12 |
| MRZ, CAN, PIN and PUK passwords, or a given password key K&pi; | Reading EF.CardAccess / PACEInfo (choose the algorithm and curve yourself) |
| Validation of the chip's public keys; constant-time token check | Chip Authentication, Terminal Authentication, Active Authentication (EAC) |
| BAC with 3DES session keys and SSC | |

Tests replay the ICAO worked examples byte for byte — App. G.1 for PACE (brainpoolP256r1) and App. D for BAC and
3DES Secure Messaging — and run both protocols against independent chip simulators with random keys (PACE on every
curve of Table 12).

## PACE

```java
import name.velikodniy.jcexpress.pace.*;
import name.velikodniy.jcexpress.sm.SMSession;

PaceResult result = PaceSession.builder()
        .algorithm(PaceAlgorithm.ECDH_GM_AES_CBC_CMAC_128)
        .parameterId(PaceParameterId.BRAINPOOL_P256R1)
        .mrzPassword("T22000129", "640812", "101031")   // document number, date of birth, date of expiry
        .build()
        .perform(card);

SMSession secure = result.toSMSession(card);              // AES Secure Messaging, SSC = 0
secure.send(0x00, 0xA4, 0x04, 0x0C, Hex.decode("A0000002471001"));   // SELECT the eMRTD application
```

### Passwords

| Builder method | Password reference (tag `0x83`) | `f(π)` |
|----------------|--------------------------------|--------|
| `mrzPassword(documentNumber, dateOfBirth, dateOfExpiry)` | `MRZ` (`01`) | SHA-1 of the MRZ_information |
| `canPassword("123456")` | `CAN` (`02`) | ISO/IEC 8859-1 string |
| `pinPassword(pin)` | `PIN` (`03`) | ISO/IEC 8859-1 string (BSI TR-03110) |
| `pukPassword(puk)` | `PUK` (`04`) | ISO/IEC 8859-1 string (BSI TR-03110) |
| `password(PasswordRef ref, byte[] kPi)` | `ref` | — the password key K&pi; itself, used as given |

The builder derives the password key `K_π = KDF(f(π), 3)` (ICAO 9303-11, 9.7.3) with the key length of the
algorithm. MRZ document numbers shorter than nine characters are padded with `<` as printed in the MRZ (`L898902C`
and `L898902C<` are the same); longer ones are used completely.

### Algorithms

| `PaceAlgorithm` | Session keys | OID |
|-----------------|--------------|-----|
| `ECDH_GM_AES_CBC_CMAC_128` | AES-128 | 0.4.0.127.0.7.2.2.4.2.2 |
| `ECDH_GM_AES_CBC_CMAC_192` | AES-192 | 0.4.0.127.0.7.2.2.4.2.3 |
| `ECDH_GM_AES_CBC_CMAC_256` | AES-256 | 0.4.0.127.0.7.2.2.4.2.4 |

### Domain Parameters

The standardized elliptic curves of ICAO 9303-11 Table 12. The ID is the `parameterId` of the chip's PACEInfo and is
sent in tag `0x84` of MSE:Set AT (`PaceParameterId.fromId(13)` finds `BRAINPOOL_P256R1`).

| ID | `PaceParameterId` | ID | `PaceParameterId` |
|----|-------------------|----|-------------------|
| 8 | `NIST_P192` | 14 | `BRAINPOOL_P320R1` |
| 9 | `BRAINPOOL_P192R1` | 15 | `NIST_P384` |
| 10 | `NIST_P224` | 16 | `BRAINPOOL_P384R1` |
| 11 | `BRAINPOOL_P224R1` | 17 | `BRAINPOOL_P512R1` |
| 12 | `NIST_P256` | 18 | `NIST_P521` |
| 13 | `BRAINPOOL_P256R1` | | |

Tag `0x84` is CONDITIONAL (required only when the chip offers several parameter sets); `includeParameterId(false)`
sends the minimal MSE:Set AT of ICAO App. G.1.

### Reading Data Under Secure Messaging

A protected short response has room for at most 223 bytes of plain data under AES (231 under 3DES), so read files
in chunks:

```java
APDUResponse head = secure.send(0x00, 0xB0, 0x80 | 0x1E, 0x00, null, 4);      // EF.COM (SFI 1E): tag and length
APDUResponse chunk = secure.send(0x00, 0xB0, 0x00, 0x04, null, 223);          // next bytes, up to 223
```

Asking for more than 256 bytes makes the protected command extended-length, if the card supports it.

### Protocol Steps

| Step | Command | What happens (ICAO 9303-11) |
|------|---------|-----------------------------|
| 0 | MSE:Set AT `00 22 C1 A4` | select protocol (`80`), password (`83`) and domain parameters (`84`), 4.4.4.1 |
| 1 | GENERAL AUTHENTICATE `10 86 00 00` | encrypted nonce z; `s = D(K_π, z)`, 4.4.3.3 |
| 2 | GENERAL AUTHENTICATE `10 86 00 00` | mapping keys; `G^ = s·G + H` with `H = SK_Map,IFD · PK_Map,IC`, 4.4.3.3.1 |
| 3 | GENERAL AUTHENTICATE `10 86 00 00` | ephemeral keys on `G^`; `KSEnc = KDF(K,1)`, `KSMAC = KDF(K,2)`, 4.4.1, 9.7.4 |
| 4 | GENERAL AUTHENTICATE `00 86 00 00` | authentication tokens, 4.4.3.4 |

Every GENERAL AUTHENTICATE asks for `Le = '00'`. The exchange of ICAO App. G.1 (brainpoolP256r1, MRZ password):

```
>> 00 22 C1 A4 12 80 0A 04007F00070202040202 83 01 01 84 01 0D  << 90 00   (default)
>> 00 22 C1 A4 0F 80 0A 04007F00070202040202 83 01 01           << 90 00   (includeParameterId(false), App. G.1)
>> 10 86 00 00 02 7C 00 00                                     << 7C 12 80 10 <z> 90 00
>> 10 86 00 00 45 7C 43 81 41 <PK_Map,IFD> 00                  << 7C 43 82 41 <PK_Map,IC> 90 00
>> 10 86 00 00 45 7C 43 83 41 <PK_DH,IFD> 00                   << 7C 43 84 41 <PK_DH,IC> 90 00
>> 00 86 00 00 0C 7C 0A 85 08 <T_IFD> 00                       << 7C 0A 86 08 <T_IC> 90 00
```

## Basic Access Control

```java
BacResult bac = BacSession.builder()
        .mrz("L898902C", "690806", "940623")      // document number, date of birth, date of expiry
        .build()
        .perform(card);                           // GET CHALLENGE + EXTERNAL AUTHENTICATE (4.3.4)

SMSession passport = bac.toSMSession(card);       // 3DES Secure Messaging, SSC from the nonces
passport.send(0x00, 0xA4, 0x02, 0x0C, Hex.decode("011E"));    // SELECT EF.COM
```

`BacSession.documentBasicAccessKeys(...)` returns the Document Basic Access Keys KEnc and KMAC (App. D.2), and
`accessKeys(SMKeys)` uses given keys instead of the MRZ. The terminal checks the chip's checksum, RND.IFD and RND.IC
before it derives `KSEnc`/`KSMAC = KDF(K.IFD ⊕ K.IC, 1/2)` and `SSC = RND.IC[4..7] ‖ RND.IFD[4..7]`.

## MRZ Helpers

```java
PaceMrz.mrzInformation("L898902C", "690806", "940623");   // "L898902C<369080619406236" (App. D.2)
PaceMrz.checkDigit("690806");                             // 1
PaceMrz.encodeMrzPassword("T22000129", "640812", "101031"); // K = f(π) = 7E2D2A41...E9032AAD (App. G)
PaceMrz.bacKeySeed("L898902C", "690806", "940623");       // Kseed = 239AB9CB282DAF66231DC5A4DF6BFBAE (App. D.2)
PaceMrz.kdf(k, 3, 16);                                    // KDF(K, c) of 9.7.1, here K_π
```

Document numbers may contain `0-9` and `A-Z` (with trailing `<`); dates are `YYMMDD`, with `<` for unknown parts.
Invalid fields are rejected with an `IllegalArgumentException` that does not repeat the value.

## Errors and Key Handling

- `PaceException` / `BacException`: a command failed (the message names the step and the status word, e.g. `6300`
  for a wrong password), or the chip's response failed a check (malformed data, a public key not on the curve, a wrong
  authentication token or checksum).
- `toString()` of `PaceResult`, `BacResult` and `SMKeys` does not show the keys.
- `PaceSession` and `BacSession` keep their password key or access keys so that they can be performed again (e.g.
  after a reset); call `destroy()` to wipe them.

## See Also

- [Secure Messaging module](../sm/README.md) — the SM session used after PACE or BAC
- [Core module](../core/README.md) — SmartCardSession, TLV, assertions
- [Project root](../README.md) — overview, modules, configuration
