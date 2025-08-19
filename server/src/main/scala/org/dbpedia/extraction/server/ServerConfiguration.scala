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

  val mappingTestExtractorClasses: Seq[Class[_ <: Extractor[_]]] =
    ExtractorUtils.loadExtractorClassSeq(getStrings(this, "mappingsTestExtractors", ","))

  val customTestExtractorClasses: Map[Language, Seq[Class[_ <: Extractor[_]]]] =
    ExtractorUtils.loadExtractorsMapFromConfig(languages, this)

  // Default page titles fallback
  private val defaultPageTitlesFallback: Map[String, String] =
    Map("en" -> "Berlin", "de" -> "Berlin", "fr" -> "Paris", "es" -> "Madrid")

  // Load default page titles for extraction testing - moved from Extraction.scala
  val defaultPageTitles: Map[String, String] = {
    val file = "/extractionPageTitles.txt"
    try {
      val in = getClass.getResourceAsStream(file)
      if (in == null) {
        logger.warning(s"Resource file $file not found, using defaults")
        defaultPageTitlesFallback
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
            defaultPageTitlesFallback
          } else {
            result
          }
        } catch {
          case e: Exception =>
            logger.warning(s"Error reading resource file $file: ${e.getMessage}")
            defaultPageTitlesFallback
        } finally {
          in.close()
        }
      }
    } catch {
      case e: Exception =>
        logger.log(Level.WARNING, "could not load extraction page titles from classpath resource " + file, e)
        defaultPageTitlesFallback
    }
  }

  // Helper method to validate language and throw consistent error
  private def validateLanguageEnabled(language: Language): Unit = {
    if (!isLanguageEnabled(language)) {
      val enabledLanguages = languages.map(_.wikiCode).mkString(", ")
      throw new IllegalArgumentException(
        s"Language '${language.wikiCode}' is not enabled in configuration. Enabled languages: $enabledLanguages"
      )
    }
  }

  // Helper method to create all name variations for an extractor
  private def createExtractorNameVariations(extractorClass: Class[_ <: Extractor[_]]): Seq[(String, Class[_ <: Extractor[_]])] = {
    val simpleName = extractorClass.getSimpleName
    val nameWithoutExtractor = simpleName.replace("Extractor", "")

    Seq(
      simpleName -> extractorClass,
      nameWithoutExtractor -> extractorClass,
      simpleName.toLowerCase -> extractorClass,
      nameWithoutExtractor.toLowerCase -> extractorClass
    )
  }

  // Helper method for flexible extractor name matching
  private def matchesExtractorName(extractorMap: Map[String, Class[_ <: Extractor[_]]], extractorName: String): Boolean = {
    extractorMap.contains(extractorName) ||
    extractorMap.contains(extractorName.toLowerCase) ||
    extractorMap.contains(extractorName + "Extractor") ||
    extractorMap.contains((extractorName + "Extractor").toLowerCase) ||
    extractorMap.contains(extractorName.replace("Extractor", "")) ||
    extractorMap.contains(extractorName.replace("Extractor", "").toLowerCase)
  }

  // Helper method for flexible extractor class lookup
  private def findExtractorClass(extractorMap: Map[String, Class[_ <: Extractor[_]]], extractorName: String): Option[Class[_ <: Extractor[_]]] = {
    extractorMap.get(extractorName)
      .orElse(extractorMap.get(extractorName.toLowerCase))
      .orElse(extractorMap.get(extractorName + "Extractor"))
      .orElse(extractorMap.get((extractorName + "Extractor").toLowerCase))
      .orElse(extractorMap.get(extractorName.replace("Extractor", "")))
      .orElse(extractorMap.get(extractorName.replace("Extractor", "").toLowerCase))
  }

  // Cached extractor names by language to avoid repeated computation
  private lazy val extractorNamesByLanguage: Map[Language, Seq[String]] = {
    val result = languages.map { language =>
      val customExtractors = customTestExtractorClasses.getOrElse(language, Seq.empty)
      val allExtractors = (customExtractors ++ mappingTestExtractorClasses)
        .map(_.getSimpleName)
        .distinct
        .sorted
      language -> allExtractors
    }.toMap

    result
  }

  // Cached extractor classes by language for fast lookup
  private lazy val extractorClassesByLanguage: Map[Language, Map[String, Class[_ <: Extractor[_]]]] = {
    val result = languages.map { language =>
      val customExtractors = customTestExtractorClasses.getOrElse(language, Seq.empty)
      val allExtractors = customExtractors ++ mappingTestExtractorClasses

      val extractorMappings = allExtractors.flatMap(createExtractorNameVariations)
      val extractorMap = extractorMappings.toMap

      language -> extractorMap
    }.toMap

    result
  }

  // Check if a language is enabled in this server configuration
  def isLanguageEnabled(language: Language): Boolean = {
    languages.contains(language)
  }

  // Get all configured languages
  def getConfiguredLanguages: Set[Language] = {
    languages.toSet
  }

  // Get default page title for a language code
  def getDefaultPageTitle(langCode: String): String = {
    defaultPageTitles.getOrElse(langCode, "Berlin")
  }

  // Get available extractor names for a specific language
  def getAvailableExtractors(language: Language): Seq[String] = {
    validateLanguageEnabled(language)

    val extractors = extractorNamesByLanguage.getOrElse(language, Seq.empty)
    if (extractors.isEmpty) {
      throw new IllegalStateException(
        s"Language '${language.wikiCode}' is enabled but has no extractors configured. Please check the extractor configuration."
      )
    }
    extractors
  }

  // Check if a specific extractor is available for a language using cached lookup
  def isExtractorAvailable(language: Language, extractorName: String): Boolean = {
    try {
      if (!isLanguageEnabled(language)) return false

      val extractorMap = extractorClassesByLanguage.getOrElse(language, Map.empty)
      matchesExtractorName(extractorMap, extractorName)
    } catch {
      case e: Exception =>
        logger.warning(
          s"Error checking extractor availability for '$extractorName' in language '${language.wikiCode}': ${e.getMessage}"
        )
        false
    }
  }

  // Get extractor classes for a specific language
  def getExtractorClasses(language: Language): Seq[Class[_ <: Extractor[_]]] = {
    validateLanguageEnabled(language)

    val customExtractors = customTestExtractorClasses.getOrElse(language, Seq.empty)
    (customExtractors ++ mappingTestExtractorClasses).distinct
  }

  // Get a specific extractor class by name for a language with flexible matching
  def getExtractorClass(language: Language, extractorName: String): Option[Class[_ <: Extractor[_]]] = {
    try {
      if (!isLanguageEnabled(language)) return None

      val extractorMap = extractorClassesByLanguage.getOrElse(language, Map.empty)
      findExtractorClass(extractorMap, extractorName)
    } catch {
      case e: Exception =>
        logger.warning(
          s"Error getting extractor class '$extractorName' for language '${language.wikiCode}': ${e.getMessage}"
        )
        None
    }
  }
}