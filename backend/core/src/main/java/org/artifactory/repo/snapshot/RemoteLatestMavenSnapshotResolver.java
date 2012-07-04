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

package org.artifactory.repo.snapshot;

import com.google.common.base.Charsets;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.lang.StringUtils;
import org.apache.maven.artifact.repository.metadata.Metadata;
import org.apache.maven.artifact.repository.metadata.Snapshot;
import org.apache.maven.artifact.repository.metadata.Versioning;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.module.ModuleInfo;
import org.artifactory.api.request.DownloadService;
import org.artifactory.api.request.InternalArtifactoryRequest;
import org.artifactory.log.LoggerFactory;
import org.artifactory.maven.MavenModelUtils;
import org.artifactory.mime.MavenNaming;
import org.artifactory.model.common.RepoPathImpl;
import org.artifactory.repo.InternalRepoPathFactory;
import org.artifactory.repo.RemoteRepo;
import org.artifactory.repo.Repo;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.request.InternalArtifactoryResponse;
import org.artifactory.request.InternalRequestContext;
import org.artifactory.util.PathUtils;
import org.slf4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Resolves the latest unique snapshot version given a non-unique Maven snapshot artifact request
 * for remote repositories.
 *
 * @author Shay Yaakov
 */
public class RemoteLatestMavenSnapshotResolver extends LatestMavenSnapshotResolver {
    private static final Logger log = LoggerFactory.getLogger(RemoteLatestMavenSnapshotResolver.class);

    /**
     * Downloads maven-metadata.xml from the remote and analyzes the latest version from it.
     * If it does not exist, we return the original request context.
     */
    @Override
    protected InternalRequestContext getRequestContext(InternalRequestContext requestContext, Repo repo,
            ModuleInfo originalModuleInfo) {
        if (!(repo instanceof RemoteRepo)) {
            return requestContext;
        }
        RemoteRepo remoteRepo = (RemoteRepo) repo;
        if (remoteRepo.isOffline()) {
            // will fallback to local cache search
            return requestContext;
        }

        String path = requestContext.getResourcePath();
        RepoPath repoPath = InternalRepoPathFactory.create(repo.getKey(), path);
        Metadata metadata = tryDownloadingMavenMetadata(repoPath);
        if (metadata != null) {
            try {
                Versioning versioning = metadata.getVersioning();
                if (versioning != null) {
                    Snapshot snapshot = versioning.getSnapshot();
                    if (snapshot != null) {
                        String timestamp = snapshot.getTimestamp();
                        int buildNumber = snapshot.getBuildNumber();
                        if (StringUtils.isNotBlank(timestamp) && buildNumber > 0) {
                            String originalFileName = PathUtils.getFileName(path);
                            String fileName = originalFileName.replaceFirst("SNAPSHOT", timestamp + "-" + buildNumber);
                            RepoPath parentRepoPath = repoPath.getParent();
                            String uniqueRepoPath = PathUtils.addTrailingSlash(parentRepoPath.getPath()) + fileName;
                            requestContext = translateRepoRequestContext(requestContext, repo, uniqueRepoPath);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Failed parsing maven metadata from remote repo '{}' for repoPath '{}'",
                        repoPath.getRepoKey(), repoPath.getPath());
            }
        }

        return requestContext;
    }

    private Metadata tryDownloadingMavenMetadata(RepoPath repoPath) {
        String parentFolder = PathUtils.getParent(repoPath.getPath());
        RepoPath parentRepoPath = RepoPathFactory.create(repoPath.getRepoKey(), parentFolder);
        RepoPath metadataRepoPath = new RepoPathImpl(parentRepoPath, MavenNaming.MAVEN_METADATA_NAME);
        InternalArtifactoryRequest req = new InternalArtifactoryRequest(metadataRepoPath);
        InternalCapturingResponse res = new InternalCapturingResponse();
        try {
            DownloadService downloadService = ContextHelper.get().beanForType(DownloadService.class);
            downloadService.process(req, res);
            if (res.getStatus() == HttpStatus.SC_OK) {
                String metadataStr = res.getResultAsString();
                return MavenModelUtils.toMavenMetadata(metadataStr);
            }
        } catch (Exception e) {
            log.info("Could not download remote maven metadata for repo '{}' and path '{}'",
                    metadataRepoPath.getRepoKey(), metadataRepoPath.getPath());
        }
        return null;
    }

    private class InternalCapturingResponse extends InternalArtifactoryResponse {
        /**
         * Either the output stream or the writer might be used. Never both of them.
         */
        ByteArrayOutputStream out;
        private StringWriter stringWriter;
        private PrintWriter writer;

        @Override
        public OutputStream getOutputStream() throws IOException {
            if (out == null) {
                out = new ByteArrayOutputStream();
            }
            return out;
        }

        @Override
        public PrintWriter getWriter() throws IOException {
            if (stringWriter == null) {
                stringWriter = new StringWriter();
                writer = new PrintWriter(stringWriter);
            }
            return writer;
        }

        /**
         * Returns the string representing the response. Only call this method if the expected response is text base.
         *
         * @return The response result as a stream.
         */
        public String getResultAsString() {
            if (out != null) {
                return new String(out.toByteArray(), Charsets.UTF_8);
            } else if (stringWriter != null) {
                return stringWriter.toString();
            } else {
                return null;
            }
        }
    }
}