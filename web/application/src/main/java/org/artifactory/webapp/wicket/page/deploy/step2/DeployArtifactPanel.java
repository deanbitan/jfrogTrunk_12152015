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

package org.artifactory.webapp.wicket.page.deploy.step2;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.wicket.Component;
import org.apache.wicket.MarkupContainer;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.IAjaxCallDecorator;
import org.apache.wicket.ajax.form.AjaxFormComponentUpdatingBehavior;
import org.apache.wicket.authorization.UnauthorizedInstantiationException;
import org.apache.wicket.behavior.IBehavior;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.FormComponent;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.model.*;
import org.apache.wicket.spring.injection.annot.SpringBean;
import org.artifactory.api.artifact.ArtifactInfo;
import org.artifactory.api.artifact.UnitInfo;
import org.artifactory.api.maven.MavenArtifactInfo;
import org.artifactory.api.maven.MavenNaming;
import org.artifactory.api.mime.NamingUtils;
import org.artifactory.api.repo.DeployService;
import org.artifactory.api.repo.RepoPathImpl;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.repo.exception.RepoRejectException;
import org.artifactory.api.repo.exception.RepositoryRuntimeException;
import org.artifactory.api.repo.exception.maven.BadPomException;
import org.artifactory.api.util.SerializablePair;
import org.artifactory.common.wicket.ajax.NoAjaxIndicatorDecorator;
import org.artifactory.common.wicket.behavior.collapsible.CollapsibleBehavior;
import org.artifactory.common.wicket.component.checkbox.styled.StyledCheckbox;
import org.artifactory.common.wicket.component.help.HelpBubble;
import org.artifactory.common.wicket.component.links.TitledAjaxLink;
import org.artifactory.common.wicket.component.links.TitledAjaxSubmitLink;
import org.artifactory.common.wicket.component.panel.titled.TitledActionPanel;
import org.artifactory.common.wicket.panel.editor.TextEditorPanel;
import org.artifactory.common.wicket.util.AjaxUtils;
import org.artifactory.common.wicket.util.CookieUtils;
import org.artifactory.common.wicket.util.WicketUtils;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.descriptor.repo.SnapshotVersionBehavior;
import org.artifactory.log.LoggerFactory;
import org.artifactory.maven.MavenModelUtils;
import org.artifactory.util.ExceptionUtils;
import org.artifactory.util.FileUtils;
import org.artifactory.util.PathUtils;
import org.artifactory.util.StringInputStream;
import org.artifactory.webapp.wicket.page.browse.treebrowser.BrowseRepoPage;
import org.artifactory.webapp.wicket.page.browse.treebrowser.tabs.maven.MetadataPanel;
import org.artifactory.webapp.wicket.page.deploy.DeployArtifactPage;
import org.artifactory.webapp.wicket.page.deploy.step1.UploadArtifactPanel;
import org.artifactory.webapp.wicket.panel.tabbed.PersistentTabbedPanel;
import org.artifactory.webapp.wicket.util.validation.DeployTargetPathValidator;
import org.slf4j.Logger;

import java.io.*;
import java.net.URLEncoder;
import java.util.List;

/**
 * @author Yoav Aharoni
 */
public class DeployArtifactPanel extends TitledActionPanel {
    private static final Logger log = LoggerFactory.getLogger(DeployArtifactPanel.class);

    public DeployArtifactPanel(String id, File file) {
        super(id);
        add(new DeployArtifactForm(file));
    }

    private class DeployArtifactForm extends Form {
        private static final String TARGET_REPO = "targetRepo";

        @SpringBean
        private RepositoryService repoService;

        @SpringBean
        private DeployService deployService;

        private DeployModel model;

