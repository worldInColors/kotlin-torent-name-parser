package ptt

import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.LinkedHashMap

typealias HProcessor = (title: String, m: ParseMeta, result: MutableMap<String, ParseMeta>) -> ParseMeta
typealias HTransformer = (title: String, m: ParseMeta, result: MutableMap<String, ParseMeta>) -> Unit
typealias HMatchValidator = (input: String, idxs: IntArray) -> Boolean

data class Handler(
    val field: String,
    val pattern: Regex? = null,
    val validateMatch: HMatchValidator? = null,
    val transform: HTransformer? = null,
    val process: HProcessor? = null,
    
    val remove: Boolean = false,
    val keepMatching: Boolean = false,
    val skipIfFirst: Boolean = false,
    val skipIfBefore: List<String> = emptyList(),
    val skipFromTitle: Boolean = false,
    
    val matchGroup: Int = 0,
    val valueGroup: Int = 1
)

fun validateAnd(vararg validators: HMatchValidator): HMatchValidator {
    return { input, idxs ->
        validators.all { it(input, idxs) }
    }
}

fun validateNotAtStart(): HMatchValidator {
    return { _, match -> match[0] != 0 }
}

fun validateNotAtEnd(): HMatchValidator {
    return { input, match -> match[1] != input.length }
}

fun validateNotMatch(re: Regex): HMatchValidator {
    return { input, match ->
        val rv = input.substring(match[0], match[1])
        !re.matches(rv)
    }
}

fun validateMatch(re: Regex): HMatchValidator {
    return { input, match ->
        val rv = input.substring(match[0], match[1])
        re.matches(rv)
    }
}

fun validateMatchedGroupsAreSame(vararg indices: Int): HMatchValidator {
    return { input, match ->
        val first = input.substring(match[indices[0] * 2], match[indices[0] * 2 + 1])
        indices.drop(1).all { index ->
            val other = input.substring(match[index * 2], match[index * 2 + 1])
            other == first
        }
    }
}

fun toValue(value: Any): HTransformer {
    return { _, m, _ -> m.value = value }
}

fun toLowercase(): HTransformer {
    return { _, m, _ -> m.value = (m.value as String).lowercase() }
}

fun toUppercase(): HTransformer {
    return { _, m, _ -> m.value = (m.value as String).uppercase() }
}

fun toTrimmed(): HTransformer {
    return { _, m, _ -> m.value = (m.value as String).trim() }
}

fun toCleanDate(): HTransformer {
    val re = Regex("""(\d+)(?:st|nd|rd|th)""")
    return { _, m, _ ->
        if (m.value is String) {
            m.value = re.replace(m.value as String, "$1")
        }
    }
}

fun toCleanMonth(): HTransformer {
    val re = Regex("""(?i)(?:feb(?:ruary)?|jan(?:uary)?|mar(?:ch)?|apr(?:il)?|may|june?|july?|aug(?:ust)?|sept?(?:ember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)""")
    return { _, m, _ ->
        if (m.value is String) {
            m.value = re.replace(m.value as String) { it.value.take(3) }
        }
    }
}

fun toDate(format: String): HTransformer {
    val separatorRe = Regex("""[.\-/\\]""")
    return { _, m, _ ->
        if (m.value is String) {
            try {
                val formatter = SimpleDateFormat(format, Locale.ENGLISH)
                val date = formatter.parse(separatorRe.replace(m.value as String, " "))
                val outputFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
                m.value = outputFormatter.format(date)
            } catch (e: Exception) {
                m.value = ""
            }
        }
    }
}

fun toYear(): HTransformer {
    return lambda@{ _, m, _ ->
        val vstr = m.value as? String ?: run {
            m.value = ""
            return@lambda
        }
        val parts = nonDigitsRegex.split(vstr)
        if (parts.size == 1) {
            m.value = parts[0]
            return@lambda
        }
        val start = parts[0]
        val end = parts[1]
        val endYear = end.toIntOrNull()
        if (endYear == null) {
            m.value = start
            return@lambda
        }
        val startYear = start.toIntOrNull()
        if (startYear == null) {
            m.value = ""
            return@lambda
        }
        val adjustedEndYear = if (endYear < 100) {
            endYear + startYear - startYear % 100
        } else {
            endYear
        }
        if (adjustedEndYear <= startYear) {
            m.value = ""
            return@lambda
        }
        m.value = "$startYear-$adjustedEndYear"
    }
}

