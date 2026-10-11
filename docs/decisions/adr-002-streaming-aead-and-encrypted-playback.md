# ADR-002: Stream-encrypt media with a segmented AEAD (Tink) and play it from ciphertext

## Status
Accepted. Supersedes the single-message AES-GCM file format (still readable, see below).

## Date
2026-10-09

## Context
The first format was `iv(12) || AES-GCM(ciphertext) || tag` as one message. Problems found while supporting videos up to 500 MB:

- The authentication tag is only checked at the very end, and Android's GCM provider buffers the whole message, so large files can exhaust memory.
- No random access: playback therefore decrypted the whole video into a plaintext file in the app cache. That is a plaintext copy of the most sensitive data on disk, and it is slow to start.
- No protection against truncation or swapping ciphertext between items.

## Decision
Use Tink's `AesGcmHkdfStreaming` (AES-256-GCM, HKDF per-file key, 1 MiB segments). File layout:

```
"VLTSTRM2" (8-byte magic) || Tink stream: header(salt, nonce prefix) || segment_0 || ... || segment_n(final)
```

- Associated data is the file name, so ciphertext renamed to another item fails to authenticate.
- The stream key comes from `HMAC-SHA256(master, "vault-stream-v2")`; the master key is never used as a stream key.
- Playback uses Media3 ExoPlayer with `VaultDataSource`, which reads through `VaultFiles.openSeekable` and decrypts only the segments touched. No plaintext file is created.
- Imports larger than 500 MiB are refused up front (`MediaTransfer.MAX_IMPORT_BYTES`).
- Legacy v1 files stay readable (`VaultFiles.decryptStream` detects the magic) and are rewritten in place on first play (`upgradeToStreaming`).

## Alternatives Considered

### Hand-rolled chunked AES-GCM
- Pros: no dependency.
- Cons: nonce management, final-segment marking and truncation detection are exactly the places hand-written streaming crypto goes wrong.
- Rejected: use an audited implementation.

### Keep decrypt-to-temp-file for playback
- Rejected: leaves plaintext on disk and cannot meet the 500 MB goal without long waits.

### AES-CTR + separate HMAC
- Rejected: reinvents AEAD, easy to get the encrypt-then-MAC ordering or the MAC coverage wrong.

## Consequences
- Memory use is about one segment regardless of file size; overhead is 16 bytes per MiB (a 413 MiB video grew by about 6.6 KB).
- Each segment is authenticated independently, so corruption is reported while reading rather than after the whole file.
- Adds `tink-android`, `media3-exoplayer`, `media3-ui` to the dependency set (release APK stays about 5 MB after R8).
- The key is bound to the master key only; changing the master key would require re-encrypting files (not currently possible, the master key is fixed for the life of a vault).
