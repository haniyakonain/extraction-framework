package org.dbpedia.extraction.server.resources

import java.net.{URI, URL}

import org.dbpedia.extraction.destinations.formatters.{RDFJSONFormatter, TerseFormatter}
import org.dbpedia.extraction.util.Language
import javax.ws.rs._
import javax.ws.rs.core.{Context, HttpHeaders, MediaType, Response}
import java.util.logging.{Level, Logger}

import scala.xml.Elem
import org.dbpedia.extraction.server.Server
import org.dbpedia.extraction.wikiparser.WikiTitle
import org.dbpedia.extraction.destinations.{DeduplicatingDestination, WriterDestination}
import org.dbpedia.extraction.sources.{Source, WikiSource, XMLSource}
import stylesheets.TriX
import java.io.StringWriter

object Extraction {
  private val logger = Logger.getLogger(getClass.getName)

  /**
   * Get available extractors using server configuration
   */
  def getAvailableExtractors(language: Language): Seq[String] = {
    logger.info(s"Getting available extractors for language: ${language.wikiCode}")

    try {
      if (Server.instance == null) {
        throw new IllegalStateException("Server instance is not initialized")
      }

      // Use server configuration to get available extractors
      Server.config.getAvailableExtractors(language)
    } catch {
      case e: IllegalArgumentException =>
        logger.warning(s"Language ${language.wikiCode} not enabled in configuration: ${e.getMessage}")
        throw e
      case e: IllegalStateException =>
        logger.severe(s"Configuration issue for language ${language.wikiCode}: ${e.getMessage}")
        throw e
      case e: Exception =>
        logger.severe(s"Unexpected error getting extractors for ${language.wikiCode}: ${e.getMessage}")
        throw new IllegalStateException(s"Failed to get extractors for language '${language.wikiCode}': ${e.getMessage}", e)
    }
  }

  /**
   * Check if a specific extractor is available for a language
   */
  def isExtractorAvailable(language: Language, extractorName: String): Boolean = {
    logger.info(s"Checking if extractor '$extractorName' is available for language ${language.wikiCode}")

    try {
      if (Server.instance == null) {
        logger.warning("Server instance is null when checking extractor availability")
        return false
      }

      // Use server configuration to check extractor availability
      Server.config.isExtractorAvailable(language, extractorName)
    } catch {
      case e: Exception =>
        logger.warning(s"Error checking extractor availability for '$extractorName' in language '${language.wikiCode}': ${e.getMessage}")
        false
    }
  }

  /**
   * Get configured languages from server configuration
   */
  def getConfiguredLanguages: Set[Language] = {
    logger.info("Getting configured languages")
    try {
      if (Server.instance == null) {
        throw new IllegalStateException("Server instance is not initialized")
      }
      Server.config.getConfiguredLanguages
    } catch {
      case e: Exception =>
        logger.warning(s"Error getting configured languages: ${e.getMessage}")
        throw new IllegalStateException(s"Failed to get configured languages: ${e.getMessage}", e)
    }
  }

  /**
   * Check if language is enabled using server configuration
   */
  def isLanguageEnabled(language: Language): Boolean = {
    logger.info(s"Checking if language ${language.wikiCode} is enabled")
    try {
      if (Server.instance == null) {
        logger.warning("Server instance is null when checking language")
        return false
      }
      Server.config.isLanguageEnabled(language)
    } catch {
      case e: Exception =>
        logger.warning(s"Error checking if language ${language.wikiCode} is enabled: ${e.getMessage}")
        false
    }
  }
}

/**
 * TODO: merge Extraction.scala and Mappings.scala
 */
@Path("/extraction/{lang}/")
class Extraction(@PathParam("lang") langCode: String) {
  private val language = Language.getOrElse(langCode, throw new WebApplicationException(new Exception("invalid language " + langCode), 404))

  Extraction.logger.info(s"Creating Extraction resource for language: $langCode")

  // Use server configuration for language validation
  if (!Extraction.isLanguageEnabled(language))
    throw new WebApplicationException(new Exception("language " + langCode + " not configured in server"), 404)

  private def getTitle: String = {
    // Get default page title from server config - no fallback, let it fail if config is broken
    Server.config.getDefaultPageTitle(langCode)
  }

  private val logger = Logger.getLogger(getClass.getName)

