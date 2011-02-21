/*
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2011 JFrog Ltd.
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

package org.artifactory.search.deployable;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import org.apache.commons.lang.StringUtils;
import org.apache.jackrabbit.util.ISO9075;
import org.artifactory.api.fs.VersionUnit;
import org.artifactory.api.module.ModuleInfo;
import org.artifactory.api.module.ModuleInfoBuilder;
import org.artifactory.api.search.SearchResults;
import org.artifactory.api.search.deployable.VersionUnitSearchControls;
import org.artifactory.api.search.deployable.VersionUnitSearchResult;
import org.artifactory.jcr.JcrPath;
import org.artifactory.jcr.JcrTypes;
import org.artifactory.repo.Repo;
import org.artifactory.repo.RepoPath;
import org.artifactory.search.SearcherBase;
import org.artifactory.util.PathUtils;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.query.QueryResult;
import java.util.Set;

import static org.artifactory.jcr.JcrTypes.PROP_ARTIFACTORY_NAME;

/**
 * Holds the version unit search logic
 *
 * @author Noam Y. Tenne
 */
public class VersionUnitSearcher extends SearcherBase<VersionUnitSearchControls, VersionUnitSearchResult> {

    @Override
    public SearchResults<VersionUnitSearchResult> doSearch(VersionUnitSearchControls controls)
            throws RepositoryException {

        RepoPath pathToSearch = controls.getPathToSearchWithin();

        StringBuilder queryBuilder = getPathQueryBuilder(controls);

        //Add path
        if (!StringUtils.isEmpty(pathToSearch.getPath()) && PathUtils.hasText(pathToSearch.getPath())) {
            String relativePath = ISO9075.encodePath(pathToSearch.getPath());
            addElementsToSb(queryBuilder, relativePath);
        }

        queryBuilder.append(FORWARD_SLASH).append("element(*, ").append(JcrTypes.NT_ARTIFACTORY_FILE)
                .append(") [@").append(PROP_ARTIFACTORY_NAME).append("]");

        QueryResult queryResult = performQuery(controls.isLimitSearchResults(), queryBuilder.toString());
        NodeIterator nodeIterator = queryResult.getNodes();

        Multimap<ModuleInfo, RepoPath> moduleInfoToRepoPaths = HashMultimap.create();

        try {
            Repo repo = getRepoService().repositoryByKey(pathToSearch.getRepoKey());

            while (nodeIterator.hasNext()) {
                Node fileNode = nodeIterator.nextNode();
                RepoPath fileRepoPath = JcrPath.get().getRepoPath(fileNode.getPath());

                ModuleInfo moduleInfo = repo.getItemModuleInfo(fileRepoPath.getPath());
                if (moduleInfo.isValid()) {
                    ModuleInfo stripped = stripModuleInfoFromUnnecessaryData(moduleInfo);
                    moduleInfoToRepoPaths.put(stripped, fileRepoPath);
                }
            }
        } catch (RepositoryException re) {
            handleNotFoundException(re);
        }
        Set<VersionUnitSearchResult> results = getVersionUnitResults(moduleInfoToRepoPaths);
        return new SearchResults<VersionUnitSearchResult>(Lists.newArrayList(results), nodeIterator.getSize());
    }

    private Set<VersionUnitSearchResult> getVersionUnitResults(Multimap<ModuleInfo, RepoPath> moduleInfoToRepoPaths) {
        Set<VersionUnitSearchResult> searchResults = Sets.newHashSet();

        for (ModuleInfo moduleInfo : moduleInfoToRepoPaths.keySet()) {

            searchResults.add(new VersionUnitSearchResult(
                    new VersionUnit(moduleInfo, Sets.<RepoPath>newHashSet(moduleInfoToRepoPaths.get(moduleInfo)))));
        }

        return searchResults;
    }

    private ModuleInfo stripModuleInfoFromUnnecessaryData(ModuleInfo moduleInfo) {
        ModuleInfoBuilder moduleInfoBuilder = new ModuleInfoBuilder().organization(moduleInfo.getOrganization()).
                module(moduleInfo.getModule()).baseRevision(moduleInfo.getBaseRevision());
        if (moduleInfo.isIntegration()) {
            String pathRevision = moduleInfo.getFolderIntegrationRevision();
            String artifactRevision = moduleInfo.getFileIntegrationRevision();

            boolean hasPathRevision = StringUtils.isNotBlank(pathRevision);
            boolean hasArtifactRevision = StringUtils.isNotBlank(artifactRevision);

            if (hasPathRevision && !hasArtifactRevision) {
                moduleInfoBuilder.folderIntegrationRevision(pathRevision);
                moduleInfoBuilder.fileIntegrationRevision(pathRevision);
            } else if (!hasPathRevision && hasArtifactRevision) {
                moduleInfoBuilder.fileIntegrationRevision(artifactRevision);
                moduleInfoBuilder.folderIntegrationRevision(artifactRevision);
            } else {
                moduleInfoBuilder.folderIntegrationRevision(pathRevision);
                moduleInfoBuilder.fileIntegrationRevision(artifactRevision);
            }
        }
        return moduleInfoBuilder.build();
    }
}