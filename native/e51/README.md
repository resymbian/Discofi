# NotiHelper (Nokia E51 / S60 3rd FP1)

The E51's Java runtime has **no** Nokia UI soft notifications and **no** Pigler
support. This small native EXE is the missing piece: it listens on
`127.0.0.1:4711`, shows an Avkon global note and stores the notification as an
unread received-SMS entry in Messaging's Inbox. Any MIDlet that speaks the
protocol below can use it - Discofi does, and so does ResyGram.

## Status

Built and signed here, with the S60 3rd Edition FP1 SDK inside Docker:

```
native/e51/out/NotiHelper.sisx      (signed, self-signed certificate)
```

Each notification produces two things:

1. a global note over whatever is on screen, for the seconds it lasts;
2. an entry in **Messaging -> Inbox**, unread, with the title as the sender and
   the text as the body - the same place a Bluetooth transfer lands in, and it
   stays after the note fades.

The Inbox half writes an SMS-DELIVER header plus body directly to the message
store. `CSmsClientMtm::SaveMessageL()` would create an SMS-SUBMIT (an outgoing
message) and must not be used for this. The link libraries are `msgs` (the
messaging server client: `CMsvSession`, `CClientMtmRegistry`), `smcm` (the SMS
MTM: `CSmsClientMtm`, `CSmsHeader`), `etext` (rich text) and `gsmu` (the PDU
classes). There is no `msvapi.lib` in this SDK; that name is only the header.

The message is not sent over the network and carries no PDU from an operator:
it is a local store entry. The phone may still offer Reply on it, and replies
would be real SMS to a sender that is not a phone number, so treat it as a
notification log, not a chat.

What has *not* been verified is either effect on a real handset: it is linked
against the real Avkon and Messaging libraries and signed, but nothing in this
environment can run S60 3.1. If the messaging store refuses the write (no SMS
service configured, capability check), the note is still shown and the protocol
still answers `1`; the Inbox copy is the part that can silently not happen.

## Build

Everything happens in the container; no SDK is installed on the host.

```
cd native/e51
docker build -f docker/Dockerfile -t discofi-e51-sdk .
docker build -f docker/Dockerfile.signtool -t discofi-e51-sdk:signfix .
chmod 777 out                      # the container runs as uid 10001
docker run --rm -v "$PWD:/src:ro" -v "$PWD/out:/out" discofi-e51-sdk:signfix
```

`Dockerfile` also works on its own once `gnupoc-openssl11.patch` carries the
`ERR_GET_FUNC` fix (see below); the `signfix` layer exists so the already-built
3.7 GB SDK image can be used without downloading and extracting the SDK again.

Three GnuPoc/OpenSSL 1.1 problems had to be solved before this produced a file,
in this order:

1. **`makekeys`/`signsis` reject every certificate** with
   `PEM routines:get_name:no start line: Expecting: CERTIFICATE`.
   `signutils.cpp` decides "end of chain" with
   `ERR_GET_FUNC(err) == PEM_F_PEM_READ_BIO`, but OpenSSL 1.1 removed the
   function code from the error queue, so the comparison never matches and the
   normal end-of-input error is treated as a corrupt certificate.
   `Dockerfile.signtool` rebuilds the tool with the library+reason check only.
2. **`openssl dsaparam -genkey` fails** in this image, so the certificate is
   generated as RSA (`openssl req -x509 -newkey rsa:2048`), which `signsis`
   accepts just as well.
3. **`makesis`/`signsis` reject a bare output filename** (`invalid destination
   file`); `./Name.sisx` is accepted. The build scripts use `./`.

The PKG header also needs a language list, and the vendor as two lines -
`%{"Resymbian"}` (localised) and `:"Resymbian"` (unique) - or `makesis` stops
with `No vendor name specified`.

## Install and run

1. Keep Open4All and the installserver patch active (already needed for the
   TLS-patched Discofi JAR).
2. Install `NotiHelper.sisx`. The UID is the same one the earlier
   `DiscofiNotify` package used, so it replaces that install instead of sitting
   beside it.
3. Launch **NotiHelper** once after each reboot. It has no visible UI; it
   just starts the loopback listener.
4. In Discofi: **Options -> Settings -> Notifications -> Background
   notifications = On**, then choose **Send test notification**. In ResyGram:
   **Settings -> Notifications**, then **Test notification** in the chat list
   menu. A system note saying `<title>: Test notification...` confirms the
   bridge works, and the same text appears in **Messaging -> Inbox**.

## Protocol

One connection per notification:

* `writeUTF(title)`
* `writeUTF(text)`
* read one byte: `1` = note shown (and the Inbox write was attempted),
  `0` = error

`writeUTF` is Java's modified UTF-8 with a two-byte big-endian length. The
native side decodes it, so accented text and emoji work. `ResyGram` writes those
bytes itself because `java.io.DataOutputStream` drags an SE-only superclass into
a CLDC artifact; the wire format is identical.

## Battery and background execution

S60v3 Java MIDlets keep their threads while minimized, so polling continues.
The default interval is 5 minutes; shorten it in Settings if you want faster
notifications and accept the extra battery/network use. If the phone suspends
Java threads while the display is off, notifications will only appear when the
app resumes - true real-time alerts would need a gateway push.
