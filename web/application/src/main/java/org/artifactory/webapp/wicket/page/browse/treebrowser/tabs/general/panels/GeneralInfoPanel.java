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

package org.artifactory.webapp.wicket.page.browse.treebrowser.tabs.general.panels;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.time.DurationFormatUtils;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.ExternalLink;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.spring.injection.annot.SpringBean;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.wicket.LicensesWebAddon;
import org.artifactory.addon.wicket.WatchAddon;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.maven.MavenArtifactInfo;
import org.artifactory.api.maven.MavenNaming;
import org.artifactory.api.mime.NamingUtils;
import org.artifactory.api.repo.ArtifactCount;
import org.artifactory.api.repo.RepoPathImpl;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.storage.StorageUnit;
import org.artifactory.common.wicket.component.LabeledValue;
import org.artifactory.common.wicket.component.border.fieldset.FieldSetBorder;
import org.artifactory.common.wicket.component.help.HelpBubble;
import org.artifactory.common.wicket.component.links.TitledAjaxLink;
import org.artifactory.common.wicket.util.WicketUtils;
import org.artifactory.descriptor.repo.LocalCacheRepoDescriptor;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.descriptor.repo.RemoteRepoDescriptor;
import org.artifactory.fs.ItemInfo;
import org.artifactory.log.LoggerFactory;
import org.artifactory.repo.RepoPath;
import org.artifactory.webapp.actionable.RepoAwareActionableItem;
import org.artifactory.webapp.actionable.model.FolderActionableItem;
import org.artifactory.webapp.actionable.model.LocalRepoActionableItem;
import org.artifactory.webapp.wicket.page.browse.treebrowser.BrowseRepoPage;
import org.slf4j.Logger;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

/**
 * Displays general item information. Placed inside the general info panel.
 *
 * @author Yossi Shaul
 */
public class GeneralInfoPanel extends Panel {
    private static final Logger log = LoggerFactory.getLogger(GeneralInfoPanel.class);

    @SpringBean
    private AddonsManager addonsManager;

    @SpringBean
    private CentralConfigService centralConfigService;

    @SpringBean
    private RepositoryService repositoryService;

    public GeneralInfoPanel(String id, RepoAwareActionableItem repoItem) {
        super(id);
        addGeneralInfo(repoItem);
    }

