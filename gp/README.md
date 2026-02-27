# JavaCard Express :: GlobalPlatform

[![Maven Central](https://img.shields.io/maven-central/v/name.velikodniy/javacard-express-gp)](https://search.maven.org/artifact/name.velikodniy/javacard-express-gp)
[![javadoc](https://javadoc.io/badge2/name.velikodniy/javacard-express-gp/javadoc.svg)](https://javadoc.io/doc/name.velikodniy/javacard-express-gp)

Full GlobalPlatform card management with SCP02 and SCP03 secure channels. Automates INITIALIZE UPDATE + EXTERNAL AUTHENTICATE, then wraps all subsequent commands with C-MAC/C-ENC transparently. Includes key management, key diversification, lifecycle management, Security Domain operations, and CAP file parsing.

## Installation

```xml
<dependency>
    <groupId>name.velikodniy</groupId>
    <artifactId>javacard-express-gp</artifactId>
    <version>0.1.0</version>
    <scope>test</scope>
</dependency>
```

Depends on `javacard-express-core` (pulled transitively).

## Quick Start

```java
// One line — opens a secure channel with default keys (40..41..42..)
GPSession gp = GPSession.on(card).open();

// All commands are auto-wrapped with secure messaging
List<AppletInfo> apps = gp.getStatus();
byte[] iin = gp.getIIN();
byte[] cin = gp.getCIN();

gp.close();
```

No explicit `.keys()` call needed — defaults to `SCPKeys.defaultKeys()`. SCP version (02 or 03) is auto-detected from the card response.

## Table of Contents

- [GPSession](#gpsession)
  - [Secure Channel Configuration](#secure-channel-configuration)
  - [Full Applet Deployment](#full-applet-deployment)
- [SCP03 Pseudo-Random Challenge](#scp03-pseudo-random-challenge-i60)
- [Response Protection](#response-protection)
- [GET DATA — Card Information](#get-data--card-information)
- [Key Management](#key-management)
- [Key Diversification](#key-diversification)
- [Lifecycle Management](#lifecycle-management)
- [Secure Domain Management](#secure-domain-management)
- [CAP File Parsing](#cap-file-parsing)
- [See Also](#see-also)

## GPSession

Open a GlobalPlatform session — sensible defaults, zero configuration:

```java
import name.velikodniy.jcexpress.gp.*;
import name.velikodniy.jcexpress.scp.*;

// Open with defaults — default keys, auto-detect SCP version
GPSession gp = GPSession.on(card).open();

// All subsequent commands are auto-wrapped with secure messaging
List<AppletInfo> apps = gp.getStatus();
for (AppletInfo app : apps) {
    System.out.println(app.aidHex() + " selectable=" + app.isSelectable());
}

// Delete, install, load
gp.deleteAid("A0000000031010");
gp.installForLoad("A000000003", null);  // null SD = ISD
gp.load(CAPFile.from(capBytes));
gp.installForInstall("A000000003", "A000000003", "A0000000031010", 0x00, null);

// Or one-step convenience — performs all three in sequence:
CAPFile cap = CAPFile.fromFile(Path.of("applet.cap"));
gp.loadAndInstall(cap, "A0000000031010", 0x00, null);

gp.close();
```

The authentication flow (INITIALIZE UPDATE + EXTERNAL AUTHENTICATE) is performed by `open()`. After that, every command is wrapped transparently — you never need to handle MAC computation or encryption manually.

### Secure Channel Configuration

```java
GPSession gp = GPSession.on(card)
    .keys(SCPKeys.of(encKey, macKey, dekKey))  // custom key set
    .securityLevel(GP.SECURITY_C_MAC_C_ENC)    // command encryption + integrity
    .keyVersion(0x30)                           // specific key version (P2 in INIT UPDATE)
    .scpVersion(3)                              // force SCP03 (default: auto-detect)
    .open();
```

### Full Applet Deployment

A complete deployment lifecycle — check existing apps, clean up, load, install, and verify:

```java
@Test
void shouldDeployApplet() {
    GPSession gp = GPSession.on(card).open();

    String packageAid = "A000000003";
    String appletAid  = "A0000000031010";

    // Check what's already on the card
    List<AppletInfo> existing = gp.getStatus();
    for (AppletInfo app : existing) {
        System.out.println(app.aidHex() + " " + app.lifeCycleDescription());
    }

    // Delete old version if present (ignore errors if not found)
    try {
        gp.deleteAid(appletAid);
        gp.deleteAid(packageAid);
    } catch (GPException e) {
        // Not found — OK
    }

    // Load and install from CAP file
    CAPFile cap = CAPFile.fromFile(Path.of("applet.cap"));
    gp.loadAndInstall(cap, appletAid, 0x00, null);

    // Verify the applet is installed and selectable
    List<AppletInfo> apps = gp.getStatus();
    AppletInfo installed = apps.stream()
        .filter(a -> a.aidHex().equals(appletAid))
        .findFirst()
        .orElseThrow();
    assertThat(installed.isSelectable()).isTrue();

    gp.close();
}
```

## SCP03 Pseudo-Random Challenge (i=60)

Some cards (NXP JCOP, Infineon) use pseudo-random challenge mode (i=60) instead of the default explicit mode (i=70). In this mode, the INITIALIZE UPDATE response includes a 3-byte sequence counter and a 6-byte card challenge (29 bytes total, vs 28 for explicit):

```java
GPSession gp = GPSession.on(card)
    .scpVersion(3)
    .pseudoRandomChallenge(true)  // enable i=60 mode
    .open();

// Check card info after authentication
CardInfo info = gp.cardInfo();
byte[] seqCounter = info.sequenceCounter();  // 3 bytes (only in i=60)
byte[] cardChallenge = info.cardChallenge(); // 6 bytes (vs 8 in i=70)
```

## Response Protection

### Response MAC (R-MAC)

Verify integrity of card responses — protects against response tampering on the communication channel:

```java
GPSession gp = GPSession.on(card)
    .keys(SCPKeys.defaultKeys())
    .securityLevel(GP.SECURITY_C_MAC_C_ENC_R_MAC)  // C-MAC + C-ENC + R-MAC
    .open();

// All responses are automatically unwrapped:
// 1. 8-byte R-MAC verified
// 2. R-MAC stripped from response data
APDUResponse r = gp.send(0x80, 0xF2, 0x40, 0x00, data);
// r.data() contains clean data (R-MAC verified and stripped)
```

Supported for both SCP02 (retail MAC) and SCP03 (AES-CMAC).

### Response Encryption (R-ENC)

Decrypt card responses — protects against eavesdropping (SCP03 only):

```java
GPSession gp = GPSession.on(card)
    .keys(SCPKeys.defaultKeys())
    .securityLevel(GP.SECURITY_C_MAC_C_ENC_R_MAC_R_ENC)  // full protection
    .open();

// All responses are automatically unwrapped:
// 1. R-MAC verified (over encrypted data)
// 2. AES-CBC decrypted
// 3. ISO 9797-1 Method 2 padding stripped
APDUResponse r = gp.send(0x80, 0xCA, 0x00, 0xCF);
// r.data() contains clean plaintext data
```

### Security Levels

| Constant | Value | Protection |
|----------|-------|-----------|
| `GP.SECURITY_C_MAC` | `0x01` | Command integrity only |
| `GP.SECURITY_C_MAC_C_ENC` | `0x03` | Command integrity + command encryption |
| `GP.SECURITY_C_MAC_R_MAC` | `0x11` | Command + response integrity |
| `GP.SECURITY_C_MAC_C_ENC_R_MAC` | `0x13` | Command + response integrity + command encryption |
| `GP.SECURITY_C_MAC_C_ENC_R_MAC_R_ENC` | `0x33` | Full protection (SCP03 only for R-ENC) |

## GET DATA — Card Information

Query card identification, configuration, and production data:

```java
// Issuer Identification Number / Card Image Number
byte[] iin = gp.getIIN();
byte[] cin = gp.getCIN();

// Card Data with Card Recognition Data (OIDs, capabilities)
CardData cardData = gp.getCardData();
List<String> oids = cardData.oidStrings();     // e.g., "1.2.840.114283.1"
String gpVer = cardData.gpVersion().orElse("unknown");
List<String> scps = cardData.scpVersions();    // supported SCP protocols

// Card Production Life Cycle Data (CPLC)
CPLCData cplc = gp.getCPLC();
System.out.println("Fabricator: " + String.format("%04X", cplc.icFabricator()));
System.out.println("Serial: " + cplc.serialNumberHex());
System.out.println("OS ID: " + String.format("%04X", cplc.osId()));

// Key Information Template (all key sets on the card)
List<KeyInfoEntry> keys = gp.getKeyInformation();
for (KeyInfoEntry key : keys) {
    System.out.println("Key " + key.keyId() + " v" + key.keyVersion()
        + " " + key.components());  // e.g., [DES3(16)]
}

// Sequence counter
int counter = gp.getSequenceCounter();

// Raw GET DATA for any P1/P2 combination
APDUResponse r = gp.getData(0x00, 0xCF);
```

### Complete Card Survey

Query everything in one test:

```java
@Test
void shouldSurveyCard() {
    GPSession gp = GPSession.on(card).open();

    // Identity
    byte[] iin = gp.getIIN();
    byte[] cin = gp.getCIN();
    System.out.println("IIN: " + Hex.encode(iin));
    System.out.println("CIN: " + Hex.encode(cin));

    // Production data
    CPLCData cplc = gp.getCPLC();
    System.out.printf("Fabricator: %04X, OS: %04X, Serial: %s%n",
        cplc.icFabricator(), cplc.osId(), cplc.serialNumberHex());

    // Card capabilities
    CardData cardData = gp.getCardData();
    System.out.println("GP version: " + cardData.gpVersion().orElse("N/A"));
    System.out.println("SCP versions: " + cardData.scpVersions());

    // Key information
    List<KeyInfoEntry> keys = gp.getKeyInformation();
    System.out.println("Key sets: " + keys.size());
    for (KeyInfoEntry key : keys) {
        System.out.printf("  Key %d v%d: %s%n",
            key.keyId(), key.keyVersion(), key.components());
    }

    // Installed applications
    List<AppletInfo> apps = gp.getStatus();
    System.out.println("Applications: " + apps.size());
    for (AppletInfo app : apps) {
        System.out.printf("  %s %s%n",
            app.aidHex(), app.lifeCycleDescription());
    }

    gp.close();
}
```

## Key Management

Replace default keys with custom ones after authentication:

```java
// Replace all three keys (ENC, MAC, DEK) at once
SCPKeys newKeys = SCPKeys.fromMasterKey(myNewKey);
gp.putKeys(newKeys, 0x01);  // new key version = 0x01

// Replace with explicit existing key version
gp.putKeys(newKeys, 0x02, 0x01);  // newVer=2, existingVer=1

// Replace a single key by index
gp.putKey(1, encKeyBytes, 0x02, 0x01);  // index=1 (ENC), newVer, oldVer
```

Key data is encrypted with the session DEK (SCP02) or static DEK (SCP03) and accompanied by a 3-byte Key Check Value (KCV) for verification.

### Key Rotation

Replace default keys and verify the new keys work:

```java
@Test
void shouldRotateKeys() {
    byte[] newMasterKey = Hex.decode("00112233445566778899AABBCCDDEEFF");
    GPSession gp = GPSession.on(card).open();  // default keys

    // Replace all keys with new ones
    SCPKeys newKeys = SCPKeys.fromMasterKey(newMasterKey);
    gp.putKeys(newKeys, 0x01);
    gp.close();

    // Re-authenticate with the new keys
    GPSession gp2 = GPSession.on(card)
        .keys(newKeys)
        .keyVersion(0x01)
        .open();

    // Verify authentication works
    List<AppletInfo> apps = gp2.getStatus();
    assertThat(apps).isNotEmpty();

    gp2.close();
}
```

## Key Diversification

Derive card-specific keys from a master key using standard diversification algorithms:

```java
GPSession gp = GPSession.on(card)
    .keys(SCPKeys.fromMasterKey(masterKey))
    .diversification(KeyDiversification::visa2)  // apply diversification
    .open();
```

Available algorithms:

| Algorithm | Method | Protocol | Description |
|-----------|--------|----------|-------------|
| VISA2 | `KeyDiversification::visa2` | SCP02 (3DES) | VISA Integrated Circuit Card Specification |
| EMV CPS 1.1 | `KeyDiversification::emvCps11` | SCP02 (3DES) | EMV Card Personalization Specification |
| KDF3 | `KeyDiversification::kdf3` | SCP03 (AES) | AES-CMAC based key derivation |

The diversification data (first 10 bytes of the INITIALIZE UPDATE response) is automatically extracted and passed to the algorithm.

## Lifecycle Management

Lock, unlock, and terminate applications and the card itself using SET STATUS (INS=0xF0):

```java
// Application-level operations
gp.lockApp("A0000000031010");      // set LOCKED state (bit 7)
gp.unlockApp("A0000000031010");    // revert to SELECTABLE
gp.terminateApp("A0000000031010"); // terminate (irreversible)

// Card-level operations (Issuer Security Domain)
gp.lockCard();                     // all apps become inaccessible
gp.terminateCard();                // permanent, irreversible

// Low-level SET STATUS with explicit scope and state
gp.setStatus(Lifecycle.SCOPE_APPS, "A0000000031010", Lifecycle.APP_PERSONALIZED);
```

Query lifecycle state via GET STATUS:

```java
List<AppletInfo> apps = gp.getStatus();       // scope=0x40 (apps + SDs)
List<AppletInfo> apps = gp.getStatus(0x80);   // scope=0x80 (ISD only)

for (AppletInfo app : apps) {
    System.out.println(app.aidHex()
        + " " + app.lifeCycleDescription()        // "SELECTABLE (07)"
        + " locked=" + app.isLocked()
        + " personalized=" + app.isPersonalized()
        + " terminated=" + app.isTerminated());
}
```

Lifecycle state constants:

| Constant | Value | Description |
|----------|-------|-------------|
| `Lifecycle.APP_INSTALLED` | `0x03` | Installed but not selectable |
| `Lifecycle.APP_SELECTABLE` | `0x07` | Installed and selectable |
| `Lifecycle.APP_PERSONALIZED` | `0x0F` | Personalized |
| `Lifecycle.APP_LOCKED` | `0x80` | Locked (bit 7 set) |
| `Lifecycle.APP_TERMINATED` | `0xFF` | Terminated (irreversible) |
| `Lifecycle.CARD_OP_READY` | `0x01` | Card Manager: OP_READY |
| `Lifecycle.CARD_INITIALIZED` | `0x07` | Card Manager: INITIALIZED |
| `Lifecycle.CARD_SECURED` | `0x0F` | Card Manager: SECURED |
| `Lifecycle.CARD_LOCKED` | `0x7F` | Card Manager: CARD_LOCKED |
| `Lifecycle.CARD_TERMINATED` | `0xFF` | Card Manager: TERMINATED |

## Secure Domain Management

Query, extradite, personalize, and update Security Domains on the card. These operations are part of the GlobalPlatform Card Specification (primarily GP 2.2+).

```java
// List all Security Domains on the card
List<AppletInfo> domains = gp.getDomains();
for (AppletInfo sd : domains) {
    System.out.println(sd.aidHex()
        + " " + sd.privilegeDescription());     // "SECURITY_DOMAIN | DELEGATED_MANAGEMENT"
}

// List Executable Load Files (packages loaded on the card)
List<AppletInfo> loadFiles = gp.getLoadFiles();

// Extradite — transfer an applet from one Security Domain to another
gp.extradite("A0000000031010", "A000000004");

// Personalize — prepare a Security Domain for use
gp.personalize("A000000004");

// Registry update — change applet privileges (GP 2.2+)
gp.registryUpdate("A0000000031010",
    Privileges.SECURITY_DOMAIN | Privileges.DELEGATED_MANAGEMENT);
```

Privilege bit constants in the `Privileges` class:

| Constant | Value | Description |
|----------|-------|-------------|
| `Privileges.SECURITY_DOMAIN` | `0x80` | Entry is a Security Domain |
| `Privileges.DAP_VERIFICATION` | `0x40` | DAP verification privilege |
| `Privileges.DELEGATED_MANAGEMENT` | `0x20` | Can manage applets (install/delete) |
| `Privileges.CARD_LOCK` | `0x10` | Can lock the card |
| `Privileges.CARD_TERMINATE` | `0x08` | Can terminate the card |
| `Privileges.CARD_RESET` | `0x04` | Default selected / card reset |
| `Privileges.CVM_MANAGEMENT` | `0x02` | CVM (Cardholder Verification Method) management |
| `Privileges.MANDATED_DAP` | `0x01` | Mandated DAP verification |

Privilege helpers:

```java
Privileges.isSecurityDomain(0x80);        // true
Privileges.hasDelegatedManagement(0xA0);  // true (0x80 | 0x20)
Privileges.describe(0xA0);                // "SECURITY_DOMAIN | DELEGATED_MANAGEMENT"
Privileges.describe(0x00);                // "none"
```

`AppletInfo` also provides convenience methods:

```java
AppletInfo sd = domains.get(0);
sd.isSecurityDomain();       // true if privilege bit 8 set
sd.hasDelegatedManagement(); // true if privilege bit 6 set
sd.privilegeDescription();   // "SECURITY_DOMAIN | DELEGATED_MANAGEMENT"
```

## CAP File Parsing

Parse and inspect JavaCard CAP files (ZIP archives containing compiled applet components):

```java
CAPFile cap = CAPFile.from(Files.readAllBytes(path));
CAPFile cap = CAPFile.fromFile(Path.of("applet.cap"));

cap.packageAidHex();       // "A0000000031010"
cap.majorVersion();        // 1
cap.minorVersion();        // 0
cap.componentNames();      // [Header, Directory, Class, Method, ...]
cap.loadFileData();        // C4 BER-TLV wrapped load data (for LOAD command)
cap.loadBlocks(247);       // split into blocks for LOAD chunking
```

## See Also

- [Core module](../core/README.md) — SmartCardSession, APDU builder, TLV parser, assertions
- [Project root](../README.md) — overview, modules, configuration
