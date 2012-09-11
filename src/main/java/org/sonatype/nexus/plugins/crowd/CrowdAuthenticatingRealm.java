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
package org.sonatype.nexus.plugins.crowd;

import java.rmi.RemoteException;
import java.util.Set;

import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.authc.pam.UnsupportedTokenException;
import org.apache.shiro.authz.AuthorizationException;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.realm.Realm;
import org.apache.shiro.subject.PrincipalCollection;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Disposable;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Initializable;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.InitializationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.nexus.plugins.crowd.client.CrowdClientHolder;

@Component(role = Realm.class, hint = CrowdAuthenticatingRealm.ROLE, description = "OSS Crowd Authentication Realm")
public class CrowdAuthenticatingRealm extends AuthorizingRealm implements Initializable, Disposable {

	public static final String ROLE = "NexusCrowdAuthenticationRealm";
	private static final String DEFAULT_MESSAGE = "Could not retrieve info from Crowd.";
	private static boolean active;

	@Requirement
	private CrowdClientHolder crowdClientHolder;

	private Logger logger = LoggerFactory.getLogger(CrowdAuthenticatingRealm.class);

	public static boolean isActive() {
		return active;
	}

	public void dispose() {
		active = false;
		logger.info("Crowd Realm deactivated...");
	}

	@Override
	public String getName() {
		return CrowdAuthenticatingRealm.class.getName();
	}

	public void initialize() throws InitializationException {
		logger.info("Crowd Realm activated...");
		active = true;
	}

	@Override
	protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken authenticationToken)
			throws AuthenticationException {
		if (!(authenticationToken instanceof UsernamePasswordToken)) {
			throw new UnsupportedTokenException("Token of type " + authenticationToken.getClass().getName()
					+ " is not supported.  A " + UsernamePasswordToken.class.getName() + " is required.");
		}
		UsernamePasswordToken token = (UsernamePasswordToken) authenticationToken;

		String password = new String(token.getPassword());

		try {
			crowdClientHolder.getAuthenticationManager().authenticate(token.getUsername(), password);
			return new SimpleAuthenticationInfo(token.getPrincipal(), token.getCredentials(), getName());
		} catch (RemoteException e) {
			throw new AuthenticationException(DEFAULT_MESSAGE, e);
		}
	}

	@Override
	protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
		String username = (String) principals.getPrimaryPrincipal();
		try {
			Set<String> groups = crowdClientHolder.getRestClient().getNestedGroups(username);
			return new SimpleAuthorizationInfo(groups);
		} catch (RemoteException e) {
			throw new AuthorizationException(DEFAULT_MESSAGE, e);
		}
	}
}
