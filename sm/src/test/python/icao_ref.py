#!/usr/bin/env python3
"""
Independent reference implementation of ICAO Doc 9303 Part 11 (8th ed.) mechanisms,
written from the public spec text only (sections 4.4, 9.7, 9.8, Appendices D and G).

Used as an oracle for the javacard-express sm/pace audit:
  * BAC key derivation (9.7.1, 9.7.2, App. D.1/D.2)
  * Secure Messaging wrap/unwrap for 3DES (9.8.6) and AES (9.8.7: IV = E(KSenc, SSC), CMAC-8)
  * PACE ECDH Generic Mapping (4.4.3.3.1), KDF (9.7.1), password encoding (9.7.3),
    authentication token (4.4.3.4)

Self-test: `python icao_ref.py selftest` checks every value of the ICAO worked examples
(App. D.2-D.4 BAC and 3DES SM APDUs, App. G.1 PACE ECDH-GM on brainpoolP256r1).

Vector generation for the Java tests of the sm module:
  python icao_ref.py properties ../resources/icao-sm-vectors
writes the *.properties files read by the tests in name.velikodniy.jcexpress.sm. The generated vectors
were cross-checked locally (black box, not a build dependency) against JMRTD 0.8.8, an independent
open-source eMRTD implementation; the CLA vectors for further interindustry classes (0x40-0x7F) follow
ISO/IEC 7816-4 5.4.1 Table 3 (b6 = SM indication), which JMRTD does not implement.

Requires the 'cryptography' package (pip install cryptography).
"""
import hashlib
import json
import sys

from cryptography.hazmat.primitives.ciphers import Cipher, modes
from cryptography.hazmat.primitives.ciphers.algorithms import AES
from cryptography.hazmat.primitives.cmac import CMAC

try:  # cryptography >= 43 moved 3DES to the "decrepit" package
    from cryptography.hazmat.decrepit.ciphers.algorithms import TripleDES
except ImportError:  # pragma: no cover
    from cryptography.hazmat.primitives.ciphers.algorithms import TripleDES

H = bytes.fromhex


def hx(b):
    return b.hex().upper()


# ---------------------------------------------------------------- block ciphers

def _cbc(alg, key, data, iv, enc=True):
    c = Cipher(alg(key), modes.CBC(iv))
    op = c.encryptor() if enc else c.decryptor()
    return op.update(data) + op.finalize()


def des3_cbc(key16, data, iv=bytes(8), enc=True):
    return _cbc(TripleDES, key16 + key16[:8], data, iv, enc)


def des_cbc(key8, data, iv=bytes(8)):
    return _cbc(TripleDES, key8 * 3, data, iv, True)  # K1=K2=K3 -> single DES


def aes_cbc(key, data, iv=bytes(16), enc=True):
    return _cbc(AES, key, data, iv, enc)


def aes_ecb(key, block):
    c = Cipher(AES(key), modes.ECB()).encryptor()
    return c.update(block) + c.finalize()


def pad(data, bs):
    """ISO/IEC 9797-1 padding method 2."""
    data = data + b"\x80"
    while len(data) % bs:
        data += b"\x00"
    return data


def unpad(data):
    i = len(data) - 1
    while data[i] == 0:
        i -= 1
    assert data[i] == 0x80, "bad padding"
    return data[:i]


def retail_mac(key16, data):
    """ISO/IEC 9797-1 MAC algorithm 3, DES, IV=0 (data must already be padded)."""
    ka, kb = key16[:8], key16[8:16]
    h = des_cbc(ka, data)[-8:]
    # output transformation 3: E_Ka(D_Kb(H_q))
    c = Cipher(TripleDES(kb * 3), modes.ECB()).decryptor()
    h = c.update(h) + c.finalize()
    c = Cipher(TripleDES(ka * 3), modes.ECB()).encryptor()
    return c.update(h) + c.finalize()


def aes_cmac(key, data):
    c = CMAC(AES(key))
    c.update(data)
    return c.finalize()


# ---------------------------------------------------------------- KDF (9.7.1)

def kdf(k, c, alg="3DES", keylen=16):
    d = k + c.to_bytes(4, "big")
    if alg == "3DES" or (alg == "AES" and keylen == 16):
        return hashlib.sha1(d).digest()[:16]
    return hashlib.sha256(d).digest()[:keylen]


def adjust_parity(key):
    out = bytearray(key)
    for i, b in enumerate(out):
        ones = bin(b >> 1).count("1")
        out[i] = (b & 0xFE) | (0 if ones % 2 else 1)
    return bytes(out)


