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

package org.artifactory.build;

import com.gc.iotools.stream.is.InputStreamFromOutputStream;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.thoughtworks.xstream.XStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.AbstractFileFilter;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.lang.StringUtils;
import org.apache.jackrabbit.util.Text;
import org.artifactory.addon.AddonsManager;
import org.artifactory.api.build.BasicBuildInfo;
import org.artifactory.api.build.ImportableExportableBuild;
import org.artifactory.api.cache.ArtifactoryCache;
import org.artifactory.api.cache.Cache;
import org.artifactory.api.cache.CacheService;
import org.artifactory.api.common.MultiStatusHolder;
import org.artifactory.api.config.ExportSettings;
import org.artifactory.api.config.ImportSettings;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.fs.ChecksumInfo;
import org.artifactory.api.fs.ChecksumsInfo;
import org.artifactory.api.fs.FileInfo;
import org.artifactory.api.jackson.JacksonFactory;
import org.artifactory.api.md.Properties;
import org.artifactory.api.mime.ChecksumType;
import org.artifactory.api.repo.RepoPath;
import org.artifactory.api.repo.exception.RepositoryRuntimeException;
import org.artifactory.api.rest.artifact.MoveCopyResult;
import org.artifactory.api.rest.constant.BuildRestConstants;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.xstream.XStreamFactory;
import org.artifactory.build.cache.ChecksumPair;
import org.artifactory.build.cache.MissingChecksumCallable;
import org.artifactory.cache.InternalCacheService;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.jcr.JcrPath;
import org.artifactory.jcr.JcrService;
import org.artifactory.jcr.JcrSession;
import org.artifactory.jcr.fs.FileInfoProxy;
import org.artifactory.jcr.md.MetadataDefinitionService;
import org.artifactory.log.LoggerFactory;
import org.artifactory.repo.jcr.JcrHelper;
import org.artifactory.schedule.CachedThreadPoolTaskExecutor;
import org.artifactory.search.InternalSearchService;
import org.artifactory.spring.Reloadable;
import org.artifactory.version.CompoundVersionDetails;
import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonToken;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ObjectNode;
import org.jfrog.build.api.Build;
import org.jfrog.build.api.BuildFileBean;
import org.jfrog.build.api.Dependency;
import org.jfrog.build.api.Module;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.jcr.Item;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Calendar;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

import static org.apache.jackrabbit.JcrConstants.JCR_LASTMODIFIED;
import static org.artifactory.jcr.JcrTypes.*;
import static org.artifactory.repo.jcr.JcrHelper.*;

/**
 * Build service main implementation
 *
 * @author Noam Y. Tenne
 */
@Service
@Reloadable(beanClass = InternalBuildService.class, initAfter = {JcrService.class, InternalCacheService.class})
public class BuildServiceImpl implements InternalBuildService {
    private static final Logger log = LoggerFactory.getLogger(BuildServiceImpl.class);

    private static final String EXPORTABLE_BUILD_VERSION = "v1";

    @Autowired
    private AddonsManager addonsManager;

    @Autowired
    private AuthorizationService authorizationService;

    @Autowired
    private CachedThreadPoolTaskExecutor executor;

    @Autowired
    private CacheService cacheService;

    @Autowired
    private JcrService jcrService;

    @Autowired
    private MetadataDefinitionService metadataDefinitionService;

    @Autowired
    private InternalSearchService searchService;

    /**
     * Keep a cache for each type of checksums because we can get requests for different types of checksum for the same
     * item
     */
    private Cache<String, FutureTask<ChecksumPair>> md5Cache;
    private Cache<String, FutureTask<ChecksumPair>> sha1Cache;

    public void init() {
        md5Cache = cacheService.getCache(ArtifactoryCache.buildItemMissingMd5);
        sha1Cache = cacheService.getCache(ArtifactoryCache.buildItemMissingSha1);
        //Create initial builds folder
        jcrService.getOrCreateUnstructuredNode(getJcrPath().getBuildsJcrRootPath());
    }

    public void reload(CentralConfigDescriptor oldDescriptor) {
        //Nothing to reload
    }

    public void destroy() {
        //Nothing to destroy
    }

    public void convert(CompoundVersionDetails source, CompoundVersionDetails target) {
    }

    public void addBuild(final Build build) {
        String buildName = build.getName();
        String escapedBuildName = escapeAndGetJcrCompatibleString(buildName);
        String buildNumber = build.getNumber();
        String escapedBuildNumber = escapeAndGetJcrCompatibleString(buildNumber);
        String started = build.getStarted();
        String escapedStarted = escapeAndGetJcrCompatibleString(started);
        String currentUser = authorizationService.currentUsername();

        log.debug("Adding info for build '{}' #{}", buildName, buildNumber);

        build.setArtifactoryPrincipal(currentUser);

        populateMissingChecksums(build.getModules());

        Set<String> artifactChecksums = Sets.newHashSet();
        Set<String> dependencyChecksums = Sets.newHashSet();
        collectModuleChecksums(build.getModules(), artifactChecksums, dependencyChecksums);

        Node buildNumberNode = createBuildNumberNode(escapedBuildName, escapedBuildNumber);

        InputStreamFromOutputStream stream = null;
        try {
            stream = new InputStreamFromOutputStream() {

                @Override
                protected Object produce(OutputStream outputStream) throws Exception {
                    JsonGenerator jsonGenerator = JacksonFactory.createJsonGenerator(outputStream);
                    jsonGenerator.writeObject(build);
                    return null;
                }
            };
            jcrService.setStream(buildNumberNode, escapedStarted, stream, BuildRestConstants.MT_BUILD_INFO, currentUser,
                    false);
        } catch (Exception e) {
            String errorMessage = String.format("An error occurred while writing JSON data to the node of build name " +
                    "'%s', number '%s', started at '%s'.", buildName, buildNumber, started);
            throw new RuntimeException(errorMessage, e);
        } finally {
            IOUtils.closeQuietly(stream);
        }

        Node buildStartedNode = jcrService.getOrCreateUnstructuredNode(buildNumberNode, escapedStarted);

        try {
            saveBuildFileChecksums(buildStartedNode, artifactChecksums, dependencyChecksums);
        } catch (Exception e) {
            String errorMessage = String.format("An error occurred while saving checksum properties on the node of " +
                    "build name '%s', number '%s', started at '%s'.", buildName, buildNumber, started);
            throw new RuntimeException(errorMessage, e);
        }
        log.debug("Added info for build '{}' #{}", buildName, buildNumber);
    }

