#!/bin/bash
# Generates the Oracle reference CAP files used by the converter's black-box comparison tests
# (OracleByteIdentityTest, OracleReferenceComparisonTest, PerVersionOracleComparisonTest).
#
# The Oracle Java Card SDK converters are run as black boxes on this project's own test applets.
# Their output is covered by the SDK licence (OTN) and is NOT redistributable: it is written to the
# git-ignored directory build/oracle-refs/ and must never be committed. See PROVENANCE.md.
#
# Requirements (none of them is part of the repository):
#   - Oracle Java Card SDKs in build/oracle-sdks/<kit> (jc212_kit, jc221_kit, jc222_kit, jc303_kit,
#     jc304_kit, jc305u3_kit, jc310r20210706_kit, jc320v24.0_kit), downloaded from Oracle under its licence.
#   - A Java 8 runtime for the old converters: JAVA8_HOME, or Docker (image eclipse-temurin:8-jdk) when
#     JAVA8_HOME is unset or not runnable on this machine.
#   - A Java 11 runtime for the 3.2.0 kit: JAVA11_HOME.
#
# Environment overrides: ORACLE_SDKS (kit directory), ORACLE_REFS (output directory), JAVA8_HOME, JAVA11_HOME.
# Usage: tools/oracle/generate-oracle-refs.sh
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
SDK_BASE="$(cd "${ORACLE_SDKS:-$PROJECT_DIR/build/oracle-sdks}" && pwd -P)"
OUT_DIR="${ORACLE_REFS:-$PROJECT_DIR/build/oracle-refs}"
WORK_DIR="$PROJECT_DIR/build/oracle-refs-work"
TEST_SRC="$PROJECT_DIR/converter/src/test/java"
JAVA8_IMAGE="eclipse-temurin:8-jdk"

mkdir -p "$OUT_DIR"
OUT_DIR="$(cd "$OUT_DIR" && pwd -P)"
rm -rf "$WORK_DIR"
mkdir -p "$WORK_DIR"
WORK_DIR="$(cd "$WORK_DIR" && pwd -P)"

if [ -n "${JAVA8_HOME:-}" ] && "$JAVA8_HOME/bin/java" -version >/dev/null 2>&1; then
    JAVA8_MODE=native
elif command -v docker >/dev/null 2>&1; then
    JAVA8_MODE=docker
else
    echo "No runnable Java 8: set JAVA8_HOME or install Docker" >&2
    exit 1
fi
if [ -z "${JAVA11_HOME:-}" ] || ! "$JAVA11_HOME/bin/java" -version >/dev/null 2>&1; then
    echo "JAVA11_HOME must point to a Java 11 runtime (needed by the 3.2.0 kit)" >&2
    exit 1
fi

# Runs a Java 8 tool (javac or java) natively or in Docker with the project, kits and output mounted.
java8() {
    local tool=$1
    shift
    if [ "$JAVA8_MODE" = native ]; then
        "$JAVA8_HOME/bin/$tool" "$@"
    else
        docker run --rm -u "$(id -u):$(id -g)" -e "JAVA_TOOL_OPTIONS=-Duser.home=$WORK_DIR" \
            -v "$PROJECT_DIR:$PROJECT_DIR" -v "$SDK_BASE:$SDK_BASE" -v "$OUT_DIR:$OUT_DIR" \
            -v "$WORK_DIR:$WORK_DIR" -w "$WORK_DIR" "$JAVA8_IMAGE" "$tool" "$@"
    fi
}

java11() {
    local tool=$1
    shift
    "$JAVA11_HOME/bin/$tool" "$@"
}

SUCCESS=0
FAILED=0