def check_digit(s):
    w = [7, 3, 1]
    total = 0
    for i, ch in enumerate(s):
        if ch.isdigit():
            v = ord(ch) - 48
        elif ch == "<":
            v = 0
        else:
            v = ord(ch) - 55
        total += v * w[i % 3]
    return total % 10


def mrz_information(doc, dob, doe):
    return (doc + str(check_digit(doc)) + dob + str(check_digit(dob)) + doe + str(check_digit(doe))).encode()


# ---------------------------------------------------------------- Secure Messaging (9.8)

class SM:
    def __init__(self, alg, ks_enc, ks_mac, ssc):
        self.alg, self.kenc, self.kmac, self.ssc = alg, ks_enc, ks_mac, int.from_bytes(ssc, "big")
        self.bs = 8 if alg == "3DES" else 16

    def ssc_bytes(self):
        return self.ssc.to_bytes(self.bs, "big")

    def _enc(self, data):
        if self.alg == "3DES":
            return des3_cbc(self.kenc, pad(data, 8))                      # 9.8.6.1: zero IV
        iv = aes_ecb(self.kenc, self.ssc_bytes())                          # 9.8.7.1: IV = E(KSenc, SSC)
        return aes_cbc(self.kenc, pad(data, 16), iv)

    def _dec(self, ct):
        if self.alg == "3DES":
            return unpad(des3_cbc(self.kenc, ct, enc=False))
        iv = aes_ecb(self.kenc, self.ssc_bytes())
        return unpad(aes_cbc(self.kenc, ct, iv, enc=False))

    def _mac(self, data):
        n = pad(self.ssc_bytes() + data, self.bs)
        if self.alg == "3DES":
            return retail_mac(self.kmac, n)
        return aes_cmac(self.kmac, n)[:8]

    @staticmethod
    def tlv(tag, value):
        n = len(value)
        if n < 0x80:
            ln = bytes([n])
        elif n < 0x100:
            ln = bytes([0x81, n])
        else:
            ln = bytes([0x82, n >> 8, n & 0xFF])
        return bytes([tag]) + ln + value

    @staticmethod
    def sm_cla(cla):
        """ISO/IEC 7816-4:2013 5.4.1: first interindustry CLA 000x xxxx -> b4b3 = 11 (SM, header authenticated),
        keeping chaining (b5) and channel (b2b1); further interindustry 01xx xxxx -> b6 = 1 (SM indication).
        Proprietary CLA 1xxx xxxx: same bit layout convention as the interindustry classes (b7 selects it)."""
        if cla == 0xFF or 0x20 <= cla <= 0x3F:
            raise ValueError("invalid/RFU CLA")
        if cla & 0x40:
            return cla | 0x20
        return (cla & 0xF3) | 0x0C

    @staticmethod
    def do97(le):
        """DO'97': Le as 1 byte (1..256, 256 -> '00') or 2 bytes (257..65536, 65536 -> '0000')."""
        if le <= 256:
            return SM.tlv(0x97, bytes([le & 0xFF]))
        return SM.tlv(0x97, (le & 0xFFFF).to_bytes(2, "big"))

    def wrap(self, cla, ins, p1, p2, data=None, le=None, extended=False):
        """ICAO 9303-11 9.8.4 / Figure 5. The protected command uses extended Lc'/Le' (ISO 7816-4 5.1,
        Figure 5 Le' '00 00') when the protected body exceeds 255 bytes or Le exceeds 256; the framing of the
        unprotected command does not matter (the 'extended' argument is ignored, kept for call compatibility)."""
        extended = le is not None and le > 256
        self.ssc += 1  # SSC incremented before the command is generated (9.8.2)
        header = bytes([self.sm_cla(cla), ins, p1, p2])
        body = b""
        if data:
            ct = self._enc(data)
            body += self.tlv(0x87, b"\x01" + ct) if ins % 2 == 0 else self.tlv(0x85, ct)
        if le is not None:
            body += self.do97(le)
        mac = self._mac(pad(header, self.bs) + body)
        body += self.tlv(0x8E, mac)
        if extended or len(body) > 255:
            return header + b"\x00" + len(body).to_bytes(2, "big") + body + b"\x00\x00"
        return header + bytes([len(body)]) + body + b"\x00"

    def protect_response(self, data, sw, odd=False):
        """Chip side (Figure 6); odd INS -> DO'85' without padding-content indicator (9.8.4)."""
        self.ssc += 1
        body = b""
        if data:
            ct = self._enc(data)
            body += self.tlv(0x85, ct) if odd else self.tlv(0x87, b"\x01" + ct)
        body += self.tlv(0x99, sw.to_bytes(2, "big"))
        mac = self._mac(body)
        return body + self.tlv(0x8E, mac) + sw.to_bytes(2, "big")

    def unwrap(self, rapdu):
        self.ssc += 1
        body, sw = rapdu[:-2], rapdu[-2:]
        i, dos, macd = 0, {}, b""
        while i < len(body):
            tag = body[i]
            ln = body[i + 1]
            j = i + 2
            if ln == 0x81:
                ln = body[j]; j += 1
            elif ln == 0x82:
                ln = int.from_bytes(body[j:j + 2], "big"); j += 2
            if tag != 0x8E:
                macd += body[i:j + ln]
            dos[tag] = body[j:j + ln]
            i = j + ln
        assert self._mac(macd) == dos[0x8E], "MAC mismatch"
        if 0x87 in dos:
            data = self._dec(dos[0x87][1:])
        elif 0x85 in dos:
            data = self._dec(dos[0x85])
        else:
            data = b""
        return data, dos[0x99]