    public Node getOrCreateBuildsRootNode() {
        return jcrService.getOrCreateUnstructuredNode(getJcrPath().getBuildsJcrRootPath());
    }

    public Build getBuild(String buildName, String buildNumber, String buildStarted) {
        String buildPath = getBuildPathFromParams(buildName, buildNumber, buildStarted);
        return getBuild(buildPath);
    }

    public Build getBuild(Node buildNode) {
        if (buildNode != null) {
            try {
                return getBuild(buildNode.getPath());
            } catch (RepositoryException e) {
                String nodeToString = buildNode.toString();
                log.error("Unable to retrieve the path of node '{}': '{}'", nodeToString, e.getMessage());
                log.debug("Unable to retrieve the path of node '" + nodeToString + "'.", e);
            }
        }
        return null;
    }

    public String getBuildAsJson(String buildName, String buildNumber, String buildStarted) {
        String buildPath = getBuildPathFromParams(buildName, buildNumber, buildStarted);
        return jcrService.getString(buildPath);
    }

    public void deleteBuild(String buildName) {
        String buildPath = JcrPath.get().getBuildsJcrPath(escapeAndGetJcrCompatibleString(buildName));
        jcrService.delete(buildPath);
    }

    public void deleteBuild(BasicBuildInfo basicBuildInfo) {
        String buildPath = getBuildPathFromParams(basicBuildInfo.getName(), basicBuildInfo.getNumber(),
                basicBuildInfo.getStarted());
        jcrService.delete(buildPath);
    }

    public Build getLatestBuildByNameAndNumber(String buildName, String buildNumber) {
        if (StringUtils.isBlank(buildName)) {
            return null;
        }
        String escapedBuildName = escapeAndGetJcrCompatibleString(buildName);
        String escapedBuildNumber = escapeAndGetJcrCompatibleString(buildNumber);
        StringBuilder pathBuilder = new StringBuilder();
        String absPath = pathBuilder.append(JcrPath.get().getBuildsJcrRootPath()).append("/").append(escapedBuildName).
                append("/").append(escapedBuildNumber).toString();
        if (!jcrService.itemNodeExists(absPath)) {
            return null;
        }

        Node buildNumberNode = jcrService.getNode(absPath);
        if (buildNumberNode != null) {
            Build buildToReturn = null;
            try {
                Node chosenNode = null;
                Calendar chosenCreated = null;

                NodeIterator childrenIterator = buildNumberNode.getNodes();
                while (childrenIterator.hasNext()) {
                    Node child = (Node) childrenIterator.next();
                    Calendar childCreated = child.getProperty(PROP_ARTIFACTORY_CREATED).getDate();
                    if (chosenNode == null || chosenCreated.before(childCreated)) {
                        chosenNode = child;
                        chosenCreated = childCreated;
                    }
                }

                if (chosenNode != null) {
                    buildToReturn = getBuild(chosenNode);
                }
            } catch (RepositoryException e) {
                throw new RepositoryRuntimeException(e);
            }

            return buildToReturn;
        }

        return null;
    }

    public Set<BasicBuildInfo> searchBuildsByName(String buildName) {
        return getTransactionalMe().transactionalSearchBuildsByName(buildName);
    }

    public Set<BasicBuildInfo> transactionalSearchBuildsByName(String buildName) {
        Set<BasicBuildInfo> results = Sets.newHashSet();

        if (StringUtils.isBlank(buildName)) {
            return results;
        }

        String escapedBuildName = escapeAndGetJcrCompatibleString(buildName);
        String absPath = new StringBuilder().append(JcrPath.get().getBuildsJcrRootPath()).append("/").
                append(escapedBuildName).toString();
        Node buildNameNode = jcrService.getNode(absPath);

        if (buildNameNode != null) {
            try {
                NodeIterator buildNumberNodes = buildNameNode.getNodes();

                while (buildNumberNodes.hasNext()) {
                    Node buildNumberNode = buildNumberNodes.nextNode();
                    String escapedBuildNumber = buildNumberNode.getName();
                    String buildNumber = unEscapeAndGetJcrCompatibleString(escapedBuildNumber);

                    NodeIterator buildStartedNodes = buildNumberNode.getNodes();

                    while (buildStartedNodes.hasNext()) {

                        Node buildStartedNode = buildStartedNodes.nextNode();
                        String decodedBuildStarted = unEscapeAndGetJcrCompatibleString(buildStartedNode.getName());

                        results.add(new BasicBuildInfo(buildName, buildNumber, decodedBuildStarted));
                    }
                }
            } catch (RepositoryException e) {
                throw new RepositoryRuntimeException(e);
            }
        }

        return results;
    }

    public Set<BasicBuildInfo> searchBuildsByNameAndNumber(String buildName, String buildNumber) {
        return getTransactionalMe().transactionalSearchBuildsByNameAndNumber(buildName, buildNumber);
    }

