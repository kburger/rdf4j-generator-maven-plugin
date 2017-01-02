package com.github.kburger.maven.rdf4j.generator;

import java.io.BufferedWriter;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.impl.SimpleLiteral;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.vocabulary.OWL;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFHandlerException;
import org.eclipse.rdf4j.rio.RDFParser;
import org.eclipse.rdf4j.rio.Rio;
import org.eclipse.rdf4j.rio.helpers.AbstractRDFHandler;
import org.stringtemplate.v4.ST;
import org.stringtemplate.v4.STGroup;
import org.stringtemplate.v4.STGroupFile;
import org.stringtemplate.v4.StringRenderer;

@Mojo(name = "generate", defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class GeneratorMojo extends AbstractMojo {
    // TODO remove once the plugin can bootstrap itself
    private static final IRI OWL_DEPRECATED = SimpleValueFactory.getInstance().createIRI(
            OWL.NAMESPACE, "deprecated");
    
    /**
     * 
     */
    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;
    /**
     * 
     */
    @Parameter(required = true, defaultValue = "${project.build.directory}/generated-sources/")
    private String outputDirectory;
    /**
     * 
     */
    @Parameter(required = true, defaultValue = "false")
    private boolean overwrite;
    /**
     * 
     */
    @Parameter(alias = "package", required = true)
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
            
            STGroup templates = new STGroupFile(legacy ? "templates/legacy.stg" : "templates/modern.stg");
            templates.registerRenderer(String.class, new StringRenderer());
            
            ST vcb = templates.getInstanceOf("vocab");
            vcb.add("package", packageName);
            vcb.add("timestamp", ZonedDateTime.now());
            vcb.add("class", vocab.getPrefix());
            vcb.add("namespace", vocab.getNamespace());
            vcb.add("prefix", vocab.getPrefix());
            
            try {
                vcb.add("properties", parseVocabulary(vocab));
            } catch (IOException e) {
                throw new MojoExecutionException("Could not parse vocabulary", e);
            }
            
            String packageDirectories = packageName.replace('.', '/');
            Path root = Paths.get(outputDirectory, packageDirectories);
            
            try {
                Files.createDirectories(root);
            } catch (IOException e) {
                throw new MojoExecutionException(e.getMessage());
            }
            
            String filename = vocab.getPrefix().toUpperCase() + ".java";
            Path file = Paths.get(outputDirectory, packageDirectories, filename);
            try {
                BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8);
                writer.write(vcb.render());
                writer.flush();
                writer.close();
            } catch (IOException e) {
                getLog().error("Failed to write vocabulary to file", e);
            }
        }
        
        project.addCompileSourceRoot(outputDirectory);
    }
    
    protected List<VocabularyProperty> parseVocabulary(Vocabulary vocab) throws IOException {
        List<VocabularyProperty> properties = new ArrayList<>();
        
        URL url = vocab.getUrl();
        URLConnection connection = url.openConnection();
        
        Optional<RDFFormat> format = Rio.getParserFormatForFileName(url.getPath()); 
        if (!format.isPresent()) {
            format = Rio.getParserFormatForMIMEType(connection.getContentType()); // TODO handle null from getContentType
        }
        
        if (!format.isPresent()) {
            throw new RuntimeException("no format known");
        }
        
        RDFParser parser = Rio.createParser(format.get());
        
        List<Resource> classes = new ArrayList<>();
        List<Resource> objectProperties = new ArrayList<>();
        List<Resource> deprecated = new ArrayList<>();
        
        parser.setRDFHandler(new AbstractRDFHandler() {
            @Override
            public void handleStatement(Statement st) throws RDFHandlerException {
                Resource s = st.getSubject();
                IRI p = st.getPredicate();
                Value o = st.getObject();
                
                if (p.equals(RDF.TYPE) && o.equals(OWL.CLASS)) {
                    classes.add(s);
                } else if (p.equals(RDF.TYPE) && o.equals(OWL.OBJECTPROPERTY)) {
                    objectProperties.add(s);
                } else if (p.equals(OWL_DEPRECATED) && ((SimpleLiteral)o).booleanValue()) {
                    deprecated.add(s);
                }
            }
        });
        parser.parse(connection.getInputStream(), "");
        
        for (Resource clazz : classes) {
            IRI iri = (IRI)clazz;
            
            VocabularyProperty property = new VocabularyProperty();
            property.setIri(iri);
            property.setName(iri.getLocalName());
            
            properties.add(property);
        }
        
        for (Resource objectProperty : objectProperties) {
            IRI iri = (IRI)objectProperty;
            
            VocabularyProperty property = new VocabularyProperty();
            property.setIri(iri);
            
            Optional<?> match = classes.stream().map(r -> ((IRI)r))
                    .map(IRI::getLocalName)
                    .filter(c -> c.equalsIgnoreCase(iri.getLocalName()))
                    .findFirst();
            String name = match.isPresent() ? "has_" + iri.getLocalName() : iri.getLocalName();
            
            property.setName(name);
            
            properties.add(property);
        }
        
        return properties;
    }
}
