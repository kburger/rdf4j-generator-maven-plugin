package com.github.kburger.maven.rdf4j.generator;

public enum OutputType {
    /**
     * Default. Outputs classes using RDF4J 2.x.
     */
    MODERN,
    /**
     * Legacy support for Sesame 2.x.
     */
    LEGACY,
    /**
     * String constants. For use with annotations.
     */
    STRINGS
}
