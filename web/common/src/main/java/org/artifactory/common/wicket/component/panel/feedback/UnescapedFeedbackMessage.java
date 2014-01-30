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

package org.artifactory.common.wicket.component.panel.feedback;

import java.io.Serializable;

/**
 * A feedback message to signal the feedback panel not to escape the message.
 * Caution: this should be used with care. Only internal messages which doesn't propagate from a client input should use this message.
 *
 * @author Shay Yaakov
 */
public class UnescapedFeedbackMessage implements Serializable {
    private String message;

    public UnescapedFeedbackMessage(String message) {
        this.message = message;
    }

    @Override
    public String toString() {
        return message;
    }
}