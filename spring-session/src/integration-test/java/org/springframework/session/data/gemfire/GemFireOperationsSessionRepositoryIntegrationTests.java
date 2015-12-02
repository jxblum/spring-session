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

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.context.annotation.Bean;
import org.springframework.data.gemfire.CacheFactoryBean;
import org.springframework.session.ExpiringSession;
import org.springframework.session.data.gemfire.config.annotation.web.http.EnableGemFireHttpSession;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;

import com.gemstone.gemfire.cache.DataPolicy;
import com.gemstone.gemfire.cache.ExpirationAction;
import com.gemstone.gemfire.cache.Region;
import com.gemstone.gemfire.cache.query.Query;
import com.gemstone.gemfire.cache.query.QueryService;
import com.gemstone.gemfire.cache.query.SelectResults;

/**
 * The GemFireOperationsSessionRepositoryIntegrationTests class is a test suite of test cases testing
 * the findByPrincipalName query method on the GemFireOpeationsSessionRepository class.
 *
 * @author John Blum
 * @see org.junit.Test
 * @see org.junit.runner.RunWith
 * @see org.springframework.session.data.gemfire.AbstractGemFireIntegrationTests
 * @see org.springframework.session.data.gemfire.GemFireOperationsSessionRepository
 * @see org.springframework.test.context.ContextConfiguration
 * @see org.springframework.test.context.junit4.SpringJUnit4ClassRunner
 * @see org.springframework.test.context.web.WebAppConfiguration
 * @since 1.1.0
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration
@WebAppConfiguration
@SuppressWarnings("unused")
public class GemFireOperationsSessionRepositoryIntegrationTests extends AbstractGemFireIntegrationTests {

	private static final int MAX_INACTIVE_INTERVAL_IN_SECONDS = 120;

	private static final String GEMFIRE_LOG_LEVEL = "warning";
	private static final String SPRING_SESSION_GEMFIRE_REGION_NAME = "TestPartitionedSessions";

	private ExpiringSession sessionOne;
	private ExpiringSession sessionTwo;
	private ExpiringSession sessionThree;
	private ExpiringSession sessionFour;
	private ExpiringSession sessionFive;

	@Before
	public void setup() {
		assertThat(gemfireCache, is(notNullValue()));
		assertThat(gemfireSessionRepository, is(notNullValue()));
		assertThat(gemfireSessionRepository.getMaxInactiveIntervalInSeconds(), is(equalTo(MAX_INACTIVE_INTERVAL_IN_SECONDS)));

		Region<Object, ExpiringSession> sessionRegion = gemfireCache.getRegion(SPRING_SESSION_GEMFIRE_REGION_NAME);

		assertRegion(sessionRegion, SPRING_SESSION_GEMFIRE_REGION_NAME, DataPolicy.PARTITION);
		assertEntryIdleTimeout(sessionRegion, ExpirationAction.INVALIDATE, MAX_INACTIVE_INTERVAL_IN_SECONDS);

		setupTestSessionData(sessionRegion);
	}

	private void setupTestSessionData(Region<Object, ExpiringSession> sessionRegion) {
		if (sessionRegion.isEmpty()) {
			sessionOne = save(touch(createSession("robWinch")));
			sessionTwo = save(touch(createSession("johnBlum")));
			sessionThree = save(touch(createSession("robWinch")));
			sessionFour = save(touch(createSession("johnBlum")));
			sessionFive = save(touch(createSession("robWinch")));
		}
	}

	protected Map<String, ExpiringSession> doFindByPrincipalName(String principalName) {
		return gemfireSessionRepository.findByPrincipalName(principalName);
	}

	@SuppressWarnings("unchecked")
	protected Map<String, ExpiringSession> doFindByPrincipalName(String regionName, String principalName) {
		try {
			Region<String, ExpiringSession> region = gemfireCache.getRegion(regionName);

			assertThat(region, is(notNullValue()));

			QueryService queryService = region.getRegionService().getQueryService();

			String queryString = String.format("SELECT s FROM %1$s s WHERE s.principalName = $1", region.getFullPath());

			Query query = queryService.newQuery(queryString);

			SelectResults<ExpiringSession> results = (SelectResults<ExpiringSession>) query.execute(
				new Object[] { principalName });

			Map<String, ExpiringSession> sessions = new HashMap<String, ExpiringSession>(results.size());

			for (ExpiringSession session : results.asList()) {
				sessions.put(session.getId(), session);
			}

			return sessions;
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Test
	public void findSessionsByPrincipalName() {
		assertThat(gemfireSessionRepository.getSession(sessionOne.getId()), is(equalTo(sessionOne)));
		assertThat(gemfireSessionRepository.getSession(sessionTwo.getId()), is(equalTo(sessionTwo)));
		assertThat(gemfireSessionRepository.getSession(sessionThree.getId()), is(equalTo(sessionThree)));
		assertThat(gemfireSessionRepository.getSession(sessionFour.getId()), is(equalTo(sessionFour)));
		assertThat(gemfireSessionRepository.getSession(sessionFive.getId()), is(equalTo(sessionFive)));

		Map<String, ExpiringSession> johnBlumSessions = doFindByPrincipalName("johnBlum");

		assertThat(johnBlumSessions, is(notNullValue()));
		assertThat(johnBlumSessions.size(), is(equalTo(2)));
		assertThat(johnBlumSessions.containsKey(sessionOne.getId()), is(false));
		assertThat(johnBlumSessions.containsKey(sessionThree.getId()), is(false));
		assertThat(johnBlumSessions.containsKey(sessionFive.getId()), is(false));
		assertThat(johnBlumSessions.get(sessionTwo.getId()), is(equalTo(sessionTwo)));
		assertThat(johnBlumSessions.get(sessionFour.getId()), is(equalTo(sessionFour)));

		Map<String, ExpiringSession> robWinchSessions = doFindByPrincipalName("robWinch");

		assertThat(robWinchSessions, is(notNullValue()));
		assertThat(robWinchSessions.size(), is(equalTo(3)));
		assertThat(robWinchSessions.containsKey(sessionTwo.getId()), is(false));
		assertThat(robWinchSessions.containsKey(sessionFour.getId()), is(false));
		assertThat(robWinchSessions.get(sessionOne.getId()), is(equalTo(sessionOne)));
		assertThat(robWinchSessions.get(sessionThree.getId()), is(equalTo(sessionThree)));
		assertThat(robWinchSessions.get(sessionFive.getId()), is(equalTo(sessionFive)));
	}

	@Test
	public void findsNoSessionsByNonExistingPrincipal() {
		Map<String, ExpiringSession> nonExistingPrincipalSessions = doFindByPrincipalName("nonExistingPrincipalName");

		assertThat(nonExistingPrincipalSessions, is(notNullValue()));
		assertThat(nonExistingPrincipalSessions.isEmpty(), is(true));
	}

	@EnableGemFireHttpSession(regionName = SPRING_SESSION_GEMFIRE_REGION_NAME,
		maxInactiveIntervalInSeconds = MAX_INACTIVE_INTERVAL_IN_SECONDS)
	static class SpringSessionGemFireConfiguration {

		@Bean
		Properties gemfireProperties() {
			Properties gemfireProperties = new Properties();

			gemfireProperties.setProperty("name", GemFireOperationsSessionRepositoryIntegrationTests.class.getName());
			gemfireProperties.setProperty("mcast-port", "0");
			gemfireProperties.setProperty("log-level", GEMFIRE_LOG_LEVEL);

			return gemfireProperties;
		}

		@Bean
		CacheFactoryBean gemfireCache() {
			CacheFactoryBean gemfireCache = new CacheFactoryBean();

			gemfireCache.setLazyInitialize(false);
			gemfireCache.setProperties(gemfireProperties());
			gemfireCache.setUseBeanFactoryLocator(false);

			return gemfireCache;
		}
	}

}