# ---------------------------------------------------------------- EC math (brainpoolP256r1, RFC 5639)

class Curve:
    def __init__(self, p, a, b, gx, gy, n):
        self.p, self.a, self.b, self.g, self.n = p, a, b, (gx, gy), n

    def on_curve(self, pt):
        x, y = pt
        return (y * y - (x * x * x + self.a * x + self.b)) % self.p == 0

    def add(self, P, Q):
        if P is None:
            return Q
        if Q is None:
            return P
        p = self.p
        if P[0] == Q[0]:
            if (P[1] + Q[1]) % p == 0:
                return None
            lam = (3 * P[0] * P[0] + self.a) * pow(2 * P[1], -1, p) % p
        else:
            lam = (Q[1] - P[1]) * pow(Q[0] - P[0], -1, p) % p
        x = (lam * lam - P[0] - Q[0]) % p
        return (x, (lam * (P[0] - x) - P[1]) % p)

    def mul(self, k, P):
        R = None
        while k:
            if k & 1:
                R = self.add(R, P)
            P = self.add(P, P)
            k >>= 1
        return R


BP256 = Curve(
    0xA9FB57DBA1EEA9BC3E660A909D838D726E3BF623D52620282013481D1F6E5377,
    0x7D5A0975FC2C3057EEF67530417AFFE7FB8055C126DC5C6CE94A4B44F330B5D9,
    0x26DC5C6CE94A4B44F330B5D9BBD77CBF958416295CF7E1CE6BCCDC18FF8C07B6,
    0x8BD2AEB9CB7E57CB2C4B482FFC81B7AFB9DE27E1E3BD23C23A4453BD9ACE3262,
    0x547EF835C3DAC4FD97F8461A14611DC9C27745132DED8E545C1D54C72F046997,
    0xA9FB57DBA1EEA9BC3E660A909D838D718C397AA3B561A6F7901E0E82974856A7)


def enc_point(pt, size=32):
    return b"\x04" + pt[0].to_bytes(size, "big") + pt[1].to_bytes(size, "big")


def dec_point(b, size=32):
    assert b[0] == 4
    return (int.from_bytes(b[1:1 + size], "big"), int.from_bytes(b[1 + size:], "big"))


OID_ECDH_GM_AES128 = H("04007F00070202040202")


def auth_token(ks_mac, oid, pk_bytes):
    inner = SM.tlv(0x06, oid) + SM.tlv(0x86, pk_bytes)
    data = b"\x7F\x49" + bytes([len(inner)]) + inner
    return aes_cmac(ks_mac, data)[:8], data


# ---------------------------------------------------------------- self test against the ICAO examples

def check(name, got, exp):
    ok = got == exp
    print(("OK   " if ok else "FAIL ") + name + ": " + (hx(got) if isinstance(got, bytes) else str(got)))
    if not ok:
        print("     expected " + (hx(exp) if isinstance(exp, bytes) else str(exp)))
    return ok


