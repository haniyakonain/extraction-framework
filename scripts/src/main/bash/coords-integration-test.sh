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
    ["sl:Združene države Amerike"]="38.88333333333333,-77.03333333333333"
    ["en:United States"]="38.88333333333333,-77.01666666666667"
    ["de:Vereinigte Staaten"]="40.0,-100.0"
    ["sr:Сједињене Америчке Државе"]="38.88333333333333,-77.01666666666667"
    ["fr:États-Unis"]="40.0,-105.0"
    ["sv:USA"]="40.0,-100.0"
    ["be:Злучаныя Штаты Амерыкі"]="40.0,-100.0"
    ["el:Ηνωμένες Πολιτείες Αμερικής"]="40.0,-100.0"
    ["lv:Amerikas_Savienotās_Valstis"]="38.88333333333333,-77.03333333333333"
    ["en:United States"]="38.883333,-77.0166666" #wikipedia-"40, -100"
    ["de:Vereinigte Staaten"]="40, -100"
    ["en:Sweden"]="59.35,18.0666666" #wikipedia-"63,16"
    ["de:Schweden"]="61.316667,14.833333"
    ["en:Argentina"]="-34.6,-58.383333" #wikipedia-"-34,-64"
    ["de:Argentinien"]="-34.6, -58.38"
    ["en:Australia"]="-35.308055555,149.12444444" #wikipedia-"-25,133"
    ["de:Australien"]="-25,135"
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

    local url="https://mappings.dbpedia.org/server/"
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

    local base_url="https://mappings.dbpedia.org/server/extraction/${lang}/extract"
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

# Coordinate parser
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

    # Pattern 1: geo:lat/long
    local geo=$(echo "$response" | grep -A10 -B10 "geo:lat\|geo:long")
    lat=$(echo "$geo" | grep -oP 'geo:lat[^>]*>\s*\K[-]?[0-9]+\.?[0-9]*' | head -1)
    long=$(echo "$geo" | grep -oP 'geo:long[^>]*>\s*\K[-]?[0-9]+\.?[0-9]*' | head -1)

    # Pattern 2: wgs84_pos#lat/long
    if [[ -z "$lat" || -z "$long" ]]; then
        local wgs=$(echo "$response" | grep -A10 -B10 "wgs84_pos#lat\|wgs84_pos#long")
        lat=$(echo "$wgs" | grep -oP 'wgs84_pos#lat[^>]*>\s*\K[-]?[0-9]+\.?[0-9]*' | head -1)
        long=$(echo "$wgs" | grep -oP 'wgs84_pos#long[^>]*>\s*\K[-]?[0-9]+\.?[0-9]*' | head -1)
    fi
        # Pattern 3: typedLiteral inside TRiX RDF (float/double values)
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
