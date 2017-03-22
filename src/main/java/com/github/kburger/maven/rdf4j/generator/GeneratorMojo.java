package com.github.kburger.maven.rdf4j.generator;

import static com.google.common.io.Files.getFileExtension;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
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
import org.eclipse.rdf4j.model.ValueFactory;
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
    private static final String CACHE_DIR = ".cache";
    private static final String PLUGIN_CACHE_DIR = "rdf4j-generator";
    
    // FIXME remove once the plugin can bootstrap itself?
    private static final IRI OWL_DEPRECATED;
    private static final IRI VANN_PREFERREDNAMESPACEURI;
    private static final IRI VANN_PREFERREDNAMESPACEPREFIX;
    
    static {
        ValueFactory factory = SimpleValueFactory.getInstance();
        
        OWL_DEPRECATED = factory.createIRI(OWL.NAMESPACE, "deprecated");
        VANN_PREFERREDNAMESPACEURI = factory.createIRI("http://purl.org/vocab/vann/", "preferredNamespaceUri");
        VANN_PREFERREDNAMESPACEPREFIX = factory.createIRI("http://purl.org/vocab/vann/", "preferredNamespacePrefix");
    }
    
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
    /**
     * Flag to add a 'javax.annotation.Generated' annotation to the generated classes. 
     */
    @Parameter(required = false, defaultValue = "true")
    private boolean addGeneratedAnnotation;
    /**
     * Flag to indicate whether files are to be cached.
     */
    @Parameter(required = false, defaultValue = "true")
    private boolean cacheFiles;
    /**
     * Flag to indicate whether properties or classes marked deprecated should be included.
     */
    @Parameter(required = false, defaultValue = "false")
    private boolean includeDeprecated;
    /**
     * Location of the local .m2 repository.
     */
    @Parameter(defaultValue = "${settings.localRepository}", readonly = true)
    private String localRepository;
    
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        getLog().debug("Adding " + outputDirectory + " as compile source root");
        project.addCompileSourceRoot(outputDirectory);
        
        for (Vocabulary vocabulary : vocabularies) {
            getLog().info("Parsing " + vocabulary.getUrl());
            
            InputStream inputStream;
            try {
                inputStream = getFileInputStream(vocabulary.getUrl(), cacheFiles);
            } catch (IOException e) {
                getLog().error("Could not get file from " + vocabulary.getUrl(), e);
                throw new MojoExecutionException("Could not get file from " + vocabulary.getUrl(), e);
            }
            
            getLog().debug("Input stream retrieved, continueing to parsing the file");
            
            RDFFormat format;
            try {
                format = getRdfFormat(vocabulary.getUrl());
            } catch (IOException e) {
                throw new MojoExecutionException("Coult not get RDF format for " + vocabulary.getUrl(), e);
            }
            
            List<VocabularyProperty> properties = parseVocabulary(vocabulary, format, inputStream);
            
            writeSourceFile(vocabulary, properties);
        }
    }
    
    private InputStream getFileInputStream(URL url, boolean cache) throws IOException, MojoExecutionException {
        if (cache) {
            getLog().debug("Checking for " + url + " in cache");
            
            Path cacheDirectory = Paths.get(localRepository, CACHE_DIR, PLUGIN_CACHE_DIR);
            Files.createDirectories(cacheDirectory);
            getLog().debug("Cache base directory is " + cacheDirectory);
            
            String fileName = new File(url.getFile()).getName();
            String extension = getFileExtension(fileName);
            
            getLog().debug("File name and extension are '" + fileName + "' and '" + extension + "'");
            
            if ("".equals(extension)) {
                getLog().debug("Extension is empty, trying to resolve it");
                
                RDFFormat format = getRdfFormat(url);
                
                extension = format.getDefaultFileExtension();
                fileName += "." + extension;
                getLog().debug("Determined extension on " + extension);
            }
            
            Path fileLocation = Paths.get(localRepository, ".cache", "rdf4j-generator", fileName);
            getLog().debug("File location is " + fileLocation);
            
            if (!Files.exists(fileLocation)) {
                getLog().debug("File does not exist yet, downloading it");
                URLConnection connection = url.openConnection();
                connection.setRequestProperty(HttpHeaders.ACCEPT, "text/turtle;q=1,application/rdf+xml;q=0.5");
                Files.copy(connection.getInputStream(), fileLocation, StandardCopyOption.REPLACE_EXISTING);
                getLog().debug("File downloaded");
            }
            
            getLog().debug("Returning cached file");
            return Files.newInputStream(fileLocation);
        } else {
            getLog().debug("Cache was disabled, so returning URL stream content");
            return url.openStream();
        }
    }
    
    private RDFFormat getRdfFormat(URL url) throws IOException {
        Optional<RDFFormat> format = Rio.getParserFormatForFileName(url.getPath());
        
        if (!format.isPresent()) {
            URLConnection connection = url.openConnection();
            
            connection.setRequestProperty(HttpHeaders.ACCEPT, "text/turtle;q=1,application/rdf+xml;q=0.5");
            format = Rio.getParserFormatForMIMEType(connection.getContentType());
        }
        
        return format.orElse(RDFFormat.TURTLE);
    }
    
    private List<VocabularyProperty> parseVocabulary(Vocabulary vocabulary, RDFFormat format, InputStream inputStream) throws MojoExecutionException {
        RDFParser parser = Rio.createParser(format);
        
        List<IRI> classes = new ArrayList<>();
        List<IRI> properties = new ArrayList<>();
        List<IRI> deprecated = new ArrayList<>();
        
        parser.setRDFHandler(new AbstractRDFHandler() {
            @Override
            public void handleNamespace(String prefix, String uri) throws RDFHandlerException {
                if (uri.equals(vocabulary.getUrl().toString())) {
                    if (vocabulary.getPrefix() == null) {
                        vocabulary.setPrefix(prefix);
                    }
                    
                    if (vocabulary.getNamespace() == null) {
                        vocabulary.setNamespace(uri);
                    }
                }
            }
            
            @Override
            public void handleStatement(Statement st) throws RDFHandlerException {
                Resource s = st.getSubject();
                IRI p = st.getPredicate();
                Value o = st.getObject();
                
                if (p.equals(VANN_PREFERREDNAMESPACEURI) && vocabulary.getNamespace() == null) {
                    vocabulary.setNamespace(o.stringValue());
                } else if (p.equals(VANN_PREFERREDNAMESPACEPREFIX) && vocabulary.getPrefix() == null) {
                    vocabulary.setPrefix(o.stringValue());
                }
                
                if (s instanceof IRI && vocabulary.getNamespace() != null) {
                    String namespace = ((IRI)s).getNamespace();
                    if (!namespace.equals(vocabulary.getNamespace())) {
                        return;
                    }
                }
                
                if (p.equals(RDF.TYPE)) {
                    if (o.equals(OWL.CLASS) || o.equals(RDFS.CLASS)) {
                        if (!classes.contains(s)) {
                            classes.add((IRI)s);
                        }
                    }
                    
                    if (o.equals(OWL.OBJECTPROPERTY) || o.equals(OWL.DATATYPEPROPERTY) || o.equals(RDF.PROPERTY)) {
                        if (!properties.contains(s)) {
                            properties.add((IRI)s);
                        }
                    }
                } else if (p.equals(OWL_DEPRECATED) && ((SimpleLiteral)o).booleanValue()) {
                    deprecated.add((IRI)s);
                }
            }
        });
        
        try {
            parser.parse(inputStream, "");
        } catch (IOException e) {
            getLog().warn("Could not parse " + vocabulary.getUrl() + ": " + e.getMessage(), e);
            throw new MojoExecutionException("", e);
        }
        
        if (!includeDeprecated) {
            classes.removeAll(deprecated);
            properties.removeAll(deprecated);
        }
        
        List<VocabularyProperty> vocabProperties = new ArrayList<>(classes.size() + properties.size());
        
        for (IRI clazz : classes) {
            VocabularyProperty p = new VocabularyProperty();
            p.setIri(clazz);
            p.setName(clazz.getLocalName());
            
            if (includeDeprecated && deprecated.contains(clazz)) {
                p.setDeprecated(true);
            }
            
            vocabProperties.add(p);
        }
        
        for (IRI property : properties) {
            VocabularyProperty p = new VocabularyProperty();
            p.setIri(property);
            
            Optional<?> match = classes.stream()
                    .map(IRI::getLocalName)
                    .filter(clazz -> clazz.equalsIgnoreCase(property.getLocalName()))
                    .findAny();
            String name = match.isPresent() ? "has_" + property.getLocalName() : property.getLocalName();
            
            Optional<?> clash = vocabProperties.stream()
                    .map(VocabularyProperty::getName)
                    .filter(name::equalsIgnoreCase)
                    .findFirst();
            if (!clash.isPresent()) {
                p.setName(name);
                
                if (includeDeprecated && deprecated.contains(property)) {
                    p.setDeprecated(true);
                }
                
                vocabProperties.add(p);
            }
        }
        
        return vocabProperties;
    }
    
    private void writeSourceFile(Vocabulary vocabulary, List<VocabularyProperty> properties) {
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
        
        ST vocab = templates.getInstanceOf("vocab");
        
        vocab.add("package", packageName);
        vocab.add("addGeneratedAnnotation", addGeneratedAnnotation);
        vocab.add("generator", this.getClass().getName());
        vocab.add("timestamp", ZonedDateTime.now());
        vocab.add("class", vocabulary.getPrefix());
        vocab.add("namespace", vocabulary.getNamespace());
        vocab.add("prefix", vocabulary.getPrefix());
        vocab.add("properties", properties);
        
        // create the package hierarchy
        String hierarchy = packageName.replace('.', '/');
        Path root = Paths.get(outputDirectory, hierarchy);
        
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            getLog().warn("Could not create package hierarchy " + root + ": " + e.getMessage(), e);
        }
        
        String fileName = vocabulary.getPrefix().toUpperCase() + ".java";
        Path path = Paths.get(outputDirectory, hierarchy, fileName);
        
        if (!overwrite && Files.exists(path)) {
            getLog().info(path + " already exists and overwrite is set to false, skipping");
        } else {
            try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                writer.write(vocab.render());
            } catch (IOException e) {
                getLog().warn("Could not write file " + path + ": " + e.getMessage(), e);
            }
        }
    }
}
