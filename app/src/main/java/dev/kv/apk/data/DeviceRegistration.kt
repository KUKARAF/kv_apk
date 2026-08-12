package dev.kv.apk.data

/**
 * Thrown when device registration reaches the passkey (WebAuthn assertion) step, which is not
 * yet implemented in this build. The server-side two-step contract is exercised up to and
 * including the `begin` call; the `finish` call needs a real signed assertion from the
 * account's passkey via the platform Credential Manager / FIDO2 API.
 *
 * FOLLOW-UP: wire androidx.credentials CredentialManager to obtain the assertion (see
 * [registerDeviceViaPasskey]) and add the `androidx.credentials` dependency to build.gradle.kts.
 */
class PasskeyEnrolmentRequiredException(challengeId: String) : Exception(
    "This device must be enrolled with a passkey (WebAuthn), which is not yet available in " +
        "this build. The server issued challenge $challengeId but the app cannot sign it yet. " +
        "Register this device from the web admin panel for now.",
)

/**
 * Two-step WebAuthn-gated device registration against the current server contract:
 *   1. POST api/devices/register/begin  -> { challenge_id, options }
 *   2. POST api/devices/register/finish -> { id }   (requires a signed WebAuthn assertion)
 *
 * Step 1 is fully wired. Step 2's assertion ceremony is a follow-up: this function performs the
 * begin call (so the contract and auth/passkey precondition are actually exercised) and then
 * raises [PasskeyEnrolmentRequiredException], which callers surface to the user instead of
 * silently registering a device that the server will not accept.
 *
 * Returns the server-assigned device id once the ceremony is implemented.
 */
suspend fun registerDeviceViaPasskey(api: KvApi, name: String, pubKeySpki: String): String {
    val begin = api.deviceRegisterBegin(
        DeviceRegisterBeginRequest(name = name, publicKey = pubKeySpki, keyType = "p256"),
    )

    // TODO(passkey follow-up): drive the platform authenticator with begin.options
    // (PublicKeyCredentialRequestOptions) to obtain a signed assertion, then:
    //   val finish = api.deviceRegisterFinish(
    //       DeviceRegisterFinishRequest(challengeId = begin.challengeId, assertion = assertion),
    //   )
    //   return finish.id
    throw PasskeyEnrolmentRequiredException(begin.challengeId)
}
