import java.io.File
import java.net.URL
import java.net.HttpURLConnection
import java.nio.file.Paths
import java.io.*
import java.util.zip.ZipInputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption


const val ROOT_PATH = "/etc/ship/"
const val SHIP_FILE = "/etc/ship/ship.pkg"
const val PACKAGE_LIST_PATH = "/etc/ship/packagelists/"
const val DOWNLOAD_PATH = "/etc/ship/downloads/"
const val INSTALL_PATH = "/etc/ship/installed/"

// Customizable things done by fetching from the ship.pkg file
var messageEnd = ""
var errorPrefix = ""
var warningPrefix = ""
var infoPrefix = ""
var inputPrefix = ""
var noticePrefix = ""

var suffixColor = ""
var errorColor = ""
var warningColor = ""
var infoColor = ""
var inputColor = ""
var spinnerColor = ""
var textColor = ""
var noticeColor = ""


/* IMPLEMENTING: SYNCING */


enum class LogType {
    INFO,
    WARN,
    ERR,
    INPUT,
    DBG,
    NULL,
    NOTICE
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
        LogType.NULL -> ""
        LogType.NOTICE -> noticePrefix
    }
    
    val color = when (type) {
        LogType.INFO -> colorTable[infoColor] ?: ""
        LogType.WARN -> colorTable[warningColor] ?: ""
        LogType.ERR -> colorTable[errorColor] ?: ""
        LogType.INPUT -> colorTable[inputColor] ?: ""
        LogType.DBG -> colorTable["purple"] ?: ""
        LogType.NULL -> colorTable["reset"] ?: ""
        LogType.NOTICE -> colorTable[noticeColor] ?: ""
    }

    val messageColor = colorTable[textColor] ?: ""
    val endColor = colorTable[suffixColor] ?: ""

    println("$color$prefix $messageColor$Message $endColor$messageEnd\u001B[0m")
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


fun extractZip(zipFilePath: String, outputDir: String) {
    File(outputDir).mkdirs()

    ZipInputStream(FileInputStream(zipFilePath)).use { zip ->
        var entry = zip.nextEntry
        while (entry != null) {
            val outFile = File(outputDir, entry.name)
            if (entry.isDirectory) {
                outFile.mkdirs()
            } else {
                outFile.parentFile.mkdirs()
                FileOutputStream(outFile).use { out ->
                    zip.copyTo(out)
                }
            }
            zip.closeEntry()
            entry = zip.nextEntry
        }
    }
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


fun runShellScript(scriptPath: String): Int {
    try {
        // Make sure the script is executable
        val process = ProcessBuilder("/bin/bash", scriptPath)
            .inheritIO()  // Optional: sends the script's output/error to your program's stdout/stderr
            .start()

        val exitCode = process.waitFor()  // Wait for script to finish
        if (exitCode == 0) {
            return exitCode
        } else {
            return exitCode
        }
    } catch (e: Exception) {
        e.printStackTrace()
        return -1
    }
}


fun makeExecutable(filePath: String) {
    val file = File(filePath)
    if (!file.exists()) {
        println("File does not exist: $filePath")
        return
    }

    // Make executable by owner
    val success = file.setExecutable(true, true)  
    if (success) {
        println("$filePath is now executable")
    } else {
        println("Failed to make $filePath executable")
    }
}


fun moveFile(sourcePath: String, targetPath: String) {
    val source = Paths.get(sourcePath)
    val target = Paths.get(targetPath)
    try {
        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
        println("Moved $sourcePath to $targetPath")
    } catch (e: Exception) {
        println("Failed to move file: ${e.message}")
    }
}


fun createShortcut(targetPath: String, shortcutPath: String) {
    val target = Paths.get(targetPath)       // The file or folder you want to point to
    val shortcut = Paths.get(shortcutPath)   // The path where the shortcut should be created

    try {
        // Create symlink, replace if it exists
        Files.createSymbolicLink(shortcut, target)
        println("Shortcut created: $shortcutPath → $targetPath")
    } catch (e: Exception) {
        println("Failed to create shortcut: ${e.message}")
    }
}


fun copyFile(sourcePath: String, destinationPath: String) {
    val source = Paths.get(sourcePath)
    val destination = Paths.get(destinationPath)

    try {
        Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING)
        println("File copied: $sourcePath → $destinationPath")
    } catch (e: Exception) {
        println("Failed to copy file: ${e.message}")
    }
}


