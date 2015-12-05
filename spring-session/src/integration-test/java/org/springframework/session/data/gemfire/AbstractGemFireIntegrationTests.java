/*
 * Copyright 2002-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.session.data.gemfire;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.Before;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.session.ExpiringSession;
import org.springframework.session.data.gemfire.support.GemFireUtils;

import com.gemstone.gemfire.cache.Cache;
import com.gemstone.gemfire.cache.DataPolicy;
import com.gemstone.gemfire.cache.ExpirationAction;
import com.gemstone.gemfire.cache.ExpirationAttributes;
import com.gemstone.gemfire.cache.GemFireCache;
import com.gemstone.gemfire.cache.Region;

/**
 * AbstractGemFireIntegrationTests is an abstract base class encapsulating common operations for writing
 * Spring Session GemFire integration tests.
 *
 * @author John Blum
 * @see org.springframework.session.ExpiringSession
 * @see com.gemstone.gemfire.cache.Cache
 * @see com.gemstone.gemfire.cache.ExpirationAttributes
 * @see com.gemstone.gemfire.cache.GemFireCache
 * @see com.gemstone.gemfire.cache.Region
 * @since 1.1.0
 */
@SuppressWarnings("unused")
public class AbstractGemFireIntegrationTests {

	@Autowired
	protected Cache gemfireCache;

	@Autowired
	protected GemFireOperationsSessionRepository gemfireSessionRepository;

	@Before
	public void setup() {
		System.setProperty("gemfire.Query.VERBOSE", String.valueOf(enableQueryDebugging()));
	}

	protected void assertRegion(Region<?, ?> actualRegion, String expectedName, DataPolicy expectedDataPolicy) {
		assertThat(actualRegion, is(notNullValue()));
		assertThat(actualRegion.getName(), is(equalTo(expectedName)));
		assertThat(actualRegion.getFullPath(), is(equalTo(GemFireUtils.toRegionPath(expectedName))));
		assertThat(actualRegion.getAttributes(), is(notNullValue()));
		assertThat(actualRegion.getAttributes().getDataPolicy(), is(equalTo(expectedDataPolicy)));
	}

	protected void assertEntryIdleTimeout(Region<?, ?> region, ExpirationAction expectedAction, int expectedTimeout) {
		assertEntryIdleTimeout(region.getAttributes().getEntryIdleTimeout(), expectedAction, expectedTimeout);
	}

	protected void assertEntryIdleTimeout(ExpirationAttributes actualExpirationAttributes,
			ExpirationAction expectedAction, int expectedTimeout) {
		assertThat(actualExpirationAttributes, is(notNullValue()));
		assertThat(actualExpirationAttributes.getAction(), is(equalTo(expectedAction)));
		assertThat(actualExpirationAttributes.getTimeout(), is(equalTo(expectedTimeout)));
	}

	@SuppressWarnings("unchecked")
	protected <T extends ExpiringSession> T createSession() {
		T expiringSession = (T) gemfireSessionRepository.createSession();
		assertThat(expiringSession, is(notNullValue()));
		return expiringSession;
	}

	@SuppressWarnings("unchecked")
	protected <T extends ExpiringSession> T createSession(String principalName) {
		GemFireOperationsSessionRepository.GemFireSession session = createSession();
		session.setPrincipalName(principalName);
		return (T) session;
	}

	protected boolean enableQueryDebugging() {
		return false;
	}

	protected List<String> listRegions(GemFireCache gemfireCache) {
		Set<Region<?, ?>> regions = gemfireCache.rootRegions();

		List<String> regionList = new ArrayList<String>(regions.size());

		for (Region region : regions) {
			regionList.add(region.getFullPath());
		}

		return regionList;
	}

	@SuppressWarnings("unchecked")
	protected <T extends ExpiringSession> T get(String sessionId) {
		return (T) gemfireSessionRepository.getSession(sessionId);
	}

	protected <T extends ExpiringSession> T save(T session) {
		gemfireSessionRepository.save(session);
		return session;
	}

	protected <T extends ExpiringSession> T touch(T session) {
		session.setLastAccessedTime(System.currentTimeMillis());
		return session;
	}

}
