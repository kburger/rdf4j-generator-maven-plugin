package com.github.kburger.maven.rdf4j.generator;

import org.eclipse.rdf4j.model.IRI;

public class VocabularyProperty {
    private String name;
    private IRI iri;
    
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
}
