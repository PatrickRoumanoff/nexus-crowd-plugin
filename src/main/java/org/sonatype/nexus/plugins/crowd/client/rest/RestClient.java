/*
 * This program is licensed to you under the Apache License Version 2.0,
 * and you may not use this file except in compliance with the Apache License Version 2.0.
 * You may obtain a copy of the Apache License Version 2.0 at http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Apache License Version 2.0 is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Apache License Version 2.0 for the specific language governing permissions and limitations there under.
 */
package org.sonatype.nexus.plugins.crowd.client.rest;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.rmi.RemoteException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.ws.rs.core.MediaType;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.nexus.plugins.crowd.client.rest.jaxb.ConfigCookieGetResponse;
import org.sonatype.nexus.plugins.crowd.client.rest.jaxb.GroupResponse;
import org.sonatype.nexus.plugins.crowd.client.rest.jaxb.SearchUserGetResponse;
import org.sonatype.nexus.plugins.crowd.client.rest.jaxb.SessionPost;
import org.sonatype.nexus.plugins.crowd.client.rest.jaxb.SessionPostResponse;
import org.sonatype.nexus.plugins.crowd.client.rest.jaxb.UserResponse;
import org.sonatype.nexus.plugins.crowd.client.rest.jaxb.GroupsResponse;
import org.sonatype.nexus.plugins.crowd.config.model.v1_0_0.Configuration;
import org.sonatype.security.authorization.Role;
import org.sonatype.security.usermanagement.DefaultUser;
import org.sonatype.security.usermanagement.User;
import org.sonatype.security.usermanagement.UserStatus;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.UniformInterfaceException;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.client.apache.ApacheHttpClient;
import com.sun.jersey.client.apache.config.ApacheHttpClientConfig;
import com.sun.jersey.client.apache.config.ApacheHttpClientState;
import com.sun.jersey.client.apache.config.DefaultApacheHttpClientConfig;

/**
 * @author Issa Gorissen
 */
public class RestClient {
	
	private static final Logger LOG = LoggerFactory.getLogger(RestClient.class);
	
	private static final Pattern ERROR_XML = Pattern.compile(".*<reason>(.*)</reason>.*<message>(.*)</message>.*", Pattern.CASE_INSENSITIVE);
	
	private Client client;
	private URI crowdServer;
	
	public RestClient(Configuration config) throws URISyntaxException {
		DefaultApacheHttpClientConfig clientConfig = new DefaultApacheHttpClientConfig();
		clientConfig.getProperties().put(ApacheHttpClientConfig.PROPERTY_HANDLE_COOKIES, Boolean.TRUE);
		clientConfig.getProperties().put(ApacheHttpClientConfig.PROPERTY_PREEMPTIVE_AUTHENTICATION, Boolean.TRUE);
		clientConfig.getProperties().put(ClientConfig.PROPERTY_CONNECT_TIMEOUT, new Integer(config.getHttpTimeout()));
		clientConfig.getProperties().put(ClientConfig.PROPERTY_READ_TIMEOUT, new Integer(config.getHttpTimeout()));
		clientConfig.getProperties().put(ClientConfig.PROPERTY_THREADPOOL_SIZE, new Integer(config.getHttpMaxConnections()));
		
		crowdServer = new URI(config.getCrowdServerUrl()).resolve("rest/usermanagement/1/");
		
		ApacheHttpClientState httpState = new ApacheHttpClientState();
		httpState.setCredentials(null, crowdServer.getHost(), crowdServer.getPort(), config.getApplicationName(), config.getApplicationPassword());
		if (StringUtils.isNotBlank(config.getHttpProxyHost()) && config.getHttpProxyPort() > 0) {
			clientConfig.getProperties().put(ApacheHttpClientConfig.PROPERTY_PROXY_URI, config.getHttpProxyHost() + ':' + config.getHttpProxyPort());
			
			if (config.getHttpProxyUsername() != null && config.getHttpProxyPassword() != null) {
				httpState.setProxyCredentials(null, config.getHttpProxyHost(), config.getHttpProxyPort(), config.getHttpProxyUsername(), config.getHttpProxyPassword());
			}
		}
		clientConfig.getProperties().put(ApacheHttpClientConfig.PROPERTY_HTTP_STATE, httpState);
		
		if (LOG.isDebugEnabled()) {
			LOG.debug("HTTP Client config");
			LOG.debug(config.getCrowdServerUrl());
			LOG.debug(crowdServer.toString());
			LOG.debug("PROPERTY_THREADPOOL_SIZE:" + clientConfig.getProperty(ClientConfig.PROPERTY_THREADPOOL_SIZE));
			LOG.debug("PROPERTY_READ_TIMEOUT:" + clientConfig.getProperty(ClientConfig.PROPERTY_READ_TIMEOUT));
			LOG.debug("PROPERTY_CONNECT_TIMEOUT:" + clientConfig.getProperty(ClientConfig.PROPERTY_CONNECT_TIMEOUT));
			LOG.debug("PROPERTY_PROXY_URI:" + clientConfig.getProperty(ApacheHttpClientConfig.PROPERTY_PROXY_URI));
			LOG.debug("Crowd application name:" + config.getApplicationName());
		}
		
		client = ApacheHttpClient.create(clientConfig);
	}
	
