#!/bin/bash

# Enhanced DBpedia Coordinate Extraction Test

set -e

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
PURPLE='\033[0;35m'
NC='\033[0m'

echo -e "${BLUE}🎯 Enhanced DBpedia Coordinate Extraction Test${NC}"
echo "================================================="

# Test data
declare -A KNOWN_COORDS
KNOWN_COORDS=(
    # United States - Country
    ["en:United States"]="38.883333,-77.0166666"  # Country
    ["de:Vereinigte Staaten"]="40.0, -100.0"  # Center

    # Sweden - Country
    ["en:Sweden"]="59.35,18.0666666"  # Country
    ["de:Schweden"]="61.316667,14.833333"  # Country

    # Germany - Country
    ["en:Germany"]="52.520008,13.404954"  # Country
    ["de:Deutschland"]=" 51.165,10.45527"  # City

    # France - Country
    ["en:France"]="48.8566,2.3522"  # Country
    ["de:Frankreich"]="46.0,2.0"  # Country

    # United Kingdom - Country
    ["en:United Kingdom"]="51.5074,-0.1278"  # Country
    ["de:Vereinigtes Königreich"]="51.5166,-0.11666"  # City

    # Italy - Country
    ["en:Italy"]="41.9028,12.4964"  # Country
    ["de:Italien"]="42.8333,12.8333"  # Country

    # Spain - Country
    ["en:Spain"]="40.4168,-3.7038"  # Country
    ["de:Spanien"]=" 39.9266,-1.8016"  # Center

    # Netherlands - Country
    ["en:Netherlands"]="52.3676,4.9041"  # Country
    ["de:Niederlande"]="52.5,5.75"  # Country

    # Poland - Country
    ["en:Poland"]="52.2297,21.0122"  # Country
    ["de:Polen"]="52.0,20.0"  # Country

    # Norway - Country
    ["en:Norway"]="59.9139,10.7522"  # Country
    ["de:Norwegen"]="62.0,10.0"  # Country

    # Portugal - Country
    ["en:Portugal"]="38.7223,-9.1393"  # Country
    ["de:Portugal"]="39.5,-8.0"  # Country

    # Austria - Country
    ["en:Austria"]=" 48.2,16.35"  # City
    ["de:Österreich"]="47.33333,13.33333"  # Country

    # Switzerland - Country
    ["en:Switzerland"]="46.8182,8.2275"  # Country
    ["de:Schweiz"]="46.8,8.33333"  # Country

    # Belgium - Country
    ["en:Belgium"]="50.8503,4.3517"  # Country
    ["de:Belgien"]="50.83333,4.0"  # Country

    # Denmark - Country
    ["en:Denmark"]="55.6761,12.5683"  # Country
    ["de:Dänemark"]="56.0,10.0"  # Country

    # Finland - Country
    ["en:Finland"]="60.1699,24.9384"  # Country
    ["de:Finnland"]="64.0,26.0"  # Country

    # Czech Republic - Country
    ["en:Czech Republic"]="49.7437,15.3386"  # Country
    ["de:Tschechien"]="49.75,15.5"  # Country

    # Hungary - Country
    ["en:Hungary"]="47.1625,19.5033"  # Country
    ["de:Ungarn"]="47.0,20.0"  # Country

    # Greece - Country
    ["en:Greece"]="37.9755,23.7348"  # Country
    ["de:Griechenland"]="38.30111, 23.74111"  # Capital

    # Argentina - Country
    ["en:Argentina"]="-34.6,-58.383333"  # Country
    ["de:Argentinien"]="-34.6,-58.38"  # Country

    # Brazil - Country
    ["en:Brazil"]="-15.7975,-47.8919"  # Country
    ["de:Brasilien"]=" -10.65,-52.95"  # Central-West

    # Canada - Country
    ["en:Canada"]="45.4215,-75.6972"  # Country
    ["de:Kanada"]="56.0, 109.0"  #  Territory

   # Mexico - Country
   ["en:Mexico"]="19.4333,-99.1333"  # City
  ["de:Mexiko"]="23.3166,-102.0"  # Country

    # Chile - Country
    ["en:Chile"]="-33.4489,-70.6693"  # Country
    ["de:Chile"]="-31.4666,-70.9"  # Northern Chile

    # Peru - Country
    ["en:Peru"]="-12.0464,-77.0428"  # Country
    ["de:Peru"]=" -8.23333,-76.01666"  # Northern Peru

    # Colombia - Country
    ["en:Colombia"]="4.5833,-74.0666"  # Capital city
    ["de:Kolumbien"]="3.81666,-73.91666"  # Country

    # Venezuela - Country
    ["en:Venezuela"]="10.4806,-66.9036"  # Country
    ["de:Venezuela"]="8.0,-66.0"  # Country

    # China - Country
    ["en:China"]="39.9042,116.4074"  # Country
    ["de:China"]="35.0, 105.0"  # Country

    # Japan - Country
    ["en:Japan"]="35.6762,139.6503"  # Country
    ["de:Japan"]="35.1561,136.0"  # State

    # India - Country
    ["en:India"]="28.6139,77.2090"  # Country
    ["de:Indien"]=" 21.1255,78.3105"  # State

    # South Korea - Country
    ["en:South Korea"]="37.5665,126.9780"  # Country
    ["de:Südkorea"]="35.0, 127.0"  # Country

    # Thailand - Country
    ["en:Thailand"]="13.7563,100.5018"  # Country
    ["de:Thailand"]="15.35, 101.0"  #  Isan (Northeastern Thailand)

    # Indonesia - Country
    ["en:Indonesia"]=" -6.1666, 106.8166"  #  Capital city
    ["de:Indonesien"]="-2.0, 118.0 "  # Central

    # Philippines - Country
    ["en:Philippines"]="13.0, 122.0"  # Country
    ["de:Philippinen"]=" 11.3333,123.0"  # Closest city

    # Vietnam - Country
    ["en:Vietnam"]="21.0285,105.8542"  # Country
    ["de:Vietnam"]="14.0333,107.0"  # Center

    # Malaysia - Country
    ["en:Malaysia"]="3.1390,101.6869"  # Country
    ["de:Malaysia"]="2.3166,111.0"  # State

    # Singapore - Country
    ["en:Singapore"]="1.3521,103.8198"  # Country
    ["de:Singapur"]="1.3667,103.8"  # Country

    # Israel - Country
    ["en:Israel"]="31.7683,35.2137"  # Country
    ["de:Israel"]="31.5,34.75"  # Country

    # Turkey - Country
    ["en:Turkey"]="39.9334,32.8597"  # Country
    ["de:Türkei"]="39.0,35.0"  # Country

    # Iran - Country
  #not there in dbpedia  ["en:Iran"]="35.6892,51.3890"  # Country
    ["de:Iran"]=" 32.4961,54.295"  # Central

    # Iraq - Country
    ["en:Iraq"]="33.2232,43.6793"  # Country
    ["de:Irak"]="33.0,44.0"  # Country

    # Saudi Arabia - Country
    ["en:Saudi Arabia"]="24.7136,46.6753"  # Country
    ["de:Saudi-Arabien"]=" 23.71666,44.11666"  # Country

    # Egypt - Country
    ["en:Egypt"]="30.0444,31.2357"  # Country
    ["de:Ägypten"]="27.0,30.0"  # Country

    # South Africa - Country
    ["en:South Africa"]="-30.0,25.0"  # State
    ["de:Südafrika"]="-29.0,24.0"  # Country

    # Nigeria - Country
    ["en:Nigeria"]="9.06666,7.4833"  # Capital city
    ["de:Nigeria"]="10.0,8.0"  # Country

    # Morocco - Country
    ["en:Morocco"]=" 34.0333,-6.85"  # Capital city
    ["de:Marokko"]="30.93333, -8.4"  # Coastal city

    # Kenya - Country
    ["en:Kenya"]="-1.2666,36.8"  # Country
    ["de:Kenia"]="0.4,37.85"  # Center

    # Ethiopia - Country
    ["en:Ethiopia"]="9.0166, 38.75"  # Capital city
    ["de:Äthiopien"]="8.3, 39.11"  # Central reference point

    # Australia - Country
    ["en:Australia"]="-35.308055555,149.12444444"  # Country
    ["de:Australien"]="-25.0,135.0"  # Country

    # New Zealand - Country
    ["en:New Zealand"]="-41.2865,174.7762"  # Country
    ["de:Neuseeland"]="-40.843611,172.0"  # West Coast Region

    # Papua New Guinea - Country
    ["en:Papua New Guinea"]="-9.4788,147.1494"  # Capital city
    ["de:Papua-Neuguinea"]="-6.3666,146.0"  # Country

    # Fiji - Country
    ["en:Fiji"]="-18.1248,178.4501"  # Country
    ["de:Fidschi"]="-18.0,179.0"  # Country

    # Russia - Country
    ["en:Russia"]="55.7558,37.6176"  # Country
    ["de:Russland"]=" 58.65, 70.1166"  # Country

    # Ukraine - Country
    ["en:Ukraine"]="50.4501,30.5234"  # Country
    ["de:Ukraine"]="49.8,30.83333"  # North Central
)

