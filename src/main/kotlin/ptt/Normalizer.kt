package ptt

fun normalizeAudio(audio: List<String>): List<String> {
    var isChanged = false
    val normalizedAudio = audio.map { item ->
        when (item) {
            "AC3" -> {
                isChanged = true
                "DD"
            }
            "EAC3" -> {
                isChanged = true
                "DDP"
            }
            else -> item
        }
    }
    
    if (!isChanged) {
        return audio
    }
    
    // Remove duplicates while preserving order
    val seen = mutableSetOf<String>()
    return normalizedAudio.filter { seen.add(it) }
}

fun normalizeCodec(codec: String): String {
    return when (codec.lowercase()) {
        "avc", "h264", "x264" -> "AVC"
        "hevc", "h265", "x265" -> "HEVC"
        "mpeg2" -> "MPEG-2"
        "divx", "dvix" -> "DivX"
        "xvid" -> "Xvid"
        else -> codec
    }
}

fun normalizeReleaseTypes(rtypes: List<String>): List<String> {
    return rtypes.map { type ->
        when (type) {
            "OAV" -> "OVA"
            "ODA" -> "OAD"
            else -> type
        }
    }
}

fun normalizeResolution(resolution: String): String {
    return when (resolution.lowercase()) {
        "2160p" -> "4k"
        "1440p" -> "2k"
        else -> resolution
    }
}
