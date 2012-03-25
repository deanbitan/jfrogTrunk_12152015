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

package org.artifactory.repo.service;

import org.artifactory.api.repo.Async;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.repo.exception.RepoRejectException;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.repo.RemoteRepoDescriptor;
import org.artifactory.fs.RepoResource;
import org.artifactory.jcr.fs.JcrTreeNode;
import org.artifactory.repo.LocalRepo;
import org.artifactory.repo.RealRepo;
import org.artifactory.repo.RemoteRepo;
import org.artifactory.repo.Repo;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoRepoPath;
import org.artifactory.repo.jcr.StoringRepo;
import org.artifactory.repo.virtual.VirtualRepo;
import org.artifactory.request.InternalRequestContext;
import org.artifactory.resource.ResourceStreamHandle;
import org.artifactory.sapi.common.ExportSettings;
import org.artifactory.sapi.common.Lock;
import org.artifactory.spring.ReloadableBean;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Nullable;
import javax.jcr.ItemNotFoundException;
import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import java.io.IOException;
import java.util.List;

/**
 * A repository service to be used by the core
 * <p/>
 * User: freds Date: Jul 31, 2008 Time: 5:50:18 PM
 */
public interface InternalRepositoryService extends RepositoryService, ReloadableBean {

    boolean isAnonAccessEnabled();

    /**
     * @param key The repository key
     * @return Repository with the exact given key (no special meaning for remote/cache repo keys). Null if not found.
     */
    Repo repositoryByKey(String key);

    VirtualRepo virtualRepositoryByKey(String key);

    @Nullable
    LocalRepo localOrCachedRepositoryByKey(String key);

    @Nullable
    RealRepo localOrRemoteRepositoryByKey(String key);

    /**
     * Get the holder object that holds the actual repository as well as the path.
     *
     * @param repoPath The repo path
     * @param <R>      The type of repository.
     * @return The holder object that holds the actual repository as well as the path.
     */
    <R extends Repo> RepoRepoPath<R> getRepoRepoPath(RepoPath repoPath);

    /**
     * Get a repository that can store artifacts. Will get either a local repo (regular or cache) or a virtual repo. If
     * remote repository key is given, the cache repository is returned.
     */
    StoringRepo storingRepositoryByKey(String key);

    List<RealRepo> getLocalAndRemoteRepositories();

    List<VirtualRepo> getVirtualRepositories();

    List<LocalRepo> getLocalAndCachedRepositories();

    /**
     * Returns a local non-cache repository by key
     *
     * @param key The repository key
     * @return Local non-cahce repository or null if not found
     */
    LocalRepo localRepositoryByKey(String key);

    RemoteRepo remoteRepositoryByKey(String key);

    Repo nonCacheRepositoryByKey(String key);

    /**
     * This will verify the permission to deploy to the path, and will not acquire any FsItem. ATTENTION: No read lock
     * acquire, pure JCR and ACL tests are done.
     *
     * @param repo The storing repository (cache or local) to deploy to
     * @param path The path for deployment
     * @return A status holder with info on error
     */
    @Transactional
    void assertValidDeployPath(LocalRepo repo, String path) throws RepoRejectException;

    @Lock(transactional = true)
    <T extends RemoteRepoDescriptor> ResourceStreamHandle downloadAndSave(InternalRequestContext requestContext,
            RemoteRepo<T> remoteRepo, RepoResource res) throws IOException, RepositoryException, RepoRejectException;

    @Lock(transactional = true)
    RepoResource unexpireIfExists(LocalRepo localCacheRepo, String path);

    @Lock(transactional = true)
    ResourceStreamHandle unexpireAndRetrieveIfExists(InternalRequestContext requestContext, LocalRepo localCacheRepo,
            String path) throws IOException, RepositoryException, RepoRejectException;

    @Lock(transactional = true)
    ResourceStreamHandle getResourceStreamHandle(InternalRequestContext requestContext, Repo repo, RepoResource res)
            throws IOException, RepoRejectException, RepositoryException;

    @Override
    @Lock(transactional = true)
    void exportTo(ExportSettings settings);

    List<StoringRepo> getStoringRepositories();

    LocalRepo getLocalRepository(RepoPath repoPath);

    /**
     * Executes the maven metadata calculator on all the folders marked with the maven metadata recalculation flag. This
     * method is internal to the repository service and should execute during startup to make sure there are no folders
     * that the maven metadata wasn't recalculated on them (the recalculation might execute in metadata and interrupted
     * in the middle)
     */
    @Async(delayUntilAfterCommit = true, transactional = true)
    void recalculateMavenMetadataOnMarkedFolders();

    /**
     * Removes a mark for maven metadata recalculation if such exists.
     *
     * @param basePath Repo path to remove the mark from. Must be a local non-cache repository path.
     */
    @Lock(transactional = true)
    void removeMarkForMavenMetadataRecalculation(RepoPath basePath) throws ItemNotFoundException;

    /**
     * Asynchronous method called at the end of the transaction to acquire a write lock on the repo path and activate
     * the automatic save. The write will be acquired only if the fs item is not already write locked.
     *
     * @param repoPath The RepoPath of the item with dirty state.
     */
    @Async(delayUntilAfterCommit = true, transactional = true)
    void markForSaveOnCommit(RepoPath repoPath);

    @Override
    Repository getJcrHandle();

    @Override
    @Lock(transactional = true)
    void reload(CentralConfigDescriptor oldDescriptor);

    @Lock(transactional = true)
    void setXmlMetadataLater(RepoPath repoPath, String metadataName, String metadataContent);

    /**
     * @param treeNode Tree node to check recursively
     * @return True if this node or any of its file node descendants represents a maven plugin
     */
    @Lock(transactional = true)
    boolean treeNodeContainsMavenPlugins(JcrTreeNode treeNode);

    RepoPath getExplicitDescriptorPathByArtifact(RepoPath repoPath);

    VirtualRepo getGlobalVirtualRepo();
}