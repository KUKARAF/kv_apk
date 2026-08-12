package dev.kv.apk.data

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.gson.JsonParser

/**
 * Thrown when the passkey (WebAuthn assertion) step cannot proceed because the account/device
 * has no usable passkey to sign with — i.e. the platform Credential Manager reports
 * [NoCredentialException]. Callers surface this to the user as "enrol a passkey first" rather
 * than as a generic failure, because the fix is an enrolment action, not a retry.
 *
 * NOTE on RP association: the server's WebAuthn Relying Party is `kv.osmosis.page`. For the
 * platform authenticator to release an assertion for that RP to THIS app, the app must be
 * associated with the origin via Digital Asset Links — the server must serve
 * `https://kv.osmosis.page/.well-known/assetlinks.json` including this app's package
 * (`dev.kv.apk`) and its signing-cert SHA-256 fingerprint under the
 * `delegate_permission/common.get_login_creds` relation. The server-side WebAuthn config must
 * also accept the resulting Android origin (`android:apk-key-hash:<b64url-sha256-of-cert>`) when
 * verifying the assertion. Until that association exists, Credential Manager will not surface a
 * passkey for this RP and this exception is what the user sees.
 */
class PasskeyEnrolmentRequiredException(message: String) : Exception(message)

/**
 * Two-step WebAuthn-gated device registration against the current server contract:
 *   1. POST api/devices/register/begin  -> { challenge_id, options }
 *   2. POST api/devices/register/finish -> { id }   (requires a signed WebAuthn assertion)
 *
 * Step 1 obtains a WebAuthn authentication challenge (`options`, a
 * PublicKeyCredentialRequestOptions). Step 2 drives the platform authenticator via AndroidX
 * Credential Manager to produce a signed assertion (PublicKeyCredential) and submits it. On
 * success the server persists the device and returns its id.
 *
 * @param context an Activity context — Credential Manager needs it to host the passkey UI.
 * @throws PasskeyEnrolmentRequiredException when there is no passkey to sign with, or the
 *   ceremony otherwise fails; the message is user-facing.
 */
suspend fun registerDeviceViaPasskey(
    context: Context,
    api: KvApi,
    name: String,
    pubKeySpki: String,
): String {
    val begin = api.deviceRegisterBegin(
        DeviceRegisterBeginRequest(name = name, publicKey = pubKeySpki, keyType = "p256"),
    )

    // webauthn-rs serialises the challenge as a WebAuthn RequestChallengeResponse, which wraps
    // the PublicKeyCredentialRequestOptions in a top-level {"publicKey": {...}} envelope.
    // Credential Manager's GetPublicKeyCredentialOption wants just the inner
    // PublicKeyCredentialRequestOptionsJSON, so unwrap the envelope when present.
    val optionsElement = begin.options
    val requestJson = if (optionsElement.has("publicKey") && optionsElement.get("publicKey").isJsonObject) {
        optionsElement.getAsJsonObject("publicKey")
    } else {
        optionsElement
    }.toString()

    val credentialManager = CredentialManager.create(context)
    val getRequest = GetCredentialRequest(
        listOf(GetPublicKeyCredentialOption(requestJson = requestJson)),
    )

    val assertionJson: String = try {
        val response = credentialManager.getCredential(context, getRequest)
        val credential = response.credential
        if (credential !is PublicKeyCredential) {
            throw PasskeyEnrolmentRequiredException(
                "Passkey ceremony returned an unexpected credential type (${credential.type}); " +
                    "expected a WebAuthn passkey.",
            )
        }
        credential.authenticationResponseJson
    } catch (e: NoCredentialException) {
        throw PasskeyEnrolmentRequiredException(
            "No passkey is available for kv.osmosis.page on this device. Enrol a passkey for " +
                "your account (via the web admin panel), then register this device. (If a passkey " +
                "does exist, the app may not yet be associated with the site — see assetlinks setup.)",
        )
    } catch (e: GetCredentialException) {
        throw PasskeyEnrolmentRequiredException(
            "Passkey authentication failed: ${e.errorMessage ?: e.type}",
        )
    }

    val finish = api.deviceRegisterFinish(
        DeviceRegisterFinishRequest(
            challengeId = begin.challengeId,
            assertion = JsonParser.parseString(assertionJson).asJsonObject,
        ),
    )
    return finish.id
}
