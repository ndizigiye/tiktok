# REA playbook: answering the open questions

[REA](https://github.com/morluto/rea) ("Reverse Engineer Anything") is an MCP
server and CLI that lets an agent inspect shipped software and explain how a
feature works, with evidence. Its intended loop is: *understand how a feature
works, show the evidence, then build your own version*. This playbook covers
the questions [feature-analysis.md](feature-analysis.md) left open. They need
an APK, so they could not be answered in the sandbox Loop was built in.

## Boundaries

These follow REA's own [DISCLAIMER](https://github.com/morluto/rea/blob/main/DISCLAIMER.md).
Read it before you start.

- Analyse only artifacts you are entitled to examine, under the law that
  applies to you and the app's licence terms. Reverse engineering rules differ
  between jurisdictions. Check with your organisation's legal team first,
  especially before you publish anything.
- Take **behaviour and architecture** from the analysis: which components
  exist, which open-source libraries are bundled, which preload or caching
  strategy is used. Then write Loop's code yourself. Do not copy decompiled
  code, resources, artwork, sounds or strings into this repository.
- Do not investigate or reimplement request signing, device attestation,
  anti-bot or DRM mechanisms, and do not point Loop at another service's
  private API. REA does not support circumventing protection measures.
- Keep investigation artifacts (APKs, decompiled output, evidence bundles)
  out of git. `.gitignore` does not cover arbitrary paths, so store them
  outside the repository.

## Setup

1. Node.js 22.19+ (or 24.11+), npm, a full JDK 17+ (Android Studio's bundled
   JBR works) and a procps-compatible `ps` on Linux.
2. Register REA with your agent: `npx rea-agents setup` (review the plan, then
   approve it), and restart the agent.
3. Download jadx-headless-mcp 0.7.1 and verify its SHA-256 as described in
   REA's [Android guide](https://github.com/morluto/rea/blob/main/docs/android-analysis.md#supply-tools),
   then set `REA_JADX_MCP_JAR=/absolute/path/jadx-headless-mcp-0.7.1-all.jar`
   in the MCP server's environment.
4. Optional: `adb` on PATH (or `REA_ADB_PATH`) for the device tools in REA's
   [ADB guide](https://github.com/morluto/rea/blob/main/docs/adb-device-analysis.md).

## Getting the artifact

Use the copy installed on your own test device instead of a third-party mirror.
Pulling it preserves the split APKs and gives you SHA-256 digests to cite:

```sh
rea list-adb-devices
rea pull-adb-package <serial> <package.name> --output-directory /absolute/path/outside/repo/acq
```

(MCP: `list_adb_devices`, `pull_adb_package`.) Modern apps ship as a base APK
plus splits; analyse `base.apk` for code and the manifest.

## Questions

### Q1 — Components, permissions, platform features (feature-analysis #12)

```sh
rea inspect-android-package /abs/acq/base.apk
```

MCP: `inspect_android_package` with `detail: "summary"`, then
`inspect_analysis_view` on the returned `parent_evidence_id` with
`view: {kind: "android", facet: "manifest"}` to page the decoded manifest.

Record: min/target SDK, permissions, activities that declare picture-in-picture
or special `configChanges`, foreground services (background playback/upload),
deep-link intent filters. **Feeds into Loop:** whether to add picture-in-picture,
a foreground upload service, or app links for `/v/{id}`.

### Q2 — Bundled open-source libraries (feature-analysis #12)

```sh
rea search-android-classes /abs/acq/base.apk androidx.media3
rea search-android-classes /abs/acq/base.apk com.google.android.exoplayer2
rea search-android-classes /abs/acq/base.apk androidx.camera
rea search-android-classes /abs/acq/base.apk okhttp3
```

Record only *which* well-known libraries are present (a presence or absence
observation with its Evidence ID). Obfuscated or absent package names stay
**unknown**: absence from a search does not prove a library is absent.
**Feeds into Loop:** confirms or challenges the Media3/CameraX choice.

### Q3 — Preload strategy (feature-analysis #4)

1. Find candidate player-setup classes:
   `rea search-android-classes /abs/acq/base.apk LoadControl` (also `Preload`,
   `CacheDataSource`, `SimpleCache`).
2. For each candidate, list members (`rea inspect-android-class ...`) and trace
   who constructs it (`rea trace-android-references ... --method-name <init>`).
3. Read only the configuration you need: buffer durations, cache size, how many
   items ahead are preloaded (`rea inspect-android-method ...`).

Write the answer down as **parameters and behaviour** ("prepares N items ahead,
caches the first X KB, buffer min/max Y/Z ms") with Evidence IDs and
limitations. Reflection, dynamic loading and native code remain unknown.
**Feeds into Loop:** tune `FeedPlayerPool` (pool size), `VideoCache.prefetch`
(bytes, look-ahead) and the `DefaultLoadControl` values in
`app/src/main/java/com/ndizigiye/loop/player/`.

### Q4 — Capture encoder settings (feature-analysis #9)

Search for `MediaCodec`, `MediaRecorder`, `Recorder` or `VideoCapture`
references and read the configuration methods. Record the resolution, bitrate
and maximum duration as observations. **Feeds into Loop:** the CameraX
`QualitySelector` and `ApiLimits.MAX_RECORDING_MS`.

## Recording results

For every question, add to [feature-analysis.md](feature-analysis.md):

- status: answered / partially answered / unresolved;
- the observation, with Evidence IDs and the APK's SHA-256;
- what was *inferred* rather than observed, and the search boundary;
- the resulting design decision in Loop, written as your own specification.

Close any native session with `close_binary` when you are done. You can export
the evidence with `export_evidence_bundle` to a path outside the repository.
