package org.dbpedia.extraction.server

import org.dbpedia.extraction.config.ConfigUtils._
import java.io.File

import org.dbpedia.extraction.config.Config
import org.dbpedia.extraction.mappings.Extractor
import org.dbpedia.extraction.util.{ExtractorUtils, Language}
import java.util.logging.{Level, Logger}

/**
 * User: Dimitris Kontokostas
 * server config
 */
class ServerConfiguration(configPath: String) extends Config(configPath) {
  private val logger = Logger.getLogger(getClass.getName)

  val mappingsUrl: String = getString(this, "mappingsUrl", required = true)

  val localServerUrl: String = getString(this, "localServerUrl", required = true)

  val serverPassword: String = getString(this, "serverPassword", required = true)
  val statisticsDir: File = getValue(this, "statisticsDir", required = true)(new File(_))

  val mappingTestExtractorClasses: Seq[Class[_ <: Extractor[_]]] = ExtractorUtils.loadExtractorClassSeq(getStrings(this, "mappingsTestExtractors", ","))
  val customTestExtractorClasses: Map[Language, Seq[Class[_ <: Extractor[_]]]] = ExtractorUtils.loadExtractorsMapFromConfig(languages, this)

  // Load default page titles for extraction testing - moved from Extraction.scala
  val defaultPageTitles: Map[String, String] = {
    val file = "/extractionPageTitles.txt"
    try {
      val in = getClass.getResourceAsStream(file)
      if (in == null) {
        logger.warning(s"Resource file $file not found, using defaults")
        Map("en" -> "Berlin", "de" -> "Berlin", "fr" -> "Paris", "es" -> "Madrid")
      } else {
        try {
          val source = scala.io.Source.fromInputStream(in)(scala.io.Codec.UTF8)
          val titles =
            for (line <- source.getLines()
                 if line.startsWith("[[") && line.endsWith("]]") && line.contains(':')
                 ) yield {
              val colon = line.indexOf(':')
              (line.substring(2, colon), line.substring(colon + 1, line.length - 2))
            }
          val result = titles.toMap
          source.close()
          if (result.isEmpty) {
            logger.warning("No valid titles found in resource file, using defaults")
            Map("en" -> "Berlin", "de" -> "Berlin", "fr" -> "Paris", "es" -> "Madrid")
          } else {
            logger.info(s"Loaded ${result.size} default page titles from resource file")
            result
          }
        } catch {
          case e: Exception =>
            logger.warning(s"Error reading resource file $file: ${e.getMessage}")
            Map("en" -> "Berlin", "de" -> "Berlin", "fr" -> "Paris", "es" -> "Madrid")
        } finally {
          in.close()
        }
      }
    } catch {
      case e: Exception =>
        logger.log(Level.WARNING, "could not load extraction page titles from classpath resource " + file, e)
        Map("en" -> "Berlin", "de" -> "Berlin", "fr" -> "Paris", "es" -> "Madrid")
    }
  }

  // Cached extractor names by language to avoid repeated computation
  private lazy val extractorNamesByLanguage: Map[Language, Seq[String]] = {
    logger.info("Initializing extractor names cache for all languages")
    val result = languages.map { language =>
      val customExtractors = customTestExtractorClasses.getOrElse(language, Seq.empty)
      val allExtractors = (customExtractors ++ mappingTestExtractorClasses)
        .map(_.getSimpleName)
        .distinct
        .sorted

      logger.info(s"Language ${language.wikiCode}: ${allExtractors.size} extractors configured")
      language -> allExtractors
    }.toMap

    logger.info(s"Extractor names cache initialized for ${result.size} languages")
    result
  }

