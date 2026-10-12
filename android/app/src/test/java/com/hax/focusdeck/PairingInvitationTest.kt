package com.hax.focusdeck

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class PairingInvitationTest {
    private val now=1000000L
    private fun qr()=buildJsonObject {
        put("type","focusdeck_pairing_invitation");put("invitation_version",1);put("protocol_version",1)
        put("invitation_id","d5a2e47e-7f02-4a46-8b60-a4d2c60374c4");put("nonce","x".repeat(43))
        put("host","127.0.0.1");put("port",8765);put("desktop_id","disposable-host")
        put("certificate_sha256","a".repeat(64));put("role","phone");put("expires_at_ms",now+120000)
    }
    private fun rejects(fields: Map<String,JsonElement>) {
        try {PairingInvitation.parse(JsonObject(qr()+fields).toString(),now);fail("Rejected invitation accepted")}
        catch(expected: IllegalArgumentException) {assertFalse(expected.message.orEmpty().contains("x".repeat(43)))}
    }
    @Test fun invitationHasNoPermanentTokenAndRetainsPinnedIdentity() {
        val invitation=PairingInvitation.parse(qr().toString(),now)
        assertEquals("disposable-host",invitation.pin().desktopId);assertEquals("a".repeat(64),invitation.pin().certificateSha256)
        assertFalse(qr().containsKey("token"));assertFalse(invitation.pin().viewer)
    }
    @Test fun expiredAndUnreasonableFutureInvitationsAreRejected() {
        rejects(mapOf("expires_at_ms" to JsonPrimitive(now)));rejects(mapOf("expires_at_ms" to JsonPrimitive(now+180001)))
    }
    @Test fun wrongProtocolRolePinAndNonceAreRejectedWithoutEchoingPayload() {
        listOf("protocol_version" to JsonPrimitive(2),"invitation_version" to JsonPrimitive(2),"role" to JsonPrimitive("admin"),
            "certificate_sha256" to JsonPrimitive("short"),"nonce" to JsonPrimitive("short"),"host" to JsonPrimitive("host/path")).forEach {rejects(mapOf(it))}
    }
    @Test fun arbitraryQrAndPermanentPairingJsonAreNotInvitations() {
        for(raw in listOf("https://example.com", "{\"token\":\"private\"}")) {
            try {PairingInvitation.parse(raw,now);fail("Invalid QR accepted")} catch(e: IllegalArgumentException) {assertEquals("Invalid or unsupported FocusDeck QR",e.message)}
        }
    }
}
