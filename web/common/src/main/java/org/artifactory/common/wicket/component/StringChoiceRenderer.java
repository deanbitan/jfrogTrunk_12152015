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

package org.artifactory.common.wicket.component;

import org.apache.wicket.markup.html.form.IChoiceRenderer;

/**
 * @author Yoav Aharoni
 */
public class StringChoiceRenderer<T> implements IChoiceRenderer<T> {
    private static final StringChoiceRenderer INSTANCE = new StringChoiceRenderer();

    @Override
    public Object getDisplayValue(Object object) {
        if (object == null) {
            return "";
        }
        return object.toString();
    }

    @Override
    public String getIdValue(Object object, int index) {
        return String.valueOf(index);
    }

    @SuppressWarnings({"unchecked"})
    public static <T> StringChoiceRenderer<T> getInstance() {
        return (StringChoiceRenderer<T>) INSTANCE;
    }
}
