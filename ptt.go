package ptt

import (
	"regexp"
	"strings"
)

var (
	non_english_chars                                    = `\p{Hiragana}\p{Katakana}\p{Han}\p{Cyrillic}`
	russian_cast_regex                                   = regexp.MustCompile(`(\([^)]*[\p{Cyrillic}][^)]*\))$|(?:\/.*?)(\(.*\))$`)
	alt_titles_regex                                     = regexp.MustCompile(`[^/|(]*[` + non_english_chars + `][^/|]*[/|]|[/|][^/|(]*[` + non_english_chars + `][^/|]*`)
	not_only_non_english_regex                           = regexp.MustCompile(`(?:[a-zA-Z][^` + non_english_chars + `]+)([` + non_english_chars + `].*[` + non_english_chars + `])|([` + non_english_chars + `].*[` + non_english_chars + `])(?:[^` + non_english_chars + `]+[a-zA-Z])`)
	not_allowed_symbols_at_start_and_end_regex           = regexp.MustCompile(`^[^\w` + non_english_chars + `#[【★]+|[ \-:/\\[|{(#$&^]+$`)
	remaining_not_allowed_symbols_at_start_and_end_regex = regexp.MustCompile(`^[^\w` + non_english_chars + `#]+|[[\]({} ]+$`)

	movie_indicator_regex                = regexp.MustCompile(`(?i)[[(]movie[)\]]`)
	release_group_marking_at_start_regex = regexp.MustCompile(`^[[【★].*[\]】★][ .]?(.+)`)
	release_group_marking_at_end_regex   = regexp.MustCompile(`(.+)[ .]?[[【★].*[\]】★]$`)

	before_title_regex = regexp.MustCompile(`^\[([^[\]]+)]`)
	non_digit_regex    = regexp.MustCompile(`\D`)
	non_digits_regex   = regexp.MustCompile(`\D+`)
	non_alphas_regex   = regexp.MustCompile(`\W+`)
	underscores_regex  = regexp.MustCompile(`_+`)
	whitespaces_regex  = regexp.MustCompile(`\s+`)

	redundant_symbols_at_end = regexp.MustCompile(`[ \-:./\\]+$`)

	curly_brackets  = []string{"{", "}"}
	square_brackets = []string{"[", "]"}
	parentheses     = []string{"(", ")"}
	brackets        = [][]string{curly_brackets, square_brackets, parentheses}
)

func clean_title(rawTitle string) string {
	title := strings.TrimSpace(rawTitle)

	title = strings.ReplaceAll(title, "_", " ")
	title = movie_indicator_regex.ReplaceAllString(title, "") // clear movie indication flag
	title = not_allowed_symbols_at_start_and_end_regex.ReplaceAllString(title, "")
	for _, parts := range russian_cast_regex.FindAllStringSubmatch(title, -1) {
		for i, mStr := range parts {
			if i != 0 {
				// clear russian cast information
				title = strings.Replace(title, mStr, "", 1)
			}
		}
	}
	title = release_group_marking_at_start_regex.ReplaceAllString(title, "$1") // remove release group markings sections from the start
	title = release_group_marking_at_end_regex.ReplaceAllString(title, "$1")   // remove unneeded markings section at the end if present
	title = alt_titles_regex.ReplaceAllString(title, "")                       // remove alt language titles
	for i, mStr := range not_only_non_english_regex.FindStringSubmatch(title) {
		if i != 0 {
			// remove non english chars if they are not the only ones left
			title = strings.Replace(title, mStr, "", 1)
		}
	}
	title = remaining_not_allowed_symbols_at_start_and_end_regex.ReplaceAllString(title, "")

	if !strings.Contains(title, " ") && strings.Contains(title, ".") {
		title = strings.ReplaceAll(title, ".", " ")
	}

	for _, b := range brackets {
		if strings.Count(title, b[0]) != strings.Count(title, b[1]) {
			title = strings.ReplaceAll(strings.ReplaceAll(title, b[0], ""), b[1], "")
		}
	}

	title = redundant_symbols_at_end.ReplaceAllString(title, "")
	title = whitespaces_regex.ReplaceAllString(title, " ")

	return strings.TrimSpace(title)
}

