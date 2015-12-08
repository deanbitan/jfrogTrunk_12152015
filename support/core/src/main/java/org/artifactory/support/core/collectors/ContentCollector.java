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

package org.artifactory.support.core.collectors;

import org.artifactory.support.config.CollectConfiguration;

import java.io.File;
import java.util.concurrent.CountDownLatch;

/**
 * Provides ContentCollector services
 *
 * @param <T> configuration type declaration
 *
 * @author Michael Pasternak
 */
public interface ContentCollector<T extends CollectConfiguration> {
    /**
     * Collects content according to {@link CollectConfiguration}
     *
     * @param configuration instance specific {@link CollectConfiguration}
     * @param tmpDir output dir
     *
     * @return boolean
     */
    boolean collect(T configuration, File tmpDir);
}