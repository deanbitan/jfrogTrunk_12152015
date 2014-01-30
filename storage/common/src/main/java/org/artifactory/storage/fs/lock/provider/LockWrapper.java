/*
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2013 JFrog Ltd.
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

package org.artifactory.storage.fs.lock.provider;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

/**
 * @author Shay Yaakov
 */
public interface LockWrapper {

    boolean tryLock(long timeout, TimeUnit unit) throws InterruptedException;

    void unlock();

    boolean isLocked();

    boolean isHeldByCurrentThread();

    Collection<Thread> getQueuedThreads();

    void destroy();
}