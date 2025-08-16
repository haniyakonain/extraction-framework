package org.dbpedia.extraction.server

import java.net.{URI, URL}
import java.util.logging.Logger
import java.util.concurrent.TimeUnit

import com.google.common.cache.{CacheBuilder, CacheLoader, LoadingCache}
import com.sun.jersey.api.container.httpserver.HttpServerFactory
import com.sun.jersey.api.core.{PackagesResourceConfig, ResourceConfig}
import org.dbpedia.extraction.config.provenance.Dataset
import org.dbpedia.extraction.mappings._
import org.dbpedia.extraction.server.Server._
import org.dbpedia.extraction.server.stats.MappingStatsManager
import org.dbpedia.extraction.util.{ExtractionRecorder, Language}
import org.dbpedia.extraction.util.Language.wikiCodeOrdering
import org.dbpedia.extraction.util.StringUtils.prettyMillis
import org.dbpedia.extraction.wikiparser.WikiTitle
import org.dbpedia.extraction.sources.Source
import org.dbpedia.extraction.destinations.Destination

import scala.collection.immutable.SortedMap
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.reflect.{ClassTag, classTag}
import scala.util.{Failure, Success, Try}
import scala.language.existentials
import scala.collection.JavaConverters._

/**
 * Optimized DBpedia server class with Guava caching support for extraction operations
 */
