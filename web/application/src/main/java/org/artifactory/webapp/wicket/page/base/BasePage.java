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

package org.artifactory.webapp.wicket.page.base;

import org.apache.commons.lang.StringUtils;
import org.apache.wicket.AttributeModifier;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.markup.html.IHeaderContributor;
import org.apache.wicket.markup.html.IHeaderResponse;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.link.AbstractLink;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.PropertyModel;
import org.apache.wicket.request.flow.RedirectToUrlException;
import org.apache.wicket.request.http.WebResponse;
import org.apache.wicket.spring.injection.annot.SpringBean;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.AddonsWebManager;
import org.artifactory.addon.wicket.SamlAddon;
import org.artifactory.addon.wicket.WebApplicationAddon;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.storage.StorageQuotaInfo;
import org.artifactory.api.storage.StorageService;
import org.artifactory.common.wicket.WicketProperty;
import org.artifactory.common.wicket.behavior.CssClass;
import org.artifactory.common.wicket.component.links.TitledAjaxSubmitLink;
import org.artifactory.common.wicket.component.modal.HasModalHandler;
import org.artifactory.common.wicket.component.modal.ModalHandler;
import org.artifactory.common.wicket.component.panel.blocker.Blocker;
import org.artifactory.common.wicket.component.panel.feedback.FeedbackDistributer;
import org.artifactory.common.wicket.component.panel.feedback.FeedbackMessagesPanel;
import org.artifactory.common.wicket.component.panel.feedback.aggregated.AggregateFeedbackPanel;
import org.artifactory.common.wicket.component.panel.sidemenu.MenuPanel;
import org.artifactory.common.wicket.resources.domutils.CommonJsPackage;
import org.artifactory.common.wicket.util.WicketUtils;
import org.artifactory.web.ui.skins.GreenSkin;
import org.artifactory.webapp.wicket.application.ArtifactoryWebSession;
import org.artifactory.webapp.wicket.page.search.BaseSearchPage;
import org.artifactory.webapp.wicket.page.search.artifact.ArtifactSearchPage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

public abstract class BasePage extends WebPage implements HasModalHandler {
    private static final Logger log = LoggerFactory.getLogger(BasePage.class);

    @SpringBean
    private CentralConfigService centralConfig;

    @SpringBean
    private AuthorizationService authorizationService;

    @SpringBean
    private AddonsManager addonsManager;

    @SpringBean
    private AddonsWebManager addonsWebManager;

    private ModalHandler modalHandler;

    protected BasePage() {
        init();
    }

    @Override
    public ModalHandler getModalHandler() {
        return modalHandler;
    }

    @WicketProperty
    public String getPageTitle() {
        return addonsManager.addonByType(WebApplicationAddon.class).getPageTitle(this);
    }

    protected void init() {
        setVersioned(false);

        add(new CommonJsPackage());
        add(new GreenSkin());

        add(new Label("pageTitle", new PropertyModel(this, "pageTitle")));

        WebMarkupContainer revisionHeader = new WebMarkupContainer("revision", new Model());
        String revisionValue = centralConfig.getVersionInfo().getRevision();
        revisionHeader.add(new AttributeModifier("content", revisionValue));
        add(revisionHeader);

        add(new FooterLabel("footer"));

        String license = addonsWebManager.getFooterMessage(authorizationService.isAdmin());
        Label licenseLabel = new Label("license", license);
        licenseLabel.setEscapeModelStrings(false);
        add(licenseLabel);

        add(new LicenseFooterLabel("licenseFooter"));
        add(new StorageQuotaLabel("storageQuotaFooter"));

        add(new HeaderLogoPanel("logo"));

        addAjaxIndicator();
        addFeedback();
        addVersionInfo();
        addUserInfo();
        addMenu();
        addSearchForm();
        addModalHandler();
    }

    @Override
    protected void configureResponse(WebResponse response) {
        super.configureResponse(response);
        response.setHeader("Cache-Control", "no-store, no-cache, max-age=0, must-revalidate");
    }

    public abstract String getPageName();

    protected Class<? extends BasePage> getMenuPageClass() {
        return getClass();
    }

    private void addAjaxIndicator() {
        add(new Blocker("blocker"));
    }

    private void addFeedback() {
        FeedbackMessagesPanel defaultFeedback = newFeedbackPanel("defaultFeedback");
        add(defaultFeedback);

        FeedbackDistributer feedbackDistributer = new FeedbackDistributer("feedbackDistributer");
        add(feedbackDistributer);

        feedbackDistributer.setDefaultFeedbackPanel(defaultFeedback);
    }

    protected FeedbackMessagesPanel newFeedbackPanel(String id) {
        return new AggregateFeedbackPanel(id);
    }

    private void addModalHandler() {
        modalHandler = new ModalHandler("modal");
        add(modalHandler);
    }

