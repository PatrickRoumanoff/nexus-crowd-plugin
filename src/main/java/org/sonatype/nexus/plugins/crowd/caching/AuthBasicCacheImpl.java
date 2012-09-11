/**
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
/**
 * 
 */
package org.sonatype.nexus.plugins.crowd.caching;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;

/**
 * Extension of Crowd CacheImpl object which supports caching of authentication
 * tokens by username/password.
 * 
 * @author Justin Edelson
 * @author Issa Gorissen
 * 
 */
public class AuthBasicCacheImpl implements AuthBasicCache {

    private static final String USERNAME_PASSWORD_CACHE = "com.atlassian.crowd.integration-auth-username";

    private final CacheManager ehCacheManager;

    public AuthBasicCacheImpl(long ttl) {
        this.ehCacheManager = CacheManager.getInstance();

        if (!ehCacheManager.cacheExists(USERNAME_PASSWORD_CACHE)) {
        	Cache newCache = new Cache(USERNAME_PASSWORD_CACHE, 10000, false, false, ttl, ttl);
        	
            ehCacheManager.addCache(newCache);
        }

    }

    public void addOrReplaceToken(String username, String password, String token) {
        getUsernamePasswordCache().put(new Element(createCacheKey(username, password), token));
    }

    public String getToken(String username, String password) {
        Element element = getUsernamePasswordCache().get(createCacheKey(username, password));
        if (element == null) {
            return null;
        }
        return (String) element.getValue();
    }

    private String createCacheKey(String username, String password) {
        return new StringBuilder(username).append('#').append(password).toString();
    }

    private Cache getUsernamePasswordCache() {
        return ehCacheManager.getCache(USERNAME_PASSWORD_CACHE);
    }

}
