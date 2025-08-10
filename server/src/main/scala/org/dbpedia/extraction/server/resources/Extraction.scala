package org.dbpedia.extraction.server.resources

import java.net.{URI, URL}

import org.dbpedia.extraction.destinations.formatters.{RDFJSONFormatter, TerseFormatter}
import org.dbpedia.extraction.util.Language
import javax.ws.rs._
import javax.ws.rs.core.{Context, HttpHeaders, MediaType, Response}
import java.util.logging.{Level, Logger}

import scala.xml.Elem
import scala.io.{Codec, Source}
import org.dbpedia.extraction.server.Server
import org.dbpedia.extraction.wikiparser.WikiTitle
import org.dbpedia.extraction.destinations.{DeduplicatingDestination, WriterDestination}
import org.dbpedia.extraction.sources.{WikiSource, XMLSource}
import stylesheets.TriX
import java.io.StringWriter
import java.util.Properties
import java.io.FileInputStream
import java.io.File

object Extraction {
  private val logger = Logger.getLogger(getClass.getName)

  val lines: Map[String, String] = {
    val file = "/extractionPageTitles.txt"
    try {
      // ugly - returns null if file not found, which leads to NPE later
      val in = getClass.getResourceAsStream(file)
      try {
        val titles =
          for (line <- Source.fromInputStream(in)(Codec.UTF8).getLines
               if line.startsWith("[[") && line.endsWith("]]") && line.contains(':')
               ) yield {
            val colon = line.indexOf(':')
            (line.substring(2, colon), line.substring(colon + 1, line.length - 2))
          }
        titles.toMap
      }
      finally in.close
    }
    catch {
      case e: Exception =>
        logger.log(Level.WARNING, "could not load extraction page titles from classpath resource " + file, e)
        Map()
    }
  }

  // Load configuration from properties files
  private lazy val configProperties: Properties = {
    val props = new Properties()

    // Try loading server.properties first, then server.default.properties
    val configFiles = Seq("server.properties", "server.default.properties")
    var loaded = false

    for (configFile <- configFiles if !loaded) {
      try {
        val file = new File(configFile)
        if (file.exists()) {
          logger.info(s"Loading configuration from $configFile")
          val fis = new FileInputStream(file)
          try {
            props.load(fis)
            loaded = true
            logger.info(s"Successfully loaded configuration from $configFile")
          } finally {
            fis.close()
          }
        }
      } catch {
        case e: Exception =>
          logger.log(Level.WARNING, s"Could not load $configFile", e)
      }
    }

    if (!loaded) {
      logger.warning("No configuration file found. Checked: " + configFiles.mkString(", "))
    }

    props
  }

  /**
   * Get available extractors for a specific language from the server configuration
   */
  def getAvailableExtractors(language: Language): Seq[String] = {
    try {
      // Use Server's method to get available extractors
      Server.instance.getAvailableExtractorNames(language)
    } catch {
      case e: Exception =>
        logger.log(Level.WARNING, s"Could not get extractors for language ${language.wikiCode}", e)
        getDefaultExtractorsForLanguage(language.wikiCode)
    }
  }

  /**
   * Get all configured extractors (including basic ones) for debugging
   */
  def getAllExtractors(language: Language): Seq[String] = {
    try {
      // Get available extractors from the server
      val availableExtractors = Server.instance.getAvailableExtractorNames(language)

      // Add the basic extractors that are always available
      val basicExtractors = Seq(
        "LabelExtractor", "MappingExtractor", "PageIdExtractor", "RevisionIdExtractor",
        "WikiPageOutDegreeExtractor", "WikiPageLengthExtractor", "GeoExtractor",
        "ArticlePageExtractor", "ArticleCategoriesExtractor", "CategoryLabelExtractor",
        "SkosCategoriesExtractor", "ArticleTemplatesExtractor", "ExternalLinksExtractor",
        "InterLanguageLinksExtractor", "ProvenanceExtractor", "InfoboxExtractor"
      )

      (basicExtractors ++ availableExtractors).distinct.sorted
    } catch {
      case e: Exception =>
        logger.log(Level.WARNING, s"Could not get all extractors for language ${language.wikiCode}", e)
        getSafeExtractorsForLanguage(language.wikiCode)
    }
  }

  /**
   * Check if an extractor requires MediaWiki connection parameters
   */
  private def isMediaWikiDependentExtractor(extractorName: String): Boolean = {
    val mediaWikiDependentExtractors = Set(
      "NifExtractor",
      "HtmlAbstractExtractor",
      "AbstractExtractor",
      "MissingAbstractsExtractor"
    )
    mediaWikiDependentExtractors.contains(extractorName)
  }