def selftest():
    ok = True
    # --- App. D.1/D.2 BAC keys
    mi = mrz_information("L898902C<", "690806", "940623")
    ok &= check("D.2 MRZ_information", mi, b"L898902C<369080619406236")
    kseed = hashlib.sha1(mi).digest()[:16]
    ok &= check("D.2 Kseed", kseed, H("239AB9CB282DAF66231DC5A4DF6BFBAE"))
    ok &= check("D.2 KEnc", adjust_parity(kdf(kseed, 1)), H("AB94FDECF2674FDFB9B391F85D7F76F2"))
    ok &= check("D.2 KMAC", adjust_parity(kdf(kseed, 2)), H("7962D9ECE03D1ACD4C76089DCE131543"))
    ok &= check("D.2 long docno MRZ_information", mrz_information("D23145890734", "340712", "950712"),
                b"D23145890734934071279507122")
    # --- App. D.3 session keys
    ks = H("0036D272F5C350ACAC50C3F572D23600")
    ks_enc, ks_mac = adjust_parity(kdf(ks, 1)), adjust_parity(kdf(ks, 2))
    ok &= check("D.3 KSEnc", ks_enc, H("979EC13B1CBFE9DCD01AB0FED307EAE5"))
    ok &= check("D.3 KSMAC", ks_mac, H("F1CB1F1FB5ADF208806B89DC579DC1F8"))
    ok &= check("D.3 EIFD", des3_cbc(H("AB94FDECF2674FDFB9B391F85D7F76F2"),
                                     H("781723860C06C2264608F919887022120B795240CB7049B01C19B33E32804F0B")),
                H("72C29C2371CC9BDB65B779B8E8D37B29ECC154AA56A8799FAE2F498F76ED92F2"))
    ok &= check("D.3 MIFD", retail_mac(H("7962D9ECE03D1ACD4C76089DCE131543"),
                                       pad(H("72C29C2371CC9BDB65B779B8E8D37B29ECC154AA56A8799FAE2F498F76ED92F2"), 8)),
                H("5F1448EEA8AD90A7"))
    # --- App. D.4 SM
    sm = SM("3DES", ks_enc, ks_mac, H("887022120C06C226"))
    ok &= check("D.4 SELECT EF.COM", sm.wrap(0x00, 0xA4, 0x02, 0x0C, H("011E")),
                H("0CA4020C158709016375432908C044F68E08BF8B92D635FF24F800"))
    ok &= check("D.4 SELECT resp", sm.unwrap(H("990290008E08FA855A5D4C50A8ED9000")), (b"", H("9000")))
    ok &= check("D.4 READ BINARY 4", sm.wrap(0x00, 0xB0, 0x00, 0x00, None, 4),
                H("0CB000000D9701048E08ED6705417E96BA5500"))
    ok &= check("D.4 READ BINARY 4 resp", sm.unwrap(H("8709019FF0EC34F9922651990290008E08AD55CC17140B2DED9000")),
                (H("60145F01"), H("9000")))
    ok &= check("D.4 READ BINARY 18", sm.wrap(0x00, 0xB0, 0x00, 0x04, None, 0x12),
                H("0CB000040D9701128E082EA28A70F3C7B53500"))
    ok &= check("D.4 READ BINARY 18 resp",
                sm.unwrap(H("871901FB9235F4E4037F2327DCC8964F1F9B8C30F42C8E2FFF224A990290008E08C8B2787EAEA07D749000")),
                (H("04303130365F36063034303030305C026175"), H("9000")))
    # --- App. G.1 PACE ECDH GM
    K = hashlib.sha1(mrz_information("T22000129", "640812", "101031")).digest()
    ok &= check("G K=f(pi)", K, H("7E2D2A41C74EA0B38CD36F863939BFA8E9032AAD"))
    kpi = kdf(K, 3, "AES", 16)
    ok &= check("G Kpi", kpi, H("89DED1B26624EC1E634C1989302849DD"))
    s = aes_cbc(kpi, H("95A3A016522EE98D01E76CB6B98B42C3"), enc=False)
    ok &= check("G.1 nonce s", s, H("3F00C4D39D153F2B2A214A078D899B22"))
    c = BP256
    sk_map_ifd = 0x7F4EF07B9EA82FD78AD689B38D0BC78CF21F249D953BC46F4C6E19259C010F99
    pk_map_ifd = c.mul(sk_map_ifd, c.g)
    ok &= check("G.1 PK_map,IFD", enc_point(pk_map_ifd), H(
        "047ACF3EFC982EC45565A4B155129EFBC74650DCBFA6362D896FC70262E0C2CC5E"
        "544552DCB6725218799115B55C9BAA6D9F6BC3A9618E70C25AF71777A9C4922D"))
    pk_map_ic = dec_point(H("04824FBA91C9CBE26BEF53A0EBE7342A3BF178CEA9F45DE0B70AA601651FBA3F57"
                            "30D8C879AAA9C9F73991E61B58F4D52EB87A0A0C709A49DC63719363CCD13C54"))
    Hpt = c.mul(sk_map_ifd, pk_map_ic)
    ok &= check("G.1 H", enc_point(Hpt), H(
        "0460332EF2450B5D247EF6D3868397D398852ED6E8CAF6FFEEF6BF85CA57057FD5"
        "0840CA7415BAF3E43BD414D35AA4608B93A2CAF3A4E3EA4E82C9C13D03EB7181"))
    Gm = c.add(c.mul(int.from_bytes(s, "big"), c.g), Hpt)
    ok &= check("G.1 mapped generator", enc_point(Gm), H(
        "048CED63C91426D4F0EB1435E7CB1D74A46723A0AF21C89634F65A9AE87A9265E2"
        "8C879506743F8611AC33645C5B985C80B5F09A0B83407C1B6A4D857AE76FE522"))
    sk_dh_ifd = 0xA73FB703AC1436A18E0CFA5ABB3F7BEC7A070E7A6788486BEE230C4A22762595
    pk_dh_ifd = enc_point(c.mul(sk_dh_ifd, Gm))
    ok &= check("G.1 PK_DH,IFD", pk_dh_ifd, H(
        "042DB7A64C0355044EC9DF190514C625CBA2CEA48754887122F3A5EF0D5EDD301C"
        "3556F3B3B186DF10B857B58F6A7EB80F20BA5DC7BE1D43D9BF850149FBB36462"))
    pk_dh_ic = H("049E880F842905B8B3181F7AF7CAA9F0EFB743847F44A306D2D28C1D9EC65DF6DB"
                 "7764B22277A2EDDC3C265A9F018F9CB852E111B768B326904B59A0193776F094")
    shared = c.mul(sk_dh_ifd, dec_point(pk_dh_ic))[0].to_bytes(32, "big")
    ok &= check("G.1 shared secret", shared, H("28768D20701247DAE81804C9E780EDE582A9996DB4A315020B2733197DB84925"))
    ks_enc = kdf(shared, 1, "AES", 16)
    ks_mac = kdf(shared, 2, "AES", 16)
    ok &= check("G.1 KSEnc", ks_enc, H("F5F0E35C0D7161EE6724EE513A0D9A7F"))
    ok &= check("G.1 KSMAC", ks_mac, H("FE251C7858B356B24514B3BD5F4297D1"))
    t_ifd, inp = auth_token(ks_mac, OID_ECDH_GM_AES128, pk_dh_ic)
    ok &= check("G.1 T_IFD input", inp, H(
        "7F494F060A04007F000702020402028641049E880F842905B8B3181F7AF7CAA9F0EFB743847F44A306D2D28C1D9EC65D"
        "F6DB7764B22277A2EDDC3C265A9F018F9CB852E111B768B326904B59A0193776F094"))
    ok &= check("G.1 T_IFD", t_ifd, H("C2B0BD78D94BA866"))
    ok &= check("G.1 T_IC", auth_token(ks_mac, OID_ECDH_GM_AES128, pk_dh_ifd)[0], H("3ABB9674BCE93C08"))
    # --- what javacard-express computes instead of H (x(H) * G) -- for the report
    wrong_H = c.mul(Hpt[0], c.g)
    wrong_G = c.add(c.mul(int.from_bytes(s, "big"), c.g), wrong_H)
    print("INFO javacard-express-style mapped generator x(H)*G + s*G = " + hx(enc_point(wrong_G)))
    print("SELFTEST " + ("PASSED" if ok else "FAILED"))
    return ok


