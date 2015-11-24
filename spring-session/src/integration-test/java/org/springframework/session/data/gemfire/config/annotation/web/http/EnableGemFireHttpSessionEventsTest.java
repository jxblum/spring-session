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

package org.springframework.session.data.gemfire.config.annotation.web.http;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.junit.Assert.assertThat;

import java.util.Properties;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.data.gemfire.CacheFactoryBean;
import org.springframework.session.ExpiringSession;
import org.springframework.session.Session;
import org.springframework.session.data.gemfire.GemFireOperationsSessionRepository;
import org.springframework.session.events.AbstractSessionEvent;
import org.springframework.session.events.SessionCreatedEvent;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.gemstone.gemfire.cache.Cache;
import com.gemstone.gemfire.cache.DataPolicy;
import com.gemstone.gemfire.cache.ExpirationAction;
import com.gemstone.gemfire.cache.ExpirationAttributes;
import com.gemstone.gemfire.cache.Region;

/**
 * The EnableGemFireHttpSessionEventsTest class...
 *
 * @author John Blum
 * @see org.junit.Test
 * @see org.springframework.test.context.ContextConfiguration
 * @see org.springframework.test.context.junit4.SpringJUnit4ClassRunner
 * @since 1.0.0
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration
@SuppressWarnings("unused")
@Ignore
public class EnableGemFireHttpSessionEventsTest {

	private static final int MAX_INACTIVE_INTERVAL_IN_SECONDS = 1;

	private static final String SPRING_SESSION_GEMFIRE_REGION_NAME = "TestSessionRegion";

	@Autowired
	private Cache gemfireCache;

	@Autowired
	private GemFireOperationsSessionRepository gemfireSessionRepository;

	@Autowired
	private SessionEventListener sessionEventListener;

	@Before
	public void setup() {
		assertThat(gemfireCache, is(notNullValue()));
		assertThat(gemfireSessionRepository, is(notNullValue()));
		assertThat(sessionEventListener, is(notNullValue()));

		Region<Object, ExpiringSession> sessionRegion = gemfireCache.getRegion(SPRING_SESSION_GEMFIRE_REGION_NAME);

		assertThat(sessionRegion, is(notNullValue()));
		assertThat(sessionRegion.getName(), is(equalTo(SPRING_SESSION_GEMFIRE_REGION_NAME)));
		assertThat(sessionRegion.getFullPath(), is(equalTo(toRegionPath(SPRING_SESSION_GEMFIRE_REGION_NAME))));
		assertThat(sessionRegion.getAttributes(), is(notNullValue()));
		assertThat(sessionRegion.getAttributes().getDataPolicy(), is(equalTo(DataPolicy.PARTITION)));

		ExpirationAttributes sessionRegionExpirationPolicy = sessionRegion.getAttributes().getEntryIdleTimeout();

		assertThat(sessionRegionExpirationPolicy, is(notNullValue()));
		assertThat(sessionRegionExpirationPolicy.getAction(), is(equalTo(ExpirationAction.INVALIDATE)));
		assertThat(sessionRegionExpirationPolicy.getTimeout(), is(equalTo(MAX_INACTIVE_INTERVAL_IN_SECONDS)));
	}

	protected String toRegionPath(String regionName) {
		return String.format("%1$s%2$s", Region.SEPARATOR, regionName);
	}

	protected <T extends ExpiringSession> T touch(T session) {
		session.setLastAccessedTime(System.currentTimeMillis());
		return session;
	}

	@Test
	public void sessionCreatedEvent() {
		ExpiringSession session = touch(gemfireSessionRepository.createSession());

		assertThat(session.getLastAccessedTime(), is(lessThanOrEqualTo(System.currentTimeMillis())));
		assertThat(session.getMaxInactiveIntervalInSeconds(), is(equalTo(MAX_INACTIVE_INTERVAL_IN_SECONDS)));

		gemfireSessionRepository.save(session);

		AbstractSessionEvent sessionEvent = sessionEventListener.getSessionEvent();

		assertThat(sessionEvent, is(instanceOf(SessionCreatedEvent.class)));
		assertThat(sessionEvent.getSession(), is((Session) equalTo(session)));
	}

	@EnableGemFireHttpSession(regionName = SPRING_SESSION_GEMFIRE_REGION_NAME,
		maxInactiveIntervalInSeconds = MAX_INACTIVE_INTERVAL_IN_SECONDS)
	static class SpringSessionGemFireConfiguration {

		@Bean
		Properties gemfireProperties() {
			Properties gemfireProperties = new Properties();

			gemfireProperties.setProperty("name", EnableGemFireHttpSessionEventsTest.class.getName());
			gemfireProperties.setProperty("mcast-port", "0");
			gemfireProperties.setProperty("log-level", "warning");

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

		@Bean
		SessionEventListener sessionEventListener() {
			return new SessionEventListener();
		}
	}

	static class SessionEventListener implements ApplicationListener<AbstractSessionEvent> {

		private volatile AbstractSessionEvent sessionEvent;

		@SuppressWarnings("unchecked")
		public <T extends AbstractSessionEvent> T getSessionEvent() {
			return (T) sessionEvent;
		}

		@Override
		public void onApplicationEvent(final AbstractSessionEvent event) {
			sessionEvent = event;
		}
	}

}
