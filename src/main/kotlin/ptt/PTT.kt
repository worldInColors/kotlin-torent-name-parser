package ptt

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json

@Serializable
data class Result(
    val audio: List<String> = emptyList(),
    val bitDepth: String = "",
    val channels: List<String> = emptyList(),
    val codec: String = "",
    val commentary: Boolean = false,
    val complete: Boolean = false,
    val container: String = "",
    val convert: Boolean = false,
    val date: String = "",
    val documentary: Boolean = false,
    val dubbed: Boolean = false,
    val edition: String = "",
    val episodeCode: String = "",
    val episodes: List<Int> = emptyList(),
    val extended: Boolean = false,
    val extension: String = "",
    val group: String = "",
    val hdr: List<String> = emptyList(),
    val hardcoded: Boolean = false,
    val languages: List<String> = emptyList(),
    val network: String = "",
    val proper: Boolean = false,
    val quality: String = "",
    val region: String = "",
    val releaseTypes: List<String> = emptyList(),
    val remastered: Boolean = false,
    val repack: Boolean = false,
    val resolution: String = "",
    val retail: Boolean = false,
    val seasons: List<Int> = emptyList(),
    val site: String = "",
    val size: String = "",
    val subbed: Boolean = false,
    val threeD: String = "",
    val title: String = "",
    val uncensored: Boolean = false,
    val unrated: Boolean = false,
    val upscaled: Boolean = false,
    val volumes: List<Int> = emptyList(),
    val year: String = ""
) {
    @Transient
    var error: Exception? = null
        private set
    
    var isNormalized: Boolean = false
        private set
    
    fun withError(error: Exception): Result {
        return this.copy().apply { this.error = error }
    }
    
    fun normalized(): Result {
        if (error != null) return this
        if (isNormalized) return this
        
        return this.copy(
            audio = normalizeAudio(audio),
            codec = normalizeCodec(codec),
            releaseTypes = normalizeReleaseTypes(releaseTypes),
            resolution = normalizeResolution(resolution)
        ).apply { 
            isNormalized = true 
        }
    }
}

data class ParseMeta(
    var mIndex: Int = 0,
    var mValue: String = "",
    var value: Any? = null,
    var remove: Boolean = false,
    var processed: Boolean = false
)

private val valueSetFieldMap = setOf(
    "audio", "channels", "hdr", "languages", "releaseTypes"
)

private fun hasValueSet(field: String): Boolean = field in valueSetFieldMap

