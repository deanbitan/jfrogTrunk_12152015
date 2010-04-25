/*
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2010 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.artifactory.api.mime;

import org.apache.commons.io.FilenameUtils;
import org.artifactory.api.maven.MavenNaming;
import org.artifactory.api.util.Pair;
import org.artifactory.common.ArtifactoryHome;
import org.artifactory.mime.MimeType;
import org.artifactory.util.PathUtils;

import javax.annotation.Nonnull;
import java.io.File;

/**
 * Used when deploying manually to artifactory, and classifying pom files. Only jar are queried to contain a pom file.
 *
 * @author freds
 * @author yoavl
 */
public class NamingUtils {
    public static final String METADATA_PREFIX = ":";

    public static MimeType getContentType(File file) {
        return getContentType(file.getName());
    }

    /**
     * @param path A file path
     * @return The content type for the path. Will return default content type if not mapped.
     */
    @Nonnull
    public static MimeType getContentType(String path) {
        String extension = PathUtils.getExtension(path);
        return getContentTypeByExtension(extension);
    }

    @Nonnull
    public static MimeType getContentTypeByExtension(String extension) {
        MimeType result = null;
        if (extension != null) {
            result = ArtifactoryHome.get().getMimeTypes().getByExtension(extension);
        }

        return result != null ? result : MimeType.def;
    }

    public static String getMimeTypeByPathAsString(String path) {
        MimeType ct = getContentType(path);
        return ct.getType();
    }

    public static boolean isChecksum(String path) {
        MimeType ct = getContentType(path);
        return MimeType.checksum.equalsIgnoreCase(ct.getType());
    }

    public static boolean isJarVariant(String path) {
        MimeType ct = getContentType(path);
        return MimeType.javaArchive.equalsIgnoreCase(ct.getType());
    }

    public static boolean isPom(String path) {
        MimeType ct = NamingUtils.getContentType(path);
        return "application/x-maven-pom+xml".equalsIgnoreCase(ct.getType());
    }

    public static Pair<String, String> getMetadataNameAndParent(String path) {
        int mdPrefixIdx = path.lastIndexOf(METADATA_PREFIX);
        String name = null;
        String parent = null;
        if (mdPrefixIdx >= 0) {
            name = path.substring(mdPrefixIdx + METADATA_PREFIX.length());
            parent = path.substring(0, mdPrefixIdx);
        } else {
            //Fallback to checking maven metadata
            final File file = new File(path);
            if (MavenNaming.MAVEN_METADATA_NAME.equals(file.getName())) {
                name = MavenNaming.MAVEN_METADATA_NAME;
                parent = file.getParent();
            }
        }
        return new Pair<String, String>(name, parent);
    }

    public static boolean isMetadata(String path) {
        String fileName = PathUtils.getName(path);
        if (fileName == null || fileName.length() == 0) {
            return false;
        }
        //First check for the metadata pattern of x/y/z/resourceName:metadataName
        if (fileName.contains(METADATA_PREFIX)) {
            return true;
        }
        //Fallback to checking maven metadata
        return MavenNaming.isMavenMetadataFileName(fileName);
    }

    /**
     * @param path The path to check
     * @return True if the path represents a checksum for metadata (ie metadata path and ends with checksum file extension)
     */
    public static boolean isMetadataChecksum(String path) {
        if (isChecksum(path)) {
            String checksumTargetFile = MavenNaming.getChecksumTargetFile(path);
            return isMetadata(checksumTargetFile);
        } else {
            return false;
        }
    }

    /**
     * @return True if the path points to a system file (e.g., maven index)
     */
    public static boolean isSystem(String path) {
        return MavenNaming.isIndex(path) || path.endsWith(".index");
    }

    /**
     * Return the name of the requested metadata. Should be called on a path after determining that it is indeed a
     * metadata path.
     * <pre>
     * getMetadataName("x/y/z/resourceName#file-info") = "file-info"
     * getMetadataName("x/y/z/resourceName/maven-metadata.xml") = "maven-metadata.xmlo"
     * </pre>
     *
     * @param path A metadata path in the pattern of x/y/z/resourceName#metadataName or a path that ends with
     *             maven-metadata.xml.
     * @return The metadata name from the path. Null if not valid.
     */
    public static String getMetadataName(String path) {
        //First check for the metadata pattern of x/y/z/resourceName#metadataName
        int mdPrefixIdx = path.lastIndexOf(METADATA_PREFIX);
        String name = null;
        if (mdPrefixIdx >= 0) {
            name = path.substring(mdPrefixIdx + METADATA_PREFIX.length());
        } else {
            //Fallback to checking maven metadata
            String fileName = PathUtils.getName(path);
            if (MavenNaming.isMavenMetadataFileName(fileName)) {
                name = MavenNaming.MAVEN_METADATA_NAME;
            }
        }
        return name;
    }