  /**
   * Get safe extractors that don't require MediaWiki connections
   */
  private def getSafeExtractorsForLanguage(langCode: String): Seq[String] = {
    langCode match {
      case "en" => Seq(
        "DisambiguationExtractor", "HomepageExtractor", "PersondataExtractor",
        "PndExtractor", "TopicalConceptsExtractor", "ImageExtractorNew"
      )
      case "de" => Seq(
        "DisambiguationExtractor", "HomepageExtractor", "PersondataExtractor",
        "PndExtractor", "ImageExtractorNew"
      )
      case "fr" => Seq(
        "DisambiguationExtractor", "HomepageExtractor", "PndExtractor",
        "TopicalConceptsExtractor", "PopulationExtractor"
      )
      case _ => Seq(
        "DisambiguationExtractor", "HomepageExtractor", "TopicalConceptsExtractor"
      )
    }
  }

  /**
   * Get default extractors based on language from typical DBpedia configuration
   */
  private def getDefaultExtractorsForLanguage(langCode: String): Seq[String] = {
    langCode match {
      case "en" => Seq(
        "DisambiguationExtractor", "HomepageExtractor", "PersondataExtractor",
        "PndExtractor", "TopicalConceptsExtractor", "ImageExtractorNew"
      )
      case "de" => Seq(
        "DisambiguationExtractor", "HomepageExtractor", "PersondataExtractor",
        "PndExtractor", "ImageExtractorNew"
      )
      case "fr" => Seq(
        "DisambiguationExtractor", "HomepageExtractor", "PndExtractor",
        "TopicalConceptsExtractor", "PopulationExtractor"
      )
      case "es" => Seq(
        "DisambiguationExtractor", "HomepageExtractor", "TopicalConceptsExtractor"
      )
      case "it" => Seq(
        "DisambiguationExtractor", "HomepageExtractor", "TopicalConceptsExtractor"
      )
      case "pt" => Seq(
        "DisambiguationExtractor", "HomepageExtractor", "TopicalConceptsExtractor"
      )
      case "ru" => Seq(
        "DisambiguationExtractor", "HomepageExtractor", "TopicalConceptsExtractor"
      )
      case "ca" => Seq(
        "DisambiguationExtractor", "HomepageExtractor", "TopicalConceptsExtractor"
      )
      case "nl" => Seq(
        "DisambiguationExtractor"
      )
      case "pl" => Seq(
        "DisambiguationExtractor", "HomepageExtractor"
      )
      case "ko" => Seq(
        "DisambiguationExtractor"
      )
      case "ar" => Seq(
        "TopicalConceptsExtractor"
      )
      case "el" => Seq(
        "DisambiguationExtractor", "HomepageExtractor", "TopicalConceptsExtractor"
      )
      case "eu" => Seq(
        "DisambiguationExtractor", "HomepageExtractor", "TopicalConceptsExtractor"
      )
      case "ga" => Seq(
        "HomepageExtractor"
      )
      case _ => Seq(
        "DisambiguationExtractor", "HomepageExtractor", "TopicalConceptsExtractor"
      )
    }
  }
}

/**
 * TODO: merge Extraction.scala and Mappings.scala
 */
@Path("/extraction/{lang}/")
class Extraction(@PathParam("lang") langCode: String) {
  private val language = Language.getOrElse(langCode, throw new WebApplicationException(new Exception("invalid language " + langCode), 404))

  if (!Server.instance.managers.contains(language))
    throw new WebApplicationException(new Exception("language " + langCode + " not configured in server"), 404)

  private def getTitle: String = Extraction.lines.getOrElse(langCode, "Berlin")

  // Get extractors for this language using Server's method
  private def getAvailableExtractors: Seq[String] = {
    try {
      Server.instance.getAvailableExtractorNames(language)
    } catch {
      case e: Exception =>
        Extraction.logger.log(Level.WARNING, s"Could not get extractors from server for language ${language.wikiCode}", e)
        Extraction.getAvailableExtractors(language)
    }
  }

  @GET
  @Path("extractors")
  @Produces(Array("application/json"))
  def getExtractorsList(): Response = {
    try {
      val availableExtractors = getAvailableExtractors
      val allExtractors = Extraction.getAllExtractors(language)

      val result = Map(
        "language" -> langCode,
        "availableExtractors" -> availableExtractors,
        "allExtractors" -> allExtractors
      )

      val availableExtractorsJson = availableExtractors.map(e => s""""$e"""").mkString(",")
      val allExtractorsJson = allExtractors.map(e => s""""$e"""").mkString(",")

      val jsonString =
        s"""{
                "language": "${result("language")}",
                "availableExtractors": [$availableExtractorsJson],
                "allExtractors": [$allExtractorsJson]
            }"""

      Response.ok(jsonString)
        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON + "; charset=UTF-8")
        .build()
    } catch {
      case e: Exception =>
        val errorJson = s"""{"error": "${e.getMessage}"}"""
        Response.status(500)
          .entity(errorJson)
          .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON + "; charset=UTF-8")
          .build()
    }
  }

