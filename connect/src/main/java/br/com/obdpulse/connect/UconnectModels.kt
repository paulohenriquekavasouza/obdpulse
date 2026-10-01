package br.com.obdpulse.connect

data class UconnectVehicle(
    val vin: String,
    val label: String,
    val commands: List<String>,
)

data class VehicleLocation(
    val latitude: Double,
    val longitude: Double,
    val updatedMs: Long?,
)

data class AwsCreds(
    val accessKey: String,
    val secretKey: String,
    val sessionToken: String,
)

data class UconnectSession(
    val uid: String,
    val creds: AwsCreds,
)

class UconnectException(message: String) : Exception(message)
