package br.com.obdpulse.obd

import java.util.Locale

class PidDef(
    val pid: Int,
    val name: String,
    val unit: String,
    val length: Int,
    val decimals: Int,
    val fast: Boolean,
    val decode: (IntArray) -> Double,
) {
    val key: String = "%02X".format(pid)
    val priority: Int get() = Pids.priority(key)
}

object Keys {
    const val BOOST = "BOOST"
    const val BATTERY = "VBAT"
    const val CONSUMPTION = "KMPL"
}

private fun byteA(d: IntArray) = d[0].toDouble()
private fun wordAB(d: IntArray) = (d[0] * 256 + d[1]).toDouble()
private fun percent(d: IntArray) = d[0] * 100.0 / 255.0
private fun temperature(d: IntArray) = d[0] - 40.0
private fun fuelTrim(d: IntArray) = (d[0] - 128) * 100.0 / 128.0
private fun lambda(d: IntArray) = wordAB(d) * 2.0 / 65536.0
private fun torque(d: IntArray) = d[0] - 125.0
private fun oxygenVoltage(d: IntArray) = d[0] / 200.0
private fun signedWord(d: IntArray): Double {
    val raw = d[0] * 256 + d[1]
    return (if (raw >= 0x8000) raw - 0x10000 else raw).toDouble()
}

object Pids {

