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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.data.gemfire.GemfireAccessor;
import org.springframework.data.gemfire.GemfireOperations;
import org.springframework.session.ExpiringSession;
import org.springframework.session.FindByPrincipalNameSessionRepository;
import org.springframework.session.data.gemfire.config.annotation.web.http.GemFireHttpSessionConfiguration;
import org.springframework.session.events.SessionCreatedEvent;
import org.springframework.session.events.SessionDeletedEvent;
import org.springframework.session.events.SessionDestroyedEvent;
import org.springframework.session.events.SessionExpiredEvent;
import org.springframework.util.Assert;

import com.gemstone.gemfire.cache.EntryEvent;
import com.gemstone.gemfire.cache.Region;
import com.gemstone.gemfire.cache.util.CacheListenerAdapter;

/**
 * AbstractGemFireOperationsSessionRepository is an abstract base class encapsulating functionality common
 * to all implementations that support SessionRepository operations backed by GemFire.
 *
 * @author John Blum
 * @see org.springframework.beans.factory.InitializingBean
 * @see org.springframework.context.ApplicationEventPublisherAware
 * @see org.springframework.data.gemfire.GemfireOperations
 * @see org.springframework.session.ExpiringSession
 * @see org.springframework.session.FindByPrincipalNameSessionRepository
 * @see org.springframework.session.data.gemfire.config.annotation.web.http.GemFireHttpSessionConfiguration
 * @see com.gemstone.gemfire.cache.Region
 * @see com.gemstone.gemfire.cache.util.CacheListenerAdapter
 * @since 1.1.0
 */
