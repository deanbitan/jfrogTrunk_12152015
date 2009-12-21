/*
 * This file is part of Artifactory.
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

package org.artifactory.webapp.wicket.page.deploy;

import org.apache.wicket.authorization.UnauthorizedInstantiationException;
import org.apache.wicket.authorization.strategies.role.annotations.AuthorizeInstantiation;
import org.apache.wicket.spring.injection.annot.SpringBean;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.webapp.wicket.page.base.AuthenticatedPage;
import org.artifactory.webapp.wicket.page.deploy.step1.UploadArtifactPanel;

import java.util.List;

@AuthorizeInstantiation(AuthorizationService.ROLE_USER)
public class DeployArtifactPage extends AuthenticatedPage {
    @SpringBean
    private RepositoryService repoService;

    public DeployArtifactPage() {
        checkAuthorization();
        add(new UploadArtifactPanel());
    }

    private void checkAuthorization() {
        List<LocalRepoDescriptor> repos = repoService.getDeployableRepoDescriptors();
        if (repos.isEmpty()) {
            throw new UnauthorizedInstantiationException(DeployArtifactPage.class);
        }
    }

    @Override
    public String getPageName() {
        return "Artifact Deployer";
    }
}