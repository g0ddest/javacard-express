# JavaCard Express :: GlobalPlatform

[![Maven Central](https://img.shields.io/maven-central/v/name.velikodniy/javacard-express-gp)](https://search.maven.org/artifact/name.velikodniy/javacard-express-gp)
[![javadoc](https://javadoc.io/badge2/name.velikodniy/javacard-express-gp/javadoc.svg)](https://javadoc.io/doc/name.velikodniy/javacard-express-gp)

GlobalPlatform card management with the SCP02 (GlobalPlatform Card Specification v2.3.1, Appendix E) and
SCP03 (Amendment D, S8 and S16 modes) secure channels. `GPSession` performs INITIALIZE UPDATE + EXTERNAL
AUTHENTICATE and then wraps every command with C-MAC/C-ENC and unwraps R-MAC/R-ENC transparently. Includes
card content management (LOAD, INSTALL, DELETE), GET STATUS / GET DATA, SET STATUS, PUT KEY, key
diversification and CAP file parsing.

The protocol code is checked against known-answer transcripts that were not produced by this project: an
independent implementation written from the specifications, public sessions of real cards (an SCP02 card and an
NXP JCOP4 SCP03 card) and the Samsung OpenSCP-Java AES-128/192/256 S8/S16 transcripts. See
[Conformance](#conformance).

## Installation

```xml
<dependency>
    <groupId>name.velikodniy</groupId>
    <artifactId>javacard-express-gp</artifactId>
    <version>0.4.0</version>
    <scope>test</scope>
</dependency>
```

Depends on `javacard-express-core` (pulled transitively).

## Quick Start

```java
GPSession gp = GPSession.on(card)
    .keys(SCPKeys.defaultKeys())   // the well-known 40..4F test keys of development cards
    .open();

List<AppletInfo> apps = gp.getStatus();
byte[] iin = gp.getIIN();

gp.close();                        // destroys the session keys
```

Keys are always explicit: there is no silent fallback to the test keys. The protocol (SCP02 or SCP03) is
detected from the INITIALIZE UPDATE response.

## Table of Contents

- [Authentication](#authentication)
- [Implementation Options ("i")](#implementation-options-i)
- [Security Levels](#security-levels)
- [Responses: Errors, 61XX and 6CXX](#responses-errors-61xx-and-6cxx)
- [Loading and Installing Applets](#loading-and-installing-applets)
- [GET STATUS and GET DATA](#get-status-and-get-data)
- [Key Management](#key-management)
- [Key Diversification](#key-diversification)
- [Lifecycle Management](#lifecycle-management)
- [Security Domain Management](#security-domain-management)
- [CAP File Parsing](#cap-file-parsing)
- [Conformance](#conformance)
- [Limitations](#limitations)
- [See Also](#see-also)

## Authentication

```java
GPSession gp = GPSession.on(card)
    .securityDomain("A000000151000000")        // SELECT the ISD first (optional)
    .keys(SCPKeys.of(encKey, macKey, dekKey))   // static keys
    .keyVersion(0x30)                           // INITIALIZE UPDATE P1 (0 = card's choice)
    .securityLevel(GP.SECURITY_C_MAC_C_ENC)     // EXTERNAL AUTHENTICATE P1
    .open();
```

`open()` follows the explicit secure channel initiation of GPCS v2.3.1 E.1.2.1 / Amendment D 5.2:

1. INITIALIZE UPDATE (`80 50 KVN 00 08 host-challenge 00`, Lc `10` in SCP03 S16 mode) with a fresh random
   host challenge.
2. The response is parsed (SCP02 Table E-8, SCP03 Table 7-3), session keys are derived and the card
   cryptogram is verified. **On a mismatch EXTERNAL AUTHENTICATE is never sent**, so wrong keys do not
   consume the card's authentication attempts.
3. The requested security level is validated against the protocol and the card's "i" parameter before
   anything else is sent.
4. EXTERNAL AUTHENTICATE carries the host cryptogram and a C-MAC; it is never encrypted. A rejection
   (e.g. `6300`) is reported as `GPException` and **never retried**: cards count failed authentications.

All later commands are wrapped by the channel. Responses whose R-MAC does not verify close the session. Data that
would not fit into a short APDU after secure messaging (GPCS 11.1.5) is rejected before anything is sent.
`close()` zeroizes the session keys; a new `open()` uses a new host challenge.

## Implementation Options ("i")

**SCP03** cards report "i" in INITIALIZE UPDATE (Amendment D Table 5-1): b5 = pseudo-random card challenge
(a 3-byte sequence counter follows the cryptogram), b6/b7 = R-MAC / R-ENCRYPTION support, b1 = S16 mode. The
response layout is chosen from it automatically; `gp.cardInfo().implementationOption()` returns it.
S16 cards need a 16-byte host challenge, which must be known before INITIALIZE UPDATE:

```java
GPSession.on(card).keys(keys).scp03S16(true).open();
```

**SCP02** cards do not send "i" in INITIALIZE UPDATE. The default is `'15'` (explicit initiation, C-MAC on the
modified APDU, ICV encryption, three keys), which covers the mandatory options `'15'` and `'55'` of GPCS E.1.1.
The card publishes its value in the Card Recognition Data:

```java
CardData data = CardData.parse(card.send(0x80, 0xCA, 0x00, 0x66, null, 256).data());
data.secureChannelProtocols();   // e.g. [SCP02 i=15]

GPSession.on(card).keys(keys).scp02Option(0x55).open();   // only needed for options other than 15/55
```

Options without ICV encryption (`'05'`, `'45'`) and with R-MAC support (`'35'`, `'75'`) are supported; implicit
initiation and C-MAC on the unmodified APDU are not.

## Security Levels

| Constant | P1 | Protection | SCP02 | SCP03 |
|----------|----|------------|-------|-------|
| `GP.SECURITY_NONE` | `00` | Authentication only, commands sent without secure messaging | yes | yes |
| `GP.SECURITY_C_MAC` | `01` | Command integrity (default) | yes | yes |
| `GP.SECURITY_C_MAC_C_ENC` | `03` | + command data encryption | yes | yes |
| `GP.SECURITY_C_MAC_R_MAC` | `11` | + response integrity | i with R-MAC (b6) | i with R-MAC (b6) |
| `GP.SECURITY_C_MAC_C_ENC_R_MAC` | `13` | C-ENC + R-MAC | i with R-MAC (b6) | i with R-MAC (b6) |
| `GP.SECURITY_C_MAC_C_ENC_R_MAC_R_ENC` | `33` | + response encryption | no (RFU) | i with R-ENC (b7) |

Responses with R-MAC are verified and stripped; with R-ENC they are also decrypted. SCP03 error status words
carry no R-MAC (Amendment D 6.2.5) and are returned as they are.

## Responses: Errors, 61XX and 6CXX

Every command protected by the secure channel is transmitted **exactly once**: the card verifies its C-MAC
whatever it answers and chains the next C-MAC on it ("a verified C-MAC shall never be discarded", GPCS E.4.4;
Amendment D 6.2.4, and the SCP03 encryption counter, 6.2.6). The same bytes sent again would fail the MAC check
and the card would abort the secure channel (E.1.6). The C-MAC does not cover Le (E.4.4, Amendment D 6.2.4).

| Card answer | What `GPSession` does |
|-------------|------------------------|
| error status word (`6A88`, `6985`, ...) | returns it (the command helpers throw `GPException`); the session stays open. With SCP02 R-MAC the card generates an R-MAC for the error and chains the next one on it, but usually sends only the status word (GPCS E.4.5, ISO/IEC 7816-4 5.1.3): the host computes the same R-MAC (an error that comes with its 8-byte R-MAC is verified). SCP03 errors carry no R-MAC (Amendment D 6.2.5). In R-MAC sessions an error with any other data is rejected, as no R-MAC covers that data. |
| `61XX` | fetches the rest with GET RESPONSE in plain (no C-MAC), with the class byte of the protected command, and unwraps the reassembled response once: the R-MAC and R-ENC cover the complete response (GPCS 11.1.5.2) |
| `6CXX` | protects the plain command again with Le = `XX` (new C-MAC, next SCP03 counter) and sends it once more (ISO/IEC 7816-4 5.1.3); a second `6CXX` is returned |

To use `APDUSequence` with commands wrapped by `SCP02`/`SCP03` directly, turn its Le correction off and wrap the
command again yourself after `6CXX`:

```java
byte[] wrapped = scp.wrap(plainApdu);
APDUResponse r = scp.unwrap(APDUSequence.on(card).leCorrection(false).transmit(wrapped));   // 61XX completed
```

The guarantee ends at the `SmartCardSession`: with `PcscSession`, the JDK provider (SunPCSC) completes `61XX` and
re-sends a command answered `6CXX` itself, below `GPSession` (and below any guard or transcript), unless the JVM
is started with `-Dsun.security.smartcardio.t0GetResponse=false -Dsun.security.smartcardio.t1GetResponse=false`
(the provider reads them once per JVM, they cannot be set per session). Completing `61XX` is harmless; the `6CXX`
re-send repeats a consumed C-MAC. Over T=0 a protected command (it always has a data field) is never answered
`6CXX` by the transmission protocol, only by a Security Domain or applet that rejects its Le; for such a card,
set both properties so that `GPSession` handles `6CXX` (plain `send(...)` calls then see `61XX` too and need
`APDUSequence`).

## Loading and Installing Applets

```java
CAPFile cap = CAPFile.fromFile(Path.of("applet.cap"));

// INSTALL [for load] + LOAD + INSTALL [for install and make selectable]
gp.loadAndInstall(cap, null, 0x00, null);                       // single-applet CAP, instance AID = applet AID
gp.loadAndInstall(cap, "A0000000031010", 0x00, null);           // other instance AID
gp.loadAndInstall(cap, appletAid, instanceAid, 0x00, params);   // CAP files with several applets
```

The Executable Module AID of INSTALL [for install] is the applet AID read from the CAP file's Applet component
(`cap.appletAids()`), not the package AID. The module and the number of LOAD blocks are checked before anything
is sent. The individual steps are available too:

```java
gp.installForLoad(cap.packageAidHex(), null);                    // null = the selected Security Domain
gp.installForLoad(cap.packageAid(), sdAid, cap.loadFileDataBlockHash("SHA-256", true), loadParams);
gp.load(cap);                                                    // or gp.load(cap.loadFileData(false)) without Descriptor
gp.installForInstall(packageAid, appletAid, instanceAid, 0x00, installParams);
gp.deleteAid(instanceAid);
gp.deleteAid(Hex.decode(packageAid), true);                      // package and all its applications
```

LOAD blocks are as large as the security level allows (247 bytes with C-MAC, 239 with C-ENC; 239 and 223 in SCP03
S16 mode); a load file that needs more than 256 blocks is rejected before INSTALL [for load]. INSTALL parameter
lengths are BER-encoded and privileges may be 1 or 3 bytes (`0x80C000` = three bytes).

## GET STATUS and GET DATA

```java
List<AppletInfo> apps = gp.getStatus();                                    // applications and SDs ('40')
AppletInfo isd = gp.getStatus(Lifecycle.SCOPE_ISD).getFirst();             // '80'
List<AppletInfo> files = gp.getLoadFiles();                                // '20'
List<AppletInfo> modules = gp.getStatus(Lifecycle.SCOPE_LOAD_FILES_AND_MODULES);  // '10'

isd.privilegeBytes();                 // all three privilege bytes (tag 'C5')
files.getFirst().versionNumber();     // tag 'CE'
modules.getFirst().executableModuleAids();   // tags '84'
```

GET STATUS requests the format of GPCS Tables 11-36/11-37, follows `6310` with GET STATUS [next occurrence],
returns an empty list for `6A88` and reports malformed data instead of returning a truncated list.

```java
byte[] iin = gp.getIIN();                       // GET DATA '0042'
byte[] cin = gp.getCIN();                       // GET DATA '0045'
CardData cardData = gp.getCardData();           // GET DATA '0066', Card Recognition Data
cardData.gpVersion();                           // e.g. "1.2.840.114283.2.2.1.1" (GP 2.1.1)
cardData.secureChannelProtocols();              // e.g. [SCP02 i=15]
CPLCData cplc = gp.getCPLC();                   // GET DATA '9F7F', 42-byte CPLC
List<KeyInfoEntry> keys = gp.getKeyInformation();   // GET DATA '00E0'
int counter = gp.getSequenceCounter();          // GET DATA '00C1'
APDUResponse r = gp.getData(0x00, 0xCF);        // any GET DATA, Le '00'
```

## Key Management

```java
// Replace the current key set, or add one if the card still has a factory key set (KVN above '7F')
gp.putKeys(SCPKeys.aes(enc, mac, dek), 0x01);

// Replace key set 01 by key set 02: same key type and length as the keys being replaced
gp.putKeys(SCPKeys.aes(enc, mac, dek), 0x02, 0x01);

// Add a single key (Key Identifier 1) as a new key set 03
gp.putKey(1, keyBytes, KeyInfo.KeyType.AES, 0x03, 0x00);
```

The key type comes from the key set (`SCPKeys.aes`, `SCPKeys.des3`; untyped key sets from `SCPKeys.of` take the
type of the current protocol), so an AES key set can be added over SCP02 on a card that also supports SCP03.
Replacing keys cannot change their type or length (GPCS 11.8.2.3.3). Key values are encrypted with the DEK of the
current channel (SCP02: session DEK, 3DES-ECB; SCP03: static Key-DEK, AES-CBC) and sent with their key check
value (GPCS 11.8.2.3, Amendment D 6.2.8), so the card rejects a key that does not decrypt to the intended value.
Key Version Numbers are `01`-`7F`. What a card does with its factory key set when a new key set is added is
card-specific. PUT KEY changes the keys the card will accept: keep the new keys before sending it.

## Key Diversification

```java
GPSession gp = GPSession.on(card)
    .keys(SCPKeys.fromMasterKey(masterKey))
    .diversification(KeyDiversification::visa2)
    .open();
```

| Algorithm | Method | Keys | Notes |
|-----------|--------|------|-------|
| VISA2 | `KeyDiversification::visa2` | 3DES | |
| EMV CPS 1.1 | `KeyDiversification::emvCps11` | 3DES | |
| KDF3 | `KeyDiversification::kdf3` | AES | the GlobalPlatformPro `kdf3` scheme (AES-CMAC counter KDF); not defined by a GP specification |

The diversification data (first 10 bytes of the INITIALIZE UPDATE response) is passed to the function. All three
schemes reproduce the public GlobalPlatformPro key check value vectors.

## Lifecycle Management

```java
gp.lockApp("A0000000031010");      // SET STATUS 40 80 <AID>: to LOCKED
gp.unlockApp("A0000000031010");    // SET STATUS 40 00 <AID>: back from LOCKED
gp.setStatus(Lifecycle.SCOPE_SD_AND_APPS, sdAid, Lifecycle.APP_LOCKED);   // SD and its applications
```

The data field is the AID itself (GPCS 11.10.2.3). A Security Domain can only lock and unlock other applications
(11.10.2.2), so `terminateApp()` is deprecated and throws; use `deleteAid()` to remove an application.

| Constant | Value | Description |
|----------|-------|-------------|
| `Lifecycle.APP_INSTALLED` | `0x03` | Installed but not selectable |
| `Lifecycle.APP_SELECTABLE` | `0x07` | Installed and selectable |
| `Lifecycle.APP_PERSONALIZED` | `0x0F` | Personalized |
| `Lifecycle.APP_LOCKED` | `0x80` | Locked (bit 8 set) |
| `Lifecycle.APP_TERMINATED` | `0xFF` | Terminated |
| `Lifecycle.CARD_OP_READY` | `0x01` | Card: OP_READY |
| `Lifecycle.CARD_INITIALIZED` | `0x07` | Card: INITIALIZED |
| `Lifecycle.CARD_SECURED` | `0x0F` | Card: SECURED |
| `Lifecycle.CARD_LOCKED` | `0x7F` | Card: CARD_LOCKED (see the rules below) |
| `Lifecycle.CARD_TERMINATED` | `0xFF` | Card: TERMINATED, irreversible |

### Card Life Cycle: Lock and Terminate

> **Warning:** these commands change the life cycle of the whole card, not of one application. Locking can be
> impossible to undo and terminating is irreversible. Do not send them to a card you cannot afford to lose.

```java
gp.lockCard();                     // SET STATUS 80 7F: SECURED -> CARD_LOCKED, read the rules below first
gp.unlockCard();                   // SET STATUS 80 0F: CARD_LOCKED -> SECURED, only where it is still possible
gp.terminateCard();                // SET STATUS 80 FF: TERMINATED, irreversible, the card is unusable
```

- **CARD_LOCKED** (GPCS 5.1.1.4, 9.6.3): only the application with the Final Application privilege can be
  selected; Security Domains process only GET DATA, GET STATUS and SET STATUS (Table 11-1), so nothing can be
  loaded, installed, deleted or personalized and no key can be changed.
- **Who can lock and unlock:** a Security Domain with the Card Lock privilege (normally the Issuer Security
  Domain), over a secure channel authenticated with that domain's keys (9.6.3, 11.10.2.2, Table 11-2). Without
  those keys the card cannot be unlocked.
- **Unlocking can be impossible:** applications stay selected until their session ends (9.6.3); afterwards the
  Security Domain can be selected again only if it holds the Final Application privilege (by default the ISD,
  6.6.2; Table 11-1 Note 1). If another application holds that privilege, or the card's issuer policy restricts
  unlocking, a lock is irreversible in practice.
- **TERMINATED** (5.1.1.5, 9.6.4) is irreversible by definition: card content management and life cycle changes
  are disabled for good, only the Final Application can be selected and a Security Domain with that privilege
  answers GET DATA only. It needs the Card Terminate privilege. `setStatus(Lifecycle.SCOPE_ISD, ...)` with
  `Lifecycle.CARD_LOCKED` or `Lifecycle.CARD_TERMINATED` has the same effects.

## Security Domain Management

```java
List<AppletInfo> domains = gp.getDomains();     // GET STATUS '40' filtered by the SD privilege
gp.extradite("A0000000031010", "A000000004");   // INSTALL [for extradition]
gp.personalize("A0000000031010");               // INSTALL [for personalization], then storeData(...)
gp.storeData(data);                             // STORE DATA blocks, P1 '80' on the last one
gp.registryUpdate("A0000000031010", Privileges.CARD_RESET);   // INSTALL [for registry update]
```

| Constant | Value | Description |
|----------|-------|-------------|
| `Privileges.SECURITY_DOMAIN` | `0x80` | Entry is a Security Domain |
| `Privileges.DAP_VERIFICATION` | `0x40` | DAP verification privilege |
| `Privileges.DELEGATED_MANAGEMENT` | `0x20` | Delegated management |
| `Privileges.CARD_LOCK` | `0x10` | Can lock and unlock the card |
| `Privileges.CARD_TERMINATE` | `0x08` | Can terminate the card (irreversible) |
| `Privileges.CARD_RESET` | `0x04` | Default selected / card reset |
| `Privileges.CVM_MANAGEMENT` | `0x02` | CVM management |
| `Privileges.MANDATED_DAP` | `0x01` | Mandated DAP verification |

## CAP File Parsing

```java
CAPFile cap = CAPFile.fromFile(Path.of("applet.cap"));

cap.packageAidHex();          // Load File AID (package AID, or CAP AID of an Extended CAP file)
cap.appletAids();             // applet AIDs from the Applet component
cap.isExtended();             // Extended format (CAP 2.3)
cap.componentNames();         // loadable components in the JCVM 3.1 section 6.3 install order
cap.loadFileData();           // 'C4' BER-length components (with Descriptor)
cap.loadFileData(false);      // without the Descriptor, which is optional for loading
```

`.cap` and `.capx` component files (Extended format, Static Resources) are supported, the Debug component is
never loaded, and a CAP file without a required component is rejected.

## Conformance

The test suite replays known-answer transcripts through the `SCP02`/`SCP03` classes and through `GPSession`:

- spec-derived transcripts of an independent implementation written from GPCS v2.3.1 Appendix E and
  Amendment D: SCP02 i=15/55/75/45 at levels 01/03/11/13, SCP03 AES-128/192/256 S8/S16 at levels
  01/03/11/13/33, including commands without data, error and warning status words and maximum-size blocks;
- public real-card sessions: an SCP02 card (i=15) driven by GlobalPlatformPro, including GET STATUS for all
  scopes and INSTALL [for load] / LOAD, and an NXP JCOP4 SCP03 (i=70) authentication;
- Samsung OpenSCP-Java AES-128/192/256 S8 and S16 transcripts at level 33 (C-ENC, C-MAC, R-MAC, R-ENC).

## Limitations

- SCP01, SCP10 and SCP11 are not implemented; SCP02 implicit initiation, C-MAC on the unmodified APDU, level
  `10` and BEGIN/END R-MAC SESSION are not implemented.
- Commands are short APDUs (GPCS 11.1.5); GlobalPlatform command chaining for data over 255 bytes is not
  implemented, such commands are rejected.
- DAP blocks, tokens, receipts and delegated management are not implemented.

## See Also

- [Core module](../core/README.md) — SmartCardSession, APDU builder, TLV parser, assertions
- [Project root](../README.md) — overview, modules, configuration