        private DeployArtifactForm(File file) {
            super("form");
            model = new DeployModel();
            model.file = file;
            model.mavenArtifactInfo = guessArtifactInfo();
            model.pomXml = deployService.getPomModelString(file);
            model.deployPom = isPomArtifact() || !isPomExists(getPersistentTargetRepo());
            model.repos = getRepos();
            model.targetRepo = getPersistentTargetRepo();

            setDefaultModel(new CompoundPropertyModel(model));

            add(new Label("file.name"));
            addPathField();
            addTargetRepoDropDown();
            addDeployMavenCheckbox();

            WebMarkupContainer artifactInfoContainer = newMavenArtifactContainer();
            add(artifactInfoContainer);

            artifactInfoContainer.add(newPomEditContainer());

            addDefaultButton(new DeployLink("deploy"));
            addButton(new CancelLink("cancel"));
        }

        //will be first initialize from cookie value, fall back to default

        private LocalRepoDescriptor getPersistentTargetRepo() {
            String cookieName = buildCookieName();
            String cookie = CookieUtils.getCookie(cookieName);
            int value = 0;
            if (cookie != null) {
                try {
                    value = Integer.parseInt(cookie);
                } catch (NumberFormatException e) {
                    log.debug("Failed to parse cookie for upload target repo, will use default repo");
                }
            }
            List<LocalRepoDescriptor> repos = getRepos();
            return value < repos.size() ? repos.get(value) : repos.get(0);
        }

        private String buildCookieName() {
            StringBuilder name = new StringBuilder(DeployArtifactPanel.this.getId());
            name.append(".").append(this.getId()).append(".").append(TARGET_REPO);
            return name.toString();
        }

        private List<LocalRepoDescriptor> getRepos() {
            List<LocalRepoDescriptor> repos = repoService.getDeployableRepoDescriptors();
            if (repos.isEmpty()) {
                throw new UnauthorizedInstantiationException(DeployArtifactPage.class);
            }
            return repos;
        }

        private Component newPomEditContainer() {
            MarkupContainer pomEditContainer = new WebMarkupContainer("pomEditContainer");
            pomEditContainer.setOutputMarkupPlaceholderTag(true);
            pomEditContainer.add(newGeneratePomCheckBox());
            pomEditContainer.add(newPomEditorPanel());
            return pomEditContainer;
        }

        private Component newPomEditorPanel() {
            final String helpMessage =
                    "View the resulting POM and handle possible discrepancies: fix bad coordinates, remove " +
                            "unwanted repository references, etc. Use with caution!";

            TextEditorPanel pomEditPanel = new TextEditorPanel("pomEditPanel", "POM Editor", helpMessage) {
                @Override
                protected IModel newTextModel() {
                    return new PropertyModel(model, "pomXml");
                }

                @Override
                public boolean isVisible() {
                    return model.deployPom;
                }
            };
            pomEditPanel.add(new CollapsibleBehavior().setUseAjax(true));
            pomEditPanel.addTextAreaBehavior(new OnPomXmlChangeBehavior());
            return pomEditPanel;
        }

        private Component newGeneratePomCheckBox() {
            FormComponent checkbox = new StyledCheckbox("deployPom");
            checkbox.setOutputMarkupId(true);
            checkbox.setVisible(!isPomArtifact());
            checkbox.setLabel(new Model("Also Deploy Jar's Internal POM/Generate Default POM"));
            checkbox.add(new OnGeneratePomChangeBehavior());
            return checkbox;
        }

        private WebMarkupContainer newMavenArtifactContainer() {
            WebMarkupContainer artifactInfoContainer = new WebMarkupContainer("artifactInfo") {
                @Override
                public boolean isVisible() {
                    return model.isMavenArtifact;
                }
            };
            artifactInfoContainer.setOutputMarkupPlaceholderTag(true);
            artifactInfoContainer.add(newGavcField("artifactInfo.groupId", true, new OnGavcChangeBehavior()));
            artifactInfoContainer.add(newGavcField("artifactInfo.artifactId", true, new OnGavcChangeBehavior()));
            artifactInfoContainer.add(newGavcField("artifactInfo.version", true, new OnGavcChangeBehavior()));
            artifactInfoContainer.add(newGavcField("artifactInfo.classifier", false, new OnGavcChangeBehavior()));
            artifactInfoContainer.add(newGavcField("artifactInfo.type", true, new OnPackTypeChangeBehavior()));
            return artifactInfoContainer;
        }