  // Cached extractor classes by language for fast lookup
  private lazy val extractorClassesByLanguage: Map[Language, Map[String, Class[_ <: Extractor[_]]]] = {
    logger.info("Initializing extractor classes cache for all languages")
    val result = languages.map { language =>
      val customExtractors = customTestExtractorClasses.getOrElse(language, Seq.empty)
      val allExtractors = customExtractors ++ mappingTestExtractorClasses

      // Fix: Split the operation to avoid type inference issues
      val extractorMappings = scala.collection.mutable.ArrayBuffer[(String, Class[_ <: Extractor[_]])]()

      for (extractorClass <- allExtractors) {
        val simpleName = extractorClass.getSimpleName
        val nameWithoutExtractor = simpleName.replace("Extractor", "")

        // Create multiple mappings for flexible matching
        extractorMappings += simpleName -> extractorClass
        extractorMappings += nameWithoutExtractor -> extractorClass
        extractorMappings += simpleName.toLowerCase -> extractorClass
        extractorMappings += nameWithoutExtractor.toLowerCase -> extractorClass
      }

      val extractorMap = extractorMappings.toMap

      logger.info(s"Language ${language.wikiCode}: ${extractorMap.size} extractor mappings created")
      language -> extractorMap
    }.toMap

    logger.info(s"Extractor classes cache initialized for ${result.size} languages")
    result
  }

  /**
   * Get default page title for a language code
   */
  def getDefaultPageTitle(langCode: String): String = {
    defaultPageTitles.getOrElse(langCode, "Berlin")
  }

  /**
   * Check if a language is enabled in this server configuration
   */
  def isLanguageEnabled(language: Language): Boolean = {
    languages.contains(language)
  }

  /**
   * Get all configured languages
   */
  def getConfiguredLanguages: Set[Language] = {
    languages.toSet
  }

  /**
   * Get available extractor names for a specific language
   * @param language The language to get extractors for
   * @return Sequence of extractor names available for the language
   * @throws IllegalArgumentException if language is not enabled
   * @throws IllegalStateException if language is enabled but has no extractors
   */
  def getAvailableExtractors(language: Language): Seq[String] = {
    if (!isLanguageEnabled(language)) {
      val enabledLanguages = languages.map(_.wikiCode).mkString(", ")
      throw new IllegalArgumentException(s"Language '${language.wikiCode}' is not enabled in configuration. Enabled languages: $enabledLanguages")
    }

    val extractors = extractorNamesByLanguage.getOrElse(language, Seq.empty)

    if (extractors.isEmpty) {
      throw new IllegalStateException(s"Language '${language.wikiCode}' is enabled but has no extractors configured. Please check the extractor configuration.")
    }

    extractors
  }

  /**
   * Check if a specific extractor is available for a language using cached lookup
   * @param language The language to check
   * @param extractorName The name of the extractor to check
   * @return true if the extractor is available for the language, false otherwise
   */
  def isExtractorAvailable(language: Language, extractorName: String): Boolean = {
    try {
      if (!isLanguageEnabled(language)) {
        return false
      }

      val extractorMap = extractorClassesByLanguage.getOrElse(language, Map.empty)

      // Try exact matches first
      if (extractorMap.contains(extractorName)) {
        return true
      }

      // Try case-insensitive matches
      val lowerExtractorName = extractorName.toLowerCase
      if (extractorMap.contains(lowerExtractorName)) {
        return true
      }

      // Try with/without "Extractor" suffix
      val withExtractor = extractorName + "Extractor"
      val withoutExtractor = extractorName.replace("Extractor", "")

      extractorMap.contains(withExtractor) ||
      extractorMap.contains(withoutExtractor) ||
      extractorMap.contains(withExtractor.toLowerCase) ||
      extractorMap.contains(withoutExtractor.toLowerCase)

    } catch {
      case e: Exception =>
        logger.warning(s"Error checking extractor availability for '$extractorName' in language '${language.wikiCode}': ${e.getMessage}")
        false
    }
  }

  /**
   * Get extractor classes for a specific language
   * @param language The language to get extractor classes for
   * @return Sequence of extractor classes for the language
   */
  def getExtractorClasses(language: Language): Seq[Class[_ <: Extractor[_]]] = {
    if (!isLanguageEnabled(language)) {
      val enabledLanguages = languages.map(_.wikiCode).mkString(", ")
      throw new IllegalArgumentException(s"Language '${language.wikiCode}' is not enabled in configuration. Enabled languages: $enabledLanguages")
    }

    val customExtractors = customTestExtractorClasses.getOrElse(language, Seq.empty)
    (customExtractors ++ mappingTestExtractorClasses).distinct
  }