type Result struct {
	Audio        []string `json:"audio"`
	BitDepth     string   `json:"bit_depth"`
	Channels     []string `json:"channels"`
	Codec        string   `json:"codec"`
	Commentary   bool     `json:"commentary"`
	Complete     bool     `json:"complete"`
	Container    string   `json:"container"`
	Convert      bool     `json:"convert"`
	Date         string   `json:"date"`
	Documentary  bool     `json:"documentary"`
	Dubbed       bool     `json:"dubbed"`
	Edition      string   `json:"edition"`
	EpisodeCode  string   `json:"episode_code"`
	Episodes     []int    `json:"episodes"`
	Extended     bool     `json:"extended"`
	Extension    string   `json:"extension"`
	Group        string   `json:"group"`
	HDR          []string `json:"hdr"`
	Hardcoded    bool     `json:"hardcoded"`
	Languages    []string `json:"languages"`
	Network      string   `json:"network"`
	Proper       bool     `json:"proper"`
	Quality      string   `json:"quality"`
	Region       string   `json:"region"`
	ReleaseTypes []string `json:"release_types"`
	Remastered   bool     `json:"remastered"`
	Repack       bool     `json:"repack"`
	Resolution   string   `json:"resolution"`
	Retail       bool     `json:"retail"`
	Seasons      []int    `json:"seasons"`
	Site         string   `json:"site"`
	Size         string   `json:"size"`
	Subbed       bool     `json:"subbed"`
	ThreeD       string   `json:"3d"`
	Title        string   `json:"title"`
	Uncensored   bool     `json:"uncensored"`
	Unrated      bool     `json:"unrated"`
	Upscaled     bool     `json:"upscaled"`
	Volumes      []int    `json:"volumes"`
	Year         string   `json:"year"`

	err           error `json:"-"`
	is_normalized bool  `json:"-"`
}

func (r *Result) Error() error {
	if r.err == nil {
		return nil
	}
	return r.err
}

type parseMeta struct {
	mIndex    int
	mValue    string
	value     any
	remove    bool
	processed bool
}

var value_set_field_map = map[string]struct{}{
	"audio":        {},
	"channels":     {},
	"hdr":          {},
	"languages":    {},
	"releaseTypes": {},
}

func has_value_set(field string) bool {
	_, ok := value_set_field_map[field]
	return ok
}