public abstract class AbstractGemFireOperationsSessionRepository extends CacheListenerAdapter<Object, ExpiringSession>
		implements InitializingBean, FindByPrincipalNameSessionRepository<ExpiringSession>,
			ApplicationEventPublisherAware {

	private int maxInactiveIntervalInSeconds = GemFireHttpSessionConfiguration.DEFAULT_MAX_INACTIVE_INTERVAL_IN_SECONDS;

	private ApplicationEventPublisher applicationEventPublisher = new ApplicationEventPublisher() {
		public void publishEvent(ApplicationEvent event) {
		}
	};

	private final GemfireOperations template;

	protected final Log logger = newLogger();

	private String fullyQualifiedRegionName;

	/**
	 * Constructs an instance of AbstractGemFireOperationsSessionRepository with a required GemfireOperations instance
	 * used to perform GemFire data access operations and interactions supporting the SessionRepository operations.
	 *
	 * @param template the GemfireOperations instance used to interact with GemFire.
	 * @see org.springframework.data.gemfire.GemfireOperations
	 */
	public AbstractGemFireOperationsSessionRepository(GemfireOperations template) {
		Assert.notNull(template, "GemfireOperations must not be null");
		this.template = template;
	}

	/**
	 * Used for testing purposes only to override the Log implementation with a mock.
	 *
	 * @return an instance of Log constructed from Apache commons-logging LogFactory.
	 * @see org.apache.commons.logging.LogFactory#getLog(Class)
	 */
	Log newLogger() {
		return LogFactory.getLog(getClass());
	}

	/**
	 * Sets the ApplicationEventPublisher used to publish Session events corresponding to GemFire cache events.
	 *
	 * @param applicationEventPublisher the Spring ApplicationEventPublisher used to publish Session-based events.
	 * @see org.springframework.context.ApplicationEventPublisher
	 */
	public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
		Assert.notNull(applicationEventPublisher, "ApplicationEventPublisher must not be null");
		this.applicationEventPublisher = applicationEventPublisher;
	}

	/**
	 * Gets the ApplicationEventPublisher used to publish Session events corresponding to GemFire cache events.
	 *
	 * @return the Spring ApplicationEventPublisher used to publish Session-based events.
	 * @see org.springframework.context.ApplicationEventPublisher
	 */
	protected ApplicationEventPublisher getApplicationEventPublisher() {
		return applicationEventPublisher;
	}

	/**
	 * Gets the fully-qualified name of the GemFire cache {@link Region} used to store and manage Session data.
	 *
	 * @return a String indicating the fully qualified name of the GemFire cache {@link Region} used to store
	 * and manage Session data.
	 */
	protected String getFullyQualifiedRegionName() {
		return fullyQualifiedRegionName;
	}

	/**
	 * Sets the maximum interval in seconds in which a Session can remain inactive before it is considered expired.
	 *
	 * @param maxInactiveIntervalInSeconds an integer value specifying the maximum interval in seconds that a Session
	 * can remain inactive before it is considered expired.
	 */
	public void setMaxInactiveIntervalInSeconds(int maxInactiveIntervalInSeconds) {
		this.maxInactiveIntervalInSeconds = maxInactiveIntervalInSeconds;
	}

	/**
	 * Gets the maximum interval in seconds in which a Session can remain inactive before it is considered expired.
	 *
	 * @return an integer value specifying the maximum interval in seconds that a Session can remain inactive
	 * before it is considered expired.
	 */
	public int getMaxInactiveIntervalInSeconds() {
		return maxInactiveIntervalInSeconds;
	}

	/**
	 * Gets a reference to the GemfireOperations (template) used to perform data access operations
	 * and other interactions on the GemFire cache {@link Region} backing this SessionRepository.
	 *
	 * @return a reference to the GemfireOperations used to interact with GemFire.
	 * @see org.springframework.data.gemfire.GemfireOperations
	 */
	public GemfireOperations getTemplate() {
		return template;
	}

	/**
	 * Callback method during Spring bean initialization that will capture the fully-qualified name
	 * of the GemFire cache {@link Region} used to manage Session state and register this SessionRepository
	 * as a GemFire {@link com.gemstone.gemfire.cache.CacheListener}.
	 *
	 * @throws Exception if an error occurs during the initialization process.
	 */
	public void afterPropertiesSet() throws Exception {
		GemfireOperations template = getTemplate();

		Assert.isInstanceOf(GemfireAccessor.class, template);

		Region<Object, ExpiringSession> region = ((GemfireAccessor) template).getRegion();

		fullyQualifiedRegionName = region.getFullPath();
		region.getAttributesMutator().addCacheListener(this);
	}

	/**
	 * Callback method triggered when an entry is created in the GemFire cache {@link Region}.
	 *
	 * @param event an EntryEvent containing the details of the cache operation.
	 * @see com.gemstone.gemfire.cache.EntryEvent
	 * @see #handleCreated(String, ExpiringSession)
	 */
	@Override
	public void afterCreate(EntryEvent<Object, ExpiringSession> event) {
		handleCreated(event.getKey().toString(), event.getNewValue());
	}

	/**
	 * Callback method triggered when an entry is destroyed in the GemFire cache {@link Region}.
	 *
	 * @param event an EntryEvent containing the details of the cache operation.
	 * @see com.gemstone.gemfire.cache.EntryEvent
	 * @see #handleDestroyed(String, ExpiringSession)
	 */
	@Override
	public void afterDestroy(EntryEvent<Object, ExpiringSession> event) {
		handleDestroyed(event.getKey().toString(), event.getOldValue());
	}

	/**
	 * Callback method triggered when an entry is invalidated in the GemFire cache {@link Region}.
	 *
	 * @param event an EntryEvent containing the details of the cache operation.
	 * @see com.gemstone.gemfire.cache.EntryEvent
	 * @see #handleExpired(String, ExpiringSession)
	 */
	@Override
	public void afterInvalidate(EntryEvent<Object, ExpiringSession> event) {
		handleExpired(event.getKey().toString(), event.getOldValue());
	}

	/**
	 * Causes Session created events to be published to the Spring application context.
	 *
	 * @param sessionId a String indicating the ID of the Session.
	 * @param session a reference to the Session triggering the event.
	 * @see org.springframework.session.events.SessionCreatedEvent
	 * @see org.springframework.session.ExpiringSession
	 * @see #publishEvent(ApplicationEvent)
	 */
	protected void handleCreated(String sessionId, ExpiringSession session) {
		publishEvent(session != null ? new SessionCreatedEvent(this, session)
			: new SessionCreatedEvent(this, sessionId));
	}

	/**
	 * Causes Session deleted events to be published to the Spring application context.
	 *
	 * @param sessionId a String indicating the ID of the Session.
	 * @param session a reference to the Session triggering the event.
	 * @see org.springframework.session.events.SessionDeletedEvent
	 * @see org.springframework.session.ExpiringSession
	 * @see #publishEvent(ApplicationEvent)
	 */
	protected void handleDeleted(String sessionId, ExpiringSession session) {
		publishEvent(session != null ? new SessionDeletedEvent(this, session)
			: new SessionDeletedEvent(this, sessionId));
	}

	/**
	 * Causes Session destroyed events to be published to the Spring application context.
	 *
	 * @param sessionId a String indicating the ID of the Session.
	 * @param session a reference to the Session triggering the event.
	 * @see org.springframework.session.events.SessionDestroyedEvent
	 * @see org.springframework.session.ExpiringSession
	 * @see #publishEvent(ApplicationEvent)
	 */
	protected void handleDestroyed(String sessionId, ExpiringSession session) {
		publishEvent(session != null ? new SessionDestroyedEvent(this, session)
			: new SessionDestroyedEvent(this, sessionId));
	}

	/**
	 * Causes Session expired events to be published to the Spring application context.
	 *
	 * @param sessionId a String indicating the ID of the Session.
	 * @param session a reference to the Session triggering the event.
	 * @see org.springframework.session.events.SessionExpiredEvent
	 * @see org.springframework.session.ExpiringSession
	 * @see #publishEvent(ApplicationEvent)
	 */
	protected void handleExpired(String sessionId, ExpiringSession session) {
		publishEvent(session != null ? new SessionExpiredEvent(this, session)
			: new SessionExpiredEvent(this, sessionId));
	}

	/**
	 * Publishes the specified ApplicationEvent to the Spring application context.
	 *
	 * @param event the ApplicationEvent to publish.
	 * @see org.springframework.context.ApplicationEventPublisher#publishEvent(ApplicationEvent)
	 * @see org.springframework.context.ApplicationEvent
	 */
	protected void publishEvent(ApplicationEvent event) {
		try {
			getApplicationEventPublisher().publishEvent(event);
		}
		catch (Throwable t) {
			logger.error(String.format("error occurred publishing event (%1$s)", event), t);
		}
	}

}