    public Set<BasicBuildInfo> transactionalSearchBuildsByNameAndNumber(String buildName, String buildNumber) {
        Set<BasicBuildInfo> results = Sets.newHashSet();

        if (StringUtils.isBlank(buildName) || StringUtils.isBlank(buildNumber)) {
            return results;
        }

        String escapedBuildName = escapeAndGetJcrCompatibleString(buildName);
        String escapedBuildNumber = escapeAndGetJcrCompatibleString(buildNumber);
        String absPath = new StringBuilder().append(JcrPath.get().getBuildsJcrRootPath()).append("/").
                append(escapedBuildName).append("/").append(escapedBuildNumber).toString();

        Node buildNumberNode = jcrService.getNode(absPath);

        if (buildNumberNode != null) {
            try {
                NodeIterator buildStartedNodes = buildNumberNode.getNodes();

                while (buildStartedNodes.hasNext()) {

                    Node buildStartedNode = buildStartedNodes.nextNode();
                    String decodedBuildStarted = unEscapeAndGetJcrCompatibleString(buildStartedNode.getName());

                    results.add(new BasicBuildInfo(buildName, buildNumber, decodedBuildStarted));
                }

            } catch (RepositoryException e) {
                throw new RepositoryRuntimeException(e);
            }
        }

        return results;
    }

    public FileInfo getBestMatchingResult(Set<RepoPath> searchResults, Map<RepoPath, Properties> resultProperties,
            String buildName, String buildNumber) {

        if (resultProperties.isEmpty()) {
            return getLatestItem(searchResults);
        } else {
            return matchResultBuildNameAndNumber(searchResults, resultProperties, buildName, buildNumber);
        }
    }

    public void exportTo(ExportSettings settings) {
        MultiStatusHolder multiStatusHolder = settings.getStatusHolder();
        multiStatusHolder.setStatus("Starting build info export", log);

        File buildsFolder = new File(settings.getBaseDir(), "builds");
        if (!buildsFolder.exists()) {
            try {
                FileUtils.forceMkdir(buildsFolder);
            } catch (IOException e) {
                multiStatusHolder.setError("Failed to create builds backup dir: " + buildsFolder, e, log);
                return;
            }
        }

        try {
            JcrSession session = jcrService.getManagedSession();
            Item buildRootItem = session.getItem(JcrPath.get().getBuildsJcrRootPath());
            if (!buildRootItem.isNode()) {
                multiStatusHolder
                        .setError("Found build root JCR item, but it's not a node. Build export was not performed."
                                , log);
                return;
            }
            Node buildRoot = (Node) buildRootItem;
            NodeIterator buildNameNodes = buildRoot.getNodes();

            long exportedBuildCount = 1;
            while (buildNameNodes.hasNext()) {
                Node buildNameNode = buildNameNodes.nextNode();
                String buildName = buildNameNode.getName();

                NodeIterator buildNumberNodes = buildNameNode.getNodes();

                while (buildNumberNodes.hasNext()) {
                    Node buildNumberNode = buildNumberNodes.nextNode();
                    String escapedBuildNumber = buildNumberNode.getName();
                    String buildNumber = unEscapeAndGetJcrCompatibleString(escapedBuildNumber);

                    NodeIterator buildStartedNodes = buildNumberNode.getNodes();

                    while (buildStartedNodes.hasNext()) {
                        Node buildStartedNode = buildStartedNodes.nextNode();
                        try {
                            exportBuild(settings, buildStartedNode, buildName, buildNumber, exportedBuildCount,
                                    buildsFolder);
                            exportedBuildCount++;
                        } catch (Exception e) {
                            String errorMessage = String.format("Failed to export build info: %s:%s", buildName,
                                    buildNumber);
                            if (settings.isFailFast()) {
                                throw new Exception(errorMessage, e);
                            }
                            multiStatusHolder.setError(errorMessage, e, log);
                        }
                    }
                }
            }
        } catch (Exception e) {
            multiStatusHolder.setError("Error occurred during build info export.", e, log);
        }
        multiStatusHolder.setStatus("Finished build info export", log);
    }

    public void importFrom(ImportSettings settings) {
        MultiStatusHolder multiStatusHolder = settings.getStatusHolder();
        multiStatusHolder.setStatus("Starting build info import", log);

        try {
            // delete existing root builds node
            jcrService.delete(getJcrPath().getBuildsJcrRootPath());
            getTransactionalMe().getOrCreateBuildsRootNode();
        } catch (Exception e) {
            multiStatusHolder.setError("Failed to delete builds root node", e, log);
            return;
        }

        File buildsFolder = new File(settings.getBaseDir(), "builds");
        String buildsFolderPath = buildsFolder.getPath();
        if (!buildsFolder.exists()) {
            multiStatusHolder.setStatus("'" + buildsFolderPath + "' folder is either non-existant or not a " +
                    "directory. Build info import was not performed", log);
            return;
        }

        IOFileFilter buildExportFileFilter = new AbstractFileFilter() {
            @Override
            public boolean accept(File file) {
                String fileName = file.getName();
                return fileName.startsWith("build") && fileName.endsWith(".xml");
            }
        };

        @SuppressWarnings({"unchecked"})
        Collection<File> buildExportFiles =
                FileUtils.listFiles(buildsFolder, buildExportFileFilter, DirectoryFileFilter.DIRECTORY);

        if (buildExportFiles.isEmpty()) {
            multiStatusHolder.setStatus("'" + buildsFolderPath + "' folder does not contain build export files. " +
                    "Build info import was not performed", log);
            return;
        }

        importBuildFiles(settings, buildExportFiles);
        multiStatusHolder.setStatus("Finished build info import", log);
    }

