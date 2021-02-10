#!/bin/bash

set -e
DIR="$(
  cd "$(dirname "${BASH_SOURCE[0]}")"
  pwd -P
)"
cd "$DIR"

# clean build
# mvn release will perform mvn clean && mvn build, but we still
# want to ensure that we build the frontend as well
bash -c "./package.sh -n"

# publish the artifact
mvn release:prepare release:perform