fun httpPingWithLatency(urlString: String, timeout: Int = 5000): Long? {
    return try {
        val start = System.currentTimeMillis()
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "HEAD"
        connection.connectTimeout = timeout
        connection.readTimeout = timeout
        connection.connect()
        val code = connection.responseCode
        if (code in 200..399) System.currentTimeMillis() - start else null
    } catch (e: Exception) {
        null
    }
}


fun DownloadFile(url: String, OutputPath: String) {
    // Open a connection to the URL
    val connection = URL(url).openConnection() as HttpURLConnection

    // Table of colors
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

    // Get the total file size in bytes. Used to calculate percentage.
    val totalSize = connection.contentLengthLong
    if (totalSize <= 0) {
        log(LogType.WARN, "Could not determine file size for download. Download won't show procent.")
    }

    // Buffer for reading chunks of the file (8 KB per read)
    val buffer = ByteArray(8192)
    var downloaded: Long = 0  // Counter for how many bytes we have downloaded so far

    // Open the output file
    val file = File(OutputPath)
    file.outputStream().use { output ->
        // Open the input stream from the URL
        connection.inputStream.use { input ->
            var read: Int  // Number of bytes read in each iteration

            // Spinner characters for animated effect
            val spinner = arrayOf("|", "/", "-", "\\")
            var spinIndex = 0  // Tracks which spinner character to show

            // Loop until the entire file is read
            while (true) {
                read = input.read(buffer)  // Read up to buffer.size bytes
                if (read == -1) break      // End of file reached

                output.write(buffer, 0, read)  // Write chunk to file
                downloaded += read             // Update downloaded counter

                // Calculate percentage downloaded (0-100)
                val percent = if (totalSize > 0) (downloaded * 100 / totalSize) else 0

                // Choose the spinner character
                val spinChar = spinner[spinIndex % spinner.size]
                spinIndex++

                // Print spinner and percentage on the same line
                // \r returns cursor to the beginning of the line
                val spinnerColor = colorTable[spinnerColor]
                val currentTextColor = colorTable[textColor]
                val color = colorTable[suffixColor] ?: ""
                print("\r$spinnerColor$spinChar : $currentTextColor Downloading... $url $color$percent%")
            }
        }
    }

    val spinnerColor = colorTable[spinnerColor]
    val currentTextColor = colorTable[textColor]
    val color = colorTable[suffixColor] ?: ""

    // Print final completion message and add spaces to overwrite spinner line
    println("\r$spinnerColor✔$currentTextColor Completed $url$color 100%              ")
}


fun add(pkg: String, shipPKGFile: File, shipPKG: MutableMap<String, MutableMap<String, String>>) {
    log(LogType.INFO, "Looking for package availability...")

    val Servers = shipPKG["Servers"] ?: mutableMapOf()

    var availableServers: MutableMap<String, String> = mutableMapOf()

    /*for ((name, url) in Servers) {
        val latency = httpPingWithLatency(url)
        if (latency != null) {
            log(LogType.NULL, " - $name: $url (ping: ${latency}ms)")
            availableServers[name] = url
        }
    }*/
    availableServers = Servers

    var Match: String = ""
    var Version: String = ""

    for (name in availableServers.keys) {
        val packageListFile = File("${PACKAGE_LIST_PATH}${name}.ship")
        val packages = parseTOML(packageListFile.readText())

        // Find all keys starting with pkg + "."
        val matchingKeys = packages.keys.filter { it.startsWith("$pkg.") }

        if (matchingKeys.isNotEmpty()) {
            // Pick the one with the highest number after the dot
            val highestKey = matchingKeys.maxByOrNull { it.substringAfter(".").toIntOrNull() ?: 0 }!!
            Match = name
            Version = packages[highestKey]?.get("Version") ?: ""
            break
        }

        //packageListFile.appendText("\n" + writeTOML(MapOf("Package.$package" to MapOf("Version" to ""))))
    }

    if (Match == "") {
        log(LogType.ERR, "Package '$pkg' not found on any available server.")
        return
    }

    if (shipPKG["Package.$pkg"] != null) {
        log(LogType.WARN, "Package '$pkg' is already added. Manually remove \"Package.$pkg\" from ship.pkg and re-run.")
        return
    }

    log(LogType.INFO, "Adding package '$pkg' version '$Version' from server '$Match'...")
    shipPKGFile.appendText("\n" + writeTOML(mapOf("Package.$pkg" to mapOf("Version" to Version, "Server" to Match))))
}