class Server(
  private val password : String,
  languages : Seq[Language],
  val paths: Paths,
  mappingTestExtractors: Seq[Class[_ <: Extractor[_]]],
  customTestExtractors: Map[Language, Seq[Class[_ <: Extractor[_]]]])
{
    val managers: SortedMap[Language, MappingStatsManager] = {
      val tuples = languages.map(lang => lang -> new MappingStatsManager(paths.statsDir, lang))
      SortedMap(tuples: _*)
    }

  val redirects: Map[Language, Redirects] = {
    managers.map(manager => (manager._1, Server.buildTemplateRedirects(manager._2.wikiStats.redirects, manager._1)))
  }

  // Cache key for single extractor managers
  private case class ExtractorCacheKey(language: Language, extractorClass: Class[_ <: Extractor[_]])

  // Guava LoadingCache for single extractor managers with proper cache loader
private val singleExtractorCache: LoadingCache[ExtractorCacheKey, ExtractionManager] = {
  CacheBuilder.newBuilder()
    .maximumSize(20)
    .expireAfterAccess(10, TimeUnit.MINUTES)
    .build(new CacheLoader[ExtractorCacheKey, ExtractionManager]() {
      override def load(key: ExtractorCacheKey): ExtractionManager = {
        logger.info(s"Cache loader: Creating extraction manager for ${key.extractorClass.getSimpleName} in ${key.language.wikiCode}")

        val manager = new DynamicExtractionManager(
          managers(_).updateStats(_),
          Seq(key.language),
          paths,
          redirects,
          if (mappingTestExtractors.contains(key.extractorClass)) Seq(key.extractorClass) else Seq.empty,
          if (customTestExtractors.getOrElse(key.language, Seq.empty).contains(key.extractorClass))
            Map(key.language -> Seq(key.extractorClass)) else Map.empty
        )

        // ADD ERROR HANDLING HERE:
        try {
          manager.updateAll
          logger.info(s"Cache loader: Successfully initialized extraction manager for ${key.extractorClass.getSimpleName} in ${key.language.wikiCode}")
          manager
        } catch {
          case e: Exception =>
            logger.warning(s"Failed to initialize extraction manager for ${key.extractorClass.getSimpleName} in ${key.language.wikiCode}: ${e.getMessage}")
            // Return a manager without calling updateAll to avoid mapping issues
            manager
        }
      }
    })
}

  // Main extraction manager with ALL extractors
  val extractor: ExtractionManager = {
    logger.info("Creating main DynamicExtractionManager with ALL configured extractors")
    logger.info(s"Mapping extractors: ${mappingTestExtractors.map(_.getSimpleName).mkString(", ")}")
    customTestExtractors.foreach { case (lang, extractors) =>
      logger.info(s"Custom extractors for ${lang.wikiCode}: ${extractors.map(_.getSimpleName).mkString(", ")}")
    }

    new DynamicExtractionManager(managers(_).updateStats(_), languages, paths, redirects, mappingTestExtractors, customTestExtractors)
  }


  // Log final configuration
  logExtractorConfiguration()

  def adminRights(pass: String): Boolean = password == pass

  /**
   * Enhanced extract using a specific extractor with Guava caching optimization
   */
  def extractWithSpecificExtractor(source: Source, destination: Destination, language: Language, extractorName: String): Unit = {
    logger.info(s"Starting single extractor extraction: '$extractorName' for language: ${language.wikiCode}")

    // Validate language support - this will throw appropriate exceptions
    val availableExtractors = getAvailableExtractorNames(language)

    // Find the extractor class
    val extractorClass = findExtractorClass(language, extractorName) match {
      case Some(clazz) =>
        logger.info(s"Found extractor class: ${clazz.getSimpleName} for request '$extractorName'")
        clazz
      case None =>
        throw new IllegalArgumentException(s"Extractor '$extractorName' not found for language '${language.wikiCode}'. Available extractors: ${availableExtractors.mkString(", ")}")
    }

    // Get from Guava cache (will load if not present)
    val cacheKey = ExtractorCacheKey(language, extractorClass)
    val singleExtractorManager = singleExtractorCache.get(cacheKey)

    logger.info(s"Using cached extraction manager for ${extractorClass.getSimpleName} in ${language.wikiCode}")

    // Run extraction with the cached manager
    singleExtractorManager.extract(source, destination, language, true)

    logger.info(s"Successfully completed extraction with ${extractorClass.getSimpleName} for language ${language.wikiCode}")
  }

  /**
   * Find extractor class with flexible matching
   */
  private def findExtractorClass(language: Language, extractorName: String): Option[Class[_ <: Extractor[_]]] = {
    val customExtractorsForLang = customTestExtractors.getOrElse(language, Seq.empty)
    val allExtractors = mappingTestExtractors ++ customExtractorsForLang

    allExtractors.find { extractorClass =>
      val className = extractorClass.getSimpleName
      className == extractorName ||
        className == (extractorName + "Extractor") ||
        className.endsWith(extractorName) ||
        extractorName.endsWith(className) ||
        className.replace("Extractor", "") == extractorName.replace("Extractor", "")
    }
  }

  /**
   * Get available extractor names for a specific language
   */
  def getAvailableExtractorNames(language: Language): Seq[String] = {
    // Check if language is enabled in configuration
    if (!managers.contains(language)) {
      throw new IllegalArgumentException(s"Language '${language.wikiCode}' is not enabled in the configuration. Enabled languages: ${managers.keys.map(_.wikiCode).mkString(", ")}")
    }

    val customExtractorsForLang = customTestExtractors.getOrElse(language, Seq.empty)
    val allConfiguredExtractors = (mappingTestExtractors ++ customExtractorsForLang).distinct
    val extractorNames = allConfiguredExtractors.map(_.getSimpleName).sorted

    // Check if we have any extractors for this enabled language
    if (extractorNames.isEmpty) {
      throw new IllegalStateException(s"Language '${language.wikiCode}' is enabled in configuration but has no extractors configured. Please check the extractor configuration.")
    }

    extractorNames
  }

  /**
   * Check if a specific extractor is available for a language
   */
  def isExtractorAvailable(language: Language, extractorName: String): Boolean = {
    try {
      // Check if language is enabled in configuration
      if (!managers.contains(language)) {
        return false
      }

      findExtractorClass(language, extractorName).isDefined
    } catch {
      case e: Exception =>
        logger.warning(s"Error checking extractor availability for '$extractorName' in language '${language.wikiCode}': ${e.getMessage}")
        false
    }
  }

  /**
   * Log the final extractor configuration
   */
  private def logExtractorConfiguration(): Unit = {
    logger.info("=== EXTRACTOR INITIALIZATION COMPLETE ===")
    languages.foreach { lang =>
      try {
        val availableExtractors = getAvailableExtractorNames(lang)
        logger.info(s"${lang.wikiCode}: ${availableExtractors.length} extractors available")
      } catch {
        case e: Exception =>
          logger.warning(s"${lang.wikiCode}: Failed to get extractor configuration - ${e.getMessage}")
      }
    }
    logger.info("=== END EXTRACTOR INITIALIZATION ===")
  }

  /**
   * Get cache statistics for monitoring
   */
  def getCacheStats: String = {
    val stats = singleExtractorCache.stats()
    s"Cache stats - Size: ${singleExtractorCache.size()}, Hit Rate: ${stats.hitRate()}, Miss Count: ${stats.missCount()}, Load Count: ${stats.loadCount()}"
  }

  /**
   * Clear the extractor cache if needed
   */
  def clearCache(): Unit = {
    singleExtractorCache.invalidateAll()
    logger.info("Single extractor cache cleared")
  }
}

