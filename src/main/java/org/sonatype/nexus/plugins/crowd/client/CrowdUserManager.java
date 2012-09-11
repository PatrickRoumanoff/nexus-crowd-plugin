/*
 * Copyright (c) 2010 Sonatype, Inc. All rights reserved.
 *
 * This program is licensed to you under the Apache License Version 2.0,
 * and you may not use this file except in compliance with the Apache License Version 2.0.
 * You may obtain a copy of the Apache License Version 2.0 at http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Apache License Version 2.0 is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Apache License Version 2.0 for the specific language governing permissions and limitations there under.
 */
package org.sonatype.nexus.plugins.crowd.client;

import java.rmi.RemoteException;
import java.util.Collections;
import java.util.Set;

import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Configuration;
import org.codehaus.plexus.component.annotations.Requirement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.configuration.validation.InvalidConfigurationException;
import org.sonatype.security.usermanagement.AbstractReadOnlyUserManager;
import org.sonatype.security.usermanagement.RoleIdentifier;
import org.sonatype.security.usermanagement.RoleMappingUserManager;
import org.sonatype.security.usermanagement.User;
import org.sonatype.security.usermanagement.UserManager;
import org.sonatype.security.usermanagement.UserNotFoundException;
import org.sonatype.security.usermanagement.UserSearchCriteria;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;

/**
 * @author justin
 * @author Issa Gorissen
 */
@Component(role = UserManager.class, hint = "Crowd")
public class CrowdUserManager extends AbstractReadOnlyUserManager implements UserManager, RoleMappingUserManager {

    protected static final String REALM_NAME = "Crowd";

    protected static final String SOURCE = "Crowd";

    /**
     * The maximum number of results that will be returned from a user query.
     */
    @Configuration(value = "100")
    private int maxResults;

    @Requirement
    private CrowdClientHolder crowdClientHolder;

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    
    public CrowdUserManager() {
        logger.info("CrowdUserManager is starting...");
    }

    /**
     * {@inheritDoc}
     */
    public String getAuthenticationRealmName() {
        return REALM_NAME;
    }

    /**
     * {@inheritDoc}
     */
    public String getSource() {
        return SOURCE;
    }

    /**
     * {@inheritDoc}
     */
    public User getUser(String userId) throws UserNotFoundException {
        if (crowdClientHolder.isConfigured()) {
            try {
                User user = crowdClientHolder.getRestClient().getUser(userId);
                return completeUserRolesAndSource(user);
            } catch (RemoteException e) {
                logger.error("Unable to look up user " + userId, e);
                throw new UserNotFoundException(userId, e.getMessage(), e);
            }
        } else {
            throw new UserNotFoundException("Crowd plugin is not configured.");
        }
    }

    /**
     * {@inheritDoc}
     */
    public Set<RoleIdentifier> getUsersRoles(String userId, String userSource) throws UserNotFoundException {
        if (SOURCE.equals(userSource)) {
            if (crowdClientHolder.isConfigured()) {
                Set<String> roleNames = null;
                try {
                    roleNames = crowdClientHolder.getRestClient().getNestedGroups(userId);
                } catch (RemoteException e) {
                    logger.error("Unable to look up user " + userId, e);
                    return Collections.emptySet();
                }
                return Sets.newHashSet(Iterables.transform(roleNames, new Function<String, RoleIdentifier>() {

                    public RoleIdentifier apply(String from) {
                        return new RoleIdentifier(SOURCE, from);
                    }
                }));
            } else {
                throw new UserNotFoundException("Crowd plugin is not configured.");
            }
        } else {
            return Collections.emptySet();
        }
    }

    /**
     * {@inheritDoc}
     */
    public Set<String> listUserIds() {
        if (crowdClientHolder.isConfigured()) {
            try {
                return crowdClientHolder.getRestClient().getAllUsernames();
            } catch (Exception e) {
                logger.error("Unable to get username list", e);
                return Collections.emptySet();
            }
        } else {
            UnconfiguredNotifier.unconfigured();
            return Collections.emptySet();
        }
    }

    /**
     * {@inheritDoc}
     */
    public Set<User> listUsers() {
        return searchUsers(new UserSearchCriteria());
    }

    /**
     * {@inheritDoc}
     */
    public Set<User> searchUsers(UserSearchCriteria criteria) {
        if (!crowdClientHolder.isConfigured()) {
            UnconfiguredNotifier.unconfigured();
            return Collections.emptySet();
        }
        
        if (!SOURCE.equals(criteria.getSource())) {
        	return Collections.emptySet();
        }

        try {
            Set<User> result = crowdClientHolder.getRestClient().searchUsers(
            		criteria.getUserId(),
            		criteria.getEmail(),
            		criteria.getOneOfRoleIds(),
            		maxResults);
            
            for (User user : result) {
				completeUserRolesAndSource(user);
			}
            
            return result;
            
        } catch (Exception e) {
            logger.error("Unable to get userlist", e);
            return Collections.emptySet();
        }
    }

    /**
     * {@inheritDoc}
     */
    public void setUsersRoles(String userId, String userSource, Set<RoleIdentifier> roleIdentifiers)
            throws UserNotFoundException, InvalidConfigurationException {
        super.setUsersRoles(userId, roleIdentifiers);
    }

    private User completeUserRolesAndSource(User user) throws UserNotFoundException {
        user.setSource(SOURCE);
       	user.setRoles(getUsersRoles(user.getUserId(), SOURCE));
        return user;
    }

}