    public void importBuild(ImportSettings settings, ImportableExportableBuild build) throws Exception {
        MultiStatusHolder multiStatusHolder = settings.getStatusHolder();

        String buildName = escapeAndGetJcrCompatibleString(build.getBuildName());
        String buildNumber = escapeAndGetJcrCompatibleString(build.getBuildNumber());
        String buildStarted = escapeAndGetJcrCompatibleString(build.getBuildStarted());

        multiStatusHolder.setDebug(
                String.format("Beginning import of build: %s:%s:%s", buildName, buildNumber, buildStarted), log);
        Node buildNumberNode = createBuildNumberNode(buildName, buildNumber);

        InputStream buildInputStream = null;
        try {
            buildInputStream = IOUtils.toInputStream(build.getJson());
            jcrService.setStream(buildNumberNode, buildStarted, buildInputStream, build.getMimeType(),
                    build.getCreatedBy(), false);
        } finally {
            IOUtils.closeQuietly(buildInputStream);
        }

        Node buildStartedNode = jcrService.getNode(buildNumberNode, buildStarted);
        setCalenderProperty(buildStartedNode, PROP_ARTIFACTORY_CREATED, build.getCreated());
        setCalenderProperty(buildStartedNode, PROP_ARTIFACTORY_LAST_MODIFIED, build.getLastModified());
        setStringProperty(buildStartedNode, PROP_ARTIFACTORY_LAST_MODIFIED_BY, build.getLastModifiedBy());

        Node resNode = getResourceNode(buildStartedNode);
        resNode.setProperty(JCR_LASTMODIFIED, build.getLastModified());

        // set the current build info checksums
        ChecksumsInfo checksumsInfo = build.getChecksumsInfo();
        for (ChecksumInfo checksum : checksumsInfo.getChecksums()) {
            String actualChecksum = checksum.getActual();
            ChecksumType checksumType = checksum.getType();
            String propName = checksumType.getActualPropName();
            setStringProperty(buildStartedNode, propName, actualChecksum);
        }

        // set the artifacts and dependencies checksums
        saveBuildFileChecksums(buildStartedNode, build.getArtifactChecksums(), build.getDependencyChecksums());
        multiStatusHolder.setDebug(
                String.format("Finished import of build: %s:%s:%s", buildName, buildNumber, buildStarted), log);
    }

    public Set<String> findScopes(Build build) {
        final Set<String> scopes = Sets.newHashSet();
        if (build.getModules() != null) {
            for (Module module : build.getModules()) {
                if (module.getDependencies() != null) {
                    for (Dependency dependency : module.getDependencies()) {
                        if (dependency.getScopes() != null) {
                            scopes.addAll(dependency.getScopes());
                        }
                    }
                }
            }
        }
        return scopes;
    }

    public String getBuildCiServerUrl(BasicBuildInfo basicBuildInfo) throws IOException {
        String buildPath = getBuildPathFromParams(basicBuildInfo.getName(), basicBuildInfo.getNumber(),
                basicBuildInfo.getStarted());
        InputStream jsonStream = null;
        JsonParser parser = null;
        try {
            jsonStream = jcrService.getStream(buildPath);
            if (jsonStream != null) {
                parser = JacksonFactory.createJsonParser(jsonStream);

                if (parser.nextToken() != JsonToken.START_OBJECT) {
                    throw new IOException("Expected data stream to start with an object.");
                }

                while (parser.nextToken() != JsonToken.END_OBJECT) {
                    String fieldName = parser.getCurrentName();

                    parser.nextToken();
                    if ("url".equals(fieldName)) {
                        String url = parser.getText();
                        //The parser may take null literally
                        if (StringUtils.isBlank(url) || "null".equals(url)) {
                            return null;
                        }
                        return url;
                    }
                }
            }
        } finally {
            if (jsonStream != null) {
                IOUtils.closeQuietly(jsonStream);
            }
            if (parser != null) {
                parser.close();
            }
        }

        return null;
    }

    public MoveCopyResult moveOrCopyBuildItems(boolean move, BasicBuildInfo basicBuildInfo, String targetRepoKey,
            boolean artifacts, boolean dependencies, List<String> scopes, boolean dryRun) {
        BuildItemMoveCopyHelper itemMoveCopyHelper = new BuildItemMoveCopyHelper();
        return itemMoveCopyHelper.moveOrCopy(move, basicBuildInfo, targetRepoKey, artifacts, dependencies, scopes,
                dryRun);
    }

    public void renameBuilds(String from, String to) {
        Set<BasicBuildInfo> buildsToRename = searchBuildsByName(from);
        if (buildsToRename.isEmpty()) {
            log.error("Could not find builds by the name '{}'. No builds were renamed.", from);
            return;
        }

        for (BasicBuildInfo buildToRename : buildsToRename) {
            try {
                getTransactionalMe().renameBuildContent(buildToRename, to);
                log.info("Renamed build number '{}' that started at '{}' from '{}' to '{}'.", new String[]{
                        buildToRename.getNumber(), buildToRename.getStarted(), buildToRename.getName(), to});
            } catch (Exception e) {
                log.error("Failed to rename build: '{}' #{} that started at {}.", new String[]{buildToRename.getName(),
                        buildToRename.getNumber(), buildToRename.getStarted()});
            }
        }

        try {
            getTransactionalMe().renameBuildNode(from, to);
            log.info("Renamed build node from '{}' to '{}'.", from, to);
        } catch (Exception e) {
            log.error("Failed to rename JCR build node from '{}' to '{}'.", from, to);
        }
    }

