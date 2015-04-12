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

package org.artifactory.mime.version.converter;

import org.artifactory.mime.version.MimeTypesVersion;
import org.artifactory.version.converter.XmlConverter;
import org.jdom2.Document;
import org.jdom2.Element;

/**
 * This converter changes the version of the miemetypes.xml file. This is a special converter which should execute
 * at the end of conversion from any version.
 *
 * @author Yossi Shaul
 */
public class LatestVersionConverter implements XmlConverter {
    @Override
    public void convert(Document doc) {
        Element rootElement = doc.getRootElement();
        // take the latest version from the current mime type version
        rootElement.setAttribute("version", MimeTypesVersion.getCurrent().versionString());
    }
}