  @GET
  @Produces(Array("application/xhtml+xml"))
  def get = {
    val extractors = getAvailableExtractors
    <html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en">
      {ServerHeader.getHeader("Extractor a page")}<body>
      <div class="row">
        <div class="col-md-3 col-md-offset-5">
          <h2>Extract a page</h2>
          <form action="extract" method="get">
            Page title
            <br/>
            <input type="text" name="title" value={getTitle}/>
            <br/>
            Revision ID (optional, overrides title)
            <br/>
            <input type="text" name="revid"/>
            <br/>
            Output format
            <br/>
            <select name="format">
              <option value="trix">Trix</option>
              <option value="turtle-triples">Turtle-Triples</option>
              <option value="turtle-quads">Turtle-Quads</option>
              <option value="n-triples">N-Triples</option>
              <option value="n-quads">N-Quads</option>
              <option value="rdf-json">RDF/JSON</option>
            </select> <br/>
            <select name="extractors">
              <option value="mappings">Mappings Only</option>
              <option value="custom">All Enabled Extractors</option>{// Add one option for each specific extractor
              extractors.map(extractor =>
                <option value={extractor}>
                  {extractor}
                </option>
              )}
            </select> <br/>
            <input type="submit" value="Extract"/>
          </form>
        </div>
      </div>
    </body>
    </html>
  }

  /**
   * Extracts a MediaWiki article
   */
  @GET
  @Path("extract")
  def extract(@QueryParam("title") title: String, @QueryParam("revid") @DefaultValue("-1") revid: Long, @QueryParam("format") format: String, @QueryParam("extractors") extractors: String, @Context headers: HttpHeaders): Response = {
    import scala.collection.JavaConverters._
    import scala.collection.JavaConversions._
    if (title == null && revid < 0) throw new WebApplicationException(new Exception("title or revid must be given"), Response.Status.NOT_FOUND)

    val requestedTypesList = headers.getAcceptableMediaTypes.map(_.toString)
    val browserMode = requestedTypesList.isEmpty || requestedTypesList.contains("text/html") || requestedTypesList.contains("application/xhtml+xml") || requestedTypesList.contains("text/plain")

    val writer = new StringWriter

    var finalFormat = format
    val acceptContentBest = requestedTypesList.map(selectFormatByContentType).head

    if (!acceptContentBest.equalsIgnoreCase("unknownAcceptFormat") && !browserMode)
      finalFormat = acceptContentBest
    val contentType = if (browserMode) selectInBrowserContentType(finalFormat) else selectContentType(finalFormat)

    val formatter = finalFormat match {
      case "turtle-triples" => new TerseFormatter(false, true)
      case "turtle-quads" => new TerseFormatter(true, true)
      case "n-triples" => new TerseFormatter(false, false)
      case "n-quads" => new TerseFormatter(true, false)
      case "rdf-json" => new RDFJSONFormatter()
      case _ => TriX.writeHeader(writer, 2)
    }

    // Validate extractor parameter
    val extractorName = Option(extractors).getOrElse("mappings")
    Extraction.logger.info(s"Processing extraction request with extractor: '$extractorName' for language: '${language.wikiCode}'")

    // Enhanced validation with better error messages
    if (extractorName != "mappings" && extractorName != "custom") {
      try {
        if (!Server.instance.isExtractorAvailable(language, extractorName)) {
          val availableExtractors = Server.instance.getAvailableExtractorNames(language)
          val errorMsg = s"Unknown extractor: '$extractorName'. Available extractors for language '${language.wikiCode}': mappings, custom, ${availableExtractors.mkString(", ")}"
          Extraction.logger.warning(errorMsg)
          throw new WebApplicationException(new Exception(errorMsg), 400)
        }
        Extraction.logger.info(s"Individual extractor '$extractorName' requested for language '${language.wikiCode}'")
      } catch {
        case e: WebApplicationException => throw e
        case e: Exception =>
          Extraction.logger.log(Level.SEVERE, s"Error checking extractor availability: ${e.getMessage}", e)
          throw new WebApplicationException(new Exception(s"Error validating extractor: ${e.getMessage}"), 500)
      }
    }
        val source =
      if (revid >= 0) WikiSource.fromRevisionIDs(List(revid), new URL(language.apiUri), language)
      else WikiSource.fromTitles(List(WikiTitle.parse(title, language)), new URL(language.apiUri), language)

    val destination = new DeduplicatingDestination(new WriterDestination(() => writer, formatter))

    try {
      // Handle different extractor scenarios with enhanced error handling
      extractorName match {
        case "mappings" =>
          Extraction.logger.info(s"Running mappings-only extraction for language '${language.wikiCode}'")
          Server.instance.extractor.extract(source, destination, language, false)

        case "custom" =>
          Extraction.logger.info(s"Running all enabled extractors extraction for language '${language.wikiCode}'")
          // FIXED: Ensure we're using all extractors properly
          try {
            // Check if the Server instance and extractor are properly initialized
            if (Server.instance == null) {
              throw new IllegalStateException("Server instance is not initialized")
            }
            if (Server.instance.extractor == null) {
              throw new IllegalStateException("Server extractor is not initialized")
            }

            // Log available extractors for debugging
            val availableExtractors = Server.instance.getAvailableExtractorNames(language)
            Extraction.logger.info(s"Available extractors for '${language.wikiCode}': ${availableExtractors.mkString(", ")}")

            // Use the correct extraction method for all extractors
            Server.instance.extractor.extract(source, destination, language, true)

            Extraction.logger.info(s"Successfully completed all-extractors extraction for language '${language.wikiCode}'")

          } catch {
            case e: IllegalStateException =>
              Extraction.logger.severe(s"Server initialization error: ${e.getMessage}")
              throw new WebApplicationException(new Exception(s"Server not properly initialized: ${e.getMessage}"), 500)
            case e: Exception =>
              Extraction.logger.severe(s"All-extractors extraction failed for language '${language.wikiCode}': ${e.getMessage}")
              // Try fallback to mappings only if all extractors fail
              Extraction.logger.info("Attempting fallback to mappings-only extraction")
              Server.instance.extractor.extract(source, destination, language, false)
          }

        case specificExtractor =>
          Extraction.logger.info(s"Running specific extractor '$specificExtractor' for language '${language.wikiCode}'")
          try {
            Server.instance.extractWithSpecificExtractor(source, destination, language, specificExtractor)
            Extraction.logger.info(s"Successfully completed specific extractor '$specificExtractor' for language '${language.wikiCode}'")
          } catch {
            case e: IllegalArgumentException =>
              Extraction.logger.warning(s"${e.getMessage}. Falling back to all extractors.")
              Server.instance.extractor.extract(source, destination, language, true)
            case e: Exception =>
              Extraction.logger.severe(s"Failed to extract with specific extractor '$specificExtractor': ${e.getMessage}")
              throw new WebApplicationException(
                new Exception(s"Extraction failed with extractor '$specificExtractor': ${e.getMessage}"),
                500
              )
          }
      }
    } catch {
      case e: WebApplicationException => throw e
      case e: Exception =>
        val errorMsg = s"Extraction failed for language '${language.wikiCode}' with extractor '$extractorName': ${e.getMessage}"
        Extraction.logger.severe(errorMsg)
        throw new WebApplicationException(new Exception(errorMsg), 500)
    }
    
    Response.ok(writer.toString)
      .header(HttpHeaders.CONTENT_TYPE, contentType + "; charset=UTF-8")
      .build()
  }

