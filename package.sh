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
DIR="$( cd "$(dirname "${BASH_SOURCE[0]}")" ; pwd -P )"
cd "$DIR"

####################
# compile frontend
####################
rm -rf src/main/webapp/js
tsc

###################
# compile backend
###################
mvn clean
mvn package