    private void addGeneralInfo(RepoAwareActionableItem repoItem) {
        final boolean itemIsRepo = repoItem instanceof LocalRepoActionableItem;
        LocalRepoDescriptor repoDescriptor = repoItem.getRepo();
        final boolean isCache = repoDescriptor.isCache();
        RemoteRepoDescriptor remoteRepo = null;
        if (isCache) {
            remoteRepo = ((LocalCacheRepoDescriptor) repoDescriptor).getRemoteRepo();
        }

        FieldSetBorder infoBorder = new FieldSetBorder("infoBorder");
        add(infoBorder);

        LabeledValue nameLabel = new LabeledValue("name", "Name: ");
        infoBorder.add(nameLabel);

        String itemDisplayName = repoItem.getDisplayName();
        String pathUrl = getRepoPathUrl(repoItem.getRepoPath().getRepoKey(), repoItem.getRepoPath().getPath());
        if (StringUtils.isBlank(pathUrl)) {
            pathUrl = "";
        }
        ExternalLink treeUrl = new ExternalLink("nameLink", pathUrl, itemDisplayName);
        infoBorder.add(treeUrl);
        infoBorder.add(new HelpBubble("nameLink.help",
                "Copy this link to navigate directly to the artifact in tree view."));

        LabeledValue descriptionLabel = new LabeledValue("description", "Description: ");
        String description = null;
        if (itemIsRepo) {
            if (isCache) {
                description = remoteRepo.getDescription();
            } else {
                description = repoDescriptor.getDescription();
            }
            descriptionLabel.setValue(description);
        }
        descriptionLabel.setVisible(!StringUtils.isEmpty(description) && itemIsRepo);
        infoBorder.add(descriptionLabel);

        ItemInfo itemInfo = repoItem.getItemInfo();

        final boolean isItemARepo = (repoItem instanceof LocalRepoActionableItem);
        LabeledValue deployedByLabel = new LabeledValue("deployed-by", "Deployed by: ", itemInfo.getModifiedBy()) {
            @Override
            public boolean isVisible() {
                return !isItemARepo;
            }
        };
        infoBorder.add(deployedByLabel);

        //Add markup container in case we need to set the remote repo url
        WebMarkupContainer urlLabelContainer = new WebMarkupContainer("urlLabel");
        WebMarkupContainer urlContainer = new WebMarkupContainer("url");
        infoBorder.add(urlLabelContainer);
        infoBorder.add(urlContainer);

        if (isCache) {
            urlLabelContainer.replaceWith(new Label("urlLabel", "Remote URL: "));
            String remoteRepoUrl = remoteRepo.getUrl();
            if ((remoteRepoUrl != null) && (!StringUtils.endsWith(remoteRepoUrl, "/"))) {
                remoteRepoUrl += "/";
                if (repoItem instanceof FolderActionableItem) {
                    remoteRepoUrl += ((FolderActionableItem) repoItem).getCanonicalPath().getPath();
                } else {
                    remoteRepoUrl += repoItem.getRepoPath().getPath();
                }
            }
            ExternalLink externalLink = new ExternalLink("url", remoteRepoUrl, remoteRepoUrl);
            urlContainer.replaceWith(externalLink);
        }

        final boolean isOffline = remoteRepo == null || remoteRepo.isOffline();
        final boolean globalOffline = centralConfigService.getDescriptor().isOfflineMode();
        final boolean isCacheRepo = itemIsRepo && isCache;
        String status = (isOffline || globalOffline) ? "Offline" : "Online";
        LabeledValue offlineLabel = new LabeledValue("status", "Online Status: ", status) {
            @Override
            public boolean isVisible() {
                return isCacheRepo;
            }
        };
        infoBorder.add(offlineLabel);

        final boolean repoIsBlackedOut = repoDescriptor.isBlackedOut();
        LabeledValue blackListedLabel = new LabeledValue("blackListed", "This repository is black-listed!") {
            @Override
            public boolean isVisible() {
                return itemIsRepo && repoIsBlackedOut;
            }
        };
        infoBorder.add(blackListedLabel);

        addArtifactCount(itemIsRepo, infoBorder, itemDisplayName);

        addWatcherInfo(repoItem, infoBorder);

        final RepoPath path;
        if (repoItem instanceof FolderActionableItem) {
            path = ((FolderActionableItem) repoItem).getCanonicalPath();
        } else {
            path = repoItem.getRepoPath();
        }
        LabeledValue repoPath = new LabeledValue("repoPath", "Repository Path: ", path + "");
        infoBorder.add(repoPath);

        addItemInfoLabels(infoBorder, itemInfo);

        addLicenseInfo(infoBorder, path);
    }

    private void addArtifactCount(final boolean itemIsRepo, FieldSetBorder infoBorder, String itemDisplayName) {
        long artifactCount = 0;
        if (itemIsRepo) {
            ArtifactCount count = repositoryService.getArtifactCount(itemDisplayName);
            artifactCount = count.getCount();
        }

        LabeledValue artifactCountLabel = new LabeledValue("artifactCount", "Artifact Count: ",
                Long.toString(artifactCount)) {
            @Override
            public boolean isVisible() {
                return itemIsRepo;
            }
        };
        infoBorder.add(artifactCountLabel);
    }

    private void addWatcherInfo(RepoAwareActionableItem repoItem, FieldSetBorder infoBorder) {
        org.artifactory.fs.ItemInfo itemInfo = repoItem.getItemInfo();
        WatchAddon watchAddon = addonsManager.addonByType(WatchAddon.class);
        RepoPath selectedPath;

        if ((itemInfo.isFolder()) && (repoItem instanceof FolderActionableItem)) {
            selectedPath = ((FolderActionableItem) repoItem).getCanonicalPath();
        } else {
            selectedPath = itemInfo.getRepoPath();
        }

        infoBorder.add(watchAddon.getWatchingSinceLabel("watchingSince", selectedPath));
        infoBorder.add(watchAddon.getDirectlyWatchedPathPanel("watchedPath", selectedPath));
    }

