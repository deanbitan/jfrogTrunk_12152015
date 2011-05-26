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

package org.artifactory.addon.replication;

import org.artifactory.addon.ReplicationAddon;
import org.artifactory.repo.RepoPath;

import java.io.Writer;

/**
 * @author Noam Y. Tenne
 */
public class ReplicationSettingsBuilder {

    private final RepoPath repoPath;
    private boolean progress = false;
    private int mark = 0;
    private boolean deleteExisting = false;
    private boolean includeProperties = false;
    private ReplicationAddon.Overwrite overwrite = ReplicationAddon.Overwrite.force;
    private final Writer responseWriter;

    public ReplicationSettingsBuilder(RepoPath repoPath, Writer responseWriter) {
        this.repoPath = repoPath;
        this.responseWriter = responseWriter;
    }

    public ReplicationSettingsBuilder progress(boolean progress) {
        this.progress = progress;
        return this;
    }

    public ReplicationSettingsBuilder mark(int mark) {
        this.mark = mark;
        return this;
    }

    public ReplicationSettingsBuilder deleteExisting(boolean deleteExisting) {
        this.deleteExisting = deleteExisting;
        return this;
    }

    public ReplicationSettingsBuilder includeProperties(boolean includeProperties) {
        this.includeProperties = includeProperties;
        return this;
    }

    public ReplicationSettingsBuilder overwrite(ReplicationAddon.Overwrite overwrite) {
        this.overwrite = overwrite;
        return this;
    }

    public ReplicationSettings build() {
        if (repoPath == null) {
            throw new IllegalArgumentException("Repo path cannot be null.");
        }
        if (responseWriter == null) {
            throw new IllegalArgumentException("Response writer cannot be null.");
        }

        return new ReplicationSettings(repoPath, progress, mark, deleteExisting, includeProperties, overwrite,
                responseWriter);
    }
}