    val all: List<PidDef> = listOf(
        PidDef(0x0C, "Rotação do motor", "rpm", 2, 0, true) { wordAB(it) / 4.0 },
        PidDef(0x0D, "Velocidade", "km/h", 1, 0, true, ::byteA),
        PidDef(0x0B, "Pressão no coletor (MAP)", "kPa", 1, 0, true, ::byteA),
        PidDef(0x05, "Temperatura do motor", "°C", 1, 0, false, ::temperature),
        PidDef(0x5C, "Temperatura do óleo", "°C", 1, 0, false, ::temperature),
        PidDef(0x0F, "Temperatura do ar admitido", "°C", 1, 0, false, ::temperature),
        PidDef(0x52, "Etanol no combustível", "%", 1, 0, false, ::percent),
        PidDef(0x2F, "Nível de combustível", "%", 1, 0, false, ::percent),
        PidDef(0x04, "Carga calculada do motor", "%", 1, 0, true, ::percent),
        PidDef(0x43, "Carga absoluta do motor", "%", 2, 0, true) { wordAB(it) * 100.0 / 255.0 },
        PidDef(0x11, "Posição da borboleta", "%", 1, 0, true, ::percent),
        PidDef(0x45, "Posição relativa da borboleta", "%", 1, 0, true, ::percent),
        PidDef(0x47, "Posição da borboleta (B)", "%", 1, 0, true, ::percent),
        PidDef(0x4C, "Borboleta comandada", "%", 1, 0, true, ::percent),
        PidDef(0x49, "Pedal do acelerador (D)", "%", 1, 0, true, ::percent),
        PidDef(0x4A, "Pedal do acelerador (E)", "%", 1, 0, true, ::percent),
        PidDef(0x4B, "Pedal do acelerador (F)", "%", 1, 0, true, ::percent),
        PidDef(0x5A, "Pedal do acelerador (relativo)", "%", 1, 0, true, ::percent),
        PidDef(0x0E, "Avanço de ignição", "°", 1, 1, true) { it[0] / 2.0 - 64.0 },
        PidDef(0x5D, "Ponto de injeção", "°", 2, 1, true) { wordAB(it) / 128.0 - 210.0 },
        PidDef(0x10, "Fluxo de ar (MAF)", "g/s", 2, 2, true) { wordAB(it) / 100.0 },
        PidDef(0x5E, "Consumo instantâneo", "L/h", 2, 1, true) { wordAB(it) / 20.0 },
        PidDef(0x61, "Torque solicitado", "%", 1, 0, true, ::torque),
        PidDef(0x62, "Torque real", "%", 1, 0, true, ::torque),
        PidDef(0x8E, "Torque de atrito do motor", "%", 1, 0, false, ::torque),
        PidDef(0x63, "Torque de referência", "Nm", 2, 0, false, ::wordAB),
        PidDef(0x44, "Lambda comandado", "λ", 2, 3, true, ::lambda),
        PidDef(0x24, "Sonda lambda B1S1", "λ", 4, 3, true, ::lambda),
        PidDef(0x34, "Sonda lambda B1S1 (banda larga)", "λ", 4, 3, true, ::lambda),
        PidDef(0x14, "Sonda lambda B1S1 (tensão)", "V", 2, 3, true, ::oxygenVoltage),
        PidDef(0x15, "Sonda lambda B1S2 (tensão)", "V", 2, 3, true, ::oxygenVoltage),
        PidDef(0x06, "Correção curta de combustível", "%", 1, 1, true, ::fuelTrim),
        PidDef(0x07, "Correção longa de combustível", "%", 1, 1, false, ::fuelTrim),
        PidDef(0x23, "Pressão no rail de combustível", "bar", 2, 1, false) { wordAB(it) / 10.0 },
        PidDef(0x59, "Pressão absoluta no rail", "bar", 2, 1, false) { wordAB(it) / 10.0 },
        PidDef(0x22, "Pressão no rail (relativa)", "kPa", 2, 1, false) { wordAB(it) * 0.079 },
        PidDef(0x0A, "Pressão de combustível", "kPa", 1, 0, false) { it[0] * 3.0 },
        PidDef(0x2E, "Purga do cânister comandada", "%", 1, 0, false, ::percent),
        PidDef(0x32, "Pressão de vapor do cânister", "Pa", 2, 0, false) { signedWord(it) / 4.0 },
        PidDef(0x53, "Pressão absoluta do cânister", "kPa", 2, 2, false) { wordAB(it) / 200.0 },
        PidDef(0x33, "Pressão barométrica", "kPa", 1, 0, false, ::byteA),
        PidDef(0x46, "Temperatura ambiente", "°C", 1, 0, false, ::temperature),
        PidDef(0x3C, "Temperatura do catalisador (B1S1)", "°C", 2, 0, false) { wordAB(it) / 10.0 - 40.0 },
        PidDef(0x3E, "Temperatura do catalisador (B1S2)", "°C", 2, 0, false) { wordAB(it) / 10.0 - 40.0 },
        PidDef(0x42, "Tensão da central", "V", 2, 2, false) { wordAB(it) / 1000.0 },
        PidDef(0x1F, "Tempo desde a partida", "s", 2, 0, false, ::wordAB),
        PidDef(0xA6, "Hodômetro", "km", 4, 1, false) {
            ((it[0].toLong() shl 24) or (it[1].toLong() shl 16) or (it[2].toLong() shl 8) or it[3].toLong()) / 10.0
        },
        PidDef(0x30, "Aquecimentos desde a limpeza de falhas", "", 1, 0, false, ::byteA),
        PidDef(0x31, "Distância desde a limpeza de falhas", "km", 2, 0, false, ::wordAB),
        PidDef(0x4E, "Tempo desde a limpeza de falhas", "min", 2, 0, false, ::wordAB),
        PidDef(0x21, "Distância com luz de falha acesa", "km", 2, 0, false, ::wordAB),
        PidDef(0x4D, "Tempo com luz de falha acesa", "min", 2, 0, false, ::wordAB),
    )

    val byKey: Map<String, PidDef> = all.associateBy { it.key }

    val handled: Set<Int> = all.map { it.pid }.toSet() + 0x01

    private val order: Map<String, Int> = all.withIndex().associate { (index, def) -> def.key to (index + 1) * 10 }

    fun priority(key: String): Int = when (key) {
        Keys.BOOST -> 0
        Keys.CONSUMPTION -> priority("5E") + 5
        Keys.BATTERY -> priority("2F") + 5
        else -> order[key] ?: Int.MAX_VALUE
    }
}

object Labels {
    fun name(key: String): String = when (key) {
        Keys.BOOST -> "Pressão do turbo (calculada)"
        Keys.BATTERY -> "Tensão da bateria (leitor)"
        Keys.CONSUMPTION -> "Consumo em km/L"
        else -> Pids.byKey[key]?.name ?: key
    }
}

object Format {
    private val locale: Locale = Locale.forLanguageTag("pt-BR")

    fun number(value: Double, decimals: Int): String = String.format(locale, "%.${decimals}f", value)
}