  /**
   * Get a specific extractor class by name for a language with flexible matching
   * @param language The language
   * @param extractorName The name of the extractor
   * @return Optional extractor class if found
   */
  def getExtractorClass(language: Language, extractorName: String): Option[Class[_ <: Extractor[_]]] = {
    try {
      if (!isLanguageEnabled(language)) {
        return None
      }

      val extractorMap = extractorClassesByLanguage.getOrElse(language, Map.empty)

      // Try exact matches first
      extractorMap.get(extractorName)
        .orElse(extractorMap.get(extractorName.toLowerCase))
        .orElse(extractorMap.get(extractorName + "Extractor"))
        .orElse(extractorMap.get((extractorName + "Extractor").toLowerCase))
        .orElse(extractorMap.get(extractorName.replace("Extractor", "")))
        .orElse(extractorMap.get(extractorName.replace("Extractor", "").toLowerCase))

    } catch {
      case e: Exception =>
        logger.warning(s"Error getting extractor class '$extractorName' for language '${language.wikiCode}': ${e.getMessage}")
        None
    }
  }

  /**
   * Get statistics about configured extractors
   * @return Map of language to extractor count
   */
  def getExtractorStatistics: Map[String, Int] = {
    extractorNamesByLanguage.map { case (lang, extractors) =>
      lang.wikiCode -> extractors.size
    }
  }

  /**
   * Get mapping extractor names
   */
  def getMappingExtractorNames: Seq[String] = {
    mappingTestExtractorClasses.map(_.getSimpleName).sorted
  }

  /**
   * Get custom extractor names for a specific language
   */
  def getCustomExtractorNames(language: Language): Seq[String] = {
    customTestExtractorClasses.getOrElse(language, Seq.empty)
      .map(_.getSimpleName)
      .sorted
  }

  /**
   * Validate configuration - check if all languages have at least one extractor
   * @return Sequence of languages that have configuration issues
   */
  def validateConfiguration: Seq[String] = {
    val issues = scala.collection.mutable.ArrayBuffer[String]()

    for (language <- languages) {
      try {
        getAvailableExtractors(language)
      } catch {
        case e: IllegalStateException =>
          issues += s"Language '${language.wikiCode}': ${e.getMessage}"
        case e: Exception =>
          issues += s"Language '${language.wikiCode}': Unexpected error - ${e.getMessage}"
      }
    }

    issues.toSeq
  }

  /**
   * Get configuration summary for debugging and monitoring
   */
  def getConfigurationSummary: Map[String, Any] = {
    Map(
      "configuredLanguages" -> languages.map(_.wikiCode).sorted,
      "languageCount" -> languages.size,
      "mappingExtractors" -> getMappingExtractorNames,
      "mappingExtractorCount" -> mappingTestExtractorClasses.size,
      "extractorStatistics" -> getExtractorStatistics,
      "configurationIssues" -> validateConfiguration,
      "totalExtractorMappings" -> extractorClassesByLanguage.values.map(_.size).sum
    )
  }

  /**
   * Get detailed extractor information for a specific language
   */
  def getLanguageExtractorDetails(language: Language): Map[String, Any] = {
    if (!isLanguageEnabled(language)) {
      throw new IllegalArgumentException(s"Language '${language.wikiCode}' is not enabled in configuration")
    }

    val customExtractors = getCustomExtractorNames(language)
    val mappingExtractors = getMappingExtractorNames
    val allExtractors = getAvailableExtractors(language)

    Map(
      "language" -> language.wikiCode,
      "totalExtractors" -> allExtractors.size,
      "customExtractors" -> customExtractors,
      "customExtractorCount" -> customExtractors.size,
      "mappingExtractors" -> mappingExtractors,
      "mappingExtractorCount" -> mappingExtractors.size,
      "allExtractors" -> allExtractors
    )
  }

  // Log configuration summary at initialization
  logger.info("=== SERVER CONFIGURATION INITIALIZED ===")
  logger.info(s"Configured languages: ${languages.map(_.wikiCode).mkString(", ")}")
  logger.info(s"Mapping extractors: ${mappingTestExtractorClasses.size}")
  logger.info(s"Total extractor mappings: ${extractorClassesByLanguage.values.map(_.size).sum}")

  val configIssues = validateConfiguration
  if (configIssues.nonEmpty) {
    logger.warning(s"Configuration issues found: ${configIssues.mkString("; ")}")
  } else {
    logger.info("Configuration validation passed - all languages have extractors configured")
  }
  logger.info("=== END SERVER CONFIGURATION ===")
}