    public static String stripMetadataFromPath(String path) {
        int metadataPrefixIdx = path.lastIndexOf(NamingUtils.METADATA_PREFIX);
        if (metadataPrefixIdx >= 0) {
            path = path.substring(0, metadataPrefixIdx);
        }
        return path;
    }

    /**
     * Get the path of the metadata container. Assumes we already verified that this is a metadataPath.
     *
     * @param path
     * @return
     */
    public static String getMetadataParentPath(String path) {
        String metadataName = getMetadataName(path);
        return path.substring(0, path.lastIndexOf(metadataName) - 1);
    }

    public static boolean isSnapshotMetadata(String path) {
        //*-SNAPSHOT/*maven-metadata.xml or *-SNAPSHOT#maven-metadata.xml
        if (!isMetadata(path)) {
            return false;
        }
        String parent = getMetadataParentPath(path);
        return parent != null && parent.endsWith("-SNAPSHOT");
    }

    @SuppressWarnings({"UnusedDeclaration"})
    private static String getParameter(String path, String paramName) {
        String fileName = PathUtils.getName(path);
        String paramQueryPrefix = paramName + "=";
        int mdStart = fileName.lastIndexOf(paramQueryPrefix);
        if (mdStart > 0) {
            int mdEnd = fileName.indexOf('&', mdStart);
            String paramValue = fileName.substring(mdStart + paramQueryPrefix.length(),
                    mdEnd > 0 ? mdEnd : fileName.length());
            return paramValue;
        }
        return null;
    }

    /**
     * Recieves a metadata container path (/a/b/c.pom) and a metadata name (maven-metadata.xml) and returns the whole
     * path - "/a/b/c.pom:maven-metadata.xml".
     *
     * @param containerPath Path of metadata container
     * @param metadataName  Name of metadata item
     * @return String - complete path to metadata
     */
    public static String getMetadataPath(String containerPath, String metadataName) {
        if ((containerPath == null) || (metadataName == null)) {
            throw new IllegalArgumentException("Container path and metadata name cannot be null.");
        }
        String metadataPath = containerPath + METADATA_PREFIX + metadataName;
        return metadataPath;
    }

    /**
     * Recieves a jcr path of a metadata item/element (.../a/b/c.pom/artifactory:xml/metadataname/element/xml:text) and
     * Extracts the name of the metadata item (metadataname, in this case).
     *
     * @param path A jcr path of a metadata element/item
     * @return String - Name of metadata item
     */
    public static String getMetadataNameFromJcrPath(String path) {
        //Build the metadata prefix to search for
        String metadatPrefix = METADATA_PREFIX + "metadata/";
        int prefixStart = path.indexOf(metadatPrefix);

        //If the prefix isn't in the path, it is either not a jcr path or not a metadata item/element
        if (prefixStart < 0) {
            return "";
        }

        //Dispose of all the path before the metadata name
        int prefixEnd = prefixStart + metadatPrefix.length();
        String metadataPath = path.substring(prefixEnd);

        //Find where the name ends (either a forward slash, or the end of the path)
        int followingSlash = metadataPath.indexOf('/');
        if (followingSlash < 0) {
            followingSlash = metadataPath.length();
        }
        String metadataName = metadataPath.substring(0, followingSlash);
        return metadataName;
    }

    /**
     * @param classFilePath Path to a java class file (ends with .class)
     * @return Path of the matching java source path (.java).
     */
    public static String javaSourceNameFromClassName(String classFilePath) {
        String classFileName = FilenameUtils.getName(classFilePath);
        if (!"class".equals(FilenameUtils.getExtension(classFileName))) {
            return classFilePath;
        }

        String javaFileName;
        if (classFileName.indexOf('$') > 0) {
            // it's a subclass, take the first part (the main class name)
            javaFileName = classFileName.substring(0, classFileName.indexOf('$')) + ".java";
        } else {
            javaFileName = classFileName.replace(".class", ".java");
        }

        String javaFilePath = FilenameUtils.getFullPath(classFilePath) + javaFileName;
        return javaFilePath;
    }

    /**
     * @param fileName The file name
     * @return True if the filename represents a viewable file (ie, text based)
     */
    public static boolean isViewable(String fileName) {
        MimeType contentType = NamingUtils.getContentType(fileName);
        return contentType.isViewable();
    }
}