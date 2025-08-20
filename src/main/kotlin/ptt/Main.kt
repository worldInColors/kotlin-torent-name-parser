package ptt

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

fun main(args: Array<String>) {
    val title = if (args.isNotEmpty()) {
        args[0]
    } else {
        "The.Movie.2023.1080p.BluRay.x264-GROUP"
    }
    
    println("Parsing: $title")
    
    val result = parse(title).normalized()
    
    if (result.error != null) {
        println("Error: ${result.error?.message}")
    } else {
        val json = Json { 
            prettyPrint = true
            encodeDefaults = false
        }
        println(json.encodeToString(result))
    }
}
