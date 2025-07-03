package org.dbpedia.extraction.dataparser

import org.dbpedia.extraction.mappings.Redirects
import org.dbpedia.extraction.ontology.datatypes.Datatype
import org.scalatest.Matchers
import org.scalatest.FlatSpec
import org.dbpedia.extraction.wikiparser.{WikiPage, WikiTitle, WikiParser}
import org.dbpedia.extraction.sources.MemorySource
import org.dbpedia.extraction.util.Language
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class GeoCoordinateParserTest extends FlatSpec with Matchers
{
  // === DISTINCT COORDINATE FORMAT PATTERNS ===

  // Pattern: degrees°minutes'seconds"direction (non-template format)
  "GeoCoordinateParser - DMS with symbols" should "return (51.2, 3.216666666666667)" in {
    parse("fr", "51º12'00\"N 03º13'00\"E") should equal (Some(51.2, 3.216666666666667))
  }

  // Pattern: {{coord|decimal|direction|decimal|direction}} (2 coordinate pairs)
  "GeoCoordinateParser - decimal degrees with cardinal directions" should "return (51.2, 31.2)" in {
    parse("fr", "{{coord|51.2|N|31.2|E}}") should equal (Some(51.2, 31.2))
  }

  // Pattern: {{coord|degrees|minutes|direction|degrees|minutes|direction}} (4 parameters)
  "GeoCoordinateParser - degrees and minutes only" should "return (51.2, 3.216666666666667)" in {
    parse("fr", "{{coord|51|12|N|03|13|E}}") should equal (Some(51.2, 3.216666666666667))
  }

  // Pattern: {{coord|degrees|minutes|seconds|direction|degrees|minutes|seconds|direction}} (6 parameters)
  "GeoCoordinateParser - degrees minutes seconds" should "return (-34.833333333333336, 20.0)" in {
    parse("en", "{{coord|34|50|0|S|20|0|0|E}}") should equal (Some(-34.833333333333336, 20.0))
  }

  // Pattern: {{coord|degrees|minutes|decimal_seconds|direction|degrees|minutes|decimal_seconds|direction}} (6 parameters with decimal seconds)
  "GeoCoordinateParser - DMS with decimal seconds" should "return (-33.92486111111111, 18.424055555555555)" in {
    parse("en", "{{coord|33|55|29.5|S|18|25|26.6|E}}") should equal (Some(-33.92486111111111, 18.424055555555555))
  }

  // Pattern: {{coord|decimal|direction|decimal|direction|additional_params}} (with ignored parameters)
  "GeoCoordinateParser - coordinates with ignored parameters" should "return (51.477, 0.0)" in {
    parse("en", "{{coord|51.477|N|0.0|E|display=title|type:city|region:GB}}") should equal (Some(51.477, 0.0))
  }

  // === QUADRANT VARIATIONS ===

  // West longitude (negative)
  "GeoCoordinateParser - west longitude" should "return (43.7, -79.42)" in {
    parse("en", "{{coord|43.7|N|79.42|W}}") should equal (Some(43.7, -79.42))
  }

  // South latitude (negative)
  "GeoCoordinateParser - south latitude" should "return (-30.5595, 22.9375)" in {
    parse("en", "{{coord|30.5595|S|22.9375|E}}") should equal (Some(-30.5595, 22.9375))
  }

  // Southwest quadrant (both negative)
  "GeoCoordinateParser - southwest quadrant" should "return (-33.45, -70.66)" in {
    parse("en", "{{coord|33.45|S|70.66|W}}") should equal (Some(-33.45, -70.66))
  }

  // === EDGE CASES ===

  // Equator and Prime Meridian
  "GeoCoordinateParser - equator and prime meridian" should "return (0.0, 0.0)" in {
    parse("en", "{{coord|0.0|N|0.0|E}}") should equal (Some(0.0, 0.0))
  }

  // High precision coordinates
  "GeoCoordinateParser - high precision decimal" should "return (40.748817, -73.985428)" in {
    parse("en", "{{coord|40.748817|N|73.985428|W}}") should equal (Some(40.748817, -73.985428))
  }

  // Near pole coordinates
  "GeoCoordinateParser - arctic coordinates" should "return (89.9, -135.0)" in {
    parse("en", "{{coord|89.9|N|135.0|W}}") should equal (Some(89.9, -135.0))
  }

  // === TEMPLATE VARIATIONS ===

  // Capitalized template name
  "GeoCoordinateParser - capitalized Coord template" should "return (51.5074, -0.1278)" in {
    parse("en", "{{Coord|51.5074|N|0.1278|W}}") should equal (Some(51.5074, -0.1278))
  }

  // Alternative coordinate templates
  "GeoCoordinateParser - coor template" should "return (48.8566, 2.3522)" in {
    parse("en", "{{coor|48.8566|N|2.3522|E}}") should equal (Some(48.8566, 2.3522))
  }

  "GeoCoordinateParser - coordonnées template" should "return (45.7640, 4.8357)" in {
    parse("fr", "{{coordonnées|45.7640|N|4.8357|E}}") should equal (Some(45.7640, 4.8357))
  }

  "GeoCoordinateParser - coordinaten template" should "return (52.3702, 4.8952)" in {
    parse("nl", "{{coordinaten|52.3702|N|4.8952|E}}") should equal (Some(52.3702, 4.8952))
  }

  "GeoCoordinateParser - unsupported coordinate template" should "return None" in {
    parse("en", "{{geopoint|40.4165|N|3.7026|W}}") should equal (None)
  }

  // === LANGUAGE-SPECIFIC DIRECTION MAPPINGS ===

  // Arabic - testing Arabic directions
  "GeoCoordinateParser - Arabic directions" should "return (24.7136, 46.6753)" in {
    parse("ar", "{{coord|24.7136|شمال|46.6753|شرق}}") should equal (Some(24.7136, 46.6753))
  }

  // Arabic - testing Arabic south/west
  "GeoCoordinateParser - Arabic south west" should "return (-15.2993, -28.0473)" in {
    parse("ar", "{{coord|15.2993|جنوب|28.0473|غرب}}") should equal (Some(-15.2993, -28.0473))
  }

  // Bulgarian - testing Cyrillic directions
  "GeoCoordinateParser - Bulgarian Cyrillic directions" should "return (42.6977, 23.3219)" in {
    parse("bg", "{{coord|42.6977|С|23.3219|И}}") should equal (Some(42.6977, 23.3219))
  }

  // Czech - testing Czech directions with V for East
  "GeoCoordinateParser - Czech directions" should "return (50.0755, 14.4378)" in {
    parse("cs", "{{coord|50.0755|N|14.4378|V}}") should equal (Some(50.0755, 14.4378))
  }

  // German - testing German O for East
  "GeoCoordinateParser - German directions" should "return (52.5200, 13.4050)" in {
    parse("de", "{{coord|52.5200|N|13.4050|O}}") should equal (Some(52.5200, 13.4050))
  }

  // Spanish - testing Spanish O for West
  "GeoCoordinateParser - Spanish directions" should "return (40.4168, -3.7038)" in {
    parse("es", "{{coord|40.4168|N|3.7038|O}}") should equal (Some(40.4168, -3.7038))
  }

  // French - testing French O for East (opposite of Spanish)
  "GeoCoordinateParser - French O for East" should "return (45.7640, 4.8357)" in {
    parse("fr", "{{coord|45.7640|N|4.8357|O}}") should equal (Some(45.7640, 4.8357))
  }

  // Hindi - testing Hindi Devanagari directions
  "GeoCoordinateParser - Hindi directions" should "return (28.7041, 77.1025)" in {
    parse("hi", "{{coord|28.7041|उ|77.1025|पू}}") should equal (Some(28.7041, 77.1025))
  }

  // Italian - testing Italian O for West
  "GeoCoordinateParser - Italian O for West" should "return (45.4642, -9.1900)" in {
    parse("it", "{{coord|45.4642|N|9.1900|O}}") should equal (Some(45.4642, -9.1900))
  }

  // Japanese - testing Japanese directions
  "GeoCoordinateParser - Japanese directions" should "return (35.6762, 139.6503)" in {
    parse("ja", "{{coord|35.6762|北|139.6503|東}}") should equal (Some(35.6762, 139.6503))
  }

  // Dutch - testing Dutch O for East
  "GeoCoordinateParser - Dutch directions" should "return (52.3702, 4.8952)" in {
    parse("nl", "{{coord|52.3702|N|4.8952|O}}") should equal (Some(52.3702, 4.8952))
  }

  // Polish - testing Polish abbreviations
  "GeoCoordinateParser - Polish abbreviations" should "return (50.0647, 19.9450)" in {
    parse("pl", "{{coord|50.0647|płn|19.9450|wsch}}") should equal (Some(50.0647, 19.9450))
  }

  // Portuguese - testing Portuguese O for West
  "GeoCoordinateParser - Portuguese directions" should "return (-23.5505, -46.6333)" in {
    parse("pt", "{{coord|23.5505|S|46.6333|O}}") should equal (Some(-23.5505, -46.6333))
  }

  // Russian - testing Russian Cyrillic В for East
  "GeoCoordinateParser - Russian Cyrillic directions" should "return (55.7558, 37.6176)" in {
    parse("ru", "{{coord|55.7558|С|37.6176|В}}") should equal (Some(55.7558, 37.6176))
  }

  // Chinese - testing Chinese directions
  "GeoCoordinateParser - Chinese directions" should "return (39.9042, 116.4074)" in {
    parse("zh", "{{coord|39.9042|北|116.4074|东}}") should equal (Some(39.9042, 116.4074))
  }

  // Chinese - testing traditional Chinese
  "GeoCoordinateParser - Chinese traditional characters" should "return (39.9042, 116.4074)" in {
    parse("zh", "{{coord|39.9042|北|116.4074|東}}") should equal (Some(39.9042, 116.4074))
  }

  // Korean - testing Korean directions
  "GeoCoordinateParser - Korean directions" should "return (37.5665, 126.9780)" in {
    parse("ko", "{{coord|37.5665|북|126.9780|동}}") should equal (Some(37.5665, 126.9780))
  }

  // Korean - testing Korean hanja
  "GeoCoordinateParser - Korean hanja directions" should "return (37.5665, 126.9780)" in {
    parse("ko", "{{coord|37.5665|北|126.9780|東}}") should equal (Some(37.5665, 126.9780))
  }

  // === AFRICA DIRECTION MAPPINGS ===

  // Swahili - testing Swahili directions
  "GeoCoordinateParser - Swahili directions" should "return (-1.2921, 36.8219)" in {
    parse("sw", "{{coord|1.2921|S|36.8219|E}}") should equal (Some(-1.2921, 36.8219))
  }

  // Amharic - testing Ethiopian directions
  "GeoCoordinateParser - Amharic directions" should "return (9.1450, 40.4897)" in {
    parse("am", "{{coord|9.1450|N|40.4897|E}}") should equal (Some(9.1450, 40.4897))
  }

  // Afrikaans - testing South African directions
  "GeoCoordinateParser - Afrikaans directions" should "return (-33.9249, 18.4241)" in {
    parse("af", "{{coord|33.9249|S|18.4241|O}}") should equal (Some(-33.9249, 18.4241))
  }

  // === MALFORMED COORDINATE TESTS ===

  // Missing direction indicators
  "GeoCoordinateParser - missing direction indicators" should "return None" in {
    parse("en", "{{coord|40.7589|73.9851}}") should equal (None)
  }

  // Invalid direction indicators
  "GeoCoordinateParser - invalid direction indicators" should "return None" in {
    parse("en", "{{coord|40.7589|X|73.9851|Y}}") should equal (None)
  }

  // Too many parameters
  "GeoCoordinateParser - too many coordinate parameters" should "return None" in {
    parse("en", "{{coord|40|30|15|10|N|73|58|30|20|W}}") should equal (None)
  }

  // Empty coordinate template
  "GeoCoordinateParser - empty coordinate template" should "return None" in {
    parse("en", "{{coord}}") should equal (None)
  }

  // === FALLBACK TESTS FOR INVALID DIRECTIONS ===

  // Arabic invalid directions
  "GeoCoordinateParser - Arabic invalid directions" should "return None" in {
    parse("ar", "{{coord|24.7136|شمالي|46.6753|شرقي}}") should equal (None)
  }

  // German invalid directions
  "GeoCoordinateParser - German invalid directions" should "return None" in {
    parse("de", "{{coord|52.5200|Nord|13.4050|Ost}}") should equal (None)
  }

  // Spanish invalid directions
  "GeoCoordinateParser - Spanish invalid directions" should "return None" in {
    parse("es", "{{coord|40.4168|Norte|3.7038|Este}}") should equal (None)
  }

  // French invalid directions
  "GeoCoordinateParser - French invalid directions" should "return None" in {
    parse("fr", "{{coord|48.8566|Nord|2.3522|Est}}") should equal (None)
  }

  // Russian invalid directions
  "GeoCoordinateParser - Russian invalid directions" should "return None" in {
    parse("ru", "{{coord|55.7558|Север|37.6173|Восток}}") should equal (None)
  }

  // Chinese invalid directions
  "GeoCoordinateParser - Chinese invalid directions" should "return None" in {
    parse("zh", "{{coord|39.9042|北方|116.4074|东方}}") should equal (None)
  }

  // Japanese invalid directions
  "GeoCoordinateParser - Japanese invalid directions" should "return None" in {
    parse("ja", "{{coord|35.6762|北方|139.6503|東方}}") should equal (None)
  }

  // Korean invalid directions
  "GeoCoordinateParser - Korean invalid directions" should "return None" in {
    parse("ko", "{{coord|37.5665|북쪽|126.9780|동쪽}}") should equal (None)
  }

  // Polish invalid directions
  "GeoCoordinateParser - Polish invalid directions" should "return None" in {
    parse("pl", "{{coord|52.2297|Północ|21.0122|Wschód}}") should equal (None)
  }

  // Bulgarian invalid directions
  "GeoCoordinateParser - Bulgarian invalid directions" should "return None" in {
    parse("bg", "{{coord|42.6977|Север|23.3219|Изток}}") should equal (None)
  }

  // Czech invalid directions
  "GeoCoordinateParser - Czech invalid directions" should "return None" in {
    parse("cs", "{{coord|50.0755|Sever|14.4378|Východ}}") should equal (None)
  }

  // Dutch invalid directions
  "GeoCoordinateParser - Dutch invalid directions" should "return None" in {
    parse("nl", "{{coord|52.3702|Noord|4.8952|Oosten}}") should equal (None)
  }

  // Italian invalid directions
  "GeoCoordinateParser - Italian invalid directions" should "return None" in {
    parse("it", "{{coord|41.9028|Nord|12.4964|Est}}") should equal (None)
  }

  // Portuguese invalid directions
  "GeoCoordinateParser - Portuguese invalid directions" should "return None" in {
    parse("pt", "{{coord|38.7223|Norte|9.1393|Oeste}}") should equal (None)
  }

  // === ADDITIONAL TESTS FOR DBPEDIA EXTRACTION LANGUAGES ===

// English - United States (Washington DC coordinates)
"GeoCoordinateParser - English United States Washington DC" should "return (38.8833, -77.0167)" in {
  parse("en", "{{coord|38.8833|N|77.0167|W}}") should equal (Some(38.8833, -77.0167))
}

// German - Vereinigte Staaten (Geographic center coordinates)
"GeoCoordinateParser - German United States geographic center" should "return (40.0, -100.0)" in {
  parse("de", "{{coord|40.0|N|100.0|W}}") should equal (Some(40.0, -100.0))
}

// French - États-Unis (Geographic center variant)
"GeoCoordinateParser - French United States geographic variant" should "return (40.0, -105.0)" in {
  parse("fr", "{{coord|40.0|N|105.0|W}}") should equal (Some(40.0, -105.0))
}

// Serbian - Сједињене Америчке Државе (Washington DC coordinates)
"GeoCoordinateParser - Serbian United States Washington DC" should "return (38.8833, -77.0167)" in {
  parse("sr", "{{coord|38.8833|N|77.0167|W}}") should equal (Some(38.8833, -77.0167))
}

// Slovenian - Združene države Amerike (Washington DC variant)
"GeoCoordinateParser - Slovenian United States Washington DC variant" should "return (38.8833, -77.033)" in {
  parse("sl", "{{coord|38.8833|N|77.033|W}}") should equal (Some(38.8833, -77.033))
}

// Swedish - USA (Geographic center coordinates)
"GeoCoordinateParser - Swedish USA geographic center" should "return (40.0, -100.0)" in {
  parse("sv", "{{coord|40.0|N|100.0|W}}") should equal (Some(40.0, -100.0))
}

// Belarusian - Злучаныя Штаты Амерыкі (Geographic center coordinates)
"GeoCoordinateParser - Belarusian United States geographic center" should "return (40.0, -100.0)" in {
  parse("be", "{{coord|40.0|N|100.0|W}}") should equal (Some(40.0, -100.0))
}

// Greek - Ηνωμένες Πολιτείες Αμερικής (Geographic center coordinates)
"GeoCoordinateParser - Greek United States geographic center" should "return (40.0, -100.0)" in {
  parse("el", "{{coord|40.0|N|100.0|W}}") should equal (Some(40.0, -100.0))
}

  private val wikiParser = WikiParser.getInstance()

  private def parse(language : String, input : String) : Option[(Double, Double)] =
  {
    val lang = Language(language)
    val context = new
      {
        def language : Language = lang
        def redirects : Redirects = new Redirects(Map())
      }
    val geoCoordinateParser = new GeoCoordinateParser(context)
    val page = new WikiPage(WikiTitle.parse("TestPage", lang), input)

    wikiParser(page) match
    {
      case Some(n) => geoCoordinateParser.parse(n).map({x => (x.value.latitude, x.value.longitude)})
      case None => None
    }
  }
}