  @GET
  @Path("extractors")
  @Produces(Array("application/json"))
  def getExtractorsList(): Response = {
    Extraction.logger.info(s"REST endpoint getExtractorsList called for language: $langCode")

    try {
      val availableExtractors = Extraction.getAvailableExtractors(language)
      Extraction.logger.info(s"REST endpoint got ${availableExtractors.size} extractors for $langCode")

      val extractorsJson = availableExtractors.map(e => s""""$e"""").mkString(",")

      val jsonString =
        s"""{
                "language": "$langCode",
                "extractors": [$extractorsJson],
                "count": ${availableExtractors.size}
            }"""

      Response.ok(jsonString)
        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON + "; charset=UTF-8")
        .build()
    } catch {
      case e: IllegalArgumentException =>
        Extraction.logger.warning(s"Language not enabled: ${e.getMessage}")
        val errorJson = s"""{"error": "${e.getMessage}", "language": "$langCode"}"""
        Response.status(404)
          .entity(errorJson)
          .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON + "; charset=UTF-8")
          .build()
      case e: IllegalStateException =>
        Extraction.logger.severe(s"Configuration error: ${e.getMessage}")
        val errorJson = s"""{"error": "${e.getMessage}", "language": "$langCode"}"""
        Response.status(500)
          .entity(errorJson)
          .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON + "; charset=UTF-8")
          .build()
      case e: Exception =>
        Extraction.logger.severe(s"Unexpected error: ${e.getMessage}")
        val errorJson = s"""{"error": "Internal server error: ${e.getMessage}", "language": "$langCode"}"""
        Response.status(500)
          .entity(errorJson)
          .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON + "; charset=UTF-8")
          .build()
    }
  }

  @GET
  @Path("test/{extractor}")
  @Produces(Array("application/json"))
  def testExtractor(@PathParam("extractor") extractorName: String): Response = {
    Extraction.logger.info(s"REST endpoint testExtractor called for $langCode.$extractorName")

    try {
      val isAvailable = Extraction.isExtractorAvailable(language, extractorName)
      Extraction.logger.info(s"REST endpoint testExtractor result for $langCode.$extractorName: $isAvailable")

      val jsonString = s"""{
        "extractor": "$extractorName",
        "language": "$langCode",
        "available": $isAvailable
      }"""

      Response.ok(jsonString)
        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON + "; charset=UTF-8")
        .build()
    } catch {
      case e: Exception =>
        Extraction.logger.warning(s"Error testing extractor: ${e.getMessage}")
        val errorJson = s"""{"error": "${e.getMessage}", "extractor": "$extractorName", "language": "$langCode"}"""
        Response.status(500)
          .entity(errorJson)
          .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON + "; charset=UTF-8")
          .build()
    }
  }

  @GET
  @Path("languages")
  @Produces(Array("application/json"))
  def getConfiguredLanguages(): Response = {
    Extraction.logger.info("REST endpoint getConfiguredLanguages called")

    try {
      val languages = Extraction.getConfiguredLanguages.map(_.wikiCode).toSeq.sorted
      val languagesJson = languages.map(lang => s""""$lang"""").mkString(",")

      val jsonString = s"""{
        "languages": [$languagesJson],
        "count": ${languages.size}
      }"""

      Response.ok(jsonString)
        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON + "; charset=UTF-8")
        .build()
    } catch {
      case e: IllegalStateException =>
        Extraction.logger.severe(s"Configuration error: ${e.getMessage}")
        val errorJson = s"""{"error": "${e.getMessage}"}"""
        Response.status(500)
          .entity(errorJson)
          .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON + "; charset=UTF-8")
          .build()
      case e: Exception =>
        Extraction.logger.severe(s"Unexpected error getting configured languages: ${e.getMessage}")
        val errorJson = s"""{"error": "Failed to get configured languages: ${e.getMessage}"}"""
        Response.status(500)
          .entity(errorJson)
          .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON + "; charset=UTF-8")
          .build()
    }
  }

  @GET
  @Produces(Array("application/xhtml+xml"))
  def get = {
    Extraction.logger.info(s"Web form GET called for language: $langCode")

    try {
      val extractors = Extraction.getAvailableExtractors(language)
      Extraction.logger.info(s"Web form got ${extractors.size} extractors for $langCode")

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
                <option value="custom">All Enabled Extractors</option>{
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
    } catch {
      case e: IllegalArgumentException =>
        // Language not enabled - return error page
        <html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en">
          {ServerHeader.getHeader("Configuration Error")}<body>
          <div class="row">
            <div class="col-md-6 col-md-offset-3">
              <h2>Configuration Error</h2>
              <div class="alert alert-danger">
                <strong>Error:</strong> {e.getMessage}
              </div>
              <p>Please check the server configuration and try again.</p>
            </div>
          </div>
        </body>
        </html>
      case e: IllegalStateException =>
        // Language enabled but no extractors - return error page
        <html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en">
          {ServerHeader.getHeader("Configuration Error")}<body>
          <div class="row">
            <div class="col-md-6 col-md-offset-3">
              <h2>Configuration Error</h2>
              <div class="alert alert-danger">
                <strong>Error:</strong> {e.getMessage}
              </div>
              <p>Please check the extractor configuration for this language.</p>
            </div>
          </div>
        </body>
        </html>
      case e: Exception =>
        // Unexpected error - return generic error page
        <html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en">
          {ServerHeader.getHeader("Server Error")}<body>
          <div class="row">
            <div class="col-md-6 col-md-offset-3">
              <h2>Server Error</h2>
              <div class="alert alert-danger">
                <strong>Error:</strong> Failed to load extraction interface: {e.getMessage}
              </div>
              <p>Please contact the administrator if this problem persists.</p>
            </div>
          </div>
        </body>
        </html>
    }
  }

  /**
   * Extracts a MediaWiki article
   */
  @GET
  @Path("extract")
  def extract(@QueryParam("title") title: String, @QueryParam("revid") @DefaultValue("-1") revid: Long, @QueryParam("format") format: String, @QueryParam("extractors") extractors: String, @Context headers: HttpHeaders): Response = {
    import scala.collection.JavaConverters._
    if (title == null && revid < 0) throw new WebApplicationException(new Exception("title or revid must be given"), Response.Status.NOT_FOUND)

    val requestedTypesList = headers.getAcceptableMediaTypes.asScala.map(_.toString).toList
    val browserMode = requestedTypesList.isEmpty || requestedTypesList.contains("text/html") || requestedTypesList.contains("application/xhtml+xml") || requestedTypesList.contains("text/plain")

    val writer = new StringWriter

    var finalFormat = format
    val acceptContentBest = requestedTypesList.map(selectFormatByContentType).headOption.getOrElse("unknownAcceptFormat")

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
    val extractorName = Option(extractors).getOrElse("mappings")
    Extraction.logger.info(s"Processing extraction request with extractor: '$extractorName' for language: '${language.wikiCode}'")

    // Validate extractor parameter using server configuration
    if (extractorName != "mappings" && extractorName != "custom") {
      if (!Extraction.isExtractorAvailable(language, extractorName)) {
        try {
          val availableExtractors = Extraction.getAvailableExtractors(language)
          val errorMsg = s"Unknown extractor: '$extractorName'. Available extractors for language '${language.wikiCode}': mappings, custom, ${availableExtractors.mkString(", ")}"
          throw new WebApplicationException(new Exception(errorMsg), 400)
        } catch {
          case e: IllegalArgumentException =>
            throw new WebApplicationException(new Exception(s"Language '${language.wikiCode}' is not enabled in configuration"), 404)
          case e: IllegalStateException =>
            throw new WebApplicationException(new Exception(s"Language '${language.wikiCode}' has no extractors configured"), 500)
        }
      }
    }

    val source =
      if (revid >= 0) WikiSource.fromRevisionIDs(List(revid), new URL(language.apiUri), language)
      else WikiSource.fromTitles(List(WikiTitle.parse(title, language)), new URL(language.apiUri), language)

    val destination = new DeduplicatingDestination(new WriterDestination(() => writer, formatter))

    try {
      extractorName match {
        case "mappings" =>
          Extraction.logger.info(s"Running mappings-only extraction for language '${language.wikiCode}'")
          Server.instance.extractor.extract(source, destination, language, false)

        case "custom" =>
          Extraction.logger.info(s"Running all enabled extractors extraction for language '${language.wikiCode}'")
          Server.instance.extractor.extract(source, destination, language, true)

        case specificExtractor =>
          Extraction.logger.info(s"Running specific extractor '$specificExtractor' for language '${language.wikiCode}'")
          Server.instance.extractWithSpecificExtractor(source, destination, language, specificExtractor)
      }
    } catch {
      case e: IllegalArgumentException =>
        throw new WebApplicationException(new Exception(e.getMessage), 400)
      case e: IllegalStateException =>
        throw new WebApplicationException(new Exception(e.getMessage), 500)
      case e: WebApplicationException =>
        throw e
      case e: Exception =>
        val errorMsg = s"Extraction failed for language '${language.wikiCode}' with extractor '$extractorName': ${e.getMessage}"
        Extraction.logger.severe(errorMsg)
        throw new WebApplicationException(new Exception(errorMsg), 500)
    }

    val result = writer.toString
    Extraction.logger.info(s"Extraction completed, output length: ${result.length}")

    Response.ok(result)
      .header(HttpHeaders.CONTENT_TYPE, contentType + "; charset=UTF-8")
      .build()
  }

  private def selectFormatByContentType(format: String): String = {
    format match {
      case "text/xml" => "trix"
      case "text/turtle" => "turtle-triples"
      //case "text/nquads" => "turtle-quads" // this does not exist as mimetype
      case "application/n-triples" => "n-triples"
      case "application/n-quads" => "n-quads"
      case MediaType.APPLICATION_JSON => "rdf-json"
      //case "application/ld+json" => MediaType.APPLICATION_JSON
      case _ => "unknownAcceptFormat"
    }
  }

  // override content type in browser for some formats to display text instead of downloading a file, or
  private def selectInBrowserContentType(format: String): String = {

    format match
    {
      case "trix" => MediaType.APPLICATION_XML
      case "rdf-json" => MediaType.APPLICATION_JSON
      case _ => MediaType.TEXT_PLAIN
    }
  }

  // map format parameters to regular content types
    private def selectContentType(format: String): String = {

      format match
      {
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
    def extract(xml : Elem) =
    {
        val writer = new StringWriter
        val formatter = TriX.writeHeader(writer, 2)
        val source = XMLSource.fromXML(xml, language)
        val destination = new WriterDestination(() => writer, formatter)

        Server.instance.extractor.extract(source, destination, language)

        writer.toString
    }
}
