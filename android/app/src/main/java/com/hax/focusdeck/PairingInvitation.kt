package com.hax.focusdeck

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.*
import okhttp3.*
import java.util.UUID

@Serializable internal data class PairingInvitation(
    val type: String,
    @SerialName("invitation_version") val invitationVersion: Int,
    @SerialName("protocol_version") val protocolVersion: Int,
    @SerialName("invitation_id") val invitationId: String,
    val nonce: String,
    val host: String,val port: Int,
    @SerialName("desktop_id") val desktopId: String,
    @SerialName("certificate_sha256") val certificateSha256: String,
    val role: String,
    @SerialName("expires_at_ms") val expiresAtMs: Long
) {
    fun pin()=Pairing(protocolVersion,host,port,"invitation-validation-placeholder",certificateSha256,desktopId,role)
    fun validate(now: Long=System.currentTimeMillis()) {
        require(type=="focusdeck_pairing_invitation") { "Invalid FocusDeck QR invitation" }
        require(invitationVersion==1 && protocolVersion==1) { "Pairing protocol version mismatch" }
        pin().validate()
        require(runCatching { UUID.fromString(invitationId).toString()==invitationId }.getOrDefault(false) && nonce.matches(Regex("[A-Za-z0-9_-]{43}"))) { "Invalid pairing invitation" }
        require(expiresAtMs>now) { "Invitation expired; create a new invitation" }
        require(expiresAtMs-now<=180_000) { "Invalid invitation expiry; check the device clock" }
    }
    companion object {
        fun parse(raw: String,now: Long=System.currentTimeMillis()): PairingInvitation {
            require(raw.length<=4096) { "QR invitation is too large" }
            val parsed=try { WireJson.decodeFromString<PairingInvitation>(raw) } catch(e: Exception) { throw IllegalArgumentException("Invalid or unsupported FocusDeck QR") }
            parsed.validate(now);return parsed
        }
    }
}

/** Invites reveal permanent credentials only over pinned TLS, followed by the ordinary v1 hello. */
internal suspend fun redeemPairingInvitation(invitation: PairingInvitation,deviceId: String): Pairing = withTimeout(20_000) {
    invitation.validate()
    require(deviceId.isNotBlank()) { "Device identity is not ready" }
    suspend fun exchange(pin: Pairing,path: String,message: JsonObject,bearer: Boolean=false): JsonObject {
        val client=pinnedPairingClient(pin)
        val messages=Channel<String>(8)
        val builder=Request.Builder().url(HttpUrl.Builder().scheme("https").host(pin.host).port(pin.port).addPathSegments(path).build())
        if(bearer) builder.header("Authorization","Bearer ${pin.token}")
        val socket=client.newWebSocket(builder.build(),object: WebSocketListener() {
            override fun onOpen(ws: WebSocket,response: Response) { ws.send(message.toString()) }
            override fun onMessage(ws: WebSocket,text: String) { if(text.length<=65_536) messages.trySend(text) else messages.close(IllegalStateException("Invalid host response")) }
            override fun onFailure(ws: WebSocket,t: Throwable,response: Response?) { messages.close(IllegalStateException("Pairing host unreachable or certificate rejected")) }
            override fun onClosing(ws: WebSocket,code: Int,reason: String) { ws.close(code,"");messages.close() }
        })
        try { return WireJson.parseToJsonElement(messages.receive()).jsonObject }
        finally { socket.close(1000,"");messages.cancel();client.dispatcher.executorService.shutdown();client.connectionPool.evictAll() }
    }
    val grant=exchange(invitation.pin(),"v1/pair",buildJsonObject {
        put("type","redeem_invitation");put("protocol_version",1);put("invitation_version",1)
        put("invitation_id",invitation.invitationId);put("nonce",invitation.nonce)
        put("desktop_id",invitation.desktopId);put("role",invitation.role);put("device_id",deviceId);put("name","Android FocusDeck")
    })
    require(grant["type"]?.jsonPrimitive?.content!="pairing_error") { grant["error"]?.jsonPrimitive?.content?.take(180) ?: "Pairing invitation rejected" }
    require(grant["type"]?.jsonPrimitive?.content=="pairing_grant" && grant["protocol_version"]?.jsonPrimitive?.int==1 && grant["invitation_version"]?.jsonPrimitive?.int==1) { "Pairing protocol version mismatch" }
    val pairing=WireJson.decodeFromJsonElement<Pairing>(grant.getValue("pairing"));pairing.validate()
    require(pairing.host==invitation.host && pairing.port==invitation.port && pairing.desktopId==invitation.desktopId && pairing.role==invitation.role && pairing.certificateSha256.equals(invitation.certificateSha256,true)) { "Pairing host identity changed" }
    val hello=exchange(pairing,if(pairing.viewer) "v1/viewer" else "v1/device",deviceHello(deviceId,pairing.viewer),true)
    require(hello["type"]?.jsonPrimitive?.content=="hello_ack" && hello["protocol_version"]?.jsonPrimitive?.int==1 && hello["desktop_id"]?.jsonPrimitive?.content==pairing.desktopId && (!pairing.viewer || hello["role"]?.jsonPrimitive?.content=="viewer")) { "Authenticated pairing handshake failed; create a new invitation" }
    pairing
}