        private Component newGavcField(String id, boolean required, IBehavior behavior) {
            FormComponent textField = new TextField(id);
            textField.setRequired(required);
            textField.setOutputMarkupId(true);
            textField.add(behavior);
            return textField;
        }

        private void addPathField() {
            FormComponent path = new TextField("targetPath") {
                @Override
                public boolean isEnabled() {
                    return !model.isMavenArtifact;
                }
            };
            path.setRequired(true);
            path.setOutputMarkupId(true);
            path.add(new DeployTargetPathValidator());
            add(path);

            add(new HelpBubble("targetPath.help", new ResourceModel("targetPath.help")));
        }

        private void addDeployMavenCheckbox() {
            Component autoCalculatePath = new StyledCheckbox("isMavenArtifact").setPersistent(true);
            autoCalculatePath.add(new OnDeployTypeChangeBehavior());
            add(autoCalculatePath);
            add(new HelpBubble("isMavenArtifact.help", new ResourceModel("isMavenArtifact.help")));
        }

        private void addTargetRepoDropDown() {
            FormComponent targetRepo = new DropDownChoice(TARGET_REPO, model.repos);
            targetRepo.setPersistent(true);
            targetRepo.setRequired(true);
            add(targetRepo);

            add(new HelpBubble("targetRepo.help", new ResourceModel("targetRepo.help")));
        }

        private MarkupContainer getPomEditorContainer() {
            return (MarkupContainer) get("artifactInfo:pomEditContainer");
        }

        /**
         * Try to guess the properties from pom/jar content.
         *
         * @return artifact info
         */
        private MavenArtifactInfo guessArtifactInfo() {
            try {
                // if (pom or jar) get pom xml as string
                return deployService.getArtifactInfo(model.file);
            } catch (Exception e) {
                String msg = "Unable to analyze uploaded file content. Cause: " + e.getMessage();
                log.debug(msg, e);
                error(msg);
            }
            return new MavenArtifactInfo();
        }

        private boolean isPomArtifact() {
            UnitInfo artifactInfo = model.getArtifactInfo();
            if (!artifactInfo.isMavenArtifact()) {
                return false;
            }
            MavenArtifactInfo mavenArtifactInfo = (MavenArtifactInfo) artifactInfo;
            String packagingType = mavenArtifactInfo.getType();
            return MavenArtifactInfo.POM.equalsIgnoreCase(packagingType);
        }

        private boolean isPomExists(LocalRepoDescriptor repo) {
            try {
                String path =
                        MavenModelUtils.mavenModelToArtifactInfo(MavenModelUtils.toMavenModel(model.mavenArtifactInfo))
                                .getPath();
                String pomPath = PathUtils.stripExtension(path) + ".pom";
                return repoService.exists(new RepoPathImpl(repo.getKey(), pomPath));
            } catch (RepositoryRuntimeException e) {
                cleanupResources();
                throw e;
            }
        }

        private void cleanupResources() {
            log.debug("Cleaning up deployment resources.");
            if (model.mavenArtifactInfo != null) {
                model.mavenArtifactInfo = new MavenArtifactInfo();
            }
            FileUtils.removeFile(model.file);
            model.pomXml = "";
        }

        private class OnGavcChangeBehavior extends AjaxFormComponentUpdatingBehavior {

            private OnGavcChangeBehavior() {
                super("onchange");
            }

            @Override
            protected IAjaxCallDecorator getAjaxCallDecorator() {
                return new NoAjaxIndicatorDecorator();
            }

