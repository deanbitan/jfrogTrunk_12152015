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

package org.artifactory.api.module;

import java.io.Serializable;

/**
 * @author Noam Y. Tenne
 */
public class ModuleInfoBuilder implements Serializable {

    private String organization = null;
    private String module = null;
    private String baseRevision = null;
    private String folderIntegrationRevision = null;
    private String fileIntegrationRevision = null;
    private String classifier = null;
    private String ext = null;
    private String type = null;

    public ModuleInfoBuilder() {
    }

    public ModuleInfoBuilder organization(String organization) {
        this.organization = organization;
        return this;
    }

    public ModuleInfoBuilder module(String module) {
        this.module = module;
        return this;
    }

    public ModuleInfoBuilder baseRevision(String baseRevision) {
        this.baseRevision = baseRevision;
        return this;
    }

    public ModuleInfoBuilder folderIntegrationRevision(String folderIntegrationRevision) {
        this.folderIntegrationRevision = folderIntegrationRevision;
        return this;
    }

    public ModuleInfoBuilder fileIntegrationRevision(String fileIntegrationRevision) {
        this.fileIntegrationRevision = fileIntegrationRevision;
        return this;
    }

    public ModuleInfoBuilder classifier(String classifier) {
        this.classifier = classifier;
        return this;
    }

    public ModuleInfoBuilder ext(String ext) {
        this.ext = ext;
        return this;
    }

    public ModuleInfoBuilder type(String type) {
        this.type = type;
        return this;
    }

    public ModuleInfo build() {
        return new ModuleInfo(organization, module, baseRevision, folderIntegrationRevision, fileIntegrationRevision,
                classifier, ext, type);
    }
}