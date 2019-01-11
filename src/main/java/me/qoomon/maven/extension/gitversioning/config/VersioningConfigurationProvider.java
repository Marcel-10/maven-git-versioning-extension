package me.qoomon.maven.extension.gitversioning.config;

import com.google.common.collect.Lists;
import com.google.inject.Key;
import me.qoomon.maven.extension.gitversioning.BuildProperties;
import me.qoomon.maven.extension.gitversioning.ExtensionUtil;
import me.qoomon.maven.extension.gitversioning.config.model.Configuration;
import me.qoomon.maven.extension.gitversioning.config.model.VersionFormatDescription;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.session.scope.internal.SessionScope;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.logging.Logger;
import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;

import javax.inject.Inject;
import java.io.*;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

/**
 * Created by qoomon on 30/11/2016.
 */
@Component(role = VersioningConfigurationProvider.class, instantiationStrategy = "singleton")
public class VersioningConfigurationProvider {

    private final Logger logger;

    private static final String GIT_VERSIONING_PROPERTY_KEY = "gitVersioning";
    private static final String GIT_VERSIONING_ENVIRONMENT_VARIABLE_NAME = "MAVEN_GIT_VERSIONING";

    private static final String PROJECT_BRANCH_PROPERTY_KEY = "project.branch";
    private static final String PROJECT_BRANCH_ENVIRONMENT_VARIABLE_NAME = "MAVEN_PROJECT_BRANCH";

    private static final String PROJECT_TAG_PROPERTY_KEY = "project.tag";
    private static final String PROJECT_TAG_ENVIRONMENT_VARIABLE_NAME = "MAVEN_PROJECT_TAG";

    private static final String PROJECT_COMMIT_PROPERTY_KEY = "project.commit";
    private static final String PROJECT_COMMIT_ENVIRONMENT_VARIABLE_NAME = "MAVEN_PROJECT_COMMIT";

    private SessionScope sessionScope;

    @Inject
    public VersioningConfigurationProvider(Logger logger, SessionScope sessionScope) {
        this.logger = logger;
        this.sessionScope = sessionScope;
    }

    public VersioningConfiguration get() {
        final MavenSession session = sessionScope.scope(Key.get(MavenSession.class), null).get();

        List<VersionFormatDescription> branchVersionDescriptions = Lists.newArrayList(defaultBranchVersionFormat());
        List<VersionFormatDescription> tagVersionDescriptions = new LinkedList<>();
        VersionFormatDescription commitVersionDescription = defaultCommitVersionFormat();
        String propertiesFileName = null;
        boolean includeProperties = false;

        File configFile = ExtensionUtil.getConfigFile(session.getRequest(), BuildProperties.projectArtifactId());
        if (configFile.exists()) {
            Configuration configurationModel = loadConfiguration(configFile);
            branchVersionDescriptions.addAll(0, configurationModel.branches);
            tagVersionDescriptions.addAll(0, configurationModel.tags);
            if (configurationModel.commitVersionFormat != null) {
                commitVersionDescription = new VersionFormatDescription(".*", "", configurationModel.commitVersionFormat);
            }
            propertiesFileName = configurationModel.propertiesFileName;
            includeProperties = configurationModel.includeProperties;
        } else {
            logger.info("No configuration file found. Apply default configuration.");
        }

        String extensionToggle = session.getUserProperties().getProperty(GIT_VERSIONING_PROPERTY_KEY);
        if (extensionToggle == null) {
            extensionToggle = System.getenv(GIT_VERSIONING_ENVIRONMENT_VARIABLE_NAME);
        }
        boolean enabledExtension = extensionToggle == null || extensionToggle.equals("true");

        String providedBranch = session.getUserProperties().getProperty(PROJECT_BRANCH_PROPERTY_KEY);
        if (providedBranch == null) {
            providedBranch = System.getenv(PROJECT_BRANCH_ENVIRONMENT_VARIABLE_NAME);
        }

        String providedTag = session.getUserProperties().getProperty(PROJECT_TAG_PROPERTY_KEY);
        if (providedTag == null) {
            providedTag = System.getenv(PROJECT_TAG_ENVIRONMENT_VARIABLE_NAME);
        }

        // override branch if only tag is provided
        if (providedBranch == null && providedTag != null) {
            providedBranch = "";
        }

        if (providedBranch != null && !providedBranch.isEmpty()
                && providedTag != null && !providedTag.isEmpty()) {
            logger.warn("provided tag [" + providedTag + "] is ignored " +
                    "due to provided branch [" + providedBranch + "] !");
            providedTag = null;
        }

        String providedCommit = session.getUserProperties().getProperty(PROJECT_COMMIT_PROPERTY_KEY);
        if (providedCommit == null) {
            providedCommit = System.getenv(PROJECT_COMMIT_ENVIRONMENT_VARIABLE_NAME);
        }
        if(providedCommit != null && providedCommit.isEmpty()){
            providedCommit = null;
        }

        final VersioningConfiguration configuration = new VersioningConfiguration(enabledExtension, branchVersionDescriptions,
                tagVersionDescriptions, commitVersionDescription, providedBranch, providedTag, providedCommit,
                includeProperties);

        File propertiesFile = ExtensionUtil.getPropertiesFile(session.getRequest(), BuildProperties.projectArtifactId(), propertiesFileName);
        if (propertiesFile.exists()) {
            configuration.setProperties(loadProperties(propertiesFile));
        }

        return configuration;
    }

    private static VersionFormatDescription defaultBranchVersionFormat() {
        return new VersionFormatDescription(".*", "", "${branch}-SNAPSHOT");
    }

    private static VersionFormatDescription defaultCommitVersionFormat() {
        return new VersionFormatDescription(".*", "", "${commit}");
    }

    private Configuration loadConfiguration(File configFile) {
        try {
            logger.debug("load config from " + configFile);
            Serializer serializer = new Persister();
            return serializer.read(Configuration.class, configFile);
        } catch (Exception e) {
            throw new RuntimeException(configFile.toString(), e);
        }
    }

    private Properties loadProperties(File propertiesFile) {
        try {
            logger.debug("load properties from " + propertiesFile);
            InputStream stream = new FileInputStream(propertiesFile);
            Properties properties = new Properties();
            properties.load(stream);
            return properties;
        } catch (IOException e) {
            throw new RuntimeException(propertiesFile.toString(), e);
        }
    }
}
