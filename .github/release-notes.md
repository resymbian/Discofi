> [!WARNING]
> **Beta release.** Emojis, reactions and unread indicators have only been
> tested in the emulator/debug demo, not yet with a real Discord account on a
> Nokia E51. Back up your data and expect rough edges.

## Which file do I install?

Install **`discofi.jar`**. It contains the application and all resources,
including the bundled emojis.

`discofi.jad` is not the app. It is a small text descriptor with the version,
JAR size and the line `MIDlet-Jar-URL: discofi.jar`. It is only needed for OTA
installs through the phone browser, and it expects the JAR to be available at
that relative URL.

* **Cable, Bluetooth or memory card:** copy `discofi.jar` to the phone and open
  it from the file manager or the application installer.
* **Browser/OTA install:** serve both `discofi.jad` and `discofi.jar` from the
  same directory, then open the JAD. If the JAR is not next to it, the install
  will fail.
* Do not install `discofi_debug.jar` for normal use; it is the debug build.
* `discofi_lite.jar` omits the bundled emoji assets for devices with very
  little memory.

On Symbian S60v3 (including the E51) no signing certificate is required.
Install the nnproject TLS 1.2 patch and jailbreak with
RomPatcher/installserver first.

This build fixes four E51 problems:

* the JAD and manifest now declare `MIDlet-Permissions-Opt` for `socket`,
  `http` and file read/write. Without the socket permission S60 throws a
  `SecurityException` on the first `socket://127.0.0.1:4711` open, which the
  test notification incorrectly reported as "no native agent answered";
* the notification dialog now includes the exact exception instead of hiding
  it behind that generic message;
* reactions, edit and delete use ModernConnector on Symbian for `PUT`,
  `DELETE` and `PATCH`, because the platform's `HttpConnection` rejects those
  methods before sending;
* the JAD generator unfolds wrapped manifest lines, so the permission list is
  no longer truncated at the first 72-byte fold.

Every build is smoke-tested headlessly for startup, emojis, reactions and
shutdown.

Verify the download with `SHA256SUMS`, and optionally with
`gh attestation verify discofi.jar --repo resymbian/Discofi`.
