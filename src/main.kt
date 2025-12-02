import java.io.File
import java.net.URL


fun stripComments(source: String): String {
    val commentRegex = Regex("""(?m)#.*?$""")
    val NoComments: String = source.replace(commentRegex, "")
    val multipleNewlines = Regex("\n{2,}")
    val text = multipleNewlines.replace(NoComments, "\n")

    var IgnoreRemoval = false

    var Output: String = ""

    for (i in text) {
        if (i in listOf('"', '\'')) {
            IgnoreRemoval = !IgnoreRemoval
        }
        if (i !in listOf(' ', '\t') || IgnoreRemoval) {
            Output += i
        }
    }

    return Output
}

fun ParseTOML(data: String): MutableMap<String, MutableMap<String, String>> {
    val toml: MutableMap<String, MutableMap<String, String>> = mutableMapOf()
    val noComments = stripComments(data)

    var currentSection = "" // currently active section

    for (line in noComments.lines()) {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) continue // skip empty lines

        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            // New section
            currentSection = trimmed.substring(1, trimmed.length - 1)
            toml[currentSection] = mutableMapOf()
        } else if ("=" in trimmed) {
            // Key-value pair
            val parts = trimmed.split("=", limit = 2)
            val key = parts[0].trim()
            val value = parts[1].trim()

            // If no section yet, put in default section
            val section = currentSection.ifEmpty { "default" }
            if (section !in toml) toml[section] = mutableMapOf()
            toml[section]?.put(key, value)
        }
    }

    return toml
}



fun DownloadFile(url: String, OutputPath: String) {
    URL(url).openStream().use { input ->
        File(OutputPath).outputStream().use { output -> 
            input.copyTo(output)
        }
    }
}

fun main() {
    //DownloadFile("https://ferriit.gregtech.eu/kingjamesbible.txt", "/home/ferriit/shippkg/bible.txt");
    var InputFile = File("tests/TOML_test.toml")
    println(ParseTOML(InputFile.readText())["Servers"]?.get("Server1"))
}