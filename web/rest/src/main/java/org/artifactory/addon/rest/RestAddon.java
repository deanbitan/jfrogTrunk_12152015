/*
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2012 JFrog Ltd.
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

package org.artifactory.addon.rest;

import com.sun.istack.internal.NotNull;
import com.sun.jersey.multipart.FormDataMultiPart;
import org.artifactory.addon.Addon;
import org.artifactory.addon.license.LicenseStatus;
import org.artifactory.addon.plugin.ResponseCtx;
import org.artifactory.addon.replication.RemoteReplicationSettings;
import org.artifactory.api.common.MultiStatusHolder;
import org.artifactory.api.repo.Async;
import org.artifactory.api.repo.exception.BlackedOutException;
import org.artifactory.api.rest.artifact.ItemPermissions;
import org.artifactory.api.rest.artifact.MoveCopyResult;
import org.artifactory.api.rest.artifact.PromotionResult;
import org.artifactory.api.rest.build.artifacts.BuildArtifactsRequest;
import org.artifactory.api.rest.replication.ReplicationRequest;
import org.artifactory.api.rest.search.result.ArtifactVersionsResult;
import org.artifactory.api.rest.search.result.LicensesSearchResult;
import org.artifactory.fs.FileInfo;
import org.artifactory.repo.RepoPath;
import org.artifactory.rest.common.list.KeyValueList;
import org.artifactory.rest.common.list.StringList;
import org.artifactory.rest.resource.artifact.legacy.DownloadResource;
import org.artifactory.sapi.common.Lock;
import org.jfrog.build.api.BuildRetention;
import org.jfrog.build.api.dependency.BuildPatternArtifacts;
import org.jfrog.build.api.dependency.BuildPatternArtifactsRequest;
import org.jfrog.build.api.release.Promotion;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.text.ParseException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * An interface that holds all the REST related operations that are available only as part of Artifactory's Add-ons.
 *
 * @author Tomer Cohen
 */
public interface RestAddon extends Addon {
    /**
     * Copy an artifact from one path to another.
     *
     * @param path            The source path of the artifact.
     * @param target          The target repository where to copy/move the Artifact to.
     * @param dryRun          A flag to indicate whether to perform a dry run first before performing the actual
     *                        action.
     * @param suppressLayouts Indicates whether path translation across different layouts should be suppressed.
     * @param failFast        Indicates whether the operation should fail upon encountering an error.
     * @return A JSON object of all the messages and errors that occurred during the action.
     * @throws Exception If an error occurred during the dry run or the actual action an exception is thrown.
     */
    MoveCopyResult copy(String path, String target, int dryRun, int suppressLayouts, int failFast) throws Exception;

    /**
     * Move an artifact from one path to another.
     *
     * @param path            The source path of the artifact.
     * @param target          The target repository where to copy/move the Artifact to.
     * @param dryRun          A flag to indicate whether to perform a dry run first before performing the actual
     *                        action.
     * @param suppressLayouts Indicates whether path translation across different layouts should be suppressed.
     * @param failFast        Indicates whether the operation should fail upon encountering an error.
     * @return A JSON object of all the messages and errors that occurred during the action.
     * @throws Exception If an error occurred during the dry run or the actual action an exception is thrown.
     */
    MoveCopyResult move(String path, String target, int dryRun, int suppressLayouts, int failFast) throws Exception;

    /**
     * @deprecated use {@link RestAddon#replicate(org.artifactory.repo.RepoPath, org.artifactory.api.rest.replication.ReplicationRequest)} instead
     */
    @Deprecated
    Response download(String path, DownloadResource.Content content, int mark,
            HttpServletResponse response) throws Exception;

    /**
     * Search for artifacts within a repository matching a given pattern.<br> The pattern should be like
     * repo-key:this/is/a/pattern
     *
     * @param pattern Pattern to search for
     * @return Set of matching artifact paths relative to the repo
     */
    Set<String> searchArtifactsByPattern(String pattern) throws ExecutionException, TimeoutException,
            InterruptedException;

