# ADR-005: Remove the People / face-detection feature

## Status
Accepted

## Date
2026-10-09

## Context
The vault shipped face detection (ML Kit), face embeddings (a bundled FaceNet TFLite model) and a People tab for naming and grouping faces. For a privacy vault this adds:

- A large dependency and model (ML Kit + TensorFlow Lite + a model asset) and the code that runs them on plaintext.
- Biometric-like data (face embeddings, boxes, names) stored per photo, which increases the damage of any metadata leak.
- An undocumented telemetry queue (`com.google.android.datatransport.events`) created by the ML Kit dependency, which is at odds with a no-network app.

## Decision
Remove the feature entirely: UI (People tab, tagging dialogs, Albums section, search by person), workers, the detector and embedder, the model asset, both dependencies, the `people` table, and face data in metadata.

- `PhotoMetadataCodec` v4 drops faces; v1-v3 payloads still decode and their face block is skipped (`PhotoMetadataCodecTest`).
- Room migration `MIGRATION_4_5` drops `people` only; photos and albums are untouched (`DatabaseMigrationTest`).
- Leftover `*.face<N>.enc` crops and the telemetry database are deleted at startup.

## Alternatives Considered

### Keep faces but store nothing in clear
- Rejected: the data was already encrypted; the cost was dependencies, code on plaintext and the telemetry file.

### Make it an opt-in module
- Rejected: still ships the model and libraries in every APK.

## Consequences
- Smaller attack surface and APK; no biometric-like data at rest.
- Users lose face-based browsing and search. Re-adding it later should be a separate decision (and an opt-in build flavour).
