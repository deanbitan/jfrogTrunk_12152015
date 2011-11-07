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

package org.artifactory.factory.xstream;

import com.thoughtworks.xstream.XStream;
import org.artifactory.checksum.ChecksumInfo;
import org.artifactory.checksum.ChecksumType;
import org.artifactory.checksum.ChecksumsInfo;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * @author Yoav Landman
 */
public class ChecksumsInfoConverterTest {
    @Test
    public void testConversion() {
        ChecksumsInfo checksumsInfo = new ChecksumsInfo();
        checksumsInfo.addChecksumInfo(new ChecksumInfo(ChecksumType.sha1, null, "actual"));
        checksumsInfo.addChecksumInfo(new ChecksumInfo(ChecksumType.md5, "md5Orig", "md5Actual"));
        XStream xstream = XStreamFactory.create();
        xstream.processAnnotations(ChecksumsInfo.class);
        xstream.processAnnotations(ChecksumInfo.class);
        String xml = xstream.toXML(checksumsInfo);
        Object deserializedMap = xstream.fromXML(xml);
        Assert.assertEquals(deserializedMap, checksumsInfo);
    }
}
