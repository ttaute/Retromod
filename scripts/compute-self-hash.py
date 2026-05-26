#!/usr/bin/env python3
"""Compute Retromod's self-integrity hash for a built JAR.

Mirrors `SignatureVerifier.computeSelfHash` exactly: SHA-256 over every
`*.class` entry (sorted by name), EXCLUDING `META-INF/` and the verifier's own
class, hashing each entry's name (UTF-8) then its bytes. Output is 64 uppercase
hex chars.

Release flow:
  1. Build the final release jar (no further source changes after this).
  2. Run this over that jar.
  3. Paste the result into SignatureVerifier.EXPECTED_SELF_HASH.
  4. Rebuild — the verifier class is excluded from the hash, so re-embedding
     does not invalidate it; the official build then reports OFFICIAL.

Usage:
  python3 scripts/compute-self-hash.py target/retromod-<version>.jar
"""
import hashlib
import sys
import zipfile

SELF_ENTRY = "com/retromod/security/SignatureVerifier.class"


def compute_self_hash(jar_path: str) -> str:
    names = []
    with zipfile.ZipFile(jar_path) as z:
        for n in z.namelist():
            if not n.endswith(".class"):
                continue
            # Only Retromod's own classes; exclude relocated deps (they vary
            # per shipped variant) and the verifier class itself.
            if not n.startswith("com/retromod/"):
                continue
            if n.startswith("com/retromod/shaded/"):
                continue
            if n == SELF_ENTRY:
                continue
            names.append(n)
        names.sort()
        h = hashlib.sha256()
        for n in names:
            h.update(n.encode("utf-8"))
            h.update(z.read(n))
    return h.hexdigest().upper()


if __name__ == "__main__":
    if len(sys.argv) != 2:
        print("usage: compute-self-hash.py <jar>", file=sys.stderr)
        sys.exit(2)
    print(compute_self_hash(sys.argv[1]))
