import java.io.File
import java.net.URL


const val ROOT_PATH = "/etc/ship"
const val SHIP_FILE = "/etc/ship/ship.pkg"
const val debug = true

// Customizable things done by fetching from the ship.pkg file
var messageEnd = ""
var errorPrefix = ""
var warningPrefix = ""
var infoPrefix = ""
var inputPrefix = ""

var suffixColor = ""
var errorColor = ""
var warningColor = ""
var infoColor = ""
var inputColor = ""


enum class LogType {
    INFO,
    WARN,
    ERR,
    INPUT,
    DBG
}


fun log(type: LogType, Message: String) {
    val colorTable = mapOf(
        "default" to "\u001B[1m",
        "black"   to "\u001B[1;30m",
        "red"     to "\u001B[1;31m",
        "green"   to "\u001B[1;32m",
        "yellow"  to "\u001B[1;33m",
        "blue"    to "\u001B[1;34m",
        "purple"  to "\u001B[1;35m",
        "cyan"    to "\u001B[1;36m",
        "white"   to "\u001B[1;37m",
        "reset"   to "\u001B[0m"
    )


    val prefix = when (type) {
        LogType.INFO -> infoPrefix
        LogType.WARN -> warningPrefix
        LogType.ERR -> errorPrefix
        LogType.INPUT -> inputPrefix
        LogType.DBG -> "[DEBUG] "
    }
    
    val color = when (type) {
        LogType.INFO -> colorTable[infoColor] ?: ""
        LogType.WARN -> colorTable[warningColor] ?: ""
        LogType.ERR -> colorTable[errorColor] ?: ""
        LogType.INPUT -> colorTable[inputColor] ?: ""
        LogType.DBG -> colorTable["purple"] ?: ""
    }

    println("$color$prefix:\u001B[0m $Message $messageEnd")
}


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


fun parseTOML(data: String): MutableMap<String, MutableMap<String, String>> {
    val toml: MutableMap<String, MutableMap<String, String>> = mutableMapOf()
    val noComments = stripComments(data).replace("\"", "").replace("'", "")

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


fun writeTOML(data: Map<String, Map<String, String>>): String =
    buildString {
        for ((section, values) in data) {
            appendLine("[$section]")
            for ((key, value) in values) {
                appendLine("$key = \"$value\"")
            }
            appendLine()  // blank line after each section
        }
    }


fun DownloadFile(url: String, OutputPath: String) {
    URL(url).openStream().use { input ->
        File(OutputPath).outputStream().use { output -> 
            input.copyTo(output)
        }
    }
}


fun main(args: Array<String>) {
    var shipPKGFile = File(SHIP_FILE)

    val shipPKG = parseTOML(shipPKGFile.readText())

    // Get customization data from ship.pkg
    val customizeText = shipPKG["Customize.text"] ?: mutableMapOf()
    messageEnd = customizeText["LoggingEnd"] ?: ""
    errorPrefix = customizeText["ErrorPrefix"] ?: "E"
    warningPrefix = customizeText["WarningPrefix"] ?: "W"
    infoPrefix = customizeText["InfoPrefix"] ?: "I"
    inputPrefix = customizeText["InputPrefix"] ?: "?"

    val customizeColor = shipPKG["Customize.colors"] ?: mutableMapOf()
    suffixColor = customizeColor["LoggingEndColor"] ?: ""
    errorColor = customizeColor["ErrorPrefixColor"] ?: "red"
    warningColor = customizeColor["WarningPrefixColor"] ?: "yellow"
    infoColor = customizeColor["InfoPrefixColor"] ?: "blue"
    inputColor = customizeColor["InputPrefixColor"] ?: "white"
}