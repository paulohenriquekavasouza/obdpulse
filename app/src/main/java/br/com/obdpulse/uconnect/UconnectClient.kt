package br.com.obdpulse.uconnect

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Base64
import kotlin.random.Random

object UconnectClient {

    const val CMD_LOCK = "RDL"
    const val CMD_UNLOCK = "RDU"
    const val CMD_LOCATE = "VF"

    suspend fun login(email: String, password: String): UconnectSession = withContext(Dispatchers.IO) {
        val loginToken = gigyaLogin(email, password)
        val idToken = gigyaJwt(loginToken)
        val uid = subjectOf(idToken)
        val (token, identityId) = tokenExchange(idToken)
        val creds = cognitoCredentials(identityId, token)
        UconnectSession(uid, creds)
    }

    suspend fun listVehicles(session: UconnectSession): List<UconnectVehicle> = withContext(Dispatchers.IO) {
        val url = "${UconnectBrand.API_URL}/v4/accounts/${session.uid}/vehicles?stage=ALL&sdp=ALL"
        val (code, body) = signed("GET", url, UconnectBrand.API_KEY, ByteArray(0), session)
        if (code !in 200..299) throw UconnectException("Falha ao listar veículos ($code): $body")
        val array = JSONObject(body).optJSONArray("vehicles") ?: return@withContext emptyList()
        (0 until array.length()).map { i ->
            val v = array.getJSONObject(i)
            val make = v.optString("make")
            val model = v.optString("model")
            val year = v.optString("year")
            val nickname = v.optString("nickname")
            val label = nickname.ifBlank { listOf(make, model, year).filter { it.isNotBlank() }.joinToString(" ") }
            val cmds = v.optJSONArray("supportedCommands")
            val list = if (cmds == null) emptyList() else (0 until cmds.length()).map { cmds.getString(it) }
            UconnectVehicle(v.optString("vin"), label.ifBlank { v.optString("vin") }, list)
        }
    }

    suspend fun location(session: UconnectSession, vin: String): VehicleLocation? = withContext(Dispatchers.IO) {
        val url = "${UconnectBrand.API_URL}/v1/accounts/${session.uid}/vehicles/$vin/location/lastknown"
        val (code, body) = signed("GET", url, UconnectBrand.API_KEY, ByteArray(0), session)
        if (code !in 200..299) throw UconnectException("Falha ao obter localização ($code): $body")
        val json = JSONObject(body)
        if (!json.has("latitude") || !json.has("longitude")) return@withContext null
        VehicleLocation(
            latitude = json.getDouble("latitude"),
            longitude = json.getDouble("longitude"),
            updatedMs = json.optLong("timeStamp").takeIf { it > 0 },
        )
    }

    suspend fun authenticatePin(session: UconnectSession, pin: String): String = withContext(Dispatchers.IO) {
        val url = "${UconnectBrand.AUTH_URL}/v1/accounts/${session.uid}/ignite/pin/authenticate"
        val encoded = Base64.getEncoder().encodeToString(pin.toByteArray(Charsets.UTF_8))
        val body = JSONObject().put("pin", encoded).toString().toByteArray(Charsets.UTF_8)
        val (code, resp) = signed("POST", url, UconnectBrand.AUTH_KEY, body, session)
        if (code !in 200..299) throw UconnectException("PIN recusado ($code): $resp")
        JSONObject(resp).optString("token").ifBlank { throw UconnectException("PIN sem token na resposta.") }
    }

    suspend fun command(session: UconnectSession, vin: String, name: String, pinAuth: String): Unit =
        withContext(Dispatchers.IO) {
            val path = if (name == CMD_LOCATE) "location" else "remote"
            val url = "${UconnectBrand.API_URL}/v1/accounts/${session.uid}/vehicles/$vin/$path"
            val body = JSONObject().put("command", name).put("pinAuth", pinAuth).toString().toByteArray(Charsets.UTF_8)
            val (code, resp) = signed("POST", url, UconnectBrand.API_KEY, body, session)
            if (code !in 200..299) throw UconnectException("Comando $name falhou ($code): $resp")
        }

    private fun gigyaLogin(email: String, password: String): String {
        val params = mapOf(
            "loginID" to email,
            "password" to password,
            "APIKey" to UconnectBrand.LOGIN_API_KEY,
            "sessionExpiration" to "86400",
            "include" to "profile,data",
            "includeUserInfo" to "true",
            "loginMode" to "standard",
            "targetEnv" to "jssdk",
            "sdk" to "js_latest",
            "format" to "json",
        )
        val (_, body) = execute("POST", "${UconnectBrand.LOGIN_URL}/accounts.login", form(params), formBody(params))
        val json = JSONObject(body)
        if (json.optInt("errorCode", -1) != 0) {
            throw UconnectException("Login falhou: ${json.optString("errorMessage", body)}")
        }
        val session = json.optJSONObject("sessionInfo")
        return session?.optString("login_token").orEmpty().ifBlank { json.optString("login_token") }
            .ifBlank { throw UconnectException("Login sem login_token.") }
    }