    private void addLicenseInfo(FieldSetBorder infoBorder, RepoPath path) {
        LicensesWebAddon licensesWebAddon = addonsManager.addonByType(LicensesWebAddon.class);
        final LabeledValue licensesLabel = licensesWebAddon.getLicenseLabel("licenses", path);
        licensesLabel.setOutputMarkupId(true);
        infoBorder.add(licensesLabel);
        TitledAjaxLink addButton = licensesWebAddon.getAddLicenseLink("add", path,
                licensesLabel.getDefaultModelObjectAsString(), licensesLabel);
        infoBorder.add(addButton);
        TitledAjaxLink editLicenseLink = licensesWebAddon.getEditLicenseLink("edit", path,
                licensesLabel.getDefaultModelObjectAsString(), licensesLabel);
        infoBorder.add(editLicenseLink);
        TitledAjaxLink deleteLicenseLink = licensesWebAddon.getDeleteLink("delete", path,
                licensesLabel.getDefaultModelObjectAsString(), infoBorder);
        infoBorder.add(deleteLicenseLink);
    }

    private void addItemInfoLabels(FieldSetBorder infoBorder, ItemInfo itemInfo) {
        LabeledValue sizeLabel = new LabeledValue("size", "Size: ");
        infoBorder.add(sizeLabel);

        LabeledValue ageLabel = new LabeledValue("age", "Age: ");
        infoBorder.add(ageLabel);

        LabeledValue artifactIdLabel = new LabeledValue("artifactId", "Artifact ID: ");
        infoBorder.add(artifactIdLabel);

        // disable/enable and set info according to the node type
        if (itemInfo.isFolder()) {
            ageLabel.setVisible(false);
            sizeLabel.setVisible(false);
            artifactIdLabel.setVisible(false);
        } else {
            org.artifactory.fs.FileInfo file = (org.artifactory.fs.FileInfo) itemInfo;
            MavenArtifactInfo mavenInfo = repositoryService.getMavenArtifactInfo(itemInfo);
            long size = file.getSize();
            //If we are looking at a cached item, check the expiry from the remote repository
            String ageStr = DurationFormatUtils.formatDuration(file.getAge(), "d'd' H'h' m'm' s's'");
            ageLabel.setValue(ageStr);
            sizeLabel.setValue(StorageUnit.toReadableString(size));
            if (mavenInfo.isValid()) {
                artifactIdLabel.setValue(getPrettyArtifactId(mavenInfo));
            } else {
                artifactIdLabel.setVisible(false);
            }
        }
    }

    /**
     * Returns the maven artifact "id" in a "prettier" format.<br>
     * org.artifactory.api.maven.MavenArtifactInfo#toString() will not omit fields like the classifier and type when
     * they are not specified. This results in ugly artifact IDs.<br>
     * This implementation will simply omit fields which are not specified.
     *
     * @param mavenArtifactInfo Maven artifact info to summarize
     * @return Summarized artifact info
     */
    private String getPrettyArtifactId(MavenArtifactInfo mavenArtifactInfo) {
        StringBuilder artifactIdBuilder = new StringBuilder(mavenArtifactInfo.getGroupId()).append(":").
                append(mavenArtifactInfo.getArtifactId()).append(":").
                append(mavenArtifactInfo.getVersion());

        String classifier = mavenArtifactInfo.getClassifier();
        if (StringUtils.isNotBlank(classifier) && !MavenArtifactInfo.NA.equals(classifier)) {
            artifactIdBuilder.append(":").append(classifier);
        }

        String type = mavenArtifactInfo.getType();
        if (StringUtils.isNotBlank(type) && !MavenArtifactInfo.NA.equals(classifier)) {
            artifactIdBuilder.append(":").append(type);
        }
        return artifactIdBuilder.toString();
    }

    private String getRepoPathUrl(String repoKey, String artifactPath) {
        StringBuilder urlBuilder = new StringBuilder();
        if (NamingUtils.isChecksum(artifactPath)) {
            // if a checksum file is deployed, link to the target file
            artifactPath = MavenNaming.getChecksumTargetFile(artifactPath);
        }
        String repoPathId = new RepoPathImpl(repoKey, artifactPath).getId();

        String encodedPathId;
        try {
            encodedPathId = URLEncoder.encode(repoPathId, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            log.error("Unable to encode deployed artifact ID '{}': {}.", repoPathId, e.getMessage());
            return null;
        }

        //Using request parameters instead of wicket's page parameters. See RTFACT-2843
        urlBuilder.append(WicketUtils.mountPathForPage(BrowseRepoPage.class)).append("?").
                append(BrowseRepoPage.PATH_ID_PARAM).append("=").append(encodedPathId);
        return urlBuilder.toString();
    }
}