	/**
	 * Create new session token
	 * 
	 * @param username
	 * @param password
	 * @return session token
	 * @throws RemoteException
	 */
	public String createSessionToken(String username, String password) throws RemoteException {
		if (LOG.isDebugEnabled()) LOG.debug("session creation attempt for '" + String.valueOf(username) + "'");
		
		WebResource r = client.resource(crowdServer.resolve("session"));
		
		SessionPost rBody = new SessionPost();
		rBody.username = username;
		rBody.password = password;
		try {
			SessionPostResponse response = r.accept(MediaType.APPLICATION_XML_TYPE).post(SessionPostResponse.class, rBody);
			
			if (LOG.isDebugEnabled()) LOG.debug(response.toString());
			
			LOG.info("session created for '" + String.valueOf(username) + "'");

			return response.token;
		} catch (UniformInterfaceException uie) {
			throw handleError(uie);
		}
	}
	
	
	/**
	 * Retrieves the groups that the user is a nested member of
	 * 
	 * @param username
	 * @return a set of roles (as strings)
	 * @throws RemoteException
	 */
	public Set<String> getNestedGroups(String username) throws RemoteException {
		if (LOG.isDebugEnabled()) LOG.debug("getNestedGroups(" + String.valueOf(username) + ")");
		
		WebResource r = client.resource(crowdServer.resolve("user/group/nested?username=" + username));
		
		try {
			GroupsResponse response = r.get(GroupsResponse.class);
			if (LOG.isDebugEnabled()) LOG.debug(response.toString());

			HashSet<String> result = new HashSet<String>();
			if (response.group != null) {
				for (GroupResponse group : response.group) {
					result.add(group.name);
				}
			}
			
			return result;
			
		} catch (UniformInterfaceException uie) {
			throw handleError(uie);
		}
	}
	
	
	/**
	 * Retrieves cookie configurations
	 * 
	 * @return a <code>ConfigCookieGetResponse</code>
	 * @throws RemoteException
	 */
	public ConfigCookieGetResponse getCookieConfig() throws RemoteException {
		LOG.debug("ConfigCookieGetResponse getCookieConfig()");
		WebResource r = client.resource(crowdServer.resolve("config/cookie"));
		
		try {
			return r.get(ConfigCookieGetResponse.class);
		} catch (UniformInterfaceException uie) {
			throw handleError(uie);
		}
	}

	
	/**
	 * Get the complete list of active user ids from Crowd
	 * @return
	 * @throws RemoteException
	 */
	// XXX: does not seem to be used by Nexus 2.1.2
	public Set<String> getAllUsernames() throws RemoteException {
		LOG.debug("getAllUsernames()");
		
		WebResource r = client.resource(crowdServer.resolve("search?entity-type=user&restriction=active%3Dtrue"));
		
		try {
			SearchUserGetResponse response = r.get(SearchUserGetResponse.class);
			if (LOG.isDebugEnabled()) LOG.debug(response.toString());

			HashSet<String> result = new HashSet<String>();
			if (response.user != null) {
				for (UserResponse user : response.user) {
					result.add(user.name);			
				}
			}
			
			return result;
			
		} catch (UniformInterfaceException uie) {
			throw handleError(uie);
		}
		
	}
	
	
	/**
	 * @param userid
	 * @return a <code>org.sonatype.security.usermanagement.User</code> from Crowd by a userid
	 * @throws RemoteException
	 */
	public User getUser(String userid) throws RemoteException {
		if (LOG.isDebugEnabled()) LOG.debug("getUser(" + String.valueOf(userid) + ")");
		
		WebResource r = client.resource(crowdServer.resolve("user?username=" + userid));
		
		UserResponse response = null;
		try {
			response = r.get(UserResponse.class);
			
			if (LOG.isDebugEnabled()) LOG.debug(response.toString());
			
		} catch (UniformInterfaceException uie) {
			throw handleError(uie);
		}
		
		return convertUser(response);
	}
	
	
	
