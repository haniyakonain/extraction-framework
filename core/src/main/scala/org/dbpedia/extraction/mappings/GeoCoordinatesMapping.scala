package org.dbpedia.extraction.mappings

import org.dbpedia.extraction.config.provenance.DBpediaDatasets
import org.dbpedia.extraction.transform.Quad
import org.dbpedia.extraction.wikiparser.{PropertyNode, TemplateNode}
import org.dbpedia.extraction.dataparser._
import java.util.logging.{Logger, Level}
import org.dbpedia.extraction.ontology.{Ontology, OntologyProperty}
import org.dbpedia.extraction.util.Language
import scala.collection.mutable.ArrayBuffer
import scala.language.reflectiveCalls

/**
 * Extracts geo-coodinates.
 */
class GeoCoordinatesMapping(
  val ontologyProperty : OntologyProperty,
  val coordinates : String,
  val latitude : String,
  val longitude : String,
  val longitudeDegrees : String,
  val longitudeMinutes : String,
  val longitudeSeconds : String,
  val longitudeDirection : String,
  val latitudeDegrees : String,
  val latitudeMinutes : String,
  val latitudeSeconds : String,
  val latitudeDirection : String,
  context : {
    def ontology : Ontology
    def redirects : Redirects
    def language : Language
  }
) extends PropertyMapping {

  private val logger = Logger.getLogger(classOf[GeoCoordinatesMapping].getName)

  private val geoCoordinateParser = new GeoCoordinateParser(context)
  private val singleGeoCoordinateParser = new SingleGeoCoordinateParser(context)
  private val doubleParser = new DoubleParser(context)
  private val doubleParserEn = new DoubleParser(context = new { def language : Language = Language("en") })
  private val stringParser = StringParser
  private val wikiCode = context.language.wikiCode

  private val typeOntProperty = context.ontology.properties("rdf:type")
  private val latOntProperty = context.ontology.properties("geo:lat")
  private val lonOntProperty = context.ontology.properties("geo:long")
  private val pointOntProperty = context.ontology.properties("georss:point")
  private val featureOntClass = context.ontology.classes("geo:SpatialThing")

  // ✅ NEW hemisphere properties
  private val latnsOntProperty = context.ontology.properties("dbp:latns")
  private val longewOntProperty = context.ontology.properties("dbp:longew")

  override val datasets = Set(DBpediaDatasets.OntologyPropertiesGeo)

  override def extract(node: TemplateNode, subjectUri: String): Seq[Quad] = {
    extractGeoCoordinate(node) match {
      case Some(coord) => writeGeoCoordinate(node, coord, subjectUri, node.sourceIri)
      case None => Seq.empty
    }
  }

  private def extractGeoCoordinate(node: TemplateNode): Option[GeoCoordinate] = {
    // case 1: coordinates set (all coordinates in one template property)
    if (coordinates != null) {
      for (
        coordProperty <- node.property(coordinates);
        geoCoordinate <- geoCoordinateParser.parse(coordProperty)
      ) {
        return Some(geoCoordinate.value)
      }
    }

    // case 2: latitude and longitude set
    if (latitude != null && longitude != null) {
      for (
        latitudeProperty <- node.property(latitude);
        longitudeProperty <- node.property(longitude);
        lat <- getSingleCoordinate(latitudeProperty, -90.0, 90.0, wikiCode);
        lon <- getSingleCoordinate(longitudeProperty, -180.0, 180.0, wikiCode)
      ) {
        try {
          return Some(new GeoCoordinate(lat, lon))
        } catch {
          case ex: IllegalArgumentException =>
            logger.log(Level.FINE, "Invalid geo coordinate", ex)
            return None
        }
      }
    }

    // case 3: DMS-style coordinates
    if (longitudeDegrees != null && latitudeDegrees != null) {
      for (
        latDegProperty <- node.property(latitudeDegrees);
        lonDegProperty <- node.property(longitudeDegrees);
        latDeg <- doubleParser.parse(latDegProperty);
        lonDeg <- doubleParser.parse(lonDegProperty)
      ) {
        val latMin = node.property(latitudeMinutes).flatMap(doubleParser.parse).getOrElse(ParseResult(0.0)).value
        val latSec = node.property(latitudeSeconds).flatMap(doubleParser.parse).getOrElse(ParseResult(0.0)).value
        val latDir = node.property(latitudeDirection).flatMap(stringParser.parse).getOrElse(ParseResult("N")).value

        val lonMin = node.property(longitudeMinutes).flatMap(doubleParser.parse).getOrElse(ParseResult(0.0)).value
        val lonSec = node.property(longitudeSeconds).flatMap(doubleParser.parse).getOrElse(ParseResult(0.0)).value
        val lonDir = node.property(longitudeDirection).flatMap(stringParser.parse).getOrElse(ParseResult("E")).value

        try {
          return Some(new GeoCoordinate(latDeg.value, latMin, latSec, latDir, lonDeg.value, lonMin, lonSec, lonDir, false))
        } catch {
          case ex: IllegalArgumentException =>
            logger.log(Level.FINE, "Invalid geo coordinate", ex)
            return None
        }
      }
    }

    None
  }

  private def writeGeoCoordinate(node: TemplateNode, coord: GeoCoordinate, subjectUri: String, sourceUri: String): Seq[Quad] = {
    var quads = new ArrayBuffer[Quad]()
    var instanceUri = subjectUri

    if (ontologyProperty != null) {
      instanceUri = node.generateUri(subjectUri, ontologyProperty.name)
      quads += new Quad(context.language, DBpediaDatasets.OntologyPropertiesGeo, subjectUri, ontologyProperty, instanceUri, sourceUri)
    }

    quads += new Quad(context.language, DBpediaDatasets.OntologyPropertiesGeo, instanceUri, typeOntProperty, featureOntClass.uri, sourceUri)
    quads += new Quad(context.language, DBpediaDatasets.OntologyPropertiesGeo, instanceUri, latOntProperty, coord.latitude.toString, sourceUri)
    quads += new Quad(context.language, DBpediaDatasets.OntologyPropertiesGeo, instanceUri, lonOntProperty, coord.longitude.toString, sourceUri)
    quads += new Quad(context.language, DBpediaDatasets.OntologyPropertiesGeo, instanceUri, pointOntProperty, coord.latitude + " " + coord.longitude, sourceUri)

    // ✅ Add hemisphere indicators
    val latHemisphere = if (coord.latitude < 0) "S" else "N"
    val lonHemisphere = if (coord.longitude < 0) "W" else "E"

    quads += new Quad(context.language, DBpediaDatasets.OntologyPropertiesGeo, instanceUri, latnsOntProperty, latHemisphere, sourceUri)
    quads += new Quad(context.language, DBpediaDatasets.OntologyPropertiesGeo, instanceUri, longewOntProperty, lonHemisphere, sourceUri)

    quads
  }

  private def getSingleCoordinate(coordinateProperty: PropertyNode, rangeMin: Double, rangeMax: Double, wikiCode: String): Option[Double] = {
    singleGeoCoordinateParser.parse(coordinateProperty).map(_.value.toDouble)
      .orElse(doubleParser.parse(coordinateProperty).map(_.value)) match {
      case Some(coordinateValue) =>
        if (rangeMin <= coordinateValue && coordinateValue <= rangeMax) {
          Some(coordinateValue)
        } else if (!wikiCode.equals("en")) {
          doubleParserEn.parse(coordinateProperty) match {
            case Some(enCoordinateValue) =>
              if (rangeMin <= enCoordinateValue.value && enCoordinateValue.value <= rangeMax) {
                Some(enCoordinateValue.value)
              } else None
            case None => None
          }
        } else None
      case None => None
    }
  }
}
