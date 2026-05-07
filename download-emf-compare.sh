#!/bin/sh

set -eu

# Downloads EMF Compare and installs it as a local Maven dependency.

EMF_COMPARE_VERSION="3.5.3.202311200811"
EMF_COMPARE_URL="https://download.eclipse.org/releases/2023-12/202312061001/plugins/org.eclipse.emf.compare_${EMF_COMPARE_VERSION}.jar"
EMF_COMPARE_JAR="org.eclipse.emf.compare_${EMF_COMPARE_VERSION}.jar"

cd "$(dirname "$0")"
curl -L -C - -o "$EMF_COMPARE_JAR" "$EMF_COMPARE_URL"

mvn -B install:install-file \
  "-Dfile=$EMF_COMPARE_JAR" \
  -DgroupId=org.eclipse.emf \
  -DartifactId=org.eclipse.emf.compare \
  "-Dversion=$EMF_COMPARE_VERSION" \
  -Dpackaging=jar