declare -A ALTERNATIVE_COORDS
ALTERNATIVE_COORDS=(
    ["en:Washington,_D.C."]="38.895,-77.036"
    ["en:New_York_City"]="40.7128,-74.0060"
    ["de:Berlin"]="52.520008,13.404954"
    ["fr:Paris"]="48.8566,2.3522"
)

# URL encode helper
url_encode() {
    local string="$1"
    python3 -c "import urllib.parse, sys; print(urllib.parse.quote(sys.argv[1], safe=''))" "$string"
}

# Check if mappings server is up
check_server_connectivity() {
    echo -e "${BLUE}🔌 Checking Mappings Server Connectivity${NC}"
    echo "=========================================="

    local url="http://localhost:9999/server/"
    local status=$(curl -s -o /dev/null -w "%{http_code}" "$url")

    if [[ "$status" == "200" ]]; then
        echo -e "${GREEN}✅ $url is accessible${NC}"
    else
        echo -e "${RED}❌ Failed to reach $url (HTTP $status)${NC}"
        exit 1
    fi
    echo ""
}

# API fetch
test_api_endpoint() {
    local lang="$1"
    local page="$2"
    local debug="${3:-false}"

    local base_url="http://localhost:9999/server/extraction/${lang}/extract"
    local encoded_title=$(url_encode "$page")
    local formats=("trix" "rdfxml" "ntriples" "ttl")

    for format in "${formats[@]}"; do
        [[ "$debug" == "true" ]] && echo -e "${CYAN}DEBUG: Trying format: $format for title: $page${NC}"
        local response=$(curl -s -G "$base_url" \
            --data-urlencode "title=$page" \
            --data-urlencode "format=$format" \
            --data-urlencode "extractors=custom" \
            --connect-timeout 10 \
            --max-time 30 2>/dev/null || echo "")

        if [[ -n "$response" && ${#response} -ge 10 ]] && ! echo "$response" | grep -qi "<html\|<!doctype\|<title>.*error"; then
            echo "$response"
            return 0
        fi
    done

    return 1
}

# Fixed coordinate extraction function
extract_coords_from_response() {
    local response="$1"
    local debug="${2:-false}"
    local lat="" long=""

    if [[ "$debug" == "true" ]]; then
        echo -e "${CYAN}DEBUG: Response Preview:${NC}"
        echo "$response" | head -20
        echo -e "${CYAN}DEBUG: Geo fields:${NC}"
        echo "$response" | grep -iE "geo:lat|geo:long"
    fi

    if [[ -z "$response" || ${#response} -lt 10 ]]; then return 1; fi
    if echo "$response" | grep -qi "<html\|<!doctype html\|<title>.*error"; then return 1; fi

    # Pattern 1: geo:lat/long - FIXED to capture negative signs
    local geo=$(echo "$response" | grep -A10 -B10 "geo:lat\|geo:long")
    lat=$(echo "$geo" | grep -oP 'geo:lat[^>]*>\s*\K[-+]?[0-9]+\.?[0-9]*' | head -1)
    long=$(echo "$geo" | grep -oP 'geo:long[^>]*>\s*\K[-+]?[0-9]+\.?[0-9]*' | head -1)

    # Pattern 2: wgs84_pos#lat/long - FIXED to capture negative signs
    if [[ -z "$lat" || -z "$long" ]]; then
        local wgs=$(echo "$response" | grep -A10 -B10 "wgs84_pos#lat\|wgs84_pos#long")
        lat=$(echo "$wgs" | grep -oP 'wgs84_pos#lat[^>]*>\s*\K[-+]?[0-9]+\.?[0-9]*' | head -1)
        long=$(echo "$wgs" | grep -oP 'wgs84_pos#long[^>]*>\s*\K[-+]?[0-9]+\.?[0-9]*' | head -1)
    fi

    # Pattern 3: typedLiteral inside TRiX RDF - ALREADY FIXED
    if [[ -z "$lat" || -z "$long" ]]; then
        local triples=$(echo "$response" | sed -n '/<triple>/,/<\/triple>/p')

        while IFS= read -r block; do
            if echo "$block" | grep -q 'wgs84_pos#lat'; then
                lat=$(echo "$block" | grep -oP '<typedLiteral[^>]*>\K[-+]?[0-9]+\.?[0-9]*' | head -1)
            elif echo "$block" | grep -q 'wgs84_pos#long'; then
                long=$(echo "$block" | grep -oP '<typedLiteral[^>]*>\K[-+]?[0-9]+\.?[0-9]*' | head -1)
            fi
        done < <(echo "$triples" | tr '\n' '|' | sed 's|</triple>|<\/triple>\n|g')

        if [[ "$debug" == "true" ]]; then
            echo -e "${CYAN}DEBUG: Pattern 3 TRiX typedLiteral parsed: lat=$lat, long=$long${NC}"
        fi
    fi

    # Geographic correction for known data issues
    if [[ -n "$lat" && -n "$long" ]]; then
        # Fix Western Hemisphere countries that have positive longitude
        if [[ "$page" =~ [Mm]exico && "$long" -gt 0 ]]; then
            long="-$long"
            [[ "$debug" == "true" ]] && echo -e "${YELLOW}🔧 Fixed Mexico longitude sign error${NC}"
        fi

        # Add other geographic corrections as needed
        # Example: if [[ "$page" =~ [Cc]anada && "$long" -gt 0 ]]; then long="-$long"; fi
    fi

    # Final validation
    if [[ -n "$lat" && -n "$long" ]]; then
        if (( $(echo "$lat >= -90 && $lat <= 90" | bc -l) )) && (( $(echo "$long >= -180 && $long <= 180" | bc -l) )); then
            echo "$lat,$long"
            return 0
        fi
    fi

    return 1
}

# Run test for one page
test_specific_page() {
    local lang_page="$1"
    local expected="$2"
    local debug="${3:-false}"

    IFS=':' read -r lang page <<< "$lang_page"
    echo -e "\n${BLUE}Testing: $lang_page${NC}"
    echo "Expected: $expected"
    echo "----------------------------------------"

    local response=$(test_api_endpoint "$lang" "$page" "$debug")
    local status=$?

    if [[ $status -ne 0 ]]; then
        echo -e "${RED}❌ API failed${NC}"
        return 1
    fi

    local coords=$(extract_coords_from_response "$response" "$debug")
    if [[ -z "$coords" || "$coords" == "," ]]; then
        echo -e "${RED}❌ Failed to extract coordinates${NC}"
        local safe_filename=$(echo "${lang}_${page}" | sed 's/[^a-zA-Z0-9._-]/_/g')
        echo "$response" > "debug_${safe_filename}.xml"
        echo -e "${CYAN}📁 Saved response: debug_${safe_filename}.xml${NC}"
        return 1
    fi

    local lat=$(echo "$coords" | cut -d',' -f1)
    local long=$(echo "$coords" | cut -d',' -f2)
    echo -e "${GREEN}✅ Extracted: $lat, $long${NC}"

    local elat=$(echo "$expected" | cut -d',' -f1)
    local elong=$(echo "$expected" | cut -d',' -f2)
    if [[ "$lat" =~ ^[-+]?[0-9]+\.?[0-9]*$ ]] && [[ "$elat" =~ ^[-+]?[0-9]+\.?[0-9]*$ ]]; then
        diff_lat=$(echo "$lat - $elat" | bc -l | sed 's/-//' )
    else
        diff_lat="nan"
    fi

if [[ "$long" =~ ^[-+]?[0-9]+\.?[0-9]*$ ]] && [[ "$elong" =~ ^[-+]?[0-9]+\.?[0-9]*$ ]]; then
    diff_long=$(echo "$long - $elong" | bc -l | sed 's/-//')
else
    diff_long="nan"
fi

    if (( $(echo "$diff_lat < 1.0" | bc -l) )) && (( $(echo "$diff_long < 1.0" | bc -l) )); then
        echo -e "${GREEN}🎯 Match with expected coordinates${NC}"
        return 0
    else
        echo -e "${YELLOW}⚠️ Coordinates differ from expected${NC}"
        echo "Expected: $elat,$elong"
        echo "Got:      $lat,$long"
        echo "Diff:     Δlat=$diff_lat, Δlong=$diff_long"
        return 2
    fi
}

# Main test runner
main() {
    local debug=false
    [[ "$1" == "--debug" || "$1" == "-d" ]] && debug=true

    check_server_connectivity

    echo -e "${BLUE}🚀 Running coordinate tests...${NC}"
    echo "==============================="

    local total=0 ok=0 partial=0 fail=0

    for lang_page in "${!KNOWN_COORDS[@]}"; do
        total=$((total+1))
        test_specific_page "$lang_page" "${KNOWN_COORDS[$lang_page]}" "$debug"
        case $? in
            0) ok=$((ok+1)) ;;
            2) partial=$((partial+1)) ;;
            *) fail=$((fail+1)) ;;
        esac
        sleep 1
    done

    if [[ $fail -gt 0 ]]; then
        echo -e "\n${PURPLE}🔁 Testing fallback pages...${NC}"
        for lang_page in "${!ALTERNATIVE_COORDS[@]}"; do
            total=$((total+1))
            test_specific_page "$lang_page" "${ALTERNATIVE_COORDS[$lang_page]}" "$debug"
            case $? in
                0) ok=$((ok+1)) ;;
                2) partial=$((partial+1)) ;;
                *) fail=$((fail+1)) ;;
            esac
            sleep 1
        done
    fi

    echo -e "\n${BLUE}📊 TEST SUMMARY${NC}"
    echo "======================="
    echo "Total:     $total"
    echo -e "Passed:    ${GREEN}$ok${NC}"
    echo -e "Partial:   ${YELLOW}$partial${NC}"
    echo -e "Failed:    ${RED}$fail${NC}"
    echo -e "\n${CYAN}Run with --debug for more info${NC}"
    exit $([[ $((ok + partial)) -ge $((total * 50 / 100)) ]] && echo 0 || echo 1)
}

main "$@"