  // map
  private def selectFormatByContentType(format: String): String = {

    (format match {
      case "text/xml" => "trix"
      case "text/turtle" => "turtle-triples"
      //case "text/nquads" => "turtle-quads" // this does not exist as mimetype
      case "application/n-triples" => "n-triples"
      case "application/n-quads" => "n-quads"
      case MediaType.APPLICATION_JSON => "rdf-json"
      //case "application/ld+json" => MediaType.APPLICATION_JSON
      case _ => "unknownAcceptFormat"
    })
  }

  // override content type in browser for some formats to display text instead of downloading a file, or
  private def selectInBrowserContentType(format: String): String = {

    format match {
      case "trix" => MediaType.APPLICATION_XML
      case "rdf-json" => MediaType.APPLICATION_JSON
      case _ => MediaType.TEXT_PLAIN
    }
  }

  // map format parameters to regular content types
  private def selectContentType(format: String): String = {

    format match {
      case "trix" => MediaType.APPLICATION_XML
      case "turtle-triples" => "text/turtle"
      case "turtle-quads" => "text/nquads"
      case "n-triples" => "application/n-triples"
      case "n-quads" => "application/n-quads"
      case "rdf-json" => MediaType.APPLICATION_JSON
      case "trix" => MediaType.APPLICATION_XML
      case _ => MediaType.TEXT_PLAIN
    }
  }

  /**
   * Extracts a MediaWiki article
   */
  @POST
  @Path("extract")
  @Consumes(Array("application/xml"))
  @Produces(Array("application/xml"))
  def extract(xml: Elem) = {
    val writer = new StringWriter
    val formatter = TriX.writeHeader(writer, 2)
    val source = XMLSource.fromXML(xml, language)
    val destination = new WriterDestination(() => writer, formatter)

    Server.instance.extractor.extract(source, destination, language)

    writer.toString
  }
}
