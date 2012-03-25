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

package org.artifactory.model.xstream.security;

import com.google.common.collect.ImmutableSet;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import org.artifactory.sapi.security.SecurityConstants;
import org.artifactory.security.MutableUserInfo;
import org.artifactory.security.UserGroupInfo;
import org.artifactory.security.UserInfo;

import java.util.HashSet;
import java.util.Set;

@XStreamAlias("user")
public class UserImpl implements MutableUserInfo {

    private String username;
    private String password;
    private String email;
    private String genPasswordKey;
    private boolean admin;
    private boolean enabled;
    private boolean updatableProfile;
    private boolean accountNonExpired;
    private boolean credentialsNonExpired;
    private boolean accountNonLocked;

    private String realm;
    private String privateKey;
    private String publicKey;
    private boolean transientUser;

    private Set<UserGroupInfo> groups = new HashSet<UserGroupInfo>(1);

    private long lastLoginTimeMillis;
    private String lastLoginClientIp;

    private long lastAccessTimeMillis;
    private String lastAccessClientIp;

    public UserImpl() {
    }

    public UserImpl(String username) {
        this.username = username;
    }

    public UserImpl(UserInfo user) {
        this.username = user.getUsername();
        this.password = user.getPassword();
        this.email = user.getEmail();
        this.admin = user.isAdmin();
        this.enabled = user.isEnabled();
        this.updatableProfile = user.isUpdatableProfile();
        this.accountNonExpired = user.isAccountNonExpired();
        this.credentialsNonExpired = user.isCredentialsNonExpired();
        this.accountNonLocked = user.isAccountNonLocked();
        this.transientUser = user.isTransientUser();
        this.realm = user.getRealm();

        Set<UserGroupInfo> groups = user.getGroups();
        if (groups != null) {
            this.groups = new HashSet<UserGroupInfo>(groups);
        } else {
            this.groups = new HashSet<UserGroupInfo>(1);
        }

        setPrivateKey(user.getPrivateKey());
        setPublicKey(user.getPublicKey());
        setGenPasswordKey(user.getGenPasswordKey());
        setLastLoginClientIp(user.getLastLoginClientIp());
        setLastLoginTimeMillis(user.getLastLoginTimeMillis());
        setLastAccessClientIp(user.getLastAccessClientIp());
        setLastAccessTimeMillis(user.getLastAccessTimeMillis());
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public void setUsername(String username) {
        this.username = username;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public void setPassword(String password) {
        this.password = password;
    }

    @Override
    public String getEmail() {
        return email;
    }

    @Override
    public void setEmail(String email) {
        this.email = email;
    }

    @Override
    public String getPrivateKey() {
        return privateKey;
    }

    @Override
    public void setPrivateKey(String privateKey) {
        this.privateKey = privateKey;
    }

    @Override
    public String getPublicKey() {
        return publicKey;
    }

    @Override
    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    @Override
    public String getGenPasswordKey() {
        return genPasswordKey;
    }

    @Override
    public void setGenPasswordKey(String genPasswordKey) {
        this.genPasswordKey = genPasswordKey;
    }

    @Override
    public boolean isAdmin() {
        return admin;
    }

    @Override
    public void setAdmin(boolean admin) {
        this.admin = admin;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public boolean isUpdatableProfile() {
        return updatableProfile;
    }

    @Override
    public void setUpdatableProfile(boolean updatableProfile) {
        this.updatableProfile = updatableProfile;
    }

    @Override
    public boolean isAccountNonExpired() {
        return accountNonExpired;
    }

    @Override
    public void setAccountNonExpired(boolean accountNonExpired) {
        this.accountNonExpired = accountNonExpired;
    }

    @Override
    public boolean isAccountNonLocked() {
        return accountNonLocked;
    }

    @Override
    public void setAccountNonLocked(boolean accountNonLocked) {
        this.accountNonLocked = accountNonLocked;
    }

    @Override
    public boolean isTransientUser() {
        return transientUser;
    }

    @Override
    public void setTransientUser(boolean transientUser) {
        this.transientUser = transientUser;
    }

    @Override
    public String getRealm() {
        return realm;
    }

    @Override
    public boolean isExternal() {
        return !SecurityConstants.DEFAULT_REALM.equals(realm);
    }

    @Override
    public void setRealm(String realm) {
        this.realm = realm;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return credentialsNonExpired;
    }

    @Override
    public void setCredentialsNonExpired(boolean credentialsNonExpired) {
        this.credentialsNonExpired = credentialsNonExpired;
    }

    @Override
    public boolean isAnonymous() {
        return (username != null && username.equalsIgnoreCase(ANONYMOUS));
    }

    @Override
    public boolean isInGroup(String groupName) {
        //Use the equals() behavior with a dummy userGroupInfo
        UserGroupInfo userGroupInfo = getDummyGroup(groupName);
        return getGroups().contains(userGroupInfo);
    }

    @Override
    public void addGroup(String groupName) {
        addGroup(groupName, SecurityConstants.DEFAULT_REALM);
    }

    @Override
    public void addGroup(String groupName, String realm) {
        UserGroupInfo userGroupInfo = new UserGroupImpl(groupName, realm);
        // group equality is currently using group name only, so make sure to remove existing group with the same name
        _groups().remove(userGroupInfo);
        _groups().add(userGroupInfo);
    }

    @Override
    public void removeGroup(String groupName) {
        //Use the equals() behavior with a dummy userGroupInfo
        UserGroupInfo userGroupInfo = getDummyGroup(groupName);
        _groups().remove(userGroupInfo);
    }

    /**
     * @return The _groups() names this user belongs to. Empty list if none.
     */
    @Override
    public Set<UserGroupInfo> getGroups() {
        return ImmutableSet.copyOf(_groups());
    }

    // Needed because OCM and XStream inject nulls :(
    private Set<UserGroupInfo> _groups() {
        if (groups == null) {
            this.groups = new HashSet<UserGroupInfo>(1);
        }
        return groups;
    }

    @Override
    public void setGroups(Set<UserGroupInfo> groups) {
        if (groups == null) {
            this.groups = new HashSet<UserGroupInfo>(1);
        } else {
            this.groups = new HashSet<UserGroupInfo>(groups);
        }
    }

    @Override
    public void setInternalGroups(Set<String> groups) {
        if (groups == null) {
            this.groups = new HashSet<UserGroupInfo>(1);
            return;
        }
        //Add groups with the default internal realm
        _groups().clear();
        for (String group : groups) {
            addGroup(group);
        }
    }

    @Override
    public long getLastLoginTimeMillis() {
        return lastLoginTimeMillis;
    }

    @Override
    public void setLastLoginTimeMillis(long lastLoginTimeMillis) {
        this.lastLoginTimeMillis = lastLoginTimeMillis;
    }

    @Override
    public String getLastLoginClientIp() {
        return lastLoginClientIp;
    }

    @Override
    public void setLastLoginClientIp(String lastLoginClientIp) {
        this.lastLoginClientIp = lastLoginClientIp;
    }

    @Override
    public long getLastAccessTimeMillis() {
        return lastAccessTimeMillis;
    }

    @Override
    public void setLastAccessTimeMillis(long lastAccessTimeMillis) {
        this.lastAccessTimeMillis = lastAccessTimeMillis;
    }

    @Override
    public String getLastAccessClientIp() {
        return lastAccessClientIp;
    }

    @Override
    public void setLastAccessClientIp(String lastAccessClientIp) {
        this.lastAccessClientIp = lastAccessClientIp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        UserImpl info = (UserImpl) o;

        return !(username != null ? !username.equals(info.username) : info.username != null);
    }

    @Override
    public int hashCode() {
        return (username != null ? username.hashCode() : 0);
    }

    private static UserGroupInfo getDummyGroup(String groupName) {
        UserGroupInfo userGroupInfo = new UserGroupImpl(groupName, "whatever");
        return userGroupInfo;
    }

    @Override
    public boolean hasInvalidPassword() {
        return INVALID_PASSWORD.equals(getPassword());
    }

}