    private String escapeAndGetJcrCompatibleString(String toEscape) {
        return Text.escapeIllegalJcrChars(toEscape);
    }

    /**
     * Locates and fills in missing checksums of the module artifacts and dependencies
     *
     * @param modules Modules to populate
     */
    private void populateMissingChecksums(List<Module> modules) {
        if (modules != null) {
            for (Module module : modules) {
                handleBeanPopulation(module.getArtifacts());
                handleBeanPopulation(module.getDependencies());
            }
        }
    }

    /**
     * Locates and fills in missing checksums of a build file bean
     *
     * @param buildFiles List of build files to populate
     */
    private void handleBeanPopulation(List<? extends BuildFileBean> buildFiles) {
        if (buildFiles != null) {
            for (BuildFileBean buildFile : buildFiles) {
                boolean sha1Exists = StringUtils.isNotBlank(buildFile.getSha1());
                boolean md5Exists = StringUtils.isNotBlank(buildFile.getMd5());

                //If the bean has both or none of the checksums, return
                if ((sha1Exists && md5Exists) || ((!sha1Exists && !md5Exists))) {
                    return;
                }

                if (!sha1Exists) {
                    populateBeanSha1Checksum(buildFile);
                } else {
                    populateBeanMd5Checksum(buildFile);
                }
            }
        }
    }

    /**
     * Locates and fills the missing SHA1 checksum of a build file bean
     *
     * @param bean Bean to populate
     */
    private void populateBeanSha1Checksum(BuildFileBean bean) {
        log.trace("Populating sha1 checksum for {}", bean);
        String md5 = bean.getMd5();
        FutureTask<ChecksumPair> future = getFuture(bean, md5, md5Cache);
        try {
            ChecksumPair pair = future.get();
            if (!pair.checksumsFound()) {
                //Remove unfound checksum, so it won't reside in the cache, possibly misinforming
                md5Cache.remove(md5);
                return;
            }
            String resultSha1 = pair.getSha1();
            bean.setSha1(resultSha1);

            //Also update the SHA1 cache with the result, for future requests
            sha1Cache.put(resultSha1, future);
            log.trace("Populated sha1 checksum for {}", bean);
        } catch (Exception e) {
            //Remove the problematic checksum to avoid cache poisoning
            md5Cache.remove(md5);
            log.error("An error occurred while trying to populate missing SHA1 checksum for build file bean with MD5 " +
                    "checksum '{}' : ", md5, e.getMessage());
            log.debug("Bean '" + md5 + "' checksum population error: ", e);
        }
    }

    /**
     * Locates and fills the missing MD5 checksum of a build file bean
     *
     * @param bean Bean to populate
     */
    private void populateBeanMd5Checksum(BuildFileBean bean) {
        String sha1 = bean.getSha1();
        FutureTask<ChecksumPair> future = getFuture(bean, sha1, sha1Cache);
        try {
            ChecksumPair pair = future.get();
            if (!pair.checksumsFound()) {
                //Remove unfound checksum, so it won't reside in the cache, possibly misinforming
                sha1Cache.remove(sha1);
                return;
            }
            String resultMd5 = pair.getMd5();
            bean.setMd5(resultMd5);

            //Also update the MD5 cache with the result, for future requests
            md5Cache.put(resultMd5, future);
        } catch (Exception e) {
            //Remove the problematic checksum to avoid cache poisoning
            sha1Cache.remove(sha1);
            log.error("An error occurred while trying to populate missing MD5 checksum for build file bean with SHA1 " +
                    "checksum '{}' : ", sha1, e.getMessage());
            log.debug("Bean '" + sha1 + "' checksum population error: ", e);
        }
    }

    /**
     * Returns a FutureTask object for populating a missing checksum
     *
     * @param bean                  Bean to populate
     * @param existingChecksum      The checksum which already exists in the bean, can help us search for the missing
     *                              one
     * @param existingChecksumCache The cache of the existing checksum, will keep the missing checksum for future
     *                              requests, might already hold the value
     * @return Future that returns the value of the missing checksum search
     */
    private FutureTask<ChecksumPair> getFuture(BuildFileBean bean, String existingChecksum,
            Cache<String, FutureTask<ChecksumPair>> existingChecksumCache) {

        //Create callable and future tasks for checksum location
        Callable<ChecksumPair> callable = new MissingChecksumCallable(bean.getSha1(), bean.getMd5());
        FutureTask<ChecksumPair> future = new FutureTask<ChecksumPair>(callable);

        //Use the *put if absent* to make sure that a similar task has not been executed yet
        // Note that our cache will return the same element if it doesn't exist (should be null is real putIfAbsent implementation)
        FutureTask<ChecksumPair> cachedFuture = existingChecksumCache.put(existingChecksum, future);
        if (cachedFuture == future) {
            //Might try to run a ran task once more (cache concurrency issue), but future protects from this
            executor.submit(future);
        }

        return cachedFuture;
    }

    /**
     * Collects all the checksums from the files of the given modules
     *
     * @param modules             Modules to collect from
     * @param artifactChecksums   Artifact checksum set to append to
     * @param dependencyChecksums Dependency checksum set to append to
     */
    private void collectModuleChecksums(List<Module> modules, Set<String> artifactChecksums,
            Set<String> dependencyChecksums) {
        if (modules != null) {
            for (Module module : modules) {
                collectBuildFileChecksums(module.getArtifacts(), artifactChecksums);
                collectBuildFileChecksums(module.getDependencies(), dependencyChecksums);
            }
        }
    }

