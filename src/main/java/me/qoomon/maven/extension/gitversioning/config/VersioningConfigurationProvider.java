package me.qoomon.maven.extension.gitversioning.config;

import com.google.common.collect.Lists;
import me.qoomon.maven.BuildProperties;
import me.qoomon.maven.extension.gitversioning.ExtensionUtil;
import me.qoomon.maven.extension.gitversioning.SessionScopeUtil;
import me.qoomon.maven.extension.gitversioning.config.model.Configuration;
import me.qoomon.maven.extension.gitversioning.config.model.VersionFormatDescription;
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

    private Logger logger;

    private SessionScope sessionScope;

    private VersioningConfiguration configuration;

    @Inject
    public VersioningConfigurationProvider(Logger logger, SessionScope sessionScope) {
        this.logger = logger;
        this.sessionScope = sessionScope;
    }

    public VersioningConfiguration get() {

        if (configuration == null) {

            MavenSession session = SessionScopeUtil.get(sessionScope, MavenSession.class).get();

            List<VersionFormatDescription> branchVersionDescriptions = Lists.newArrayList(defaultBranchVersionFormat());
            List<VersionFormatDescription> tagVersionDescriptions = new LinkedList<>();
            VersionFormatDescription commitVersionDescription = defaultCommitVersionFormat();
            String propertiesFileName = null;

            File configFile = ExtensionUtil.getConfigFile(session.getRequest(), BuildProperties.projectArtifactId());
            if (configFile.exists()) {

                Configuration configurationModel = loadConfiguration(configFile);
                branchVersionDescriptions.addAll(0, configurationModel.branches);
                tagVersionDescriptions.addAll(0, configurationModel.tags);
                if (configurationModel.commitVersionFormat != null) {
                    commitVersionDescription = new VersionFormatDescription(".*", "", configurationModel.commitVersionFormat);
                }
                propertiesFileName = configurationModel.propertiesFileName;
            } else {
                logger.info("No configuration file found. Apply default configuration.");
            }

            configuration = new VersioningConfiguration(branchVersionDescriptions, tagVersionDescriptions, commitVersionDescription);

            File propertiesFile = ExtensionUtil.getPropertiesFile(session.getRequest(), BuildProperties.projectArtifactId(), propertiesFileName);
            if (propertiesFile.exists()) {
                configuration.setProperties(loadProperties(propertiesFile));
            }
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