# convert <label> <output-file> <runtime: java8|java11> <javac -source> <javac -target> <keep @Override: yes|no>
#         <api jar> <export path> <converter main class> <converter classpath> <jc.home or ""> <package> <package AID>
#         <applet class> <applet AID> <source files...>
convert() {
    local label=$1 output=$2 runtime=$3 source=$4 target=$5 keep_override=$6 api_jar=$7 export_path=$8
    local main=$9 converter_cp=${10} jc_home=${11} package=${12} package_aid=${13} applet=${14} applet_aid=${15}
    shift 15
    local work="$WORK_DIR/$label"
    mkdir -p "$work/src" "$work/classes" "$work/output"
    echo "== $label -> $output"

    # Copy the sources; old javac targets do not know @Override on interface methods, so it is stripped
    local file rel
    for file in "$@"; do
        rel="${file#"$TEST_SRC"/}"
        mkdir -p "$work/src/$(dirname "$rel")"
        if [ "$keep_override" = yes ]; then
            cp "$file" "$work/src/$rel"
        else
            sed 's/@Override//' "$file" > "$work/src/$rel"
        fi
    done

    local sources=()
    while IFS= read -r file; do sources+=("$file"); done < <(find "$work/src" -name '*.java' | sort)
    "$runtime" javac -nowarn -source "$source" -target "$target" -cp "$api_jar" -d "$work/classes" \
        "${sources[@]}" 2>&1 | grep -v '^warning:' || true

    local jc_home_flag=()
    if [ -n "$jc_home" ]; then
        jc_home_flag=("-Djc.home=$jc_home")
    fi
    "$runtime" java ${jc_home_flag[@]+"${jc_home_flag[@]}"} -cp "$converter_cp" "$main" \
        -classdir "$work/classes" -out CAP -exportpath "$export_path" -d "$work/output" \
        -applet "$applet_aid" "$applet" "$package" "$package_aid" 1.0 > "$work/converter.log" 2>&1 || true

    local cap
    cap=$(find "$work/output" -name '*.cap' | head -1)
    if [ -n "$cap" ]; then
        cp "$cap" "$OUT_DIR/$output"
        echo "   ok ($(wc -c < "$cap" | tr -d ' ') bytes)"
        SUCCESS=$((SUCCESS + 1))
    else
        echo "   FAILED, converter output:"
        sed 's/^/   | /' "$work/converter.log"
        FAILED=$((FAILED + 1))
    fi
}

# Converter class path of a 3.x kit: the converter and its libraries (not the profiler, debug proxy or
# connected-edition jars, which clash with the converter classes in some kits).
kit_classpath() {
    local kit=$1
    find "$kit/lib" -maxdepth 1 \( -name tools.jar -o -name 'api_classic*.jar' -o -name 'bcel-*.jar' \
        -o -name 'asm*.jar' -o -name 'commons-cli-*.jar' -o -name 'commons-logging-*.jar' -o -name 'jctasks*.jar' \
        -o -name json.jar \) | sort | paste -sd: -
}

# Extracts the API export files that 3.1/3.2 kits keep inside tools.jar; prints the extracted directory.
kit_exports() {
    local kit=$1 version=$2
    local dir="$WORK_DIR/exports-$version"
    mkdir -p "$dir"
    (cd "$dir" && "$JAVA11_HOME/bin/jar" xf "$kit/lib/tools.jar" "api_export_files_$version/")
    echo "$dir/api_export_files_$version"
}

# The converter test package of TestApplet: AIDs as in PerVersionOracleComparisonTest
TEST_APPLET=("$TEST_SRC/com/example/TestApplet.java")
TA_PKG=(com.example 0xA0:0x00:0x00:0x00:0x62:0x01:0x01:0x01
        com.example.TestApplet 0xA0:0x00:0x00:0x00:0x62:0x01:0x01:0x01:0x01)

for spec in "jc212:jc212_kit:api21.jar:api21_export_files" "jc221:jc221_kit:api.jar:api_export_files" \
            "jc222:jc222_kit:api.jar:api_export_files"; do
    IFS=: read -r tag kit api exports <<< "$spec"
    cp_entries="$SDK_BASE/$kit/lib/converter.jar:$SDK_BASE/$kit/lib/$api"
    [ -f "$SDK_BASE/$kit/lib/offcardverifier.jar" ] && cp_entries="$cp_entries:$SDK_BASE/$kit/lib/offcardverifier.jar"
    convert "TestApplet-$tag" "oracle-TestApplet-$tag.cap" java8 1.3 1.1 no \
        "$SDK_BASE/$kit/lib/$api" "$SDK_BASE/$kit/$exports" com.sun.javacard.converter.Converter "$cp_entries" "" \
        "${TA_PKG[@]}" "${TEST_APPLET[@]}"