    /**
     * Collects all the checksums of the given build files. Each checksum value will be prepended with a template of
     * {CHECKSUM_TYPE}
     *
     * @param buildFiles         Build files to collect from
     * @param buildFileChecksums Checksum set to append to
     */
    private void collectBuildFileChecksums(List<? extends BuildFileBean> buildFiles, Set<String> buildFileChecksums) {
        if (buildFiles != null) {
            for (BuildFileBean buildFile : buildFiles) {
                String md5 = buildFile.getMd5();
                String sha1 = buildFile.getSha1();

                if (StringUtils.isNotBlank(md5)) {
                    buildFileChecksums.add(BUILD_CHECKSUM_PREFIX_MD5 + md5);
                }
                if (StringUtils.isNotBlank(sha1)) {
                    buildFileChecksums.add(BUILD_CHECKSUM_PREFIX_SHA1 + sha1);
                }
            }
        }
    }

    /**
     * Gets or creates the build node hierarchy with the given name and number
     *
     * @param escapedBuildName   Escaped name of build
     * @param escapedBuildNumber Escaped number of build
     * @return The build number node
     */
    private Node createBuildNumberNode(String escapedBuildName, String escapedBuildNumber) {
        Node buildsNode = getOrCreateBuildsRootNode();
        Node buildNameNode = jcrService.getOrCreateUnstructuredNode(buildsNode, escapedBuildName);
        return jcrService.getOrCreateUnstructuredNode(buildNameNode, escapedBuildNumber);
    }

    /**
     * Saves the build file checksums as properties on the given build node
     *
     * @param buildStartedNode    "Build-started" level node
     * @param artifactChecksums   Artifact checksum set
     * @param dependencyChecksums Dependency checksum set
     */
    private void saveBuildFileChecksums(Node buildStartedNode, Set<String> artifactChecksums,
            Set<String> dependencyChecksums) throws RepositoryException {
        saveBuildChecksumsProperty(buildStartedNode, PROP_BUILD_ARTIFACT_CHECKSUMS, artifactChecksums);
        saveBuildChecksumsProperty(buildStartedNode, PROP_BUILD_DEPENDENCY_CHECKSUMS, dependencyChecksums);
    }

    /**
     * Assembles an absolute JCR path of the build node from the given params
     *
     * @param buildName    Build name
     * @param buildNumber  Build number
     * @param buildStarted Build started
     * @return Build absolute JCR path
     */
    private String getBuildPathFromParams(String buildName, String buildNumber, String buildStarted) {
        StringBuilder builder = new StringBuilder().append(
                JcrPath.get().getBuildsJcrPath(escapeAndGetJcrCompatibleString(buildName)));

        if (StringUtils.isNotBlank(buildNumber)) {
            String escapedNumber = escapeAndGetJcrCompatibleString(buildNumber);
            builder.append("/").append(escapedNumber);
        }

        if (StringUtils.isNotBlank(buildStarted)) {
            String escapedStarted = escapeAndGetJcrCompatibleString(buildStarted);
            builder.append("/").append(escapedStarted);
        }
        return builder.toString();
    }

    /**
     * Get the build at the given JCR path
     *
     * @param buildPath Absolute JCR path
     * @return Build object if found. Null if not
     */
    private Build getBuild(String buildPath) {
        InputStream buildInputStream = null;
        Build build = null;
        try {
            if (StringUtils.isNotBlank(buildPath) && jcrService.itemNodeExists(buildPath)) {
                buildInputStream = jcrService.getStream(buildPath);
                JsonParser parser = JacksonFactory.createJsonParser(buildInputStream);
                ObjectMapper mapper = new ObjectMapper();
                build = mapper.readValue(parser, Build.class);
            }
        } catch (Exception e) {
            log.error("Unable to parse build object from the data of '{}': '{}'", buildPath, e.getMessage());
            log.debug("Unable to parse build object from the data of '" + buildPath + "'.", e);
            build = null;
        } finally {
            IOUtils.closeQuietly(buildInputStream);
        }

        return build;
    }

    /**
     * Saves the given checksums on the given build node
     *
     * @param buildStartedNode Build node to save on
     * @param checksumPropName Checksum property name to save on
     * @param checksums        Set of checksums to save
     */
    private void saveBuildChecksumsProperty(Node buildStartedNode, String checksumPropName, Set<String> checksums)
            throws RepositoryException {
        if (!checksums.isEmpty()) {
            buildStartedNode.setProperty(checksumPropName, checksums.toArray(new String[checksums.size()]));
        }
    }

    /**
     * Returns the best matching file info object by build name and number
     *
     * @param searchResults    File bean search results
     * @param resultProperties Search result property map
     * @param buildName        Build name to search for
     * @param buildNumber      Build number to search for
     * @return The file info of a result that best matches the given build name and number
     */
    private FileInfo matchResultBuildNameAndNumber(Set<RepoPath> searchResults,
            Map<RepoPath, Properties> resultProperties, String buildName, String buildNumber) {
        Map<RepoPath, Properties> matchingBuildNames = Maps.newHashMap();

        for (Map.Entry<RepoPath, Properties> repoPathPropertiesEntry : resultProperties.entrySet()) {
            Properties properties = repoPathPropertiesEntry.getValue();
            Set<String> buildNames = properties.get("build.name");
            if (buildNames.contains(buildName)) {
                matchingBuildNames.put(repoPathPropertiesEntry.getKey(), properties);
            }
        }

        if (matchingBuildNames.isEmpty()) {
            return getLatestItem(searchResults);
        } else {
            return matchResultBuildNumber(resultProperties, matchingBuildNames, buildNumber);
        }
    }

