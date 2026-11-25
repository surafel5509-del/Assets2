#!/bin/bash
# =============================================================================
# Rebuilds the complete offline toolchain at /home/user/toolchain.
#
# The toolchain lives OUTSIDE the git workspace and is NOT persisted across
# sandbox resets — this script is the recovery path (everything it produces
# was fetched/built successfully on 2026-11-25). Run it whenever
# /home/user/toolchain is missing:
#
#     bash tools/offline-build/bootstrap-toolchain.sh
#
# Steps: JRE (PyPI) -> kotlinc + javac8 (npm) -> android.jar (GitHub sparse
# clone, patched for javac8) -> LambdaMetafactory signature stub -> JSON-java
# + Rhino jars (javac8 from source) -> Java stubs -> LocalRunner + build.sh.
# Afterwards:  bash /home/user/toolchain/build.sh all
# =============================================================================
set -e
TC=${1:-/home/user/toolchain}
REPO=$(cd "$(dirname "$0")/../.." && pwd)
SRC=$REPO/tools/offline-build
mkdir -p "$TC/jars" "$TC/out"
cd "$TC"

echo "== [1/7] JRE (jdk4py wheel, linux x86_64) =="
if [ ! -x jdk/java-runtime/bin/java ]; then
  python3 - <<'EOF'
import json, urllib.request
d = json.load(urllib.request.urlopen('https://pypi.org/pypi/jdk4py/json'))
v = d['info']['version']
urls = [u for u in d['releases'][v] if 'linux' in u['filename'] and 'x86_64' in u['filename']]
open('jdk4py.url', 'w').write(urls[0]['url'])
print(v, urls[0]['filename'])
EOF
  curl -sL -o jdk4py.whl "$(cat jdk4py.url)"
  python3 -c "import zipfile; zipfile.ZipFile('jdk4py.whl').extractall('jdkx')"
  mkdir -p jdk && rm -rf jdk/java-runtime && mv jdkx/jdk4py/java-runtime jdk/java-runtime && rm -rf jdkx
  chmod +x jdk/java-runtime/bin/java
fi
jdk/java-runtime/bin/java -version

echo "== [2/7] kotlinc + javac8 (npm tarballs) =="
[ -f kotlinc/lib/kotlin-stdlib.jar ] || { npm pack kotlin-compiler@2.4.21 >/dev/null && mkdir -p kotlinc && tar -xzf kotlin-compiler-2.4.21.tgz -C kotlinc --strip-components=1; }
[ -f tj/tools.jar ] || { npm pack dataslope-tools-jar >/dev/null && mkdir -p tj && tar -xzf dataslope-tools-jar-1.0.0.tgz -C tj --strip-components=1; }
chmod +x kotlinc/bin/kotlinc 2>/dev/null || true

echo "== [3/7] android-34 android.jar (sparse clone + javac8 MODULE patch) =="
if [ ! -f android-platforms/android-34/android.jar ]; then
  git clone --filter=blob:none --no-checkout https://github.com/Sable/android-platforms.git android-platforms
  (cd android-platforms && git sparse-checkout set android-34 && git checkout)
fi
python3 - <<'EOF'
# javac8 crashes on ElementType.MODULE values recorded in API34 class files;
# rename the6-char Utf8 "MODULE" -> "METHOD" (compile-only usage, safe).
import zipfile, os
src = 'android-platforms/android-34/android.jar'
if not os.path.exists('androids.patched.marker'):
    zin = zipfile.ZipFile(src)
    zout = zipfile.ZipFile('androids-patched.jar', 'w', zipfile.ZIP_DEFLATED)
    for item in zin.infolist():
        data = zin.read(item.filename)
        if item.filename.endswith('.class'):
            data = data.replace(b'\x00\x06MODULE', b'\x00\x06METHOD')
        zout.writestr(item, data)
    zout.close(); zin.close()
    os.replace('androids-patched.jar', src)
    open('androids.patched.marker', 'w').write('ok')
    print('android.jar patched for javac8')
EOF

echo "== [4/7] LambdaMetafactory signature stub (compile-only) =="
if [ ! -f out/lf/java/lang/invoke/LambdaMetafactory.class ]; then
  mkdir -p lfsrc/java/lang/invoke out/lf
  cat > lfsrc/java/lang/invoke/LambdaMetafactory.java <<'EOF'
package java.lang.invoke;

/** Compile-time signature stub: the real implementation ships in the running JRE. */
public final class LambdaMetafactory {
    private LambdaMetafactory() {}

