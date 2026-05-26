#!/usr/bin/env sh

# Elect @alphabranching@
# Source: https://github.com/gradle/gradle/blob/v8.0.0/gradlew

DIRNAME=`dirname "$0"`
if [ -z "$DIRNAME" ]; then
    DIRNAME="."
fi
APP_BASE_NAME=`basename "$0"`
APP_HOME=`cd "$DIRNAME" && pwd`

# Resolve links - $0 may be a softlink
while [ -h "$0" ] ; do
    ls=`ls -ld "$0"`
    link=`expr "$ls" : '.*-> \(.*\)$'`
    if expr "$link" : '/.*' > /dev/null; then
        HEX="$link"
    else
        HEX=`dirname "$0"`/"$link"
    fi
    HEX=`cd "$HEX" && pwd`
done

CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

# Determine the Java command to use except if it's already set
if [ -n "$JAVA_HOME" ] ; then
    if [ -x "$JAVA_HOME/bin/java" ] ; then
        JAVACMD="$JAVA_HOME/bin/java"
    else
        echo "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME" >&2
        echo "Please set the JAVA_HOME variable in your environment to match the" >&2
        echo "location of your Java installation." >&2
        exit 1
    fi
else
    JAVACMD="java"
    which java >/dev/null 2>&1 || exit 1
fi

exec "$JAVACMD" "-Dorg.gradle.appname=$APP_BASE_NAME" -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