    /**
     * Returns the best matching file info object by build number
     *
     * @param resultProperties Search result property map
     * @param matchingPaths    File info paths that match by build name
     * @param buildNumber      Build number to search for
     * @return The file info of a result that best matches the given build number
     */
    private FileInfo matchResultBuildNumber(Map<RepoPath, Properties> resultProperties,
            Map<RepoPath, Properties> matchingPaths, String buildNumber) {
        RepoPath selectedPath = matchingPaths.keySet().iterator().next();

        for (RepoPath repoPath : matchingPaths.keySet()) {
            Properties properties = resultProperties.get(repoPath);
            Set<String> buildNumbers = properties.get("build.number");
            if (buildNumbers.contains(buildNumber)) {
                selectedPath = repoPath;
                break;
            }
        }

        return new FileInfoProxy(selectedPath);
    }

    /**
     * Returns the file info object of the result watch was last modified
     *
     * @param searchResults Search results to search within
     * @return Latest modified search result file info. Null if no results were given
     */
    private FileInfo getLatestItem(Set<RepoPath> searchResults) {
        FileInfo latestItem = null;

        for (RepoPath result : searchResults) {
            FileInfo fileInfo = new FileInfoProxy(result);
            if ((latestItem == null) || (latestItem.getLastModified() < fileInfo.getLastModified())) {
                latestItem = fileInfo;
            }
        }
        return latestItem;
    }

    /**
     * Returns the JCR path helper object
     *
     * @return JcrPath
     */
    private JcrPath getJcrPath() {
        return JcrPath.get();
    }

    private void exportBuild(ExportSettings settings, Node buildStartedNode, String buildName, String buildNumber,
            long exportedBuildCount, File buildsFolder) throws Exception {
        MultiStatusHolder multiStatusHolder = settings.getStatusHolder();

        String buildStarted = buildStartedNode.getName();
        multiStatusHolder.setDebug(
                String.format("Beginning export of build: %s:%s:%s", buildName, buildNumber, buildStarted), log);

        ImportableExportableBuild exportedBuild = new ImportableExportableBuild();
        exportedBuild.setVersion(EXPORTABLE_BUILD_VERSION);
        exportedBuild.setBuildName(unEscapeAndGetJcrCompatibleString(buildName));
        exportedBuild.setBuildNumber(unEscapeAndGetJcrCompatibleString(buildNumber));
        exportedBuild.setBuildStarted(unEscapeAndGetJcrCompatibleString(buildStarted));

        String jsonString = jcrService.getString(buildStartedNode.getPath());
        exportedBuild.setJson(jsonString);

        exportBuildNodeMetadata(buildStartedNode, exportedBuild);

        exportBuildNodeChecksums(buildStartedNode, exportedBuild);

        File buildFile = new File(buildsFolder, "build" + Long.toString(exportedBuildCount) + ".xml");
        exportBuildToFile(exportedBuild, buildFile);

        multiStatusHolder.setDebug(
                String.format("Finished export of build: %s:%s:%s", buildName, buildNumber, buildStarted), log);
    }

    private void importBuildFiles(ImportSettings settings, Collection<File> buildExportFiles) {

        XStream xStream = getImportableExportableXStream();
        for (File buildExportFile : buildExportFiles) {
            FileInputStream inputStream = null;
            try {
                inputStream = new FileInputStream(buildExportFile);
                ImportableExportableBuild importableBuild = (ImportableExportableBuild) xStream.fromXML(inputStream);
                // import each build in a separate transaction
                getTransactionalMe().importBuild(settings, importableBuild);
            } catch (Exception e) {
                settings.getStatusHolder().setError("Error occurred during build info import", e, log);
                if (settings.isFailFast()) {
                    break;
                }
            } finally {
                IOUtils.closeQuietly(inputStream);
            }
        }
    }

    private String unEscapeAndGetJcrCompatibleString(String toUnEscape) {
        return Text.unescapeIllegalJcrChars(toUnEscape);
    }

    private void exportBuildNodeMetadata(Node buildStartedNode, ImportableExportableBuild exportedBuild)
            throws Exception {
        exportedBuild.setArtifactChecksums(getBuildFileChecksums(buildStartedNode, PROP_BUILD_ARTIFACT_CHECKSUMS));
        exportedBuild.setDependencyChecksums(getBuildFileChecksums(buildStartedNode, PROP_BUILD_DEPENDENCY_CHECKSUMS));

        exportedBuild.setCreated(getLongProperty(buildStartedNode, PROP_ARTIFACTORY_CREATED));
        exportedBuild.setLastModified(getLongProperty(buildStartedNode, PROP_ARTIFACTORY_LAST_MODIFIED));
        exportedBuild.setCreatedBy(getStringProperty(buildStartedNode, PROP_ARTIFACTORY_CREATED_BY));
        exportedBuild.setLastModifiedBy(getStringProperty(buildStartedNode, PROP_ARTIFACTORY_LAST_MODIFIED_BY));

        Node resNode = getResourceNode(buildStartedNode);
        exportedBuild.setMimeType(getMimeType(resNode));
    }

    private Set<String> getBuildFileChecksums(Node buildStartedNode, String buildFileTypePropName)
            throws Exception {
        Set<String> checksums = Sets.newHashSet();
        if (buildStartedNode.hasProperty(buildFileTypePropName)) {
            Property buildFileChecksumsProperty = buildStartedNode.getProperty(buildFileTypePropName);
            for (Value value : buildFileChecksumsProperty.getValues()) {
                checksums.add(value.getString());
            }
        }
        return checksums;
    }

