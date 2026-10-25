package com.carecircle.domain

import java.time.LocalTime

/** What was understood from a spoken medicine. Nothing here is saved until the user presses Save. */
data class VoiceDraft(
    val name: String,
    /** For example "500 mg"; empty when no dose or an unknown unit was heard. */
    val dose: String,
    val times: List<LocalTime>,
    val foodTiming: FoodTiming?,
)

/**
 * Turns "Metformin 500 morning and night", "Telma 40 milligram once a day after breakfast" or "Dolo 650 subah shaam"
 * into a draft. English number words are converted ("five hundred"); other languages fall back to digits. The parser
 * never invents a name: if none is heard the name stays empty.
 */
object VoiceParser {
    private val units = mapOf(
        "zero" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9,
        "ten" to 10, "eleven" to 11, "twelve" to 12, "thirteen" to 13, "fourteen" to 14, "fifteen" to 15, "sixteen" to 16,
        "seventeen" to 17, "eighteen" to 18, "nineteen" to 19,
    )
    private val tens = mapOf("twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50, "sixty" to 60, "seventy" to 70, "eighty" to 80, "ninety" to 90)

    private val morning = listOf("morning", "subah", "subha", "सुबह", "સવાર", "સવારે")
    private val afternoon = listOf("afternoon", "dopahar", "दोपहर", "બપોર", "બપોરે")
    private val evening = listOf("evening", "shaam", "sham", "शाम", "સાંજ", "સાંજે")
    private val night = listOf("night", "raat", "रात", "રાત", "રાત્રે")

    private val timeWords: Set<String> = (morning + afternoon + evening + night).map { it.lowercase() }.toSet()

    private val stopWords = setOf(
        "morning", "subah", "subha", "afternoon", "dopahar", "evening", "shaam", "sham", "night", "raat", "once", "twice", "thrice",
        "daily", "after", "before", "with", "at", "every", "each", "har", "roz", "din", "khane", "khali", "empty", "bedtime", "and", "aur",
        "सुबह", "दोपहर", "शाम", "रात", "સવાર", "સવારે", "બપોર", "બપોરે", "સાંજ", "સાંજે", "રાત", "રાત્રે", "खाने", "जमवा",
    )
    private val fillers = setOf("add", "new", "medicine", "medicines", "tablet", "tablets", "tab", "capsule", "take", "taking", "i", "my", "a", "the", "dawai", "dava", "दवा", "दवाई", "દવા")

    /** "five hundred" -> "500". Only English; anything else is left untouched. */
    fun wordsToDigits(text: String): String {
        val tokens = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val out = mutableListOf<String>()
        var i = 0
        while (i < tokens.size) {
            val t = tokens[i].lowercase()
            if (t in units || t in tens) {
                var total = 0; var current = 0; var j = i
                while (j < tokens.size) {
                    val w = tokens[j].lowercase()
                    when {
                        w in units -> current += units.getValue(w)
                        w in tens -> current += tens.getValue(w)
                        w == "hundred" && current > 0 -> current *= 100
                        w == "thousand" && current > 0 -> { total += current * 1000; current = 0 }
                        w == "and" && j + 1 < tokens.size && (tokens[j + 1].lowercase() in units || tokens[j + 1].lowercase() in tens) && (total > 0 || current >= 100) -> {}
                        else -> break
                    }
                    j++
                }
                out += (total + current).toString()
                i = j
            } else { out += tokens[i]; i++ }
        }
        return out.joinToString(" ")
    }

    private fun containsWord(text: String, words: List<String>): Boolean {
        val padded = " ${text.lowercase().replace(Regex("[^\\p{L}\\p{M}\\p{N}]+"), " ")} "
        return words.any { padded.contains(" ${it.lowercase()} ") }
    }

    private val onceRe = Regex("(?:once|1 time|one time|ek baar|एक बार|એક વાર)\\s*(?:a|per|in a)?\\s*(?:day|daily)?|\\bdaily\\b|\\broz\\b|रोज", RegexOption.IGNORE_CASE)
    private val twiceRe = Regex("twice|2 times|two times|do baar|दो बार|બે વાર", RegexOption.IGNORE_CASE)
    private val thriceRe = Regex("thrice|3 times|three times|teen baar|तीन बार|ત્રણ વાર", RegexOption.IGNORE_CASE)
    private val fourRe = Regex("4 times|four times|char baar|चार बार|ચાર વાર", RegexOption.IGNORE_CASE)

    private val afterRe = Regex("after (?:food|meal|meals|breakfast|lunch|dinner)|khane ke baad|खाने के बाद|जमने के बाद|જમ્યા પછી", RegexOption.IGNORE_CASE)
    private val beforeRe = Regex("before (?:food|meal|meals|breakfast|lunch|dinner)|khane se pehle|खाने से पहले|જમતા પહેલાં", RegexOption.IGNORE_CASE)
    private val withRe = Regex("with (?:food|meal|meals)|khane ke saath|खाने के साथ|જમવા સાથે", RegexOption.IGNORE_CASE)
    private val emptyRe = Regex("empty stomach|khali pet|खाली पेट|ખાલી પેટે", RegexOption.IGNORE_CASE)
    private val bedRe = Regex("bedtime|before sleep|before bed|sone se pehle|सोते समय|સૂતી વખતે", RegexOption.IGNORE_CASE)

    private val doseRe = Regex("(\\d+(?:\\.\\d+)?)\\s*([a-zA-Z]+)?")
    private val knownUnits = mapOf(
        "mg" to "mg", "milligram" to "mg", "milligrams" to "mg", "mcg" to "mcg", "microgram" to "mcg", "micrograms" to "mcg",
        "ml" to "ml", "millilitre" to "ml", "milliliter" to "ml", "g" to "g", "gm" to "g", "gram" to "g", "grams" to "g",
    )

    fun parseVoiceMedicine(raw: String): VoiceDraft {
        val text = wordsToDigits(raw.trim())
        val lower = text.lowercase()

        val food = when {
            emptyRe.containsMatchIn(lower) -> FoodTiming.EMPTY_STOMACH
            bedRe.containsMatchIn(lower) -> FoodTiming.BEDTIME
            afterRe.containsMatchIn(lower) -> FoodTiming.AFTER_FOOD
            beforeRe.containsMatchIn(lower) -> FoodTiming.BEFORE_FOOD
            withRe.containsMatchIn(lower) -> FoodTiming.WITH_FOOD
            else -> null
        }

        val times = sortedSetOf<LocalTime>()
        if (containsWord(lower, morning)) times += LocalTime.of(8, 0)
        if (containsWord(lower, afternoon)) times += LocalTime.of(14, 0)
        if (containsWord(lower, evening)) times += LocalTime.of(18, 0)
        if (containsWord(lower, night)) times += LocalTime.of(21, 0)
        if (times.isEmpty()) {
            when {
                fourRe.containsMatchIn(lower) -> times += listOf(LocalTime.of(8, 0), LocalTime.of(12, 0), LocalTime.of(16, 0), LocalTime.of(20, 0))
                thriceRe.containsMatchIn(lower) -> times += listOf(LocalTime.of(8, 0), LocalTime.of(14, 0), LocalTime.of(20, 0))
                twiceRe.containsMatchIn(lower) -> times += listOf(LocalTime.of(8, 0), LocalTime.of(20, 0))
                onceRe.containsMatchIn(lower) -> times += LocalTime.of(8, 0)
            }
        }

        // Name: the words before the first number or scheduling word.
        val tokens = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val nameTokens = mutableListOf<String>()
        for (t in tokens) {
            val bare = t.lowercase().trim(',', '.', ';')
            if (bare.any { it.isDigit() } || bare in stopWords) break
            if (nameTokens.isEmpty() && bare in fillers) continue
            nameTokens += t.trim(',', '.', ';')
        }
        val name = nameTokens.joinToString(" ").trim().replaceFirstChar { it.uppercase() }

        // Dose: the first number that is not part of a frequency phrase.
        val withoutFreq = lower.replace(twiceRe, " ").replace(thriceRe, " ").replace(fourRe, " ").replace(onceRe, " ")
        var dose = ""
        val m = doseRe.find(withoutFreq)
        if (m != null) {
            val number = m.groupValues[1]
            val unitWord = m.groupValues[2].lowercase()
            val unit = knownUnits[unitWord]
            val bare = unitWord.isEmpty() || unitWord in stopWords || unitWord in timeWords
            dose = when {
                unit != null -> "$number $unit"
                bare -> "$number mg" // "Dolo 650 subah": a bare number after a name is milligrams
                else -> ""            // unknown unit such as "tablets": leave empty
            }
        }
        return VoiceDraft(name, dose, times.toList(), food)
    }
}
