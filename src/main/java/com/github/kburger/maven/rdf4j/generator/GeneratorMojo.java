package com.github.kburger.maven.rdf4j.generator;

import java.io.File;
import java.io.StringWriter;
import java.time.ZonedDateTime;
import java.util.Collections;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.stringtemplate.v4.ST;
import org.stringtemplate.v4.STGroup;
import org.stringtemplate.v4.STGroupFile;
import org.stringtemplate.v4.StringRenderer;

@Mojo(name = "generate", defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class GeneratorMojo extends AbstractMojo {
    /**
     * 
     */
    @Parameter(required = true)
    private File outputDirectory;
    /**
     * 
     */
    @Parameter(required = true, defaultValue = "false")
    private boolean overwrite;
    /**
     * 
     */
    @Parameter(alias = "package", property = "package", required = true)
    private String packageName;
    /**
     * 
     */
    @Parameter(defaultValue = "false")
    private boolean legacy;
    /**
     * 
     */
    @Parameter(required = true)
    private Vocabulary[] vocabularies;
    
    // TOOD flag parameter to ignore vann?
    
    public void execute() throws MojoExecutionException, MojoFailureException {
        getLog().info("Parsing " + vocabularies.length + " vocabularies");
        
        for (Vocabulary vocab : vocabularies) {
            getLog().info("prefix: " + vocab.getPrefix());
            
            STGroup templates = new STGroupFile(legacy ? "templates/legacy.stg" : "templates/vocab.stg");
            templates.registerRenderer(String.class, new StringRenderer());
            
            ST vcb = templates.getInstanceOf("vocab");
            vcb.add("package", packageName);
            vcb.add("timestamp", ZonedDateTime.now());
            vcb.add("class", vocab.getPrefix());
            vcb.add("namespace", vocab.getNamespace());
            vcb.add("prefix", vocab.getPrefix());
            // tmp
            VocabularyProperty prop = new VocabularyProperty();
            prop.setName("test");
            prop.setIri(SimpleValueFactory.getInstance().createIRI("http://example.com/vocab#test"));
            vcb.add("properties", Collections.singletonList(prop));
            // /tmp
            
            StringWriter writer = new StringWriter();
            writer.write(vcb.render());
            getLog().info(writer.toString());
        }
    }
}