    /**
     * Moves or copies build artifacts and\or dependencies
     *
     * @param move        True if the items should be moved. False if they should be copied
     * @param buildName   Name of build to target
     * @param buildNumber Number of build to target
     * @param started     Start date of build to target (can be null)
     * @param to          Key of target repository to move to
     * @param arts        Zero if to exclude artifacts from the action take. One to include
     * @param deps        Zero if to exclude dependencies from the action take. One int to include
     * @param scopes      Scopes of dependencies to copy (agnostic if null or empty)
     * @param properties  Properties to tag the moved or copied artifacts.
     * @param dry         Zero if to apply the selected action. One to simulate  @return Result of action
     * @deprecated Use {@link org.artifactory.addon.rest.RestAddon#promoteBuild} instead
     */
    @Deprecated
    MoveCopyResult moveOrCopyBuildItems(boolean move, String buildName, String buildNumber, String started,
            String to, int arts, int deps, StringList scopes, KeyValueList properties, int dry) throws ParseException;

    /**
     * Promotes a build
     *
     * @param buildName   Name of build to promote
     * @param buildNumber Number of build to promote
     * @param promotion   Promotion settings
     * @return Promotion result
     */
    PromotionResult promoteBuild(String buildName, String buildNumber, Promotion promotion) throws ParseException;

    /**
     * Locally replicates the given remote path
     *
     * @param remoteReplicationSettings Settings
     * @return Response
     * @deprecated use {@link RestAddon#replicate(org.artifactory.repo.RepoPath, org.artifactory.api.rest.replication.ReplicationRequest)} instead
     */
    @Deprecated
    Response replicate(RemoteReplicationSettings remoteReplicationSettings) throws IOException;

    Response replicate(RepoPath repoPath, ReplicationRequest replicationRequest) throws IOException;

    /**
     * Renames structure, content and properties of build info objects. The actual rename is done asynchronously.
     *
     * @param from Name to replace
     * @param to   Replacement build name
     */
    void renameBuilds(String from, String to);

    /**
     * Renames structure, content and properties of build info objects in an asynchronous manner.
     *
     * @param from Name to replace
     * @param to   Replacement build name
     */
    @Async
    void renameBuildsAsync(String from, String to);

    /**
     * Deletes the build with given name and number
     *
     * @param response
     * @param buildName    Name of build to delete
     * @param buildNumbers Numbers of builds to delete
     * @param artifacts    1 if build artifacts should be deleted
     */
    void deleteBuilds(HttpServletResponse response, String buildName, StringList buildNumbers, int artifacts)
            throws IOException;

    /**
     * Discard old builds as according to count or date.
     *
     * @param name              Build name
     * @param discard           The discard object that holds a count or date.
     * @param multiStatusHolder Status holder
     */
    void discardOldBuilds(String name, BuildRetention discard, MultiStatusHolder multiStatusHolder);

    /**
     * Returns the latest modified item of the given file or folder (recursively)
     *
     * @param pathToSearch Repo path to search in
     * @return Latest modified item
     */
    org.artifactory.fs.ItemInfo getLastModified(String pathToSearch);

    /**
     * Find licenses in repositories, if empty, a scan of all repositories will take place.
     *
     * @param status            A container to hold the different license statuses.
     * @param repos             The repositories to scan, if empty, all repositories will be scanned.
     * @param servletContextUrl The contextUrl of the server.
     * @return The search results.
     */
    LicensesSearchResult findLicensesInRepos(LicenseStatus status, StringList repos, String servletContextUrl);

    /**
     * Delete a repository via REST.
     *
     * @param repoKey The repokey that is associated to the repository that is wanted for deletion.
     */
    Response deleteRepository(String repoKey);

    /**
     * Get Repository configuration according to the repository key in conjunction with the media type to enforce a
     * certain type of repository configuration.
     *
     * @param repoKey   The repokey of the repository.
     * @param mediaType The acceptable media type for this request
     * @return The response with the configuration embedded in it.
     */
    Response getRepositoryConfiguration(String repoKey, MediaType mediaType);

    /**
     * Create or replace an existing repository via REST.
     *
     * @param repoKey
     * @param repositoryConfig Map of attributes.
     * @param mediaTypes       The mediatypes of which are applicable. {@link org.artifactory.api.rest.constant.RepositoriesRestConstants}
     * @param position         The position in the map that the newly created repository will be placed
     */
    Response createOrReplaceRepository(String repoKey, Map repositoryConfig, MediaType mediaType, int position);

