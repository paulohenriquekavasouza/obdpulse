package br.com.obdpulse.obd

import java.util.Locale

class PidDef(
    val pid: Int,
    val name: String,
    val unit: String,
    val length: Int,
    val decimals: Int,
    val fast: Boolean,
    val priority: Int,
    val decode: (IntArray) -> Double,
) {
    val key: String = "%02X".format(pid)
}

object Keys {
    const val BOOST = "BOOST"
    const val BATTERY = "VBAT"
}

private fun byteA(d: IntArray) = d[0].toDouble()
private fun wordAB(d: IntArray) = (d[0] * 256 + d[1]).toDouble()
private fun percent(d: IntArray) = d[0] * 100.0 / 255.0
private fun temperature(d: IntArray) = d[0] - 40.0
private fun fuelTrim(d: IntArray) = (d[0] - 128) * 100.0 / 128.0
private fun lambda(d: IntArray) = wordAB(d) * 2.0 / 65536.0
private fun torque(d: IntArray) = d[0] - 125.0

object Pids {

    val all: List<PidDef> = listOf(
        PidDef(0x0C, "Rotação do motor", "rpm", 2, 0, true, 10) { wordAB(it) / 4.0 },
        PidDef(0x0D, "Velocidade", "km/h", 1, 0, true, 11, ::byteA),
        PidDef(0x0B, "Pressão no coletor (MAP)", "kPa", 1, 0, true, 12, ::byteA),
        PidDef(0x05, "Temperatura do motor", "°C", 1, 0, false, 13, ::temperature),
        PidDef(0x5C, "Temperatura do óleo", "°C", 1, 0, false, 14, ::temperature),
        PidDef(0x0F, "Temperatura do ar admitido", "°C", 1, 0, false, 16, ::temperature),
        PidDef(0x52, "Etanol no combustível", "%", 1, 0, false, 17, ::percent),
        PidDef(0x2F, "Nível de combustível", "%", 1, 0, false, 18, ::percent),
        PidDef(0x04, "Carga calculada do motor", "%", 1, 0, true, 20, ::percent),
        PidDef(0x43, "Carga absoluta do motor", "%", 2, 0, true, 21) { wordAB(it) * 100.0 / 255.0 },
        PidDef(0x11, "Posição da borboleta", "%", 1, 0, true, 22, ::percent),
        PidDef(0x4C, "Borboleta comandada", "%", 1, 0, true, 23, ::percent),
        PidDef(0x49, "Pedal do acelerador (D)", "%", 1, 0, true, 24, ::percent),
        PidDef(0x4A, "Pedal do acelerador (E)", "%", 1, 0, true, 25, ::percent),
        PidDef(0x5A, "Pedal do acelerador (relativo)", "%", 1, 0, true, 26, ::percent),
        PidDef(0x0E, "Avanço de ignição", "°", 1, 1, true, 27) { it[0] / 2.0 - 64.0 },
        PidDef(0x10, "Fluxo de ar (MAF)", "g/s", 2, 2, true, 28) { wordAB(it) / 100.0 },
        PidDef(0x5E, "Consumo instantâneo", "L/h", 2, 1, true, 29) { wordAB(it) / 20.0 },
        PidDef(0x61, "Torque solicitado", "%", 1, 0, true, 30, ::torque),
        PidDef(0x62, "Torque real", "%", 1, 0, true, 31, ::torque),
        PidDef(0x63, "Torque de referência", "Nm", 2, 0, false, 32, ::wordAB),
        PidDef(0x44, "Lambda comandado", "λ", 2, 3, true, 33, ::lambda),
        PidDef(0x24, "Sonda lambda B1S1", "λ", 4, 3, true, 34, ::lambda),
        PidDef(0x34, "Sonda lambda B1S1 (banda larga)", "λ", 4, 3, true, 35, ::lambda),
        PidDef(0x06, "Correção curta de combustível", "%", 1, 1, true, 36, ::fuelTrim),
        PidDef(0x07, "Correção longa de combustível", "%", 1, 1, false, 37, ::fuelTrim),
        PidDef(0x23, "Pressão no rail de combustível", "bar", 2, 1, false, 38) { wordAB(it) / 10.0 },
        PidDef(0x22, "Pressão no rail (relativa)", "kPa", 2, 1, false, 39) { wordAB(it) * 0.079 },
        PidDef(0x0A, "Pressão de combustível", "kPa", 1, 0, false, 40) { it[0] * 3.0 },
        PidDef(0x33, "Pressão barométrica", "kPa", 1, 0, false, 41, ::byteA),
        PidDef(0x46, "Temperatura ambiente", "°C", 1, 0, false, 42, ::temperature),
        PidDef(0x3C, "Temperatura do catalisador", "°C", 2, 0, false, 43) { wordAB(it) / 10.0 - 40.0 },
        PidDef(0x42, "Tensão da central", "V", 2, 2, false, 44) { wordAB(it) / 1000.0 },
        PidDef(0x1F, "Tempo desde a partida", "s", 2, 0, false, 45, ::wordAB),
        PidDef(0xA6, "Hodômetro", "km", 4, 1, false, 46) {
            ((it[0].toLong() shl 24) or (it[1].toLong() shl 16) or (it[2].toLong() shl 8) or it[3].toLong()) / 10.0
        },
        PidDef(0x31, "Distância desde a limpeza de falhas", "km", 2, 0, false, 47, ::wordAB),
        PidDef(0x4E, "Tempo desde a limpeza de falhas", "min", 2, 0, false, 48, ::wordAB),
        PidDef(0x21, "Distância com luz de falha acesa", "km", 2, 0, false, 49, ::wordAB),
        PidDef(0x4D, "Tempo com luz de falha acesa", "min", 2, 0, false, 50, ::wordAB),
    )

    val byKey: Map<String, PidDef> = all.associateBy { it.key }
}

object Labels {
    fun name(key: String): String = when (key) {
        Keys.BOOST -> "Pressão do turbo (calculada)"
        Keys.BATTERY -> "Tensão da bateria (leitor)"
        else -> Pids.byKey[key]?.name ?: key
    }
}

object Format {
    private val locale: Locale = Locale.forLanguageTag("pt-BR")

    fun number(value: Double, decimals: Int): String = String.format(locale, "%.${decimals}f", value)
}
