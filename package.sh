#!/bin/bash
###################################################################
# The purpose of this script is to package the plugin in one click
#
# You need:
# - maven
# - java 8
# - typescript compiler
###################################################################
set -e
DIR="$(
  cd "$(dirname "${BASH_SOURCE[0]}")"
  pwd -P
)"
cd "$DIR"

####################
# compile frontend
####################
rm -rf src/main/webapp/js
tsc

###################
# compile backend
###################
TEST=1
while [[ "$#" -gt 0 ]]; do
  case $1 in
  -n | --notest)
    TEST=0
    shift
    ;;
  esac
done

mvn clean
if [[ $TEST == 1 ]]; then
  echo "Package with tests"
  mvn package
else
  echo "Package without tests"
  mvn package -DskipTests
fi
