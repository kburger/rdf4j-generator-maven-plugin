package com.github.kburger.maven.rdf4j.generator;

import org.eclipse.rdf4j.model.IRI;

public class VocabularyProperty {
    private String name;
    private IRI iri;
    private boolean deprecated;
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public IRI getIri() {
        return iri;
    }
    
    public void setIri(IRI iri) {
        this.iri = iri;
    }
    
    public void setDeprecated(boolean deprecated) {
        this.deprecated = deprecated;
    }
    
    public boolean isDeprecated() {
        return deprecated;
    }
}
