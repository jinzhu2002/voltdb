#!/usr/bin/env bash
#
# Compiles the InsertEmployee stored procedure, and creates a jar file containing it
# (which is used to test the LOAD CLASSES and REMOVE CLASSES commands).
# Also adds other files to the jar file that are used to test handling of
# class loader errors.

help() {
  echo 'Usage: ./build_jar.sh [--build=BUILD]'
  echo '  Build the stored procedure jars for sqlcmd test.'
  echo '  Use BUILD for the build type.  The default build type'
  echo '  is release.'
}

# compile java source
BUILD=release
while [ -n "$1" ] ; do
  case "$1" in
  --build=*)
    BUILD=$(echo "$1" | sed 's/--build=//')
    shift
    ;;
  -h|--help)
    help
    exit 100
    ;;
  *)
    echo "$0: Unknown command line argument $1"
    help
    exit 100
    ;;
  esac
done

javac -classpath ../../obj/$BUILD/prod procedures/sqlcmdtest/*.java
javac -classpath ../../obj/$BUILD/prod functions/sqlcmdtest/*.java

# build the jar file
jar cf sqlcmdtest-funcs.jar -C functions sqlcmdtest

jar cf sqlcmdtest-procs.jar -C procedures sqlcmdtest

# sabotage some dependency classes to test handling of
# secondary class loader errors
rm procedures/sqlcmdtest/*Sabotaged*.class

# build the sabotaged jar file
jar cf sqlcmdtest-sabotaged-procs.jar -C procedures sqlcmdtest

# Further sabotage some dependency classes to test handling of
# immediate class loader errors triggered by static dependencies.
rm procedures/sqlcmdtest/*Killed*.class

# build the sabotaged jar file
jar cf sqlcmdtest-killed-procs.jar -C procedures sqlcmdtest

# removed compiled .class file(s)
rm -rf procedures/sqlcmdtest/*.class
rm -rf functions/sqlcmdtest/*.class