    private void exportBuildNodeChecksums(Node buildStartedNode, ImportableExportableBuild exportedBuild) {
        for (ChecksumType type : ChecksumType.values()) {
            String original = getStringProperty(buildStartedNode, type.getOriginalPropName(), null, true);
            String actual = getStringProperty(buildStartedNode, type.getActualPropName(), null, true);
            if (StringUtils.isNotBlank(actual) || StringUtils.isNotBlank(original)) {
                exportedBuild.addChecksumInfo(new ChecksumInfo(type, original, actual));
            }
        }
    }

    private void exportBuildToFile(ImportableExportableBuild exportedBuild, File buildFile) throws Exception {
        FileOutputStream buildFileOutputStream = null;
        try {
            buildFileOutputStream = new FileOutputStream(buildFile);
            getImportableExportableXStream().toXML(exportedBuild, buildFileOutputStream);
        } finally {
            IOUtils.closeQuietly(buildFileOutputStream);
        }
    }

    /**
     * Returns an XStream object to parse and generate {@link org.artifactory.api.build.ImportableExportableBuild}
     * classes
     *
     * @return ImportableExportableBuild ready XStream object
     */
    private XStream getImportableExportableXStream() {
        return XStreamFactory.create(ImportableExportableBuild.class);
    }

    /**
     * Returns an internal instance of the service
     *
     * @return InternalBuildService
     */
    private InternalBuildService getTransactionalMe() {
        return ContextHelper.get().beanForType(InternalBuildService.class);
    }

    public void renameBuildNode(String from, String to) throws RepositoryException {
        String oldNamePath = getBuildPathFromParams(from, null, null);

        Node oldNameNode = jcrService.getNode(oldNamePath);
        if (oldNameNode == null) {
            log.error("Could not find a build tree node by the name '{}'. Build node was not renamed.", from);
            return;
        }

        String newNamePath = getBuildPathFromParams(to, null, null);
        Node newNameNode = jcrService.getOrCreateUnstructuredNode(newNamePath);

        NodeIterator oldNumberNodesIterator = oldNameNode.getNodes();
        while (oldNumberNodesIterator.hasNext()) {
            Node oldNumberNode = oldNumberNodesIterator.nextNode();

            Node newNumberNode = jcrService.getOrCreateUnstructuredNode(newNameNode, oldNumberNode.getName());

            NodeIterator oldStartedNodesIterator = oldNumberNode.getNodes();
            while (oldStartedNodesIterator.hasNext()) {
                Node oldStartedNode = oldStartedNodesIterator.nextNode();
                oldStartedNode.getSession().move(oldStartedNode.getPath(), newNumberNode.getPath() + "/" +
                        oldStartedNode.getName());
            }
            if (!oldNumberNode.hasNodes()) {
                oldNumberNode.remove();
            }
        }
        if (!oldNameNode.hasNodes()) {
            oldNameNode.remove();
        }
    }

    public void renameBuildContent(BasicBuildInfo basicBuildInfo, String to) throws RepositoryException, IOException {
        String buildName = basicBuildInfo.getName();
        String buildNumber = basicBuildInfo.getNumber();
        String buildStarted = basicBuildInfo.getStarted();

        String buildPath = getBuildPathFromParams(buildName, buildNumber, buildStarted);
        Node buildNode = jcrService.getNode(buildPath);
        if (buildNode == null) {
            log.error("Could not find build to rename at path: {}", buildPath);
            return;
        }

        Set<String> artifactChecksums = getChecksumPropertyValue(buildNode, PROP_BUILD_ARTIFACT_CHECKSUMS);
        Set<String> dependencyChecksums = getChecksumPropertyValue(buildNode, PROP_BUILD_DEPENDENCY_CHECKSUMS);

        InputStream jsonStream = JcrHelper.getRawStringStream(buildNode);
        JsonParser parser = JacksonFactory.createJsonParser(jsonStream);
        final JsonNode rootNode = parser.readValueAsTree();
        ((ObjectNode) rootNode).put("name", to);

        InputStreamFromOutputStream stream = null;
        try {
            stream = new InputStreamFromOutputStream() {

                @Override
                protected Object produce(OutputStream outputStream) throws Exception {
                    JsonGenerator jsonGenerator = JacksonFactory.createJsonGenerator(outputStream);
                    jsonGenerator.writeTree(rootNode);
                    return null;
                }
            };
            jcrService.setStream(buildNode.getParent(), buildNode.getName(), stream, BuildRestConstants.MT_BUILD_INFO,
                    authorizationService.currentUsername(), false);
        } catch (Exception e) {
            log.error("An error occurred while writing JSON data to the node of build name '{}', number '{}', " +
                    "started at '{}'.", new String[]{buildName, buildNumber, buildStarted});
            return;
        } finally {
            IOUtils.closeQuietly(stream);
        }

        try {
            saveBuildFileChecksums(buildNode, artifactChecksums, dependencyChecksums);
        } catch (Exception e) {
            log.error("An error occurred while saving checksum properties on the node of build name '{}', number " +
                    "'{}', started at '{}'.", new String[]{buildName, buildNumber, buildStarted});
        }
    }

    /**
     * Returns the value of the build node checksum property
     *
     * @param buildNode    Build node to extract from
     * @param propertyName Name of checksum property
     * @return Property values
     */
    private Set<String> getChecksumPropertyValue(Node buildNode, String propertyName) throws RepositoryException {
        Set<String> checksums = Sets.newHashSet();
        if (buildNode.hasProperty(propertyName)) {
            Value[] checksumValues = buildNode.getProperty(propertyName).getValues();
            if (checksumValues != null) {
                for (Value checksumValue : checksumValues) {
                    String checksum = checksumValue.getString();
                    if (StringUtils.isNotBlank(checksum)) {
                        checksums.add(checksum);
                    }
                }
            }
        }
        return checksums;
    }
}