/**
 * The DBpedia server.
 * FIXME: more flexible configuration.
 */
object Server
{
    val logger: Logger = Logger.getLogger(getClass.getName)

    private var _instance: Server = _

    def instance: Server = _instance

    private var _config: ServerConfiguration = _

    def config: ServerConfiguration = _config

    def main(args : Array[String])
    {
        val millis = System.currentTimeMillis

        logger.info("DBpedia server starting")

        require(args != null && args.length == 1, "need the server configuration file as argument.")

        // Load properties
        _config = new ServerConfiguration(args(0))

        val mappingsUrl = new URL(_config.mappingsUrl)

        val localServerUrl = URI.create(_config.localServerUrl)

        val serverPassword = _config.serverPassword

        val languages = _config.languages

        val paths = new Paths(new URL(mappingsUrl, "index.php"), new URL(mappingsUrl, "api.php"), _config.statisticsDir, _config.ontologyFile, _config.mappingsDir)

        _instance = new Server(serverPassword, languages, paths, _config.mappingTestExtractorClasses, _config.customTestExtractorClasses)

        // Configure the HTTP server
        val resources = new PackagesResourceConfig("org.dbpedia.extraction.server.resources", "org.dbpedia.extraction.server.providers")

        // redirect URLs like "/foo/../extractionSamples" to "/extractionSamples/" (with a slash at the end)
        val features = resources.getFeatures
        features.put(ResourceConfig.FEATURE_CANONICALIZE_URI_PATH, true)
        features.put(ResourceConfig.FEATURE_NORMALIZE_URI, true)
        features.put(ResourceConfig.FEATURE_REDIRECT, true)
        // When trace is on, Jersey includes "X-Trace" headers in the HTTP response.
        // But when it receives a bad URI (e.g. by Apache), Jersey does no tracing. :-(
        // features.put(ResourceConfig.FEATURE_TRACE, true)

        HttpServerFactory.create(localServerUrl, resources).start()

        logger.info("DBpedia server started in "+prettyMillis(System.currentTimeMillis - millis) + " listening on " + localServerUrl)
    }

  def buildTemplateRedirects(redirects: Map[String, String], language: Language): Redirects = {
    new Redirects(redirects.map { case (from, to) =>
      (WikiTitle.parse(from, language).decoded, WikiTitle.parse(to, language).decoded)
    })
  }

    private val extractionRecorder = new mutable.HashMap[ClassTag[_], mutable.HashMap[Language, ExtractionRecorder[_]]]()
    def getExtractionRecorder[T: ClassTag](lang: Language, dataset : Dataset = null): org.dbpedia.extraction.util.ExtractionRecorder[T] = {
        extractionRecorder.get(classTag[T]) match{
            case Some(s) => s.get(lang) match {
                case None =>
                    s(lang) = new ExtractionRecorder[T](null, 2000, null, null, if(dataset != null) ListBuffer(dataset) else ListBuffer())
                    s(lang).initialize(lang)
                    s(lang).asInstanceOf[ExtractionRecorder[T]]
                case Some(er) =>
                    if(dataset != null) if(!er.datasets.contains(dataset)) er.datasets += dataset
                    er.asInstanceOf[ExtractionRecorder[T]]
            }
            case None =>
                extractionRecorder(classTag[T]) = new mutable.HashMap[Language, ExtractionRecorder[_]]()
                getExtractionRecorder[T](lang, dataset)
        }
    }
}
