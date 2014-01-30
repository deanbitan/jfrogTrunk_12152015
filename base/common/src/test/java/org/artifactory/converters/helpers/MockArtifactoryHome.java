package org.artifactory.converters.helpers;

import org.apache.commons.io.FileUtils;
import org.artifactory.common.ArtifactoryHome;
import org.artifactory.version.ArtifactoryVersion;
import org.artifactory.version.CompoundVersionDetails;

import java.io.File;
import java.io.IOException;
import java.util.Date;

/**
 * Author: gidis
 */
public class MockArtifactoryHome extends ArtifactoryHome {


    public MockArtifactoryHome() {
        super(new File(ConvertersManagerTestHelper.home + ".artifactory"));
    }

    public CompoundVersionDetails readRunningArtifactoryVersion() {
        return new CompoundVersionDetails(ArtifactoryVersion.v310, ArtifactoryVersion.v310.name(), "1",
                new Date().getTime());
    }

    public void writeBundledHomeArtifactoryProperties() {
        File artifactoryPropertiesFile = getHomeArtifactoryPropertiesFile();
        //Copy the artifactory.properties file into the data folder
        try {
            String text = "artifactory.version=" + ArtifactoryVersion.v310.getValue() + "\n" +
                    "artifactory.revision=1\n" +
                    "artifactory.release=1\n";
            //Copy from default
            FileUtils.writeStringToFile(artifactoryPropertiesFile, text);
        } catch (IOException e) {
            throw new RuntimeException("Could not copy " + ARTIFACTORY_PROPERTIES_FILE + " to " +
                    artifactoryPropertiesFile.getAbsolutePath(), e);
        }
    }

    public void writeBundledHaArtifactoryProperties() {
        File artifactoryPropertiesFile = getHaArtifactoryPropertiesFile();
        //Copy the artifactory.properties file into the data folder
        try {
            String text = "artifactory.version=" + ArtifactoryVersion.v310.getValue() + "\n" +
                    "artifactory.revision=1\n" +
                    "artifactory.release=1\n";
            //Copy from default
            FileUtils.writeStringToFile(artifactoryPropertiesFile, text);
        } catch (IOException e) {
            throw new RuntimeException("Could not copy " + ARTIFACTORY_PROPERTIES_FILE + " to " +
                    artifactoryPropertiesFile.getAbsolutePath(), e);
        }
    }
}