            @Override
            protected void onUpdate(AjaxRequestTarget target) {
                UnitInfo artifactInfo = model.getArtifactInfo();
                model.deployPom = isPomArtifact() || !isPomExists(getPersistentTargetRepo());
                if (model.deployPom && model.isMavenArtifact) {
                    model.pomChanged = true;
                    MavenArtifactInfo mavenArtifactInfo = (MavenArtifactInfo) artifactInfo;
                    org.apache.maven.model.Model mavenModel;
                    if (model.pomXml != null) {
                        mavenModel = MavenModelUtils.stringToMavenModel(model.pomXml);
                        // update the model built from the xml with the values from the maven artifact info
                        mavenModel.setGroupId(mavenArtifactInfo.getGroupId());
                        mavenModel.setArtifactId(mavenArtifactInfo.getArtifactId());
                        mavenModel.setVersion(mavenArtifactInfo.getVersion());
                        mavenModel.setPackaging(mavenArtifactInfo.getType());
                    } else {
                        // generate a model from the maven artifact info
                        mavenModel = MavenModelUtils.toMavenModel(mavenArtifactInfo);
                    }
                    model.pomXml = MavenModelUtils.mavenModelToString(mavenModel);
                }
                target.addComponent(getPomEditorContainer().get("deployPom"));
                target.addComponent(getPomEditorContainer());
                target.addComponent(get("targetPath"));
                AjaxUtils.refreshFeedback();
            }
        }

        private class OnDeployTypeChangeBehavior extends AjaxFormComponentUpdatingBehavior {
            private OnDeployTypeChangeBehavior() {
                super("onclick");
            }

            @Override
            protected IAjaxCallDecorator getAjaxCallDecorator() {
                return new NoAjaxIndicatorDecorator();
            }

            @Override
            protected void onUpdate(AjaxRequestTarget target) {
                boolean isMavenArtifact = Boolean.parseBoolean(get("isMavenArtifact").getDefaultModelObjectAsString());
                if (!isMavenArtifact) {
                    model.deployPom = false;
                }
                target.addComponent(get("artifactInfo"));
                target.addComponent(get("targetPath"));
            }
        }


        private class OnPomXmlChangeBehavior extends AjaxFormComponentUpdatingBehavior {
            private OnPomXmlChangeBehavior() {
                super("onchange");
            }

            @Override
            protected IAjaxCallDecorator getAjaxCallDecorator() {
                return new NoAjaxIndicatorDecorator();
            }

            @Override
            protected void onUpdate(AjaxRequestTarget target) {
                model.pomChanged = true;
                if (StringUtils.isEmpty(model.pomXml)) {
                    return;
                }
                try {
                    InputStream pomInputStream = IOUtils.toInputStream(model.pomXml);
                    model.mavenArtifactInfo = MavenModelUtils.mavenModelToArtifactInfo(pomInputStream);
                    model.mavenArtifactInfo.setType(PathUtils.getExtension(model.getTargetPathFieldValue()));
                } catch (Exception e) {
                    error("Failed to parse input pom");
                    AjaxUtils.refreshFeedback();
                }
                model.deployPom = isPomArtifact() || !isPomExists(getPersistentTargetRepo());
                target.addComponent(get("artifactInfo:artifactInfo.groupId"));
                target.addComponent(get("artifactInfo:artifactInfo.artifactId"));
                target.addComponent(get("artifactInfo:artifactInfo.version"));
                target.addComponent(get("targetPath"));
                target.addComponent(getPomEditorContainer());
                AjaxUtils.refreshFeedback();
            }
        }

        private class OnGeneratePomChangeBehavior extends AjaxFormComponentUpdatingBehavior {
            private OnGeneratePomChangeBehavior() {
                super("onclick");
            }

            @Override
            protected void onUpdate(AjaxRequestTarget target) {
                if (model.deployPom) {
                    model.pomChanged = false;
                    if (StringUtils.isBlank(model.pomXml)) {
                        model.pomXml = deployService.getPomModelString(model.file);
                    }
                }
                target.addComponent(getPomEditorContainer());
            }
        }

