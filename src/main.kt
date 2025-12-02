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

fun ParseTOML(Data: String) {
    /* Returns a map of every entry in the data separated by newlines */
    val toml: MutableMap<String, String> = mutableMapOf()
    val NoComments = stripComments(Data)
    for (i in NoComments) {
        print(i)
    }
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
    ParseTOML(InputFile.readText())
}