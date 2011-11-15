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

package org.artifactory.repo.remote.browse;

import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.methods.GetMethod;

import java.io.IOException;
import java.io.InputStream;

/**
 * Basic single method interface for controlling HttpMethod execution. Needed by HttpRepo to add all used headers for
 * Artifactory communication.
 *
 * @author Fred Simon
 */
public interface HttpExecutor {
    int executeMethod(HttpMethod method) throws IOException;

    /**
     * Returns the response input stream. Also handles GZip streams.
     *
     * @param method The executed method
     * @return The response input stream
     */
    InputStream getResponseStream(GetMethod method) throws IOException;
}