        private class OnPackTypeChangeBehavior extends OnGavcChangeBehavior {
            @Override
            protected void onUpdate(AjaxRequestTarget target) {
                Component deployPomCheckbox = getPomEditorContainer().get("deployPom");
                boolean pomArtifact = isPomArtifact();
                deployPomCheckbox.setVisible(!pomArtifact);
                if (pomArtifact) {
                    model.deployPom = true;
                }
                super.onUpdate(target);
            }
        }

        private void finish(AjaxRequestTarget target) {
            cleanupResources();
            Component uploadPanel = new UploadArtifactPanel();
            DeployArtifactPanel.this.replaceWith(uploadPanel);
            target.addComponent(uploadPanel);
        }

        private class DeployLink extends TitledAjaxSubmitLink {
            private DeployLink(String id) {
                super(id, "Deploy Artifact", DeployArtifactForm.this);
                setOutputMarkupId(true);
            }

            @Override
            protected void onSubmit(AjaxRequestTarget target, Form form) {
                try {
                    //Make sure not to override a good pom.
                    boolean deployPom = model.deployPom && model.isMavenArtifact;
                    if (deployPom) {
                        if (isPomArtifact()) {
                            deployPom();
                        } else {
                            deployFileAndPom();
                        }
                    } else {
                        deployFile();
                    }
                    String repoKey = model.targetRepo.getKey();
                    String artifactPath = model.getTargetPathFieldValue();
                    StringBuilder successMessagesBuilder = new StringBuilder();
                    successMessagesBuilder.append("Successfully deployed ");
                    String repoPathUrl = getRepoPathUrl(repoKey, artifactPath);
                    if (StringUtils.isNotBlank(repoPathUrl)) {
                        successMessagesBuilder.append("<a href=\"").append(repoPathUrl).append("\">").
                                append(artifactPath).append(" into ").append(repoKey).append("</a>.");
                    } else {
                        successMessagesBuilder.append(artifactPath).append(" into ").append(repoKey).append(".");
                    }
                    info(successMessagesBuilder.toString());
                    AjaxUtils.refreshFeedback(target);
                    finish(target);
                } catch (Exception e) {
                    Throwable cause = ExceptionUtils.getRootCause(e);
                    if ((cause instanceof BadPomException) || (cause instanceof RepoRejectException)) {
                        log.warn("Failed to deploy artifact: {}", e.getMessage());
                    } else {
                        log.warn("Failed to deploy artifact.", e);
                    }
                    error(e.getMessage());
                    AjaxUtils.refreshFeedback(target);
                }
            }

            private void deployPom() throws Exception {
                if (model.pomChanged) {
                    savePomXml();
                }
                deployFile();
            }

            private void savePomXml() throws Exception {
                StringInputStream input = null;
                FileOutputStream output = null;
                try {
                    input = new StringInputStream(model.pomXml);
                    output = new FileOutputStream(model.file);
                    IOUtils.copy(input, output);
                } finally {
                    IOUtils.closeQuietly(input);
                    IOUtils.closeQuietly(output);
                }
            }

            private void deployFileAndPom() throws IOException, RepoRejectException {
                deployService.validatePom(model.pomXml, model.getTargetPathFieldValue(),
                        model.targetRepo.isSuppressPomConsistencyChecks());
                deployService.deploy(model.targetRepo, model.getArtifactInfo(), model.file, model.pomXml, true, false);
            }

            private void deployFile() throws RepoRejectException {
                deployService.deploy(model.targetRepo, model.getArtifactInfo(), model.file);
            }

