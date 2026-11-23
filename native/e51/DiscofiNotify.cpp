/**
 * DiscofiNotify - tiny native companion for Nokia E51 / S60 3rd Edition FP1.
 *
 * The E51 Java runtime has no Nokia soft notifications and no Pigler support,
 * so Discofi's J2ME poller sends notifications to this process over a local
 * TCP socket, and this process shows an Avkon global note.
 *
 * Protocol (one connection per notification):
 *   writeUTF(title)   - Java modified UTF-8, 2-byte big-endian length
 *   writeUTF(text)    - same
 *   read 1 byte       - 1 = note displayed, 0 = error
 *
 * Only the note API is used. The earlier version also wrote an incoming SMS into
 * Messaging's Inbox through CMsvSession, but the S60 3.1 C++ SDK ships the
 * msvapi headers without the import libraries (msvapi.dso/.lib are not in
 * epoc32/release/armv5/lib), so that path cannot be linked. A note is what the
 * user sees anyway; the Inbox copy was a convenience.
 *
 * Build with native/e51/docker (GnuPoc toolchain + S60 SDK). See README.md.
 */

#include <e32base.h>
#include <e32std.h>
#include <es_sock.h>
#include <in_sock.h>
#include <aknglobalnote.h>
#include <aknnotifystd.h>
#include <avkon.hrh>

const TUint KDiscofiPort = 4711;
const TUint32 KDiscofiLoopback = INET_ADDR(127, 0, 0, 1);
const TInt KMaxFieldLength = 512;
const TInt KMaxNoteLength = 160;

/** RSocket::Recv until the descriptor is full. */
static void ReadExactL(RSocket& aSocket, TDes8& aBuffer)
{
    TInt offset = 0;
    const TInt maxLength = aBuffer.MaxLength();

    while (offset < maxLength)
    {
        TPtr8 chunk = aBuffer.MidTPtr(offset);
        TRequestStatus status;
        aSocket.Recv(chunk, chunk.MaxLength(), status);
        User::WaitForRequest(status);
        User::LeaveIfError(status.Int());

        if (chunk.Length() == 0)
        {
            User::Leave(KErrEof);
        }

        offset += chunk.Length();
        aBuffer.SetLength(offset);
    }
}

/**
 * Decode Java's modified UTF-8. writeUTF() emits one-, two- and three-byte
 * sequences only: UTF-16 surrogate pairs are written as two three-byte
 * sequences, so no four-byte decoding is needed.
 */
static void AppendModifiedUtf8L(const TDesC8& aSource, TDes& aTarget)
{
    TInt i = 0;

    while (i < aSource.Length())
    {
        TUint8 first = aSource[i++];

        if (first < 0x80)
        {
            aTarget.Append((TText)first);
        }
        else if ((first & 0xE0) == 0xC0)
        {
            if (i >= aSource.Length()) User::Leave(KErrCorrupt);
            TUint8 second = aSource[i++];
            aTarget.Append((TText)(((first & 0x1F) << 6) | (second & 0x3F)));
        }
        else if ((first & 0xF0) == 0xE0)
        {
            if (i + 1 >= aSource.Length()) User::Leave(KErrCorrupt);
            TUint8 second = aSource[i++];
            TUint8 third = aSource[i++];
            aTarget.Append((TText)(((first & 0x0F) << 12) |
                ((second & 0x3F) << 6) | (third & 0x3F)));
        }
        else
        {
            User::Leave(KErrCorrupt);
        }
    }
}

static HBufC* ReadUtfL(RSocket& aSocket)
{
    TBuf8<2> lengthBytes;
    lengthBytes.SetLength(2);
    ReadExactL(aSocket, lengthBytes);

    TInt length = (lengthBytes[0] << 8) | lengthBytes[1];
    if (length < 0 || length > KMaxFieldLength) User::Leave(KErrTooBig);
    if (length == 0) return HBufC::NewL(0);

    HBufC8* raw = HBufC8::NewLC(length);
    TPtr8 rawPtr = raw->Des();
    rawPtr.SetLength(length);
    ReadExactL(aSocket, rawPtr);
    raw->Des().SetLength(length);

    HBufC* text = HBufC::NewL(length);
    TPtr target = text->Des();
    AppendModifiedUtf8L(*raw, target);
    CleanupStack::PopAndDestroy(raw);
    return text;
}

/**
 * Show the note. Newlines become spaces: a global note is one or two lines and
 * the J2ME side already folds multi-line previews, but a title built from a
 * chat name can still arrive with one.
 */
static void ShowNotificationL(const TDesC& aTitle, const TDesC& aText)
{
    CAknGlobalNote* note = CAknGlobalNote::NewLC();

    TBuf<KMaxNoteLength> message;
    message.Copy(aTitle);
    message.Append(_L(": "));
    message.Append(aText);
    if (message.Length() > KMaxNoteLength) message.SetLength(KMaxNoteLength);

    note->ShowNoteL(EAknGlobalInformationNote, message);
    CleanupStack::PopAndDestroy(note);
}

static void HandleConnectionL(RSocket& aConnection, TBool& aOk)
{
    HBufC* title = ReadUtfL(aConnection);
    CleanupStack::PushL(title);
    HBufC* text = ReadUtfL(aConnection);
    CleanupStack::PushL(text);

    ShowNotificationL(*title, *text);
    aOk = ETrue;

    CleanupStack::PopAndDestroy(text);
    CleanupStack::PopAndDestroy(title);
}

static void AnswerL(RSocket& aSocket, TBool aOk)
{
    TBuf8<1> reply;
    reply.Append((TUint8)(aOk ? 1 : 0));

    TRequestStatus status;
    aSocket.Send(reply, reply.Length(), status);
    User::WaitForRequest(status);
    User::LeaveIfError(status.Int());
}

static void ServeL()
{
    RSocketServ socketServer;
    User::LeaveIfError(socketServer.Connect());
    CleanupClosePushL(socketServer);

    RSocket listener;
    User::LeaveIfError(listener.Open(socketServer, KAfInet, KSockStream,
        KProtocolInetTcp));
    CleanupClosePushL(listener);

    TInetAddr address;
    address.SetAddress(KDiscofiLoopback);
    address.SetPort(KDiscofiPort);
    User::LeaveIfError(listener.Bind(address));
    User::LeaveIfError(listener.Listen(4));

    for (;;)
    {
        RSocket connection;
        TRequestStatus acceptStatus;
        listener.Accept(connection, acceptStatus);
        User::WaitForRequest(acceptStatus);
        if (acceptStatus.Int() != KErrNone) continue;
        CleanupClosePushL(connection);

        TBool ok = EFalse;
        TRAPD(handlerError, HandleConnectionL(connection, ok));
        (void)handlerError;

        TRAPD(answerError, AnswerL(connection, ok));
        (void)answerError;

        CleanupStack::PopAndDestroy(&connection);
    }

    CleanupStack::PopAndDestroy(&listener);
    CleanupStack::PopAndDestroy(&socketServer);
}

GLDEF_C TInt E32Main()
{
    CTrapCleanup* cleanup = CTrapCleanup::New();
    if (cleanup == NULL) return KErrNoMemory;

    TInt result = KErrNone;
    TRAP(result, ServeL());

    delete cleanup;
    return result;
}
