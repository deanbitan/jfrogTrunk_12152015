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

package org.artifactory.webapp.wicket.page.search;

import com.google.common.collect.Lists;
import org.apache.commons.lang.StringUtils;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.extensions.markup.html.tabs.AbstractTab;
import org.apache.wicket.extensions.markup.html.tabs.ITab;
import org.apache.wicket.extensions.markup.html.tabs.TabbedPanel;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.Model;
import org.apache.wicket.spring.injection.annot.SpringBean;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.wicket.PropertiesAddon;
import org.artifactory.common.wicket.component.panel.sidemenu.SubMenuPanel;
import org.artifactory.log.LoggerFactory;
import org.artifactory.webapp.wicket.page.base.AuthenticatedPage;
import org.artifactory.webapp.wicket.page.search.archive.ArchiveSearchPanel;
import org.artifactory.webapp.wicket.page.search.artifact.ArtifactSearchPanel;
import org.artifactory.webapp.wicket.page.search.gavc.GavcSearchPanel;
import org.artifactory.webapp.wicket.page.search.metadata.MetadataSearchPanel;
import org.artifactory.webapp.wicket.panel.tabbed.StyledTabbedPanel;
import org.slf4j.Logger;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.List;

/**
 * Displays a tabbed panel with the different types of searches
 *
 * @author Noam Tenne
 */
public abstract class BaseSearchPage extends AuthenticatedPage {
    private static final Logger log = LoggerFactory.getLogger(BaseSearchPage.class);

    public static final String QUERY_PARAM = "q";

    @SpringBean
    private AddonsManager addons;
    public static final int ARTIFACT_SEARCH_INDEX = 0;
    public static final int ARCHIVE_SEARCH_INDEX = 1;
    public static final int GAVC_SEARCH_INDEX = 2;
    public static final int PROPERTY_SEARCH_INDEX = 3;
    public static final int METADATA_SEARCH_INDEX = 4;

    private String searchQuery;

    public BaseSearchPage() {
        String requestQuery = getRequest().getParameter(QUERY_PARAM);
        if (StringUtils.isNotBlank(requestQuery)) {
            try {
                searchQuery = URLDecoder.decode(requestQuery, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                log.error(String.format("Unable to extract the query request parameter '%s'", requestQuery), e);
            }
        }
        addTabs();
    }

    private void addTabs() {
        List<ITab> tabs = Lists.newArrayList();
        tabs.add(new AbstractTab(Model.of("Quick Search")) {
            @Override
            public Panel getPanel(String panelId) {
                /**
                 * Make a copy of the query and don't pass the original, so that the state won't be preserved.
                 * See http://issues.jfrog.org/jira/browse/RTFACT-2790
                 */
                String query = searchQuery;
                ArtifactSearchPanel searchPanel = new ArtifactSearchPanel(BaseSearchPage.this, panelId, query);
                searchQuery = null;
                return searchPanel;
            }
        });

        tabs.add(new AbstractTab(Model.of("Class Search")) {
            @Override
            public Panel getPanel(String panelId) {
                return new ArchiveSearchPanel(BaseSearchPage.this, panelId);
            }
        });

        tabs.add(new AbstractTab(Model.of("GAVC Search")) {
            @Override
            public Panel getPanel(String panelId) {
                return new GavcSearchPanel(BaseSearchPage.this, panelId);
            }
        });

        PropertiesAddon propertiesAddon = addons.addonByType(PropertiesAddon.class);
        ITab searchPanel = propertiesAddon.getPropertySearchTabPanel(this, "Property Search");
        tabs.add(searchPanel);

        tabs.add(new AbstractTab(Model.of("POM/XML Search")) {
            @Override
            public Panel getPanel(String panelId) {
                return new MetadataSearchPanel(BaseSearchPage.this, panelId);
            }
        });
        TabbedPanel tabbedPanel = new StyledTabbedPanel("searchTabs", tabs) {
            /*
            Renders the side menu panel, because after we "redirect" between the different search pages, it disappears
             */
            @SuppressWarnings({"unchecked"})
            @Override
            protected void onAjaxUpdate(AjaxRequestTarget target) {
                super.onAjaxUpdate(target);
                Class<? extends BaseSearchPage> menuPageClass =
                        ((BaseSearchPanel) get(TAB_PANEL_ID)).getMenuPageClass();

                SubMenuPanel sideMenuPanel =
                        new SubMenuPanel("sideMenuPanel", getSecondLevelMenuNodes(), menuPageClass);

                BaseSearchPage.this.replace(sideMenuPanel);
                if (target != null) {
                    target.addComponent(sideMenuPanel);
                }
            }
        };
        tabbedPanel.setSelectedTab(getSelectedTab());
        add(tabbedPanel);
    }

    @Override
    public String getPageName() {
        return "Search Artifactory";
    }

    public abstract int getSelectedTab();
}