            /**
             * Returns an HTTP link that points to the deployed item within the browser tree.
             *
             * @param repoKey      Repo key of item to point to
             * @param artifactPath Relative path of item to point to
             * @return HTTP link if permitted and valid, null if not
             */
            private String getRepoPathUrl(String repoKey, String artifactPath) {
                if (!shouldProvideTreeLink(artifactPath)) {
                    return null;
                }
                StringBuilder urlBuilder = new StringBuilder();
                if (NamingUtils.isChecksum(artifactPath)) {
                    // if a checksum file is deployed, link to the target file
                    artifactPath = MavenNaming.getChecksumTargetFile(artifactPath);
                }

                String metadataName = null;
                if (NamingUtils.isMetadata(artifactPath)) {
                    SerializablePair<String, String> nameAndParent = NamingUtils.getMetadataNameAndParent(artifactPath);
                    metadataName = nameAndParent.getFirst();
                    artifactPath = nameAndParent.getSecond();
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
                if (StringUtils.isNotBlank(metadataName)) {
                    urlBuilder.append("&").append(PersistentTabbedPanel.SELECT_TAB_PARAM).append("=Metadata");

                    try {
                        urlBuilder.append("&").append(MetadataPanel.SELECT_METADATA_PARAM).append("=").
                                append(URLEncoder.encode(metadataName, "utf-8"));
                    } catch (UnsupportedEncodingException e) {
                        log.warn("Unable to link to tree item metadata '" + metadataName + "'.", e);
                    }
                }
                return urlBuilder.toString();
            }

            /**
             * Indicates whether a link to the tree item of the deployed artifact should be provided. Links should be
             * provided if deploying a snapshot file to repository with different snapshot version policy.
             *
             * @param artifactPath The artifact deploy path
             * @return True if should provide the link
             */
            private boolean shouldProvideTreeLink(String artifactPath) {
                SnapshotVersionBehavior repoSnapshotBehavior = model.targetRepo.getSnapshotVersionBehavior();

                boolean uniqueToNonUnique = MavenNaming.isUniqueSnapshot(artifactPath)
                        && SnapshotVersionBehavior.NONUNIQUE.equals(repoSnapshotBehavior);

                boolean nonUniqueToNonUnique = MavenNaming.isNonUniqueSnapshot(artifactPath)
                        && SnapshotVersionBehavior.UNIQUE.equals(repoSnapshotBehavior);

                return !uniqueToNonUnique && !nonUniqueToNonUnique;
            }
        }

        private class CancelLink extends TitledAjaxLink {
            private CancelLink(String id) {
                super(id, "Cancel");
            }

            public void onClick(AjaxRequestTarget target) {
                finish(target);
            }
        }
    }

    private static class DeployModel implements Serializable {
        private List<LocalRepoDescriptor> repos;
        private File file;
        private LocalRepoDescriptor targetRepo;
        private String targetPath;
        private boolean pomChanged = false;
        private String pomXml;
        private boolean isMavenArtifact = true;
        private boolean deployPom;
        private MavenArtifactInfo mavenArtifactInfo;

        /**
         * Do not use this method to retrieve the actual value of the field, since this method determines the value of
         * the field (based on the model) before returning value.<br> To simply return the value of the field use
         * org.artifactory.webapp.wicket.page.deploy.step2.DeployArtifactPanel.DeployModel#getTargetPathFieldValue()
         *
         * @return Target path
         */
        public String getTargetPath() {
            /**
             * If the item should be deployed as a maven artifact, is a pom, or contains a pom, prepare the path in the
             * Maven format, otherwise, the path should just be the file name so we avoid awkward deployment paths for
             * non-Maven artifacts
             */
            if (isMavenArtifact || MavenArtifactInfo.POM.equalsIgnoreCase(mavenArtifactInfo.getType()) ||
                    StringUtils.isNotBlank(MavenModelUtils.getPomFileAsStringFromJar(file))) {
                targetPath = mavenArtifactInfo.getPath();
            } else {
                targetPath = file.getName();
            }
            return targetPath;
        }

        /**
         * Simply returns the actual value of the field
         *
         * @return Target path field value
         */
        public String getTargetPathFieldValue() {
            return targetPath;
        }

        public UnitInfo getArtifactInfo() {
            if (isMavenArtifact) {
                return mavenArtifactInfo;
            } else {
                return new ArtifactInfo(targetPath);
            }
        }
    }
}