    /**
     * Update an existing repository via REST.
     *
     * @param repoKey          The repokey of the repository to be updated.
     * @param repositoryConfig The repository config of what is to be updated.
     * @param mediaType        The acceptable media type for this REST command.
     * @return The response for this command.
     */
    Response updateRepository(String repoKey, Map repositoryConfig, MediaType mediaType);

    /**
     * Search for artifacts by their checksums
     *
     * @param md5Checksum   MD5 checksum value
     * @param sha1Checksum  SHA1 checksum value
     * @param reposToSearch Specific repositories to search in
     * @return Set of repo paths matching the given checksum
     */
    Set<RepoPath> searchArtifactsByChecksum(String md5Checksum, String sha1Checksum, StringList reposToSearch);

    /**
     * Search the repository(ies) for artifacts which have a mismatch between their server generated checksums and their
     * client generated checksums, this can result from an inequality or if one is missing.
     *
     * @param type          the type of checksum to search for (md5, sha1).
     * @param reposToSearch The list of repositories to search for the corrupt artifacts, if empty all repositories will
     *                      be searched
     * @param request       The request
     * @return The response object with the result as its entity.
     */
    @Nonnull
    Response searchBadChecksumArtifacts(String type, StringList reposToSearch,
            HttpServletRequest request);

    /**
     * Save properties on a certain path (which must be a valid {@link org.artifactory.repo.RepoPath})
     *
     * @param path       The path on which to set the properties
     * @param recursive  Whether the property attachment should be recursive.
     * @param properties The properties to attach as a list.
     * @return The response of the operation
     */
    Response savePathProperties(String path, String recursive, KeyValueList properties);

    Response deletePathProperties(String path, String recursive, StringList properties);

    ResponseCtx runPluginExecution(String executionName, Map params, boolean async);

    Response getStagingStrategy(String strategyName, String buildName, Map params);

    ItemPermissions getItemPermissions(HttpServletRequest request, String path);

    Response searchDependencyBuilds(HttpServletRequest request, String sha1) throws UnsupportedEncodingException;

    Response calculateYumMetadata(String repoKey, int async);

    Response getSecurityEntities(HttpServletRequest request, String entityType) throws UnsupportedEncodingException;

    Response getSecurityEntity(String entityType, String entityKey);

    Response deleteSecurityEntity(String entityType, String entityKey);

    Response createOrReplaceSecurityEntity(String entityType, String entityKey, HttpServletRequest request)
            throws IOException;

    Response updateSecurityEntity(String entityType, String entityKey, HttpServletRequest request) throws IOException;

    /**
     * Returns the latest replication status information
     *
     * @param repoPath Item to check for information annotations
     * @return Response
     */
    Response getReplicationStatus(RepoPath repoPath);

    /**
     * Handles the NuGet tool's test request
     *
     * @param repoKey Repo key
     * @return Response
     */
    Response handleNuGetTestRequest(@Nonnull String repoKey);

    /**
     * Handles the NuPkg metadata descriptor test
     *
     * @param repoKey Repo key
     * @return Response
     */
    Response handleNuGetMetadataDescriptorRequest(@Nonnull String repoKey);

    /**
     * Handles NuGet query requests
     *
     * @param request     Request
     * @param repoKey     Repo key
     * @param actionParam Path parameter identifying the action
     * @return Response
     */
    Response handleNuGetQueryRequest(@Nonnull HttpServletRequest request, @Nonnull String repoKey,
            @Nullable String actionParam);

    /**
     * Handles NuGet packages requests
     *
     * @param request Request
     * @param repoKey Repo key
     * @return Response
     */
    Response handleNuGetPackagesRequest(@Nonnull HttpServletRequest request, @Nonnull String repoKey);

    /**
     * Handles NuGet package search by ID requests
     *
     * @param request Request
     * @param repoKey Repo key
     * @return Response
     */
    Response handleFindPackagesByIdRequest(@Nonnull HttpServletRequest request, @Nonnull String repoKey);

    /**
     * Handles NuGet package update search requests
     *
     * @param request     Request
     * @param repoKey     Repo key
     * @param actionParam Path parameter identifying the action
     * @return Response
     */
    Response handleGetUpdatesRequest(@Nonnull HttpServletRequest request, @Nonnull String repoKey,
            @Nullable String actionParam);