fun parse(title: String, handlers: List<Handler>): Result {
    return try {
        var workingTitle = whitespacesRegex.replace(title, " ")
        workingTitle = underscoresRegex.replace(workingTitle, " ")
        
        val result = mutableMapOf<String, ParseMeta>()
        var endOfTitle = workingTitle.length
        
        for (handler in handlers) {
            val field = handler.field
            val skipFromTitle = handler.skipFromTitle
            
            var m = result[field]
            
            if (handler.pattern != null) {
                if (m != null && !handler.keepMatching) {
                    continue
                }
                
                val matchResult = handler.pattern.find(workingTitle)
                if (matchResult == null) {
                    continue
                }
                
                val idxs = intArrayOf(
                    matchResult.range.first,
                    matchResult.range.last + 1
                ).plus(
                    matchResult.groups.drop(1).flatMap { group ->
                        if (group != null) listOf(group.range.first, group.range.last + 1)
                        else listOf(-1, -1)
                    }.toIntArray()
                )
                
                if (handler.validateMatch?.invoke(workingTitle, idxs) == false) {
                    continue
                }
                
                var shouldSkip = false
                if (handler.skipIfFirst) {
                    val hasOther = result.keys.any { it != field }
                    val hasBefore = result.values.any { it.mIndex <= idxs[0] }
                    shouldSkip = hasOther && !hasBefore
                }
                if (shouldSkip) continue
                
                if (handler.skipIfBefore.isNotEmpty()) {
                    for (skipField in handler.skipIfBefore) {
                        val fm = result[skipField]
                        if (fm != null && idxs[0] < fm.mIndex) {
                            shouldSkip = true
                            break
                        }
                    }
                    if (shouldSkip) continue
                }
                
                val rawMatchedPart = workingTitle.substring(idxs[0], idxs[1])
                var matchedPart = rawMatchedPart
                if (idxs.size > 2) {
                    when {
                        handler.valueGroup == 0 -> matchedPart = workingTitle.substring(idxs[2], idxs[3])
                        idxs.size > handler.valueGroup * 2 -> {
                            val start = idxs[handler.valueGroup * 2]
                            val end = idxs[handler.valueGroup * 2 + 1]
                            if (start >= 0 && end >= 0) {
                                matchedPart = workingTitle.substring(start, end)
                            }
                        }
                    }
                }
                
                val beforeTitleRegex = Regex("""\[([^\[\]]+)\]""")
                val beforeTitleMatch = beforeTitleRegex.find(workingTitle)
                val shouldSkipFromTitle = beforeTitleMatch != null && rawMatchedPart in beforeTitleMatch.value
                
                if (m == null) {
                    m = ParseMeta()
                    if (hasValueSet(field)) {
                        m.value = ValueSet<Any>()
                    }
                    result[field] = m
                }
                
                m.mIndex = idxs[0]
                m.mValue = rawMatchedPart
                if (!hasValueSet(field)) {
                    m.value = matchedPart
                }
                
                if (handler.matchGroup != 0) {
                    val matchStart = idxs[handler.matchGroup * 2]
                    val matchEnd = idxs[handler.matchGroup * 2 + 1]
                    if (matchStart >= 0 && matchEnd >= 0) {
                        m.mIndex = matchStart
                        m.mValue = workingTitle.substring(matchStart, matchEnd)
                    }
                }
            }
            
            if (handler.process != null) {
                m = if (m != null) {
                    handler.process.invoke(workingTitle, m, result)
                } else {
                    val newM = handler.process.invoke(workingTitle, ParseMeta(), result)
                    if (newM.value != null) {
                        result[field] = newM
                        newM
                    } else null
                }
            }
            
            if (m?.value != null && handler.transform != null) {
                handler.transform.invoke(workingTitle, m, result)
            }
            
            if (m?.value == null) {
                result.remove(field)
                continue
            }
            
            if (m.processed && !handler.keepMatching && !hasValueSet(field)) {
                continue
            }
            
            if (handler.remove || m.remove) {
                m.remove = true
                workingTitle = workingTitle.substring(0, m.mIndex) + workingTitle.substring(m.mIndex + m.mValue.length)
            }
            
            if (!skipFromTitle && m.mIndex != 0 && m.mIndex < endOfTitle) {
                endOfTitle = m.mIndex
            }
            
            if (m.remove && skipFromTitle && m.mIndex < endOfTitle) {
                endOfTitle -= m.mValue.length
            }
            
            m.remove = false
            m.processed = true
        }
        
        // Convert results to final Result object
        val finalResult = Result(
            audio = extractStringList(result["audio"]),
            bitDepth = extractString(result["bitDepth"]),
            channels = extractStringList(result["channels"]),
            codec = extractString(result["codec"]),
            commentary = extractBoolean(result["commentary"]),
            complete = extractBoolean(result["complete"]),
            container = extractString(result["container"]),
            convert = extractBoolean(result["convert"]),
            date = extractString(result["date"]),
            documentary = extractBoolean(result["documentary"]),
            dubbed = extractBoolean(result["dubbed"]),
            edition = extractString(result["edition"]),
            episodeCode = extractString(result["episodeCode"]),
            episodes = extractIntList(result["episodes"]),
            extended = extractBoolean(result["extended"]),
            extension = extractString(result["extension"]),
            group = extractString(result["group"]),
            hdr = extractStringList(result["hdr"]),
            hardcoded = extractBoolean(result["hardcoded"]),
            languages = extractStringList(result["languages"]),
            network = extractString(result["network"]),
            proper = extractBoolean(result["proper"]),
            quality = extractString(result["quality"]),
            region = extractString(result["region"]),
            releaseTypes = extractStringList(result["releaseTypes"]),
            remastered = extractBoolean(result["remastered"]),
            repack = extractBoolean(result["repack"]),
            resolution = extractString(result["resolution"]),
            retail = extractBoolean(result["retail"]),
            seasons = extractIntList(result["seasons"]),
            site = extractString(result["site"]),
            size = extractString(result["size"]),
            subbed = extractBoolean(result["subbed"]),
            threeD = extractString(result["threeD"]),
            title = cleanTitle(workingTitle.substring(0, maxOf(minOf(endOfTitle, workingTitle.length), 0))),
            uncensored = extractBoolean(result["uncensored"]),
            unrated = extractBoolean(result["unrated"]),
            upscaled = extractBoolean(result["upscaled"]),
            volumes = extractIntList(result["volumes"]),
            year = extractString(result["year"])
        )
        
        finalResult
    } catch (e: Exception) {
        Result().withError(e)
    }
}

private fun extractString(meta: ParseMeta?): String {
    return meta?.value as? String ?: ""
}

private fun extractBoolean(meta: ParseMeta?): Boolean {
    return meta?.value as? Boolean ?: false
}

private fun extractStringList(meta: ParseMeta?): List<String> {
    return when (val value = meta?.value) {
        is ValueSet<*> -> value.values.map { it.toString() }
        is List<*> -> value.map { it.toString() }
        else -> emptyList()
    }
}

private fun extractIntList(meta: ParseMeta?): List<Int> {
    return when (val value = meta?.value) {
        is List<*> -> value.mapNotNull { it as? Int }
        else -> emptyList()
    }
}

fun parse(title: String): Result {
    return parse(title, handlers)
}

fun getPartialParser(fieldNames: List<String>): (String) -> Result {
    val selectedFieldMap = fieldNames.toSet()
    val selectedHandlers = handlers.filter { it.field in selectedFieldMap }
    
    return { title -> parse(title, selectedHandlers) }
}
