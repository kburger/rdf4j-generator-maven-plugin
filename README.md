# rdf4j-generator-maven-plugin
[![Build Status](https://travis-ci.org/kburger/rdf4j-generator-maven-plugin.svg?branch=develop)](https://travis-ci.org/kburger/rdf4j-generator-maven-plugin)
[![Dependency Status](https://www.versioneye.com/user/projects/5874952d41a6c10047d456f2/badge.svg?style=flat-square)](https://www.versioneye.com/user/projects/5874952d41a6c10047d456f2)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.github.kburger/rdf4j-generator-maven-plugin/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.github.kburger/rdf4j-generator-maven-plugin)

Maven plugin to generate RDF4J style vocabulary classes.

## usage
``` xml
<build>
    ...
    <plugins>
        ...
        <plugin>
            <groupId>com.github.kburger</groupId>
            <artifactId>rdf4j-generator-maven-plugin</artifactId>
            <version>0.2.0-SNAPSHOT</version>
            <executions>
                <execution>
                    <goals>
                        <goal>generate</goal>
                    </goals>
                    <configuration>
                        <package>com.example.vocab</package>
                        <vocabularies>
                            <vocabulary>
                                <url>http://purl.org/dc/terms/</url>
                            </vocabulary>
                        </vocabularies>
                    </configuration>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

See the [examples](examples) for more usage examples.

## changelog
0.2.0
- added `addGeneratedAnnotation` parameter to mark the generated classes as 'generated'
- added `cacheFiles` parameter to cache remote files locally
- added `includeDeprecated` parameter to in/exclude deprecated RDF types/properties.

0.1.0
- initial release