    private void addSearchForm() {
        Form form = new Form("searchForm") {
            @Override
            public boolean isVisible() {
                return isSignedInOrAnonymous();
            }
        };
        add(form);

        final TextField<String> searchTextField = new TextField<String>("query", Model.of(""));
        form.add(searchTextField);

        TitledAjaxSubmitLink searchButton = new TitledAjaxSubmitLink("searchButton", "Search", form) {
            @Override
            protected void onSubmit(AjaxRequestTarget target, Form form) {
                String query = searchTextField.getDefaultModelObjectAsString();
                //HttpServletRequest req = ((WebRequest) RequestCycle.get().getRequest()).getHttpServletRequest();
                StringBuilder urlBuilder =
                        new StringBuilder(WicketUtils.absoluteMountPathForPage(ArtifactSearchPage.class));
                //StringBuilder urlBuilder = new StringBuilder(HttpUtils.getServletContextUrl(req));
                //urlBuilder.append("/webapp/search/artifact");
                if (StringUtils.isNotBlank(query)) {
                    try {
                        urlBuilder.append("?").append(BaseSearchPage.QUERY_PARAM).append("=").append(
                                URLEncoder.encode(query, "UTF-8"));
                    } catch (UnsupportedEncodingException e) {
                        log.error(String.format("Unable to append the Quick-Search query '%s'", query), e);
                    }
                }
                throw new RedirectToUrlException(urlBuilder.toString());
            }
        };
        form.setDefaultButton(searchButton);
        form.add(searchButton);
    }

    private void addVersionInfo() {
        String versionInfo = addonsManager.addonByType(WebApplicationAddon.class).getVersionInfo();
        add(new Label("version", versionInfo));
    }

    private void addUserInfo() {

        // Enable only for signed in users
        SamlAddon samlAddon = addonsManager.addonByType(SamlAddon.class);
        AbstractLink logoutLink = samlAddon.getLogoutLink("logoutPage");
        add(logoutLink);

        // Enable only if signed in as anonymous
        AbstractLink loginLink = samlAddon.getLoginLink("loginPage");
        add(loginLink);

        // logged in or not logged in
        add(new Label("loggedInLabel", getLoggedInMessage()));

        // update profile link
        WebApplicationAddon applicationAddon = addonsManager.addonByType(WebApplicationAddon.class);
        AbstractLink profileLink = applicationAddon.getProfileLink("profilePage");
        add(profileLink);
    }

    private void addMenu() {
        add(new MenuPanel("menuItem", getMenuPageClass()));
    }

    private String getLoggedInMessage() {
        if (isNotSignedInOrAnonymous()) {
            return "Not Logged In";
        }
        return "Logged In as";
    }


    private boolean isSignedInOrAnonymous() {
        return (ArtifactoryWebSession.get().isSignedIn() && !authorizationService.isAnonymous()) ||
                (authorizationService.isAnonymous() && authorizationService.isAnonAccessEnabled());
    }

    private boolean isNotSignedInOrAnonymous() {
        return !ArtifactoryWebSession.get().isSignedIn() || authorizationService.isAnonymous();
    }

    private static class FooterLabel extends Label {
        @SpringBean
        private CentralConfigService centralConfig;

        @SpringBean
        private AuthorizationService authorizationService;

        @SpringBean
        private AddonsManager addons;

        public FooterLabel(String id) {
            super(id, "");
            setOutputMarkupId(true);
        }

        @Override
        protected void onBeforeRender() {
            String footer = centralConfig.getDescriptor().getFooter();
            setDefaultModelObject(footer);
            super.onBeforeRender();
        }
    }

    private static class LicenseFooterLabel extends Label implements IHeaderContributor {
        @SpringBean
        private AuthorizationService authorizationService;

        @SpringBean
        private AddonsManager addons;

        @SpringBean
        private AddonsWebManager addonsWebManager;

        public LicenseFooterLabel(String id) {
            super(id, "");
            setOutputMarkupId(true);
            setEscapeModelStrings(false);

            String message = null;
            if (authorizationService.isAdmin() || isTrial()) {
                message = addonsWebManager.getLicenseFooterMessage();
                setDefaultModelObject(message);
            }
            setVisible(StringUtils.isNotEmpty(message));
        }

        @Override
        public void renderHead(IHeaderResponse response) {
            response.renderJavaScript("DomUtils.footerHeight = 18;", getMarkupId() + "js");
        }

        private boolean isTrial() {
            return addons.isLicenseInstalled() && "Trial".equalsIgnoreCase(addons.getLicenseDetails()[2]);
        }
    }

    private static class StorageQuotaLabel extends Label implements IHeaderContributor {
        @SpringBean
        private AuthorizationService authorizationService;

        @SpringBean
        private StorageService storageService;

        public StorageQuotaLabel(String id) {
            super(id, "");
            setOutputMarkupId(true);
            setEscapeModelStrings(false);

            String message = null;
            if (authorizationService.isAdmin()) {
                StorageQuotaInfo info = storageService.getStorageQuotaInfo(0);
                if (info != null) {
                    if (info.isLimitReached()) {
                        add(new CssClass("storage-quota-limit"));
                        message = info.getErrorMessage();
                    } else if (info.isWarningLimitReached()) {
                        add(new CssClass("storage-quota-warning"));
                        message = info.getWarningMessage();
                    }
                    setDefaultModelObject(message);
                }
            }
            setVisible(StringUtils.isNotEmpty(message));
        }

        @Override
        public void renderHead(IHeaderResponse response) {
            response.renderJavaScript("DomUtils.footerHeight = 18;", getMarkupId() + "js");
        }
    }
}
