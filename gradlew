#!/bin/bash
export JAVA_HOME=/opt/jdk/jdk-17.0.2
exec /opt/gradle-dist/gradle-8.5/bin/gradle "$@"