fun toIntRange(): HTransformer {
    return lambda@{ _, m, _ ->
        val v = m.value as? String ?: run {
            m.value = null
            return@lambda
        }
        val parts = nonDigitsRegex.replace(v, " ").trim().split(" ")
        val nums = parts.mapNotNull { it.toIntOrNull() }
        
        if (nums.size == 2 && nums[0] < nums[1]) {
            val seq = (nums[0]..nums[1]).toList()
            val isSequential = seq.zipWithNext().all { (a, b) -> b == a + 1 }
            if (isSequential) {
                m.value = seq
                return@lambda
            }
        }
        
        // Check if all numbers are in ascending sequence
        val isValidSequence = nums.zipWithNext().all { (a, b) -> b == a + 1 }
        if (isValidSequence) {
            m.value = nums
        } else {
            m.value = null
        }
    }
}

fun toWithSuffix(suffix: String): HTransformer {
    return { _, m, _ ->
        if (m.value is String) {
            m.value = "${m.value}$suffix"
        } else {
            m.value = ""
        }
    }
}

fun toBoolean(): HTransformer {
    return { _, m, _ -> m.value = true }
}

class ValueSet<T> {
    private val existMap = mutableSetOf<T>()
    val values = mutableListOf<T>()
    
    fun append(v: T): ValueSet<T> {
        if (v !in existMap) {
            existMap.add(v)
            values.add(v)
        }
        return this
    }
    
    fun exists(v: T): Boolean = v in existMap
}

fun toValueSet(v: Any): HTransformer {
    return { _, m, _ ->
        val valueSet = m.value as? ValueSet<Any> ?: ValueSet()
        m.value = valueSet.append(v)
    }
}

fun toValueSetWithTransform(toV: (String) -> Any): HTransformer {
    return { _, m, _ ->
        val valueSet = m.value as? ValueSet<Any> ?: ValueSet()
        m.value = valueSet.append(toV(m.mValue))
    }
}

fun toValueSetMultiWithTransform(toV: (String) -> List<Any>): HTransformer {
    return { _, m, _ ->
        val valueSet = m.value as? ValueSet<Any> ?: ValueSet()
        toV(m.mValue).forEach { v ->
            valueSet.append(v)
        }
        m.value = valueSet
    }
}

fun toIntArray(): HTransformer {
    return { _, m, _ ->
        if (m.value is String) {
            val num = (m.value as String).toIntOrNull()
            if (num != null) {
                m.value = listOf(num)
            } else {
                m.value = emptyList<Int>()
            }
        } else {
            m.value = emptyList<Int>()
        }
    }
}

fun removeFromValue(re: Regex): HProcessor {
    return { _, m, _ ->
        if (m.value is String && (m.value as String).isNotEmpty()) {
            m.value = re.replace(m.value as String, "")
        }
        m
    }
}

// Regular expressions used in transforms
val nonDigitsRegex = Regex("""\D+""")
val nonDigitRegex = Regex("""\D""")
val nonAlphasRegex = Regex("""\W+""")
val underscoresRegex = Regex("""_+""")
val whitespacesRegex = Regex("""\s+""")