fun update(shipPKG: MutableMap<String, MutableMap<String, String>>) {
    log(LogType.INFO, "Fetching servers...")
    log(LogType.INFO, "Available servers:")
    val Servers: MutableMap<String, String> = shipPKG["Servers"] ?: mutableMapOf()

    var availableServers: MutableMap<String, String> = mutableMapOf()

    for ((name, url) in Servers) {
        val latency = httpPingWithLatency(url)
        if (latency != null) {
            log(LogType.NULL, " - $name: $url (ping: ${latency}ms)")
            availableServers[name] = url
        }
    }

    val packageListDir = File(PACKAGE_LIST_PATH)
    if (!packageListDir.exists()) {
        packageListDir.mkdirs()
    }

    for ((name, url) in availableServers) {
        //log(LogType.INFO, "Fetching package list from $url ...")
        
        DownloadFile("${url}/shippkg/packages.ship", "${PACKAGE_LIST_PATH}${name}.ship")
    }
}


fun install(name: String, values: MutableMap<String, String>, shipPKG: MutableMap<String, MutableMap<String, String>>): Int {
    val pkgName = name.removePrefix("Package.")
    val version = values["Version"] ?: ""
    val server = values["Server"] ?: ""

    val packageListFile = File("${PACKAGE_LIST_PATH}${server}.ship")
    val packageList = parseTOML(packageListFile.readText())

    val Servers = shipPKG["Servers"] ?: mutableMapOf()
    val serverURL = Servers[server] ?: ""
    val packageURL = packageList.keys
        .filter { it.startsWith("$pkgName.") }
        .maxByOrNull { it.substringAfter(".").toIntOrNull() ?: 0 }
        ?.let { packageList[it]?.get("URL") } ?: ""
    
    //log(LogType.DBG, "Package URL: ${packageURL}")

    if (serverURL == "") {
        log(LogType.ERR, "Server '$server' for package '$pkgName' not found. Skipping...")
        return 0
    }

    if (packageURL == "") {
        log(LogType.ERR, "URL for package '$pkgName' not found. Skipping...")
        return 0
    }

    log(LogType.INFO, "Downloading package '$pkgName' version '$version' from server '$server'...")
    DownloadFile(packageURL, "${DOWNLOAD_PATH}$pkgName-$version.zip")
    log(LogType.INFO, "Package '$pkgName' downloaded to '${DOWNLOAD_PATH}$pkgName-$version.zip'.")

    log(LogType.INFO, "Extracting package '$pkgName'...")
    extractZip("${DOWNLOAD_PATH}$pkgName-$version.zip", "${DOWNLOAD_PATH}$pkgName-$version/")

    log(LogType.INFO, "Package '$pkgName' extracted to '${DOWNLOAD_PATH}$pkgName-$version/'.")
    log(LogType.INFO, "Reading build information and installing package '$pkgName'...")

    val buildInfo = parseTOML(File("${DOWNLOAD_PATH}$pkgName-$version/$pkgName/packageinfo.ship").readText())

    log(LogType.NOTICE, "Adding dependencies to install queue...")

    for ((dependency, values) in buildInfo["Dependencies"] ?: mutableMapOf()) {
        if (dependency != "shippkg") {
            log(LogType.NULL, " - $dependency")
            add(dependency, File(SHIP_FILE), shipPKG)
        }
    }

    log(LogType.INFO, "Running install script for package '$pkgName'...")
    makeExecutable("${DOWNLOAD_PATH}$pkgName-$version/$pkgName/${buildInfo["meta"]?.get("BuildScript") ?: "install.sh"}")
    val exitCode = runShellScript("${DOWNLOAD_PATH}$pkgName-$version/$pkgName/${buildInfo["meta"]?.get("BuildScript") ?: "install.sh"}")
    if (exitCode != 0) {
        log(LogType.ERR, "Installation script for package '$pkgName' failed with exit code $exitCode. Aborting installation.")
        return 1
    }

    log(LogType.INFO, "Moving installed files for package '$pkgName' to install path...")
    val forceMove = buildInfo["meta"]?.get("ForceDir") ?: "no"

    val targetDir = File("${INSTALL_PATH}$pkgName")
    if (!targetDir.exists()) targetDir.mkdirs()
    copyFile("${DOWNLOAD_PATH}$pkgName-$version/$pkgName/packageinfo.ship", "${INSTALL_PATH}$pkgName/packageinfo.ship")

    if (forceMove.lowercase() == "yes") {
        //val mainFile = buildInfo["meta"]?.get("MainFile") ?: buildInfo["meta"]?.get("BuildScript") ?: ""
        val downloadPath = "${DOWNLOAD_PATH}$pkgName-$version/$pkgName/${buildInfo["meta"]?.get("MainFile")}"
        if (!File(downloadPath).exists()) File(downloadPath).mkdirs() else File(downloadPath).parentFile.mkdirs() 

        moveFile(downloadPath, buildInfo["meta"]?.get("InstallDir") ?: "/usr/local/bin/$pkgName")
    } else {
        moveFile("${DOWNLOAD_PATH}$pkgName-$version/$pkgName/", "${INSTALL_PATH}")
        val mainFile = buildInfo["meta"]?.get("MainFile") ?: buildInfo["meta"]?.get("BuildScript") ?: ""
        if (mainFile.isNotEmpty()) {
            if (!File("/usr/local/bin/$pkgName").exists()) File("/usr/local/bin/$pkgName").createNewFile()
            createShortcut("/usr/local/bin/$pkgName", "${INSTALL_PATH}$pkgName/$mainFile")
        } else {
            log(LogType.WARN, "No main file specified for package '$pkgName'. Skipping shortcut creation.")
        }
    }
    return 0
}