	/**
	 * Returns user list based on multiple criteria
	 * @param userId
	 * @param email
	 * @param filterGroups
	 * @param maxResults
	 * @return
	 * @throws RemoteException
	 * @throws UnsupportedEncodingException
	 */
	// XXX: seems Nexus 2.1.2 only search by userId
	// so we make the search in crowd on the userid OR email
	// A Nexus user will be able to make a lookup based on the email
	public Set<User> searchUsers(String userId, String email, Set<String> filterGroups, int maxResults) throws RemoteException, UnsupportedEncodingException {
		if (LOG.isDebugEnabled()) LOG.debug("searchUsers(" + String.valueOf(userId)
				+ "," + String.valueOf(email) + "," + String.valueOf(filterGroups) + "," + String.valueOf(maxResults) + ")");
		
		// find by user criteria 1st; then groups;
		if (StringUtils.isNotEmpty(userId) || StringUtils.isNotEmpty(email)) {
			StringBuilder restUri = new StringBuilder("search?entity-type=user&max-results=").append(maxResults).append("&restriction=");
			
			StringBuilder searchQuery = new StringBuilder("active = true");
			if (StringUtils.isNotEmpty(userId)) {
				searchQuery.append(" AND (name = \"").append(userId.trim()).append("\"")
					.append(" OR email = \"" + userId.trim() + "\")");
			}
			if (StringUtils.isNotEmpty(email)) {
				searchQuery.append(" AND email = \"").append(email.trim()).append("\"");
			}
			
			// URL encoding
			restUri.append(URLEncoder.encode(searchQuery.toString(), "UTF-8"));
			
			WebResource r = client.resource(crowdServer.resolve(restUri.toString()));
			HashSet<User> result = new HashSet<User>();
			try {
				SearchUserGetResponse response = r.get(SearchUserGetResponse.class);

				if (response.user != null) {
					for (UserResponse user : response.user) {
						result.add(getUser(user.name));			
					}
				}
				
			} catch (UniformInterfaceException uie) {
				throw handleError(uie);
			}
			
			// filter groups
			if (filterGroups != null && !filterGroups.isEmpty()) {
				for (User user : result) {
					Set<String> userGroups = getNestedGroups(user.getUserId());
					boolean remove = true;
					for (String filterGoup : filterGroups) {
						if (userGroups.contains(filterGoup)) {
							remove = false;
							break;
						}
					}
					if (remove) {
						result.remove(user);
					}
				}
			}
			
			return result;
		}
		
		// find by groups only
		else {
			if (filterGroups != null && !filterGroups.isEmpty()) {
				Set<User> result = new HashSet<User>();
				
				for (String filterGroup : filterGroups) {
					WebResource r = client.resource(crowdServer.resolve("group/user/nested?groupname=" + filterGroup));
					
					try {
						SearchUserGetResponse response = r.get(SearchUserGetResponse.class);
						if (response.user != null) {
							for (UserResponse user : response.user) {
								// filter out inactive users
								if (user.active) {
									result.add(getUser(user.name));
								}
							}
						}
					} catch (UniformInterfaceException uie) {
						throw handleError(uie);
					}
					
					
					if (result.size() > maxResults) {
						break;
					}
				}
				
				return result;
			}
		}
		
		return Collections.emptySet();
	}
	
	
	/**
	 * @param groupName
	 * @return a <code>org.sonatype.security.authorization.Role</code> by its name
	 * @throws RemoteException
	 */
	// XXX: Nexus 2.1.2 does not seem to use this method
	public Role getGroup(String groupName) throws RemoteException {
		if (LOG.isDebugEnabled()) LOG.debug("getGroup(" + String.valueOf(groupName) + ")");
		
		WebResource r = client.resource(crowdServer.resolve("group?groupname=" + groupName));
		
		GroupResponse response = null;
		try {
			response = r.get(GroupResponse.class);
			if (LOG.isDebugEnabled()) LOG.debug(response.toString());
		} catch (UniformInterfaceException uie) {
			throw handleError(uie);
		}
		
		return convertGroup(response);
	}
	
	
	
	/**
	 * 
	 * @return all the crowd groups
	 * @throws RemoteException
	 */
	public Set<Role> getAllGroups() throws RemoteException {
		LOG.debug("getAllGroups()");
		
		WebResource r = client.resource(crowdServer.resolve("search?entity-type=group"));
		
		try {
			GroupsResponse response = r.get(GroupsResponse.class);
			if (LOG.isDebugEnabled()) LOG.debug(response.toString());

			HashSet<Role> result = new HashSet<Role>();
			if (response.group != null) {
				for (GroupResponse group : response.group) {
					result.add(new Role(group.name, group.name, "", "", true, null, null));			
				}
			}
			
			return result;		
		} catch (UniformInterfaceException uie) {
			throw handleError(uie);
		}
	}
	
	
	private User convertUser(UserResponse in) {
		User user = new DefaultUser();
		user.setUserId(in.name);
		user.setFirstName(in.firstName);
		user.setLastName(in.lastName);
		user.setEmailAddress(in.email);
		user.setStatus(in.active ? UserStatus.active : UserStatus.disabled);
		return user;
	}
	
	private Role convertGroup(GroupResponse in) {
        Role role = new Role();
        role.setRoleId(in.name);
        role.setName(in.name);
        role.setDescription(in.description);
        role.setReadOnly(true);
		return role;
	}
	
	private RemoteException handleError(UniformInterfaceException uie) {
		ClientResponse response = uie.getResponse();
		String errorXml = response.getEntity(String.class);
		if (errorXml != null) {
			Matcher matcher = ERROR_XML.matcher(errorXml);
			if (matcher.matches()) {
				return new RemoteException(matcher.group(1) + ": " + matcher.group(2));
			}
		}
		
		return new RemoteException("Error in a Crowd REST call", uie);
	}
}