def aes_vectors():
    """AES SM vectors after the ICAO G.1 PACE run (KSEnc/KSMAC from App. G.1, SSC = 0 per 9.8.7.3)."""
    ks_enc = H("F5F0E35C0D7161EE6724EE513A0D9A7F")
    ks_mac = H("FE251C7858B356B24514B3BD5F4297D1")
    term = SM("AES", ks_enc, ks_mac, bytes(16))
    chip = SM("AES", ks_enc, ks_mac, bytes(16))
    out = {"ksEnc": hx(ks_enc), "ksMac": hx(ks_mac), "ssc0": hx(bytes(16)), "steps": []}

    def step(name, cla, ins, p1, p2, data, le, rdata, sw):
        out["steps"].append(_run(term, chip, name, cla, ins, p1, p2, data, le, rdata, sw))

    step("SELECT eMRTD application", 0x00, 0xA4, 0x04, 0x0C, H("A0000002471001"), None, b"", 0x9000)
    step("SELECT EF.COM", 0x00, 0xA4, 0x02, 0x0C, H("011E"), None, b"", 0x9000)
    step("READ BINARY 4", 0x00, 0xB0, 0x00, 0x00, None, 4, H("60145F01"), 0x9000)
    step("READ BINARY 18", 0x00, 0xB0, 0x00, 0x04, None, 0x12,
         H("04303130365F36063034303030305C026175"), 0x9000)
    step("READ BINARY past EOF (6282)", 0x00, 0xB0, 0x00, 0x10, None, 0x20,
         H("3030305C026175"), 0x6282)
    step("SELECT missing EF (6A82 protected)", 0x00, 0xA4, 0x02, 0x0C, H("0107"), None, b"", 0x6A82)
    step("READ BINARY after error", 0x00, 0xB0, 0x00, 0x00, None, 4, H("60145F01"), 0x9000)
    return out


