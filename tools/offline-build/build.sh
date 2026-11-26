#!/bin/bash
# Offline build pipeline for S Engine (no Gradle / no network needed after bootstrap).
#   build.sh stubs | main | test | run | all
set -e
TH=/home/user/Assets2
TC=/home/user/toolchain
cd "$TC"

export JAVA_HOME=$TC/jdk/java-runtime
export PATH=$JAVA_HOME/bin:$PATH
J=$JAVA_HOME/bin/java
AJ=$TC/android-platforms/android-34/android.jar
LF=$TC/out/lf
JSON=$TC/jars/json-20231013.jar
RHINO=$TC/jars/rhino-1.7.13.jar
STUBS=$TC/out/stubs
STD=$TC/kotlinc/lib/kotlin-stdlib.jar
ANN=$TC/kotlinc/lib/annotations-13.0.jar
KOTLINC=$TC/kotlinc/bin/kotlinc
JAVAC="$J -cp $TC/tj/tools.jar com.sun.tools.javac.Main"
BOOT="$LF:$AJ"
step=${1:-all}

if [ "$step" = "stubs" ] || [ "$step" = "all" ]; then
  echo "== compiling java stubs =="
  # Stub sources are versioned in the repository, not in the toolchain dir.
  find "$TH/tools/offline-build/stubs" -name '*.java' > stub.files
  rm -rf out/stubs && mkdir -p out/stubs
  $JAVAC -bootclasspath "$AJ" -d out/stubs @stub.files
  echo "stubs ok: $(find out/stubs -name '*.class' | wc -l) classes"
fi

if [ "$step" = "main" ] || [ "$step" = "all" ]; then
  echo "== compiling main (kotlinc) =="
  rm -rf out/main && mkdir -p out/main
  "$KOTLINC" -classpath "$AJ:$STUBS:$JSON:$RHINO" -d out/main $(find "$TH/app/src/main/java" -name '*.kt')
  echo "main ok: $(find out/main -name '*.class' | wc -l) classes"
fi

if [ "$step" = "test" ] || [ "$step" = "all" ]; then
  echo "== compiling tests (kotlinc) =="
  rm -rf out/test && mkdir -p out/test
  "$KOTLINC" -classpath "out/main:$STUBS:$AJ:$JSON:$RHINO" -d out/test $(find "$TH/app/src/test" -name '*.kt') LocalRunner.kt
  echo "tests ok: $(find out/test -name '*.class' | wc -l) classes"
fi

if [ "$step" = "run" ] || [ "$step" = "all" ]; then
  echo "== running tests =="
  # Auto-discover every *Test.kt so new regression suites are never silently skipped.
  TESTS=$(cd "$TH" && find app/src/test/java -name '*Test.kt' -print \
    | sed -e 's#^app/src/test/java/##' -e 's#/#.#g' -e 's#\.kt$##' | sort)
  echo "test classes: $(echo "$TESTS" | tr '\n' ' ')"
  SENGINE_REPO="$TH" $J -Dsengine.repo="$TH" \
    -cp "out/test:out/main:$STUBS:$JSON:$RHINO:$AJ:$STD:$ANN" LocalRunnerKt $TESTS
fi
