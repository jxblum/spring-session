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
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.number.OrderingComparison.lessThanOrEqualTo;
import static org.junit.Assert.assertThat;

import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.data.gemfire.CacheFactoryBean;
import org.springframework.session.ExpiringSession;
import org.springframework.session.Session;
import org.springframework.session.data.gemfire.GemFireOperationsSessionRepository;
import org.springframework.session.data.gemfire.support.GemFireUtils;
import org.springframework.session.events.AbstractSessionEvent;
import org.springframework.session.events.SessionCreatedEvent;
import org.springframework.session.events.SessionDeletedEvent;
import org.springframework.session.events.SessionExpiredEvent;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;

import com.gemstone.gemfire.cache.Cache;
import com.gemstone.gemfire.cache.DataPolicy;
import com.gemstone.gemfire.cache.ExpirationAction;
import com.gemstone.gemfire.cache.ExpirationAttributes;
import com.gemstone.gemfire.cache.GemFireCache;
import com.gemstone.gemfire.cache.Region;

/**
 * The EnableGemFireHttpSessionEventsTest class...
 *
 * @author John Blum
 * @see org.junit.Test
 * @see org.junit.runner.RunWith
 * @see org.springframework.test.context.ContextConfiguration
 * @see org.springframework.test.context.junit4.SpringJUnit4ClassRunner
 * @see org.springframework.test.context.web.WebAppConfiguration
 * @since 1.0.0
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration
@WebAppConfiguration
@SuppressWarnings("unused")
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
		assertThat(gemfireSessionRepository.getMaxInactiveIntervalInSeconds(), is(equalTo(MAX_INACTIVE_INTERVAL_IN_SECONDS)));
		assertThat(sessionEventListener, is(notNullValue()));

		// listRegions(gemfireCache);

		Region<Object, ExpiringSession> sessionRegion = gemfireCache.getRegion(SPRING_SESSION_GEMFIRE_REGION_NAME);

		assertThat(sessionRegion, is(notNullValue()));
		assertThat(sessionRegion.getName(), is(equalTo(SPRING_SESSION_GEMFIRE_REGION_NAME)));
		assertThat(sessionRegion.getFullPath(), is(equalTo(GemFireUtils.toRegionPath(SPRING_SESSION_GEMFIRE_REGION_NAME))));
		assertThat(sessionRegion.getAttributes(), is(notNullValue()));
		assertThat(sessionRegion.getAttributes().getDataPolicy(), is(equalTo(DataPolicy.PARTITION)));

		ExpirationAttributes sessionRegionExpirationPolicy = sessionRegion.getAttributes().getEntryIdleTimeout();

		assertThat(sessionRegionExpirationPolicy, is(notNullValue()));
		assertThat(sessionRegionExpirationPolicy.getAction(), is(equalTo(ExpirationAction.INVALIDATE)));
		assertThat(sessionRegionExpirationPolicy.getTimeout(), is(equalTo(MAX_INACTIVE_INTERVAL_IN_SECONDS)));
	}

	@After
	public void tearDown() {
		sessionEventListener.clear();
	}

	protected void listRegions(GemFireCache gemfireCache) {
		for (Region region : gemfireCache.rootRegions()) {
			System.out.printf("Region name (%1$s) and path (%2$s)%n", region.getName(), region.getFullPath());
		}
	}

	protected <T extends ExpiringSession> T save(T session) {
		gemfireSessionRepository.save(session);
		return session;
	}

	protected <T extends ExpiringSession> T touch(T session) {
		session.setLastAccessedTime(System.currentTimeMillis());
		return session;
	}

	@Test
	public void sessionCreatedEvent() {
		ExpiringSession expectedSession = touch(gemfireSessionRepository.createSession());

		assertThat(expectedSession.getId(), is(notNullValue()));
		assertThat(expectedSession.getCreationTime(), is(lessThanOrEqualTo(System.currentTimeMillis())));
		assertThat(expectedSession.getLastAccessedTime(), is(lessThanOrEqualTo(System.currentTimeMillis())));
		assertThat(expectedSession.getMaxInactiveIntervalInSeconds(), is(equalTo(MAX_INACTIVE_INTERVAL_IN_SECONDS)));

		gemfireSessionRepository.save(expectedSession);

		AbstractSessionEvent sessionEvent = sessionEventListener.getSessionEvent();

		assertThat(sessionEvent, is(instanceOf(SessionCreatedEvent.class)));
		assertThat(sessionEvent.getSession(), is(equalTo((Session) expectedSession)));
	}

	@Test
	public void getExistingNonExpiredSession() {
		ExpiringSession expectedSession = save(touch(gemfireSessionRepository.createSession()));

		assertThat(expectedSession, is(notNullValue()));
		assertThat(expectedSession.isExpired(), is(false));

		sessionEventListener.clear();

		ExpiringSession savedSession = gemfireSessionRepository.getSession(expectedSession.getId());

		assertThat(savedSession, is(notNullValue()));
		assertThat(savedSession, is(not(sameInstance(expectedSession))));
		assertThat(savedSession, is(equalTo(expectedSession)));
		assertThat(savedSession.isExpired(), is(false));
		assertThat(sessionEventListener.getSessionEvent(), is(nullValue()));
	}

	@Test
	public void getExistingExpiredSession() {
		ExpiringSession expectedSession = save(touch(gemfireSessionRepository.createSession()));

		assertThat(expectedSession, is(notNullValue()));
		assertThat(expectedSession.getMaxInactiveIntervalInSeconds(), is(equalTo(
			gemfireSessionRepository.getMaxInactiveIntervalInSeconds())));

		ExpiringSession savedSession = gemfireSessionRepository.getSession(expectedSession.getId());

		assertThat(savedSession, is(notNullValue()));
		assertThat(savedSession, is(not(sameInstance(expectedSession))));
		assertThat(savedSession, is(equalTo(expectedSession)));
		assertThat(savedSession.isExpired(), is(false));

		sessionEventListener.clear();

		long duration = TimeUnit.SECONDS.toMillis(gemfireSessionRepository.getMaxInactiveIntervalInSeconds() + 1);

		AbstractSessionEvent sessionEvent = sessionEventListener.waitForSessionEvent(duration);

		assertThat(sessionEvent, is(instanceOf(SessionExpiredEvent.class)));
		assertThat((ExpiringSession) sessionEvent.getSession(), is(equalTo(savedSession)));
		assertThat(((ExpiringSession) sessionEvent.getSession()).isExpired(), is(true));
	}

	@Test
	public void getNonExistingSession() {
		assertThat(gemfireSessionRepository.getSession(UUID.randomUUID().toString()), is(nullValue()));
	}

	@Test
	public void deleteExistingNonExpiredSession() {
		ExpiringSession expectedSession = save(touch(gemfireSessionRepository.createSession()));

		assertThat(expectedSession, is(notNullValue()));

		ExpiringSession savedSession = gemfireSessionRepository.getSession(expectedSession.getId());

		assertThat(savedSession, is(notNullValue()));
		assertThat(savedSession, is(not(sameInstance(expectedSession))));
		assertThat(savedSession, is(equalTo(expectedSession)));
		assertThat(savedSession.isExpired(), is(false));

		gemfireSessionRepository.delete(savedSession.getId());

		AbstractSessionEvent sessionEvent = sessionEventListener.getSessionEvent();

		assertThat(sessionEvent, is(instanceOf(SessionDeletedEvent.class)));
		assertThat((ExpiringSession) sessionEvent.getSession(), is(equalTo(savedSession)));

		ExpiringSession deletedSession = gemfireSessionRepository.getSession(savedSession.getId());

		assertThat(deletedSession, is(nullValue()));
	}

	@Test
	public void deleteExistingExpiredSession() {
		ExpiringSession expectedSession = save(touch(gemfireSessionRepository.createSession()));

		assertThat(expectedSession, is(notNullValue()));

		ExpiringSession savedSession = gemfireSessionRepository.getSession(expectedSession.getId());

		assertThat(savedSession, is(notNullValue()));
		assertThat(savedSession, is(not(sameInstance(expectedSession))));
		assertThat(savedSession, is(equalTo(expectedSession)));
		assertThat(savedSession.isExpired(), is(false));

		sessionEventListener.clear();

		long duration = TimeUnit.SECONDS.toMillis(gemfireSessionRepository.getMaxInactiveIntervalInSeconds() + 1);

		AbstractSessionEvent sessionEvent = sessionEventListener.waitForSessionEvent(duration);

		assertThat(sessionEvent, is(instanceOf(SessionExpiredEvent.class)));

		ExpiringSession expiredSession = sessionEvent.getSession();

		assertThat(expiredSession, is(equalTo(savedSession)));
		assertThat(expiredSession.isExpired(), is(true));

		gemfireSessionRepository.delete(expiredSession.getId());

		sessionEvent = sessionEventListener.getSessionEvent();

		assertThat(sessionEvent, is(instanceOf(SessionDeletedEvent.class)));
		assertThat(sessionEvent.getSession(), is(nullValue()));
		assertThat(sessionEvent.getSessionId(), is(equalTo(expiredSession.getId())));
	}

	@Test
	public void deleteNonExistingSession() {
		String sessionId = UUID.randomUUID().toString();

		gemfireSessionRepository.delete(sessionId);

		AbstractSessionEvent sessionEvent = sessionEventListener.getSessionEvent();

		assertThat(sessionEvent, is(instanceOf(SessionDeletedEvent.class)));
		assertThat(sessionEvent.getSession(), is(nullValue()));
		assertThat(sessionEvent.getSessionId(), is(equalTo(sessionId)));
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

		private final Object lock = new Object();

		public void clear() {
			sessionEvent = null;
		}

		@SuppressWarnings("unchecked")
		public <T extends AbstractSessionEvent> T getSessionEvent() {
			return (T) sessionEvent;
		}

		@Override
		public void onApplicationEvent(AbstractSessionEvent event) {
			sessionEvent = event;
		}

		protected <T extends AbstractSessionEvent> T waitForSessionEvent(final long duration) {
			final long timeout = (System.currentTimeMillis() + duration);

			while (getSessionEvent() == null && System.currentTimeMillis() < timeout) {
				try {
					synchronized (lock) {
						TimeUnit.MILLISECONDS.timedWait(lock, duration);
					}
				}
				catch (InterruptedException ignore) {
				}
			}

			return getSessionEvent();
		}

	}

}
