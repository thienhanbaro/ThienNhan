#!/bin/sh

# Định nghĩa hàm die và warn trước khi dùng
die () {
    echo
    echo "$*"
    echo
    exit 1
} >&2

warn () {
    echo "$*"
} >&2

# Xác định Java
if [ -n "$JAVA_HOME" ] ; then
    JAVACMD=$JAVA_HOME/bin/java
else
    JAVACMD=java
fi
if ! command -v "$JAVACMD" >/dev/null 2>&1 ; then
    die "ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH."
fi

# Tìm thư mục gốc dự án
APP_HOME=$( cd "$( dirname "$0" )" > /dev/null && pwd -P ) || exit
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

# Chạy Gradle
exec "$JAVACMD" -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
