# Maven Wrapper PowerShell Script
$ErrorActionPreference = "Stop"

$wrapperJar = ".\.mvn\wrapper\maven-wrapper.jar"
$javaCmd = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME "bin\java.exe" } else { "java" }

& $javaCmd -classpath $wrapperJar "-Dmaven.multiModuleProjectDirectory=." org.apache.maven.wrapper.MavenWrapperMain @args
exit $LASTEXITCODE