    /**
     * Handles a NuGet package download request
     *
     * @param response       Servlet response
     * @param repoKey        Key of storing repo
     * @param packageId      ID of requested package
     * @param packageVersion Version of requested package
     * @return Null if the result was written directly to the original response, a response object otherwise
     */
    Response handleNuGetDownloadRequest(@Nonnull HttpServletResponse response, @Nonnull String repoKey,
            @Nonnull String packageId, @Nonnull String packageVersion);

    /**
     * Handles NuGet delete requests
     *
     * @param repoKey        Repo key
     * @param packageId      ID of package to delete
     * @param packageVersion Version of package to delete
     * @return Response
     */
    Response handleNuGetDeleteRequest(@Nonnull String repoKey, @Nonnull String packageId,
            @Nonnull String packageVersion);

    /**
     * Handles calculating maven index REST requests
     *
     * @param reposToIndex Keys of repositories to index
     * @param force        force indexer execution
     * @return Response
     */
    Response runMavenIndexer(List<String> reposToIndex, int force);

    /**
     * Handles NuGet publish requests
     *
     * @param repoKey Repo key
     * @param content Request form data multi part
     * @return Response
     */
    Response handleNuGetPublishRequest(@Nonnull String repoKey, @Nonnull FormDataMultiPart content);

    /**
     * Handles requests for active user plugin info
     *
     * @param pluginType Specific plugin type to return the info for; All types if none is specified
     * @return Response
     */
    Response getUserPluginInfo(@Nullable String pluginType);

    /**
     * Returns the outputs of build matching the request
     *
     * @param buildPatternArtifactsRequest contains build name and build number or keyword
     * @param servletContextUrl            for building urls of current Artifactory
     * @return build outputs (build dependencies and generated artifacts)
     */
    @Nullable
    BuildPatternArtifacts getBuildPatternArtifacts(@Nonnull BuildPatternArtifactsRequest buildPatternArtifactsRequest,
            @NotNull String servletContextUrl);

    /**
     * Returns build artifacts map according to the param input regexp patterns.
     *
     * @param buildArtifactsRequest A wrapper which contains the necessary parameters
     * @return A map from {@link FileInfo}s to their target directories relative paths
     * @see BuildArtifactsRequest
     */
    Map<FileInfo, String> getBuildArtifacts(BuildArtifactsRequest buildArtifactsRequest);

    /**
     * Returns an archive file according to the param archive type (zip/tar/tar.gz/tgz) which contains
     * all build artifacts according to the given build name and number (can be latest or latest by status).
     *
     * @param buildArtifactsRequest A wrapper which contains the necessary parameters
     * @return The archived file of build artifacts with their hierarchy rules
     * @see BuildArtifactsRequest
     */
    File getBuildArtifactsArchive(BuildArtifactsRequest buildArtifactsRequest) throws IOException;

    /**
     * Invokes a user plugin based build promotion action
     *
     * @param promotionName Name of closure
     * @param buildName     Name of build to promote
     * @param buildNumber   Number of build to promote
     * @param params        Promotion params
     * @return Response context
     */
    ResponseCtx promote(String promotionName, String buildName, String buildNumber, Map params);

    /**
     * Searches for artifact versions by it's groupId and artifactId (version is optional and relates to
     * integration versions only). The results are sorted from latest to oldest (latest is first).
     *
     * @param groupId       the groupId of the artifact
     * @param artifactId    the artifactId of the artifact
     * @param version       the artifact version, if null then perform the search on all available versions
     * @param reposToSearch limit the search to specific repos, if null then performs the search on all real repos
     * @param remote        whether to fetch maven-metadata from remote repository or not
     * @return A wrapper class of the search results
     */
    @Lock(transactional = true)
    ArtifactVersionsResult getArtifactVersions(String groupId, String artifactId, @Nullable String version,
            @Nullable StringList reposToSearch, boolean remote);

    @Lock(transactional = true)
    void writeStreamingFileList(HttpServletResponse response, String requestUrl, String path, int deep, int depth,
            int listFolders, int mdTimestamps, int includeRootPath) throws IOException, BlackedOutException;
}