func parse(title string, handlers []handler) (r *Result) {
	r = &Result{}

	defer func() {
		if err := recover(); err != nil {
			if e, ok := err.(error); ok {
				r.err = e
			} else {
				panic(err)
			}
		}
	}()

	title = whitespaces_regex.ReplaceAllString(title, " ")
	title = underscores_regex.ReplaceAllString(title, " ")
	result := map[string]*parseMeta{}
	endOfTitle := len(title)

	for _, handler := range handlers {
		field := handler.Field
		skipFromTitle := handler.SkipFromTitle

		m, mFound := result[field]

		if handler.Pattern != nil {
			if mFound && !handler.KeepMatching {
				continue
			}

			idxs := handler.Pattern.FindStringSubmatchIndex(title)
			if len(idxs) == 0 {
				continue
			}
			if handler.ValidateMatch != nil && !handler.ValidateMatch(title, idxs) {
				continue
			}
			shouldSkip := false
			if handler.SkipIfFirst {
				hasOther := false
				hasBefore := false
				for f, fm := range result {
					if f != field {
						hasOther = true
						if idxs[0] >= fm.mIndex {
							hasBefore = true
							break
						}
					}
				}
				shouldSkip = hasOther && !hasBefore
			}
			if shouldSkip {
				continue
			}

			if len(handler.SkipIfBefore) > 0 {
				for _, skipField := range handler.SkipIfBefore {
					if fm, ok := result[skipField]; ok && idxs[0] < fm.mIndex {
						shouldSkip = true
						break
					}
				}
				if shouldSkip {
					continue
				}
			}

			rawMatchedPart := title[idxs[0]:idxs[1]]
			matchedPart := rawMatchedPart
			if len(idxs) > 2 {
				if handler.ValueGroup == 0 {
					matchedPart = title[idxs[2]:idxs[3]]
				} else if len(idxs) > handler.ValueGroup*2 {
					matchedPart = title[idxs[handler.ValueGroup*2]:idxs[handler.ValueGroup*2+1]]
				}
			}

			if strings.Contains(before_title_regex.FindString(title), rawMatchedPart) {
				skipFromTitle = true
			}

			if !mFound {
				m = &parseMeta{}
				if has_value_set(field) {
					m.value = &value_set[any]{existMap: map[any]struct{}{}, values: []any{}}
				}
				mFound = true
				result[field] = m
			}

			m.mIndex = idxs[0]
			m.mValue = rawMatchedPart
			if !has_value_set(field) {
				m.value = matchedPart
			}

			if handler.MatchGroup != 0 {
				m.mIndex = idxs[handler.MatchGroup*2]
				m.mValue = title[idxs[handler.MatchGroup*2]:idxs[handler.MatchGroup*2+1]]
			}
		}

		if handler.Process != nil {
			if mFound {
				m = handler.Process(title, m, result)
			} else {
				m = handler.Process(title, &parseMeta{}, result)
				if m.value != nil {
					result[field] = m
					mFound = true
				}
			}
		}

		if m.value != nil && handler.Transform != nil {
			handler.Transform(title, m, result)
		}

		if m.value == nil {
			delete(result, field)
			mFound = false
		}

		if !mFound || (m.processed && !handler.KeepMatching && !has_value_set(field)) {
			continue
		}

		if handler.Remove || m.remove {
			m.remove = true
			title = title[:m.mIndex] + title[m.mIndex+len(m.mValue):]
		}

		if !skipFromTitle && m.mIndex != 0 && m.mIndex < endOfTitle {
			endOfTitle = m.mIndex
		}

		if m.remove && skipFromTitle && m.mIndex < endOfTitle {
			// adjust title index in case part of it should be removed and skipped
			endOfTitle -= len(m.mValue)
		}

		m.remove = false
		m.processed = true
	}

	for field, fieldMeta := range result {
		v := fieldMeta.value
		switch field {
		case "audio":
			vs := v.(*value_set[any])
			values := make([]string, len(vs.values))
			for i, v := range vs.values {
				values[i] = v.(string)
			}
			r.Audio = values
		case "bitDepth":
			r.BitDepth = v.(string)
		case "channels":
			vs := v.(*value_set[any])
			values := make([]string, len(vs.values))
			for i, v := range vs.values {
				values[i] = v.(string)
			}
			r.Channels = values
		case "codec":
			r.Codec = v.(string)
		case "commentary":
			r.Commentary = v.(bool)
		case "complete":
			r.Complete = v.(bool)
		case "container":
			r.Container = v.(string)
		case "convert":
			r.Convert = v.(bool)
		case "date":
			r.Date = v.(string)
		case "documentary":
			r.Documentary = v.(bool)
		case "dubbed":
			r.Dubbed = v.(bool)
		case "edition":
			r.Edition = v.(string)
		case "episodeCode":
			r.EpisodeCode = v.(string)
		case "episodes":
			r.Episodes = v.([]int)
		case "extended":
			r.Extended = v.(bool)
		case "extension":
			r.Extension = v.(string)
		case "group":
			r.Group = v.(string)
		case "hardcoded":
			r.Hardcoded = v.(bool)
		case "hdr":
			vs := v.(*value_set[any])
			values := make([]string, len(vs.values))
			for i, v := range vs.values {
				values[i] = v.(string)
			}
			r.HDR = values
		case "languages":
			vs := v.(*value_set[any])
			values := make([]string, len(vs.values))
			for i, v := range vs.values {
				values[i] = v.(string)
			}
			r.Languages = values
		case "network":
			r.Network = v.(string)
		case "proper":
			r.Proper = v.(bool)
		case "region":
			r.Region = v.(string)
		case "remastered":
			r.Remastered = v.(bool)
		case "repack":
			r.Repack = v.(bool)
		case "resolution":
			r.Resolution = v.(string)
		case "retail":
			r.Retail = v.(bool)
		case "seasons":
			r.Seasons = v.([]int)
		case "size":
			r.Size = v.(string)
		case "site":
			r.Site = v.(string)
		case "quality":
			r.Quality = v.(string)
		case "releaseTypes":
			vs := v.(*value_set[any])
			values := make([]string, len(vs.values))
			for i, v := range vs.values {
				values[i] = v.(string)
			}
			r.ReleaseTypes = values
		case "subbed":
			r.Subbed = v.(bool)
		case "threeD":
			r.ThreeD = v.(string)
		case "uncensored":
			r.Uncensored = v.(bool)
		case "unrated":
			r.Unrated = v.(bool)
		case "upscaled":
			r.Upscaled = v.(bool)
		case "volumes":
			r.Volumes = v.([]int)
		case "year":
			r.Year = v.(string)
		}
	}

	r.Title = clean_title(title[:max(min(endOfTitle, len(title)), 0)])

	return r
}

func Parse(title string) *Result {
	return parse(title, handlers)
}

func GetPartialParser(fieldNames []string) func(title string) *Result {
	selectedFieldMap := map[string]struct{}{}
	for _, fieldName := range fieldNames {
		selectedFieldMap[fieldName] = struct{}{}
	}

	selectedHandlers := []handler{}
	for _, h := range handlers {
		if _, ok := selectedFieldMap[h.Field]; ok {
			selectedHandlers = append(selectedHandlers, h)
		}
	}

	return func(title string) *Result {
		return parse(title, selectedHandlers)
	}
}