    public static CallSite metafactory(MethodHandles.Lookup caller,
                                       String invokedName,
                                       MethodType invokedType,
                                       MethodType samMethodType,
                                       MethodHandle implMethod,
                                       MethodType instantiatedMethodType) {
        throw new UnsupportedOperationException("compile-time stub");
    }

    public static CallSite altMetafactory(MethodHandles.Lookup caller,
                                          String invokedName,
                                          MethodType invokedType,
                                          Object... args) {
        throw new UnsupportedOperationException("compile-time stub");
    }
}
EOF
  AJ=$PWD/android-platforms/android-34/android.jar
  jdk/java-runtime/bin/java -cp tj/tools.jar com.sun.tools.javac.Main -bootclasspath "$AJ" -d out/lf lfsrc/java/lang/invoke/LambdaMetafactory.java
fi

J=jdk/java-runtime/bin/java
AJ=$PWD/android-platforms/android-34/android.jar
BOOT=$PWD/out/lf:$AJ

echo "== [5/7] JSON-java + Rhino jars from source =="
if [ ! -f jars/json-20231013.jar ]; then
  curl -sL -o json.tgz https://codeload.github.com/stleary/JSON-java/tar.gz/refs/tags/20231013
  tar -xzf json.tgz
  find JSON-java-20231013/src/main/java -name '*.java' > json.files
  mkdir -p out/json
  $J -cp tj/tools.jar com.sun.tools.javac.Main -bootclasspath "$AJ" -d out/json @json.files
  python3 - <<'EOF'
import zipfile, os
zf = zipfile.ZipFile('jars/json-20231013.jar', 'w', zipfile.ZIP_DEFLATED)
for root, _, files in os.walk('out/json'):
    for f in files:
        p = os.path.join(root, f)
        zf.write(p, os.path.relpath(p, 'out/json'))
zf.close()
print('json jar', os.path.getsize('jars/json-20231013.jar'))
EOF
fi

if [ ! -f jars/rhino-1.7.13.jar ]; then
  curl -sL -o rhino.tgz https://codeload.github.com/mozilla/rhino/tar.gz/refs/tags/Rhino1_7_13_Release
  tar -xzf rhino.tgz
  # android.jar XMLConstants lacks ACCESS_EXTERNAL_* fields used by XmlProcessor
  sed -i '/ACCESS_EXTERNAL_DTD/d; /ACCESS_EXTERNAL_STYLESHEET/d' \
    rhino-Rhino1_7_13_Release/xmlimplsrc/org/mozilla/javascript/xmlimpl/XmlProcessor.java
  # core + XML support only (the JSR-223 engine/ needs javax.script, unused here)
  find rhino-Rhino1_7_13_Release/src rhino-Rhino1_7_13_Release/xmlimplsrc -name '*.java' \
    | grep -v '/javascript/engine/' > rhino.files
  mkdir -p out/rhino
  $J -cp tj/tools.jar com.sun.tools.javac.Main -bootclasspath "$BOOT" -encoding UTF-8 -d out/rhino @rhino.files
  python3 - <<'EOF'
import zipfile, os
zf = zipfile.ZipFile('jars/rhino-1.7.13.jar', 'w', zipfile.ZIP_DEFLATED)
n = 0
for root, _, files in os.walk('out/rhino'):
    for f in files:
        p = os.path.join(root, f)
        zf.write(p, os.path.relpath(p, 'out/rhino')); n += 1
for base in ['rhino-Rhino1_7_13_Release/src', 'rhino-Rhino1_7_13_Release/xmlimplsrc']:
    for root, _, files in os.walk(base):
        for f in files:
            if f.endswith(('.properties', '.txt', '.js')):
                p = os.path.join(root, f)
                zf.write(p, os.path.relpath(p, base)); n += 1
zf.close()
print('rhino jar entries', n, 'bytes', os.path.getsize('jars/rhino-1.7.13.jar'))
EOF
fi

echo "== [6/7] Java stubs (androidx + junit) =="
# Non-destructive: stubs are checked into the repository and must never be deleted.
find "$SRC/stubs" -name '*.java' > stub.files
mkdir -p out/stubs
$J -cp tj/tools.jar com.sun.tools.javac.Main -bootclasspath "$AJ" -d out/stubs @stub.files
echo "stubs: $(find out/stubs -name '*.class' | wc -l) classes"

echo "== [7/7] LocalRunner + build.sh =="
cp "$SRC/LocalRunner.kt" "$TC/LocalRunner.kt"
cp "$SRC/build.sh" "$TC/build.sh"
chmod +x "$TC/build.sh"

echo
echo "Toolchain ready. Run:  bash $TC/build.sh all"