def des3_error_vectors():
    """3DES SM continuation of App. D.4 (SSC = 887022120C06C22C after the ICAO example):
    protected warning 6282 with data, protected error 6A82 without data, then a normal command."""
    ks_enc = H("979EC13B1CBFE9DCD01AB0FED307EAE5")
    ks_mac = H("F1CB1F1FB5ADF208806B89DC579DC1F8")
    term = SM("3DES", ks_enc, ks_mac, H("887022120C06C22C"))
    chip = SM("3DES", ks_enc, ks_mac, H("887022120C06C22C"))
    out = {"ksEnc": hx(ks_enc), "ksMac": hx(ks_mac), "ssc0": "887022120C06C22C", "steps": []}
    for name, cla, ins, p1, p2, data, le, rdata, sw in [
        ("READ BINARY past EOF (6282)", 0x00, 0xB0, 0x00, 0x10, None, 0x20, H("3030305C026175"), 0x6282),
        ("SELECT missing EF (6A82)", 0x00, 0xA4, 0x02, 0x0C, H("0107"), None, b"", 0x6A82),
        ("READ BINARY 4", 0x00, 0xB0, 0x00, 0x00, None, 4, H("60145F01"), 0x9000),
        ("UPDATE BINARY 150 bytes (DO'87' with length '81 99')", 0x00, 0xD6, 0x00, 0x00, bytes(range(150)), None,
         b"", 0x9000),
        ("READ BINARY 200 bytes (DO'87' with length '81 D1')", 0x00, 0xB0, 0x00, 0x00, None, 200,
         bytes((i * 7) & 0xFF for i in range(200)), 0x9000)]:
        out["steps"].append(_run(term, chip, name, cla, ins, p1, p2, data, le, rdata, sw))
    return out


def plain_apdu(cla, ins, p1, p2, data, le, extended=False):
    """Unprotected command APDU (ISO/IEC 7816-4 5.1, cases 1-4, short or extended)."""
    extended = extended or (data is not None and len(data) > 255) or (le is not None and le > 256)
    out = bytes([cla, ins, p1, p2])
    if extended:
        if data:
            out += b"\x00" + len(data).to_bytes(2, "big") + data
        if le is not None:
            out += (b"" if data else b"\x00") + (le & 0xFFFF).to_bytes(2, "big")
        return out
    if data:
        out += bytes([len(data)]) + data
    if le is not None:
        out += bytes([le & 0xFF])
    return out


def _run(term, chip, name, cla, ins, p1, p2, data, le, rdata, sw, extended=False):
    """One protected exchange: terminal wraps, chip verifies (SSC+1) and protects its response, terminal unwraps."""
    ssc_before = term.ssc_bytes()
    cmd = term.wrap(cla, ins, p1, p2, data, le, extended)
    chip.ssc += 1
    rsp = chip.protect_response(rdata, sw, odd=(ins % 2 == 1))
    assert term.unwrap(rsp) == (rdata, sw.to_bytes(2, "big"))
    return {"name": name, "sscBefore": hx(ssc_before),
            "plainApdu": hx(plain_apdu(cla, ins, p1, p2, data, le, extended)), "protectedApdu": hx(cmd),
            "protectedResponse": hx(rsp), "plainResponseData": hx(rdata), "sw": "%04X" % sw}


