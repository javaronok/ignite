Apache Ignite SLF4J Module
--------------------------

Apache Ignite SLF4J module provides GridLogger implementation based on SLF4J.

To enable SLF4J module when starting a standalone node, move 'optional/ignite-slf4j' folder to
'libs' folder before running 'ignite.{sh|bat}' script. The content of the module folder will
be added to classpath in this case.

Importing SLF4J Module In Maven Project
---------------------------------------

If you are using Maven to manage dependencies of your project, you can add SLF4J module
dependency like this (replace '${ignite.version}' with actual Ignite version you are
interested in):

<project xmlns="http://maven.apache.org/POM/4.0.0"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                        http://maven.apache.org/xsd/maven-4.0.0.xsd">
    ...
    <dependencies>
        ...
        <dependency>
            <groupId>org.apache.ignite</groupId>
            <artifactId>ignite-slf4j</artifactId>
            <version>${ignite.version}</version>
        </dependency>
        ...
    </dependencies>
    ...
</project>

Use SLF4J module in server deployment
---------------------------------------

By default, Ignite uses the java.util.logging (JUL) framework.
If you start Ignite using the ignite.sh|bat script from the distribution package, Ignite uses `$IGNITE_HOME/config/java.util.logging.properties` as the default logging configuration file and outputs all messages to log files in the `$IGNITE_HOME/work/log` directory. 
You can override the default logging directory by specifying the IGNITE_LOG_DIR system property.
Check that the JUL logger is working: `[11:58:43,307][INFO][main][IgniteKernal%testGrid] Logger: JavaLogger [quiet=false, config=null]`

## Configuring SLF4J

### 1. Add to the XML configuration:
```xml
<bean id="ignite.cfg" class="org.apache.ignite.configuration.IgniteConfiguration">
 ...
		<property name="gridLogger">
			<bean class="org.apache.ignite.logger.slf4j.Slf4jLogger">
			</bean>
		</property>
 ...
</bean>
```
### 2. Module ignite-slf4j
Connect the `ignite-slf4j` module by copying it from the optional directory to the `libs` directory. 
Make sure that the classpath contains the following: `logback-classic-1.2.9.jar` and `logback-core-1.2.9.jar`.

### 3. Logback settings file
Create a file with logback settings:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>

	<!-- Logs directory -->
    <variable name="IGNITE_LOG_DIR" value="${IGNITE_HOME}/work/log"/>

	<!-- STDOUT appender -->
	<appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
		<encoder class="ch.qos.logback.classic.encoder.PatternLayoutEncoder">
			<pattern>%d{yyyy-MM-dd HH:mm:ss.SSS}, %thread, %level, %logger{0}, %msg%n</pattern>
		</encoder>
	</appender>

	<!-- File appender -->
	<appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
		<file>${IGNITE_LOG_DIR}/ignite.log</file>
		<rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
			<fileNamePattern>${IGNITE_LOG_DIR}/ignite-%d{yyyy-MM-dd}.log.gz</fileNamePattern>
		</rollingPolicy>
		<encoder class="ch.qos.logback.classic.encoder.PatternLayoutEncoder">
			<pattern>%d{yyyy-MM-dd HH:mm:ss.SSS}, %thread, %level, %logger{0}, %msg%n</pattern>
		</encoder>
	</appender>

	<!-- Logger -->
	<root level="INFO">
		<appender-ref ref="STDOUT" />
		<appender-ref ref="FILE" />
	</root>

</configuration>
```

### 4. Launch
Add a line with the path to the logback configuration file to `JVM_OPTS`: `-Dlogback.configurationFile=config/logback.xml`
Check after startup that the SLF4J logger is working:
`2026-01-26 18:11:46.692, main, INFO, IgniteKernal%testGrid, Logger: Slf4jLogger [impl=Logger[org.apache.ignite.internal.IgniteKernal%testGrid], quiet=false]`