val handlers = listOf(
    // Title cleaning handlers
    Handler(
        field = "title",
        pattern = Regex("""(?i)360.Degrees.of.Vision.The.Byakugan'?s.Blind.Spot"""),
        remove = true
    ),
    Handler(
        field = "title",
        pattern = Regex("""(?i)\b(?:INTEGRALE?|INTÉGRALE?|INTERNAL|HFR)\b"""),
        remove = true
    ),
    
    // PPV handlers
    Handler(
        field = "ppv",
        pattern = Regex("""(?i)\bPPV\b"""),
        transform = toBoolean(),
        remove = true,
        skipFromTitle = true
    ),
    Handler(
        field = "ppv",
        pattern = Regex("""(?i)\b\W?Fight.?Nights?\W?\b"""),
        transform = toBoolean(),
        skipFromTitle = true
    ),
    
    // Site handlers
    Handler(
        field = "site",
        pattern = Regex("""(?i)^(www?[., ][\w-]+[. ][\w-]+(?:[. ][\w-]+)?)\s+-\s*"""),
        keepMatching = true,
        skipFromTitle = true,
        remove = true
    ),
    Handler(
        field = "site",
        pattern = Regex("""(?i)^((?:www?[\.,])?[\w-]+\.[\w-]+(?:\.[\w-]+)*?)\s+-\s*"""),
        keepMatching = true
    ),
    Handler(
        field = "site",
        pattern = Regex("""(?i)\bwww[., ][\w-]+[., ](?:rodeo|hair)\b"""),
        remove = true,
        skipFromTitle = true
    ),
    
    // Episode code handlers
    Handler(
        field = "episodeCode",
        pattern = Regex("""([\[(]([a-z0-9]{8}|[A-Z0-9]{8})[\])])(?:\.[a-zA-Z0-9]{1,5}$|$)"""),
        transform = toUppercase(),
        remove = true,
        matchGroup = 1,
        valueGroup = 2
    ),
    Handler(
        field = "episodeCode",
        pattern = Regex("""\[([A-Z0-9]{8})]"""),
        validateMatch = validateMatch(Regex("""(?:[A-Z]+\d|\d+[A-Z])""")),
        transform = toUppercase(),
        remove = true
    ),
    
    // Resolution handlers
    Handler(
        field = "resolution",
        pattern = Regex("""(?i)\b(?:4k|2160p|1080p|720p|480p)\b.+\b(4k|2160p|1080p|720p|480p)\b"""),
        transform = toLowercase(),
        remove = true,
        matchGroup = 1
    ),
    Handler(
        field = "resolution",
        pattern = Regex("""(?i)\b[(\[]?4k[)\]]?\b"""),
        transform = toValue("4k"),
        remove = true
    ),
    Handler(
        field = "resolution",
        pattern = Regex("""(?i)21600?[pi]"""),
        transform = toValue("4k"),
        remove = true,
        keepMatching = true
    ),
    Handler(
        field = "resolution",
        pattern = Regex("""(?i)[(\[]?3840x\d{4}[)\]]?"""),
        transform = toValue("4k"),
        remove = true
    ),
    Handler(
        field = "resolution",
        pattern = Regex("""(?i)[(\[]?1920x\d{3,4}[)\]]?"""),
        transform = toValue("1080p"),
        remove = true
    ),
    Handler(
        field = "resolution",
        pattern = Regex("""(?i)[(\[]?1280x\d{3}[)\]]?"""),
        transform = toValue("720p"),
        remove = true
    ),
    Handler(
        field = "resolution",
        pattern = Regex("""(?i)[(\[]?\d{3,4}x(\d{3,4})[)\]]?"""),
        transform = toWithSuffix("p"),
        remove = true
    ),
    Handler(
        field = "resolution",
        pattern = Regex("""(?i)(480|720|1080)0[pi]"""),
        transform = toWithSuffix("p"),
        remove = true
    ),
    Handler(
        field = "resolution",
        pattern = Regex("""(?i)(?:BD|HD|M)(720|1080|2160)"""),
        transform = toWithSuffix("p"),
        remove = true
    ),
    Handler(
        field = "resolution",
        pattern = Regex("""(?i)(480|576|720|1080|2160)[pi]"""),
        transform = toWithSuffix("p"),
        remove = true
    ),
    Handler(
        field = "resolution",
        pattern = Regex("""(?i)(?:^|\D)(\d{3,4})[pi]"""),
        transform = toWithSuffix("p"),
        remove = true
    ),
    
    
    // Date handlers
    Handler(
        field = "date",
        pattern = Regex("""(?:\W|^)([(\[]?((?:19[6-9]|20[012])[0-9]([. \-/\\])(?:0[1-9]|1[012])([. \-/\\])(?:0[1-9]|[12][0-9]|3[01]))[)\]]?)(?:\W|$)"""),
        validateMatch = validateMatchedGroupsAreSame(3, 4),
        transform = toDate("yyyy MM dd"),
        remove = true,
        valueGroup = 2,
        matchGroup = 1
    ),
    
    // Year handlers
    Handler(
        field = "year",
        pattern = Regex("""[ .]?([(\[*]?((?:19\d|20[012])\d[ .]?-[ .]?(?:19\d|20[012])\d)[*)\]]?)[ .]?"""),
        transform = { input, m, result ->
            toYear()(input, m, result)
            if (result["complete"] == null && (m.value as String).contains("-")) {
                val cm = ParseMeta().apply { value = true }
                result["complete"] = cm
            }
        },
        matchGroup = 1,
        valueGroup = 2,
        remove = true
    ),
    Handler(
        field = "year",
        pattern = Regex("""[(\[*]?\b(20[0-9]{2}|2100)[*\])]?"""),
        validateMatch = { input, match ->
            val afterMatch = if (match.size > 3) input.substring(match[3]) else ""
            !Regex("""(?:\D*\d{4}\b)""").containsMatchIn(afterMatch)
        },
        transform = toYear(),
        remove = true
    ),
    
    // Extended
    Handler(
        field = "extended",
        pattern = Regex("""EXTENDED"""),
        transform = toBoolean()
    ),
    Handler(
        field = "extended",
        pattern = Regex("""(?i)- Extended"""),
        transform = toBoolean()
    ),
    
    // Edition handlers
    Handler(
        field = "edition",
        pattern = Regex("""(?i)\b\d{2,3}(?:th)?[\.\s\-\+_\/(),]Anniversary[\.\s\-\+_\/(),](?:Edition|Ed)?\b"""),
        transform = toValue("Anniversary Edition"),
        remove = true
    ),
    Handler(
        field = "edition",
        pattern = Regex("""(?i)\bUltimate[\.\s\-\+_\/(),]Edition\b"""),
        transform = toValue("Ultimate Edition"),
        remove = true
    ),
    Handler(
        field = "edition",
        pattern = Regex("""(?i)\bDirector'?s.?Cut\b"""),
        transform = toValue("Director's Cut"),
        remove = true
    ),
    Handler(
        field = "edition",
        pattern = Regex("""(?i)\bRemaster(?:ed)?\b|\b[\[(]?REKONSTRUKCJA[\])]?\b"""),
        transform = toValue("Remastered"),
        keepMatching = true,
        remove = true
    ),
    
    // Quality handlers
    Handler(
        field = "quality",
        pattern = Regex("""(?i)\b(?:H[DQ][ .-]*)?CAM(?:H[DQ])?(?:[ .-]*Rip)?\b"""),
        transform = toValue("CAM"),
        remove = true
    ),
    Handler(
        field = "quality",
        pattern = Regex("""(?i)\bWEB[ .-]*Rip\b"""),
        transform = toValue("WEBRip"),
        remove = true
    ),
    Handler(
        field = "quality",
        pattern = Regex("""(?i)\bWEB[ .-]*(DL|.BDrip|.DLRIP)\b"""),
        transform = toValue("WEB-DL"),
        remove = true
    ),
    Handler(
        field = "quality",
        pattern = Regex("""(?i)\bBlu[ .-]*Ray\b(?:[ .-]*Rip)?"""),
        validateMatch = { input, match ->
            !input.substring(match[0], match[1]).lowercase().endsWith("rip")
        },
        transform = toValue("BluRay"),
        remove = true
    ),
    
    // HDR handlers
    Handler(
        field = "hdr",
        pattern = Regex("""(?i)\bDV\b|dolby.?vision|\bDoVi\b"""),
        transform = toValueSet("DV"),
        remove = true,
        keepMatching = true
    ),
    Handler(
        field = "hdr",
        pattern = Regex("""(?i)HDR10(?:\+|plus)"""),
        transform = toValueSet("HDR10+"),
        remove = true,
        keepMatching = true
    ),
    Handler(
        field = "hdr",
        pattern = Regex("""(?i)\bHDR(?:10)?\b"""),
        transform = toValueSet("HDR"),
        remove = true,
        keepMatching = true
    ),
    
    // Audio handlers
    Handler(
        field = "audio",
        pattern = Regex("""(?i)DD2?[+p]|DD Plus|Dolby Digital Plus|DDP5[ ._]1"""),
        transform = toValueSet("DDP"),
        keepMatching = true,
        remove = true
    ),
    Handler(
        field = "audio",
        pattern = Regex("""(?i)E-?AC-?3(?:-S\d+)?"""),
        transform = toValueSet("EAC3"),
        keepMatching = true,
        remove = true
    ),
    Handler(
        field = "audio",
        pattern = Regex("""(?i)\b(DD|Dolby.?Digital|DolbyD)\b"""),
        transform = toValueSet("DD"),
        keepMatching = true,
        remove = true
    ),
    Handler(
        field = "audio",
        pattern = Regex("""(?i)\bL?PCM\b"""),
        transform = toValueSet("PCM"),
        keepMatching = true,
        remove = true
    ),
    
    // Codec handlers
    Handler(
        field = "codec",
        pattern = Regex("""(?i)\b[xh][-. ]?26[45]"""),
        transform = toLowercase(),
        remove = true
    ),
    Handler(
        field = "codec",
        pattern = Regex("""(?i)\bhevc(?:\s?10)?\b"""),
        transform = toValue("hevc"),
        remove = true,
        keepMatching = true
    ),
    
    // Language handlers  
    Handler(
        field = "languages",
        pattern = Regex("""(?i)\bengl?(?:sub[A-Z]*)?\b"""),
        transform = toValueSet("en"),
        keepMatching = true
    ),
    Handler(
        field = "languages",
        pattern = Regex("""(?i)\bFR(?:a|e|anc[eê]s|VF[FQIB2]?)?\b"""),
        transform = toValueSet("fr"),
        keepMatching = true,
        skipFromTitle = true
    ),
    Handler(
        field = "languages",
        pattern = Regex("""(?i)\b(?:audio.)?(?:ESP|spa|(?:en[ .]+)?espa[nñ]ola?|castellano)\b"""),
        transform = toValueSet("es"),
        keepMatching = true,
        remove = true
    ),
    
    // Season handlers
    Handler(
        field = "seasons",
        pattern = Regex("""(?i)(?:(?:\bthe\W)?\bcomplete\W)?(?:saison|seizoen|sezon(?:SO?)?|stagione|season|series|temp(?:orada)?):?[. ]?(\d{1,2})"""),
        transform = toIntArray()
    ),
    Handler(
        field = "seasons",
        pattern = Regex("""(?i)(?:(?:\bthe\W)?\bcomplete)?(?:\W|^)so?([01]?[0-5]?[1-9])(?:[\Wex]|\d{2}\b)"""),
        transform = toIntArray(),
        keepMatching = true
    ),
    
    // Episode handlers
    Handler(
        field = "episodes",
        pattern = Regex("""(?i)(?:[\W\d]|^)e[ .]?[(\[]?(\d{1,3}(?:[à .-]*(?:[&+]|e){1,2}[ .]?\d{1,3})+)(?:\W|$)"""),
        transform = toIntRange()
    ),
    Handler(
        field = "episodes",
        pattern = Regex("""(?i)(?:so?|t)\d{1,3}[. ]?[xх-]?[. ]?(?:e|x|х|ep)[. ]?(\d{1,4})(?:[abc]|v0?[1-4]|\D|$)"""),
        remove = true,
        transform = toIntArray()
    ),
    
    // Complete handlers
    Handler(
        field = "complete",
        pattern = Regex("""(?i)(?:\bthe\W)?(?:\bcomplete|collection|dvd)?\b[ .]?\bbox[ .-]?set\b"""),
        transform = toBoolean()
    ),
    Handler(
        field = "complete",
        pattern = Regex("""(?i)(?:\bthe\W)?(?:\bcomplete|full|all)\b.*\b(?:series|seasons|collection|episodes|set|pack|movies)\b"""),
        transform = toBoolean()
    ),
    
    // Group handlers
    Handler(
        field = "group",
        pattern = Regex("""^\[([^\[\]]+)]""")
    ),
    Handler(
        field = "group",
        pattern = Regex("""\(([\w-]+)\)(?:$|\.\w{2,4}$)""")
    ),
    
    // Container handlers
    Handler(
        field = "container",
        pattern = Regex("""(?i)\.?[\[(]?\b(MKV|AVI|MP4|WMV|MPG|MPEG)\b[\])]?"""),
        transform = toLowercase()
    ),
    
    // Extension handlers
    Handler(
        field = "extension",
        pattern = Regex("""(?i)\.(3g2|3gp|avi|flv|mkv|mk3d|mov|mp2|mp4|m4v|mpe|mpeg|mpg|mpv|webm|wmv|ogm|divx|ts|m2ts|iso|vob|sub|idx|ttxt|txt|smi|srt|ssa|ass|vtt|nfo|html)$"""),
        transform = toLowercase()
    ),
    
    // Boolean flag handlers
    Handler(
        field = "hardcoded",
        pattern = Regex("""\bHC|HARDCODED\b"""),
        transform = toBoolean(),
        remove = true
    ),
    Handler(
        field = "proper",
        pattern = Regex("""(?i)\b(?:REAL.)?PROPER\b"""),
        transform = toBoolean(),
        remove = true
    ),
    Handler(
        field = "repack",
        pattern = Regex("""\b(?i)REPACK|RERIP\b"""),
        transform = toBoolean(),
        remove = true
    ),
    Handler(
        field = "retail",
        pattern = Regex("""(?i)\bRetail\b"""),
        transform = toBoolean()
    ),
    Handler(
        field = "documentary",
        pattern = Regex("""(?i)\bDOCU(?:menta?ry)?\b"""),
        transform = toBoolean(),
        skipFromTitle = true
    ),
    Handler(
        field = "unrated",
        pattern = Regex("""(?i)\bunrated\b"""),
        transform = toBoolean(),
        remove = true
    ),
    Handler(
        field = "uncensored",
        pattern = Regex("""(?i)\buncensored\b"""),
        transform = toBoolean(),
        remove = true
    ),
    Handler(
        field = "commentary",
        pattern = Regex("""(?i)\bcommentary\b"""),
        transform = toBoolean(),
        remove = true
    ),
    Handler(
        field = "subbed",
        pattern = Regex("""(?i)\b(?:Official.*?|Dual-?)?sub(?:s|bed)?\b"""),
        transform = toBoolean(),
        remove = true
    ),
    Handler(
        field = "dubbed",
        pattern = Regex("""(?i)\b(?:Fan.*)?(?:DUBBED|dublado|dubbing|DUBS?)\b"""),
        transform = toBoolean(),
        remove = true
    ),
    
    // Size handler
    Handler(
        field = "size",
        pattern = Regex("""(?i)\b(\d+(\.\d+)?\s?(MB|GB|TB))\b"""),
        remove = true
    ),
    
    // Network handlers
    Handler(
        field = "network",
        pattern = Regex("""(?i)\bATVP?\b"""),
        transform = toValue("Apple TV"),
        remove = true
    ),
    Handler(
        field = "network",
        pattern = Regex("""(?i)\bAMZN\b"""),
        transform = toValue("Amazon"),
        remove = true
    ),
    Handler(
        field = "network",
        pattern = Regex("""(?i)\bNF|Netflix\b"""),
        transform = toValue("Netflix"),
        remove = true
    )
)