D3_KS_ENC = H("979EC13B1CBFE9DCD01AB0FED307EAE5")
D3_KS_MAC = H("F1CB1F1FB5ADF208806B89DC579DC1F8")
G1_KS_ENC = H("F5F0E35C0D7161EE6724EE513A0D9A7F")
G1_KS_MAC = H("FE251C7858B356B24514B3BD5F4297D1")


def ext_vectors():
    """Extended-length SM (ISO 7816-4 5.1, ICAO 9303-11 Figure 5 Le' '00 00'), 3DES (App. D.3 keys) and AES
    (App. G.1 keys); each step is independent (fresh SSC given in sscBefore)."""
    out = {}
    for alg, ke, km, ssc0 in (("3DES", D3_KS_ENC, D3_KS_MAC, bytes(8)), ("AES", G1_KS_ENC, G1_KS_MAC, bytes(16))):
        steps = []
        upd240 = bytes((i * 3) & 0xFF for i in range(240))
        upd300 = bytes((i * 5 + 1) & 0xFF for i in range(300))
        rd1000 = bytes((i * 11) & 0xFF for i in range(1000))
        rd300 = bytes((i * 13) & 0xFF for i in range(300))
        cases = [
            ("UPDATE BINARY 240 bytes (short plain APDU, protected body > 255)", 0x00, 0xD6, 0x00, 0x00, upd240, None, b"", 0x9000, False),
            ("UPDATE BINARY 300 bytes (extended plain APDU)", 0x00, 0xD6, 0x00, 0x00, upd300, None, b"", 0x9000, True),
            ("READ BINARY Le=1000 (extended)", 0x00, 0xB0, 0x00, 0x00, None, 1000, rd1000, 0x9000, True),
            ("READ BINARY Le=65536 (extended Le 0000)", 0x00, 0xB0, 0x00, 0x00, None, 65536, rd300, 0x6282, True),
            ("READ BINARY Le=4 (extended plain APDU with small Le -> short protected APDU)", 0x00, 0xB0, 0x00, 0x00, None, 4, H("60145F01"), 0x9000, True),
        ]
        for name, cla, ins, p1, p2, data, le, rdata, sw, ext in cases:
            term, chip = SM(alg, ke, km, ssc0), SM(alg, ke, km, ssc0)
            steps.append(_run(term, chip, name, cla, ins, p1, p2, data, le, rdata, sw, ext))
        out[alg] = steps
    return out


def odd_ins_vectors():
    """Odd INS (9.8.4: DO'85', no padding-content indicator) in commands and responses."""
    out = {}
    for alg, ke, km, ssc0 in (("3DES", D3_KS_ENC, D3_KS_MAC, bytes(8)), ("AES", G1_KS_ENC, G1_KS_MAC, bytes(16))):
        term, chip = SM(alg, ke, km, ssc0), SM(alg, ke, km, ssc0)
        rdata = H("5310") + bytes(range(16))
        out[alg] = [_run(term, chip, "READ BINARY odd INS B1, offset DO'54' 8000, Le=256", 0x00, 0xB1, 0x00, 0x00,
                         H("54028000"), 256, rdata, 0x9000)]
    return out


def cla_vectors():
    """CLA handling (ISO 7816-4 5.4.1): logical channel and chaining bits are kept, SM bits set; 3DES keys."""
    out = []
    for cla in (0x01, 0x03, 0x10, 0x13, 0x40, 0x4F, 0x80, 0x83):
        term, chip = SM("3DES", D3_KS_ENC, D3_KS_MAC, bytes(8)), SM("3DES", D3_KS_ENC, D3_KS_MAC, bytes(8))
        step = _run(term, chip, "READ BINARY Le=4 CLA %02X" % cla, cla, 0xB0, 0x00, 0x00, None, 4, H("60145F01"), 0x9000)
        step["plainCla"] = "%02X" % cla
        out.append(step)
    return out


