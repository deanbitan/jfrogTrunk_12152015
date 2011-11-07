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

package org.artifactory.webapp.actionable.model;

import com.google.common.collect.Lists;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.wicket.BuildAddon;
import org.artifactory.addon.wicket.WatchAddon;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.repo.ArtifactCount;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.fs.FileInfo;
import org.artifactory.fs.FolderInfo;
import org.artifactory.fs.ItemInfo;
import org.artifactory.mime.MimeType;
import org.artifactory.mime.NamingUtils;
import org.artifactory.repo.InternalRepoPathFactory;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.InternalRepoPathFactory;
import org.artifactory.webapp.actionable.ActionableItem;
import org.artifactory.webapp.actionable.RefreshableActionableItem;
import org.artifactory.webapp.actionable.RepoAwareActionableItem;
import org.artifactory.webapp.actionable.RepoAwareActionableItemBase;
import org.artifactory.webapp.actionable.action.CopyAction;
import org.artifactory.webapp.actionable.action.DeleteAction;
import org.artifactory.webapp.actionable.action.DeleteVersionsAction;
import org.artifactory.webapp.actionable.action.ItemAction;
import org.artifactory.webapp.actionable.action.MoveAction;
import org.artifactory.webapp.actionable.action.RefreshNodeAction;
import org.artifactory.webapp.actionable.action.ZapAction;
import org.artifactory.webapp.actionable.event.RepoAwareItemEvent;
import org.artifactory.webapp.wicket.util.ItemCssClass;

import java.util.List;
import java.util.Set;

/**
 * @author Yoav Landman
 */
public class LocalRepoActionableItem extends RepoAwareActionableItemBase
        implements HierarchicActionableItem, RefreshableActionableItem {
    private ItemAction deleteAction;
    private ItemAction zapAction;
    private DeleteVersionsAction delVersions;
    private ItemAction watchAction;
    private boolean compactAllowed;
    private MoveAction moveAction;
    private CopyAction copyAction;

    public LocalRepoActionableItem(LocalRepoDescriptor repo) {
        super(InternalRepoPathFactory.create(repo.getKey(), ""));
        Set<ItemAction> actions = getActions();
        actions.add(new RefreshNodeAction());
        actions.add(deleteAction = new RepoDeleteAction());
        actions.add(delVersions = new DeleteVersionsAction());
        actions.add(zapAction = new ZapAction());

        WatchAddon watchAddon = getAddonsProvider().addonByType(WatchAddon.class);
        watchAction = watchAddon.getWatchAction(InternalRepoPathFactory.create(repo.getKey(), ""));
        actions.add(watchAction);
        actions.add(moveAction = new MoveAction());
        moveAction.setName("Move Content...");
        actions.add(copyAction = new CopyAction());
        copyAction.setName("Copy Content...");
    }

    @Override
    public boolean isCompactAllowed() {
        return compactAllowed;
    }

    @Override
    public void setCompactAllowed(boolean compactAllowed) {
        this.compactAllowed = compactAllowed;
    }

    @Override
    public String getDisplayName() {
        return getRepoPath().getRepoKey();
    }

    @Override
    public String getCssClass() {
        if (getRepo().isCache()) {
            return ItemCssClass.repositoryCache.getCssClass();
        } else {
            return ItemCssClass.repository.getCssClass();
        }
    }

    @Override
    public void refresh() {
        //children = null;    // set the children to null will force reload
    }

    @Override
    public List<ActionableItem> getChildren(AuthorizationService authService) {
        RepositoryService repoService = getRepoService();
        List<ItemInfo> items = repoService.getChildren(getRepoPath());
        List<ActionableItem> result = Lists.newArrayListWithExpectedSize(items.size());

        for (ItemInfo pathItems : items) {

            RepoPath repoPath = pathItems.getRepoPath();
            if (!repoService.isRepoPathVisible(repoPath)) {
                continue;
            }

            RepoAwareActionableItem child;
            if (pathItems.isFolder()) {
                child = new FolderActionableItem((FolderInfo) pathItems, isCompactAllowed());
            } else {
                MimeType mimeType = NamingUtils.getMimeType(pathItems.getRelPath());
                if (mimeType.isArchive()) {
                    child = new ZipFileActionableItem((FileInfo) pathItems, compactAllowed);
                } else {
                    child = new FileActionableItem((FileInfo) pathItems);
                }
            }
            result.add(child);
        }
        return result;
    }

    @Override
    public boolean hasChildren(AuthorizationService authService) {
        RepoPath repoPath = getRepoPath();
        return getRepoService().hasChildren(repoPath);
    }

    @Override
    public void filterActions(AuthorizationService authService) {
        String key = getRepoPath().getRepoKey();
        boolean isAnonymous = authService.isAnonymous();
        boolean canDeploy = authService.canDeploy(InternalRepoPathFactory.secureRepoPathForRepo(key));
        boolean canDelete = authService.canDelete(InternalRepoPathFactory.secureRepoPathForRepo(key));
        boolean canRead = authService.canRead(InternalRepoPathFactory.secureRepoPathForRepo(key));

        if (!canDelete) {
            deleteAction.setEnabled(false);
        }

        if (isAnonymous) {
            zapAction.setEnabled(false);
        } else if (!canDeploy) {
            zapAction.setEnabled(false);
        } else if (!getRepo().isCache()) {
            zapAction.setEnabled(false);
        }

        // only admin can cleanup by version
        if (!authService.isAdmin()) {
            delVersions.setEnabled(false);
        }

        if (!canRead || isAnonymous) {
            watchAction.setEnabled(false);
        }

        if (!canDelete || !authService.canDeployToLocalRepository()) {
            moveAction.setEnabled(false);
        }

        if (!canRead || !authService.canDeployToLocalRepository()) {
            copyAction.setEnabled(false);
        }
    }

    private static class RepoDeleteAction extends DeleteAction {

        @Override
        protected String getDeleteSuccessMessage(RepoPath repoPath) {
            return "Successfully deleted repository '" + repoPath.getRepoKey() + "', content.";
        }

        @Override
        public String getDisplayName(ActionableItem actionableItem) {
            return "Delete Content";
        }

        @Override
        public String getCssClass() {
            return DeleteAction.class.getSimpleName();
        }

        @Override
        protected String getDeleteConfirmMessage(RepoAwareItemEvent e) {
            String key = e.getSource().getDisplayName();
            ArtifactCount count = getRepoService().getArtifactCount(key);
            //if remote repo has no cash
            long totalCount = count.getCount();
            StringBuilder builder = new StringBuilder("Are you sure you wish to delete the repository");
            if (totalCount == 0) {
                builder.append("?");
            } else {
                builder.append(" (").append(totalCount).append(" artifacts will be permanently deleted)?");
            }
            AddonsManager addonsManager = ContextHelper.get().beanForType(AddonsManager.class);
            BuildAddon buildAddon = addonsManager.addonByType(BuildAddon.class);
            return buildAddon.getDeleteItemWarningMessage(e.getSource().getItemInfo(), builder.toString());
        }
    }
}
