package ptt

// Regular expressions for title cleaning
private val nonEnglishChars = """\p{IsHiragana}\p{IsKatakana}\p{IsHan}\p{IsCyrillic}"""
private val russianCastRegex = Regex("""(\([^)]*[\p{IsCyrillic}][^)]*\))$|(?:\/.*?)(\(.*\))$""")
private val altTitlesRegex = Regex("""[^/|(]*[$nonEnglishChars][^/|]*[/|]|[/|][^/|(]*[$nonEnglishChars][^/|]*""")
private val notOnlyNonEnglishRegex = Regex("""(?:[a-zA-Z][^$nonEnglishChars]+)([$nonEnglishChars].*[$nonEnglishChars])|[$nonEnglishChars].*[$nonEnglishChars](?:[^$nonEnglishChars]+[a-zA-Z])""")
private val notAllowedSymbolsAtStartAndEndRegex = Regex("""^[^\w$nonEnglishChars#\[【★]+|[ \-:/\\\[|{(#$&^]+$""")
private val remainingNotAllowedSymbolsAtStartAndEndRegex = Regex("""^[^\w$nonEnglishChars#]+|[\[\]({} ]+$""")

private val movieIndicatorRegex = Regex("""(?i)[\[(]movie[\)\]]""")
private val releaseGroupMarkingAtStartRegex = Regex("""^[\[【★].*[\]】★][ .]?(.+)""")
private val releaseGroupMarkingAtEndRegex = Regex("""(.+)[ .]?[\[【★].*[\]】★]$""")

private val beforeTitleRegex = Regex("""^\[([^\[\]]+)\]""")
private val redundantSymbolsAtEnd = Regex("""[ \-:./\\]+$""")

private val curlyBrackets = listOf("{", "}")
private val squareBrackets = listOf("[", "]")
private val parentheses = listOf("(", ")")
private val brackets = listOf(curlyBrackets, squareBrackets, parentheses)

fun cleanTitle(rawTitle: String): String {
    var title = rawTitle.trim()
    
    title = title.replace("_", " ")
    title = movieIndicatorRegex.replace(title, "") // clear movie indication flag
    title = notAllowedSymbolsAtStartAndEndRegex.replace(title, "")
    
    // Clear russian cast information
    russianCastRegex.findAll(title).forEach { matchResult ->
        matchResult.groups.drop(1).forEach { group ->
            if (group != null) {
                title = title.replace(group.value, "")
            }
        }
    }
    
    title = releaseGroupMarkingAtStartRegex.replace(title, "$1") // remove release group markings sections from the start
    title = releaseGroupMarkingAtEndRegex.replace(title, "$1")   // remove unneeded markings section at the end if present
    title = altTitlesRegex.replace(title, "")                     // remove alt language titles
    
    // Remove non english chars if they are not the only ones left
    notOnlyNonEnglishRegex.findAll(title).forEach { matchResult ->
        matchResult.groups.drop(1).forEach { group ->
            if (group != null) {
                title = title.replace(group.value, "")
            }
        }
    }
    
    title = remainingNotAllowedSymbolsAtStartAndEndRegex.replace(title, "")
    
    if (!title.contains(" ") && title.contains(".")) {
        title = title.replace(".", " ")
    }
    
    for (bracket in brackets) {
        if (title.count { it.toString() == bracket[0] } != title.count { it.toString() == bracket[1] }) {
            title = title.replace(bracket[0], "").replace(bracket[1], "")
        }
    }
    
    title = redundantSymbolsAtEnd.replace(title, "")
    title = whitespacesRegex.replace(title, " ")
    
    return title.trim()
}