fun sync(shipPKG: MutableMap<String, MutableMap<String, String>>) {
    val downloadDir = File(DOWNLOAD_PATH)
    val diffDir = File("${ROOT_PATH}diffs.ship")

    if (!downloadDir.exists()) {
        log(LogType.WARN, "Downloads path is not set. Creating...")
        downloadDir.mkdirs()
    }

    if (!diffDir.exists()) {
        log(LogType.WARN, "Diff path is not set. Creating...")
        diffDir.createNewFile()
    }


    log(LogType.INFO, "Checking diffs...")

    val diffData = parseTOML(diffDir.readText())

    var installDiff = mutableMapOf<String, MutableMap<String, String>>()
    var uninstallDiff = mutableMapOf<String, MutableMap<String, String>>()

    for ((name, values) in shipPKG) {
        if (name !in diffData && name.startsWith("Package.")) {
            installDiff[name] = values
            log(LogType.NOTICE, "Package '$name' marked for installation.")
        }
    }

    for ((name, values) in diffData) {
        if (name !in shipPKG && name.startsWith("Package.")) {
            uninstallDiff[name] = values
            log(LogType.NOTICE, "Package '$name' marked for uninstallation.")
        }
    }

    if (installDiff.isEmpty() && uninstallDiff.isEmpty()) {
        log(LogType.NOTICE, "No changes detected. System is in sync. Exiting...")
        return
    }

    // Process installations
    for ((name, values) in installDiff) {
        install(name, values, shipPKG)
    }


    // Update diff file
    diffDir.writeText(writeTOML(shipPKG))

    // Install dependencies
    sync(shipPKG)
    return
}


fun main(args: Array<String>) {
    var shipPKGFile = File(SHIP_FILE)

    val shipPKG = parseTOML(shipPKGFile.readText())

    // Get customization data from ship.pkg
    val customizeText = shipPKG["Customize.text"] ?: mutableMapOf()
    messageEnd = customizeText["LoggingEnd"] ?: ""
    errorPrefix = customizeText["ErrorPrefix"] ?: "E:"
    warningPrefix = customizeText["WarningPrefix"] ?: "W:"
    infoPrefix = customizeText["InfoPrefix"] ?: "I:"
    inputPrefix = customizeText["InputPrefix"] ?: "?:"
    noticePrefix = customizeText["NoticePrefix"] ?: "N:"

    val customizeColor = shipPKG["Customize.colors"] ?: mutableMapOf()
    suffixColor = customizeColor["LoggingEndColor"] ?: ""
    errorColor = customizeColor["ErrorPrefixColor"] ?: "red"
    warningColor = customizeColor["WarningPrefixColor"] ?: "yellow"
    infoColor = customizeColor["InfoPrefixColor"] ?: "blue"
    inputColor = customizeColor["InputPrefixColor"] ?: "white"
    spinnerColor = customizeColor["SpinnerColor"] ?: "blue"
    noticeColor= customizeColor["NoticePrefixColor"] ?: "green"
    textColor = customizeColor["TextColor"] ?: "white"


    //DownloadFile("https://ferriit.gregtech.eu/kingjamesbible.txt", "bible.txt")
    if (args.isEmpty()) {
        log(LogType.ERR, "No command provided. Exiting.")
        return
    }
    when (args[0]) {
        "update" -> update(shipPKG)
        "add" -> {
            if (args.size < 2) {
                log(LogType.ERR, "No package name provided for 'add' command.")
                return
            }
            add(args[1], shipPKGFile, shipPKG)
        }
        "sync" -> sync(shipPKG)
        else -> log(LogType.ERR, "Unknown command '${args[0]}'. Exiting.")
    }
}