    private fun gigyaJwt(loginToken: String): String {
        val params = mapOf(
            "APIKey" to UconnectBrand.LOGIN_API_KEY,
            "login_token" to loginToken,
            "fields" to "profile.firstName,profile.lastName,profile.email,country,locale",
            "format" to "json",
        )
        val (_, body) = execute("POST", "${UconnectBrand.LOGIN_URL}/accounts.getJWT", form(params), formBody(params))
        val json = JSONObject(body)
        if (json.optInt("errorCode", -1) != 0) {
            throw UconnectException("getJWT falhou: ${json.optString("errorMessage", body)}")
        }
        return json.optString("id_token").ifBlank { throw UconnectException("getJWT sem id_token.") }
    }

    private fun tokenExchange(idToken: String): Pair<String, String> {
        val headers = defaultHeaders(UconnectBrand.API_KEY) + ("content-type" to "application/json")
        val body = JSONObject().put("gigya_token", idToken).toString().toByteArray(Charsets.UTF_8)
        val (code, resp) = execute("POST", UconnectBrand.TOKEN_URL, headers, body)
        if (code !in 200..299) throw UconnectException("Troca de token falhou ($code): $resp")
        val json = JSONObject(resp)
        val token = json.optString("token")
        val identityId = json.optString("IdentityId")
        if (token.isBlank() || identityId.isBlank()) throw UconnectException("Troca de token incompleta.")
        return token to identityId
    }

    private fun cognitoCredentials(identityId: String, token: String): AwsCreds {
        val headers = mapOf(
            "Content-Type" to "application/x-amz-json-1.1",
            "X-Amz-Target" to "AWSCognitoIdentityService.GetCredentialsForIdentity",
        )
        val body = JSONObject()
            .put("IdentityId", identityId)
            .put("Logins", JSONObject().put("cognito-identity.amazonaws.com", token))
            .toString().toByteArray(Charsets.UTF_8)
        val (code, resp) = execute("POST", UconnectBrand.COGNITO_URL, headers, body)
        if (code !in 200..299) throw UconnectException("Cognito falhou ($code): $resp")
        val creds = JSONObject(resp).optJSONObject("Credentials")
            ?: throw UconnectException("Cognito sem credenciais.")
        return AwsCreds(
            creds.getString("AccessKeyId"),
            creds.getString("SecretKey"),
            creds.getString("SessionToken"),
        )
    }

    private fun signed(
        method: String,
        urlStr: String,
        apiKey: String,
        body: ByteArray,
        session: UconnectSession,
    ): Pair<Int, String> {
        val url = URL(urlStr)
        val sig = AwsSigV4.headers(
            method, url, "execute-api", UconnectBrand.REGION,
            session.creds.accessKey, session.creds.secretKey, session.creds.sessionToken, body,
        )
        val headers = defaultHeaders(apiKey) + sig + ("content-type" to "application/json")
        return execute(method, urlStr, headers, if (method == "GET") null else body)
    }

    private fun defaultHeaders(apiKey: String): Map<String, String> = mapOf(
        "x-clientapp-name" to "CWP",
        "x-clientapp-version" to "1.0",
        "clientrequestid" to (randomHex(10) + System.currentTimeMillis().toString().takeLast(6)),
        "x-api-key" to apiKey,
        "locale" to UconnectBrand.LOCALE,
        "x-originator-type" to "web",
    )

    private fun execute(method: String, urlStr: String, headers: Map<String, String>, body: ByteArray?): Pair<Int, String> {
        val connection = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20_000
            readTimeout = 20_000
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
            if (body != null) {
                doOutput = true
                outputStream.use { it.write(body) }
            }
        }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
        connection.disconnect()
        return code to text
    }

    private fun form(params: Map<String, String>) = mapOf("content-type" to "application/x-www-form-urlencoded")

    private fun formBody(params: Map<String, String>): ByteArray =
        params.entries.joinToString("&") {
            "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}"
        }.toByteArray(Charsets.UTF_8)

    private fun subjectOf(jwt: String): String {
        val parts = jwt.split(".")
        if (parts.size < 2) throw UconnectException("JWT inválido.")
        val payload = String(Base64.getUrlDecoder().decode(pad(parts[1])), Charsets.UTF_8)
        return JSONObject(payload).optString("sub").ifBlank { throw UconnectException("JWT sem sub.") }
    }

    private fun pad(value: String): String = when (value.length % 4) {
        2 -> "$value=="
        3 -> "$value="
        else -> value
    }

    private fun randomHex(bytes: Int): String =
        (0 until bytes).joinToString("") { "%02x".format(Random.nextInt(256)) }
}