done

# The 3.0.3/3.0.4 converters accept class files up to version 50 (Java 6), 3.0.5u3 up to 51 (Java 7)
for spec in "jc303:jc303_kit:1.6" "jc304:jc304_kit:1.6" "jc305:jc305u3_kit:1.7"; do
    IFS=: read -r tag kit target <<< "$spec"
    convert "TestApplet-$tag" "oracle-TestApplet-$tag.cap" java8 1.5 "$target" yes \
        "$SDK_BASE/$kit/lib/api_classic.jar" "$SDK_BASE/$kit/api_export_files" com.sun.javacard.converter.Main \
        "$(kit_classpath "$SDK_BASE/$kit")" "$SDK_BASE/$kit" "${TA_PKG[@]}" "${TEST_APPLET[@]}"
done

KIT310="$SDK_BASE/jc310r20210706_kit"
convert "TestApplet-jc310" "oracle-TestApplet-jc310.cap" java8 1.6 1.7 yes \
    "$KIT310/lib/api_classic-3.1.0.jar" "$(kit_exports "$KIT310" 3.1.0)" com.sun.javacard.converter.Main \
    "$(kit_classpath "$KIT310")" "$KIT310" "${TA_PKG[@]}" "${TEST_APPLET[@]}"

KIT320="$SDK_BASE/jc320v24.0_kit"
convert "TestApplet-jc320" "oracle-TestApplet-jc320.cap" java11 1.6 1.7 yes \
    "$KIT320/lib/api_classic-3.2.0.jar" "$(kit_exports "$KIT320" 3.2.0)" com.sun.javacard.converter.Main \
    "$(kit_classpath "$KIT320")" "$KIT320" "${TA_PKG[@]}" "${TEST_APPLET[@]}"

# Multi-class, interface, exception, inheritance and crypto packages, converted with the 3.0.5u3 kit
KIT305="$SDK_BASE/jc305u3_kit"
complex() { # <label> <package> <package AID suffix> <applet simple name> <source files...>
    local label=$1 package=$2 aid=$3 applet=$4
    shift 4
    convert "$label" "oracle-$label.cap" java8 1.5 1.7 no \
        "$KIT305/lib/api_classic.jar" "$KIT305/api_export_files" com.sun.javacard.converter.Main \
        "$(kit_classpath "$KIT305")" "$KIT305" "$package" "0xA0:0x00:0x00:0x00:0x62:$aid" \
        "$package.$applet" "0xA0:0x00:0x00:0x00:0x62:$aid:0x01" "$@"
}
complex MultiClassApplet com.example.multiclass 0x03:0x01:0x01 MultiClassApplet \
    "$TEST_SRC/com/example/multiclass/Helper.java" "$TEST_SRC/com/example/multiclass/MultiClassApplet.java"
complex InterfaceApplet com.example.iface 0x04:0x01:0x01 InterfaceApplet \
    "$TEST_SRC/com/example/iface/InterfaceApplet.java"
complex ExceptionApplet com.example.exception 0x05:0x01:0x01 ExceptionApplet \
    "$TEST_SRC/com/example/exception/ExceptionApplet.java"
complex InheritanceApplet com.example.inherit 0x06:0x01:0x01 InheritanceApplet \
    "$TEST_SRC/com/example/inherit/BaseApplet.java" "$TEST_SRC/com/example/inherit/MiddleApplet.java" \
    "$TEST_SRC/com/example/inherit/InheritanceApplet.java"
complex CryptoApplet com.example.crypto 0x07:0x01:0x01 CryptoApplet \
    "$TEST_SRC/com/example/crypto/CryptoApplet.java"

echo
echo "Generated $SUCCESS reference CAP files in $OUT_DIR ($FAILED failed). Never commit them."
[ "$FAILED" -eq 0 ]
