# PTT - Parse Torrent Title (Kotlin)

This is a Kotlin port of the Go torrent title parser. It extracts metadata from torrent titles including:

- Title
- Year, Seasons, Episodes
- Quality (resolution, source)
- Video codec, audio codec
- Languages, subtitles
- Release group
- And much more...

## Usage

### Basic Usage

```kotlin
import ptt.parse

val result = parse("The.Movie.2023.1080p.BluRay.x264-GROUP").normalized()

println("Title: ${result.title}")
println("Year: ${result.year}")
println("Resolution: ${result.resolution}")
println("Quality: ${result.quality}")
println("Codec: ${result.codec}")
```

### Partial Parsing

You can create a parser that only extracts specific fields:

```kotlin
val partialParser = getPartialParser(listOf("title", "year", "resolution"))
val result = partialParser("Movie.2023.1080p.BluRay.x264")
```

## Building

```bash
./gradlew build
```

## Running

```bash
./gradlew run --args="The.Movie.2023.1080p.BluRay.x264-GROUP"
```

## Testing

```bash
./gradlew test
```

## Go Version

A lot of regexes taken from the Go version .

## Features

- ✅ Complete parsing of torrent titles
- ✅ Normalization of values
- ✅ Language detection
- ✅ Season/episode parsing
- ✅ Quality and codec detection
- ✅ Release group extraction
- ✅ And much more...

## Dependencies

- Kotlin 1.9.10
- kotlinx.serialization for JSON output
