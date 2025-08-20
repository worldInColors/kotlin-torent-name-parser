package ptt

data class Version(
    private val v: String = "0.11.1",
    private val major: String = "0", 
    private val minor: String = "11",
    private val patch: String = "1"
) {
    private var intValue: Int = 0
    
    fun toInt(): Int {
        if (intValue != 0) {
            return intValue
        }
        
        val majorInt = nonDigitsRegex.replace(major, "").toIntOrNull() ?: 0
        val minorInt = nonDigitsRegex.replace(minor, "").toIntOrNull() ?: 0  
        val patchInt = nonDigitsRegex.replace(patch, "").toIntOrNull() ?: 0
        
        intValue = majorInt * 1000000 + minorInt * 1000 + patchInt
        return intValue
    }
    
    override fun toString(): String = v
}

fun version(): Version = Version()