def bac_d3_check():
    """App. D.3 chip side: EIC/MIC from R = RND.IC || RND.IFD || KIC with the Document Basic Access Keys."""
    k_enc, k_mac = H("AB94FDECF2674FDFB9B391F85D7F76F2"), H("7962D9ECE03D1ACD4C76089DCE131543")
    r = H("4608F91988702212781723860C06C2260B4F80323EB3191CB04970CB4052790B")
    e_ic = des3_cbc(k_enc, r)
    m_ic = retail_mac(k_mac, pad(e_ic, 8))
    ok = check("D.3 EIC", e_ic, H("46B9342A41396CD7386BF5803104D7CEDC122B9132139BAF2EEDC94EE178534F"))
    ok &= check("D.3 MIC", m_ic, H("2F2D235D074D7449"))
    kseed = bytes(a ^ b for a, b in zip(H("0B795240CB7049B01C19B33E32804F0B"), H("0B4F80323EB3191CB04970CB4052790B")))
    ok &= check("D.3 Kseed = KIFD xor KIC", kseed, H("0036D272F5C350ACAC50C3F572D23600"))
    ok &= check("D.3 SSC", H("4608F91988702212")[4:] + H("781723860C06C226")[4:], H("887022120C06C226"))
    return ok


def _props(path, title, keys, steps):
    lines = ["# " + title,
             "# Generated by sm/src/test/python/icao_ref.py (independent reference written from ICAO Doc 9303-11",
             "# sections 9.8.x and ISO/IEC 7816-4); do not edit by hand. Values are hex.",
             "keys.enc=" + hx(keys[0]), "keys.mac=" + hx(keys[1]), "steps=%d" % len(steps)]
    for i, st in enumerate(steps):
        lines += ["%d.name=%s" % (i, st["name"]), "%d.ssc=%s" % (i, st["sscBefore"]),
                  "%d.plain=%s" % (i, st["plainApdu"]), "%d.command=%s" % (i, st["protectedApdu"]),
                  "%d.response=%s" % (i, st["protectedResponse"]), "%d.data=%s" % (i, st["plainResponseData"]),
                  "%d.sw=%s" % (i, st["sw"])]
        if "plainCla" in st:
            lines.append("%d.plainCla=%s" % (i, st["plainCla"]))
    open(path, "w").write("\n".join(lines) + "\n")


def emit_properties(outdir):
    import os
    os.makedirs(outdir, exist_ok=True)
    g1, d3 = (G1_KS_ENC, G1_KS_MAC), (D3_KS_ENC, D3_KS_MAC)
    _props(os.path.join(outdir, "aes-session.properties"),
           "AES SM session after ICAO App. G.1 PACE (KSEnc/KSMAC of G.1, SSC0 = 0 per 9.8.7.3)", g1,
           aes_vectors()["steps"])
    _props(os.path.join(outdir, "des3-session.properties"),
           "3DES SM continuing ICAO App. D.4 (KSEnc/KSMAC of D.3, SSC after the D.4 example): "
           "protected 6282/6A82 responses and BER long-form DO lengths", d3, des3_error_vectors()["steps"])
    ext = ext_vectors()
    _props(os.path.join(outdir, "extended-3des.properties"),
           "Extended length (ISO 7816-4 5.1, ICAO 9303-11 Figure 5), 3DES, keys of App. D.3", d3, ext["3DES"])
    _props(os.path.join(outdir, "extended-aes.properties"),
           "Extended length (ISO 7816-4 5.1, ICAO 9303-11 Figure 5), AES, keys of App. G.1", g1, ext["AES"])
    odd = odd_ins_vectors()
    _props(os.path.join(outdir, "odd-ins-3des.properties"), "Odd INS -> DO'85' (ICAO 9303-11 9.8.4), 3DES", d3,
           odd["3DES"])
    _props(os.path.join(outdir, "odd-ins-aes.properties"), "Odd INS -> DO'85' (ICAO 9303-11 9.8.4), AES", g1,
           odd["AES"])
    _props(os.path.join(outdir, "cla.properties"),
           "CLA coding under SM (ISO/IEC 7816-4 5.4.1 Tables 2/3), 3DES, keys of App. D.3", d3, cla_vectors())


if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else "selftest"
    if cmd == "selftest":
        sys.exit(0 if selftest() else 1)
    elif cmd == "aesvectors":
        print(json.dumps(aes_vectors(), indent=2))
    elif cmd == "des3errorvectors":
        print(json.dumps(des3_error_vectors(), indent=2))
    elif cmd == "extvectors":
        print(json.dumps(ext_vectors(), indent=2))
    elif cmd == "oddinsvectors":
        print(json.dumps(odd_ins_vectors(), indent=2))
    elif cmd == "clavectors":
        print(json.dumps(cla_vectors(), indent=2))
    elif cmd == "properties":
        if not (selftest() and bac_d3_check()):
            sys.exit(1)
        emit_properties(sys.argv[2])
    elif cmd == "bacd3":
        sys.exit(0 if bac_d3_check() else 1)
