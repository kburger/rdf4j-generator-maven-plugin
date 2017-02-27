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
import org.eclipse.rdf4j.model.vocabulary.RDFS;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFHandlerException;
import org.eclipse.rdf4j.rio.RDFParser;
import org.eclipse.rdf4j.rio.Rio;
import org.eclipse.rdf4j.rio.helpers.AbstractRDFHandler;
import org.stringtemplate.v4.ST;
import org.stringtemplate.v4.STGroup;
import org.stringtemplate.v4.STGroupFile;
import org.stringtemplate.v4.StringRenderer;

import com.google.common.net.HttpHeaders;

@Mojo(name = "generate", defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class GeneratorMojo extends AbstractMojo {
    // TODO remove once the plugin can bootstrap itself
    private static final IRI OWL_DEPRECATED = SimpleValueFactory.getInstance()
            .createIRI(OWL.NAMESPACE, "deprecated");
    private static final IRI VANN_PREFERREDNAMESPACEURI = SimpleValueFactory.getInstance()
            .createIRI("http://purl.org/vocab/vann/", "preferredNamespaceUri");
    private static final IRI VANN_PREFERREDNAMESPACEPREFIX = SimpleValueFactory.getInstance()
            .createIRI("http://purl.org/vocab/vann/", "preferredNamespacePrefix");
    
    /**
     * Maven project reference.
     */
    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;
    /**
     * Output directory for the generated classes.
     */
    @Parameter(required = true, defaultValue = "${project.build.directory}/generated-sources/")
    private String outputDirectory;
    /**
     * Defines the output type. Options are MODERN, LEGACY, or STRINGS.
     */
    @Parameter(required = true, defaultValue = "MODERN")
    private OutputType outputType;
    /**
     * Flag to indicate existing files to be overwritten.
     */
    @Parameter(required = true, defaultValue = "false")
    private boolean overwrite;
    /**
     * Package name of the generated code.
     */
    @Parameter(alias = "package", required = true)
    private String packageName;
    /**
     * List of vocabularies.
     */
    @Parameter(required = true)
    private Vocabulary[] vocabularies;
    // TOOD flag parameter to ignore vann?
    
    public void execute() throws MojoExecutionException, MojoFailureException {
        project.addCompileSourceRoot(outputDirectory);
        
        String packageDirectories = packageName.replace('.', '/');
        Path root = Paths.get(outputDirectory, packageDirectories);
        
        // This is a dirty workaround and only works if outputDirectory points to 'target' folder.
        // Needs proper design and logic ASAP.
        if (!overwrite && Files.exists(root)) {
            getLog().info("Path " + root + " already exists and overwrite flag is false, skipping");
            return;
        }
        
        getLog().info("Parsing " + vocabularies.length + " vocabularies");
        
        for (Vocabulary vocab : vocabularies) {
            getLog().info("Parsing " + vocab.getUrl());
            
            String template;
            switch (outputType) {
                default:
                case MODERN:
                    template = "templates/modern.stg";
                    break;
                case LEGACY:
                    template = "templates/legacy.stg";
                    break;
                case STRINGS:
                    template = "templates/strings.stg";
                    break;
            }
            
            STGroup templates = new STGroupFile(template);
            templates.registerRenderer(String.class, new StringRenderer());
            
            ST vcb = templates.getInstanceOf("vocab");

            try {
                vcb.add("properties", parseVocabulary(vocab));
            } catch (IOException e) {
                throw new MojoExecutionException("Could not parse vocabulary", e);
            }
            
            vcb.add("package", packageName);
            vcb.add("timestamp", ZonedDateTime.now());
            vcb.add("class", vocab.getPrefix());
            vcb.add("namespace", vocab.getNamespace());
            vcb.add("prefix", vocab.getPrefix());
            
            try {
                Files.createDirectories(root);
            } catch (IOException e) {
                throw new MojoExecutionException(e.getMessage());
            }
            
            String filename = vocab.getPrefix().toUpperCase() + ".java";
            Path file = Paths.get(outputDirectory, packageDirectories, filename);
            
            try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                writer.write(vcb.render());
            } catch (IOException e) {
                getLog().error("Failed to write vocabulary to file", e);
            }
        }
    }
    
    protected List<VocabularyProperty> parseVocabulary(Vocabulary vocab) throws IOException {
        URL url = vocab.getUrl();
        URLConnection connection = url.openConnection();
        
        Optional<RDFFormat> format = Rio.getParserFormatForFileName(url.getPath()); 
        if (!format.isPresent()) {
            connection.setRequestProperty(HttpHeaders.ACCEPT, "text/turtle, application/rdf+xml");
            format = Rio.getParserFormatForMIMEType(connection.getContentType()); // TODO handle null from getContentType
        }
        
        RDFParser parser = Rio.createParser(format.orElse(RDFFormat.TURTLE));
        
        List<Resource> classes = new ArrayList<>();
        List<Resource> objectProperties = new ArrayList<>();
        List<Resource> deprecated = new ArrayList<>();
        
        parser.setRDFHandler(new AbstractRDFHandler() {
            @Override
            public void handleStatement(Statement st) throws RDFHandlerException {
                Resource s = st.getSubject();
                IRI p = st.getPredicate();
                Value o = st.getObject();
                
                if (p.equals(RDF.TYPE) && (o.equals(OWL.CLASS) || o.equals(RDFS.CLASS))) {
                    if (!classes.contains(s)) {
                        classes.add(s);
                    }
                } else if (p.equals(RDF.TYPE) && 
                        (o.equals(OWL.OBJECTPROPERTY) || o.equals(OWL.DATATYPEPROPERTY) || o.equals(RDF.PROPERTY))) {
                    if (!objectProperties.contains(s)) {
                        objectProperties.add(s);
                    }
                } else if (p.equals(OWL_DEPRECATED) && ((SimpleLiteral)o).booleanValue()) {
                    deprecated.add(s);
                }
                
                else if (p.equals(VANN_PREFERREDNAMESPACEURI) && vocab.getNamespace() == null) {
                    vocab.setNamespace(o.stringValue());
                } else if (p.equals(VANN_PREFERREDNAMESPACEPREFIX) && vocab.getPrefix() == null) {
                    vocab.setPrefix(o.stringValue());
                }
            }
            
            @Override
            public void handleNamespace(String prefix, String uri) throws RDFHandlerException {
                if (uri.equals(vocab.getUrl().toString())) {
                    if (vocab.getPrefix() == null) {
                        vocab.setPrefix(prefix);
                    }
                    
                    if (vocab.getNamespace() == null) {
                        vocab.setNamespace(uri);
                    }
                }
            }
        });
        parser.parse(connection.getInputStream(), "");
        
        List<VocabularyProperty> properties = new ArrayList<>();
        
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
            
            Optional<?> match = classes.stream().map(r -> (IRI)r)
                    .map(IRI::getLocalName)
                    .filter(c -> c.equalsIgnoreCase(iri.getLocalName()))
                    .findFirst();
            String name = match.isPresent() ? "has_" + iri.getLocalName() : iri.getLocalName();
            
            // Bit of a workaround, needs a proper solution. Main problem is foaf, which provides
            // 'givenName' and 'givenname'. Capitalized that produces a duplicate. 
            Optional<?> clash = properties.stream()
                    .map(VocabularyProperty::getName)
                    .filter(n -> name.equalsIgnoreCase(n))
                    .findFirst();
            if (!clash.isPresent()) {
                property.setName(name);
                properties.add(property);
            }
        }
        
        return properties;
    }
}
