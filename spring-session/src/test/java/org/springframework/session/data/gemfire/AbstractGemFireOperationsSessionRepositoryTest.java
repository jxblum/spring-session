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
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isA;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.apache.commons.logging.Log;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.runners.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.gemfire.GemfireOperations;
import org.springframework.data.gemfire.GemfireTemplate;
import org.springframework.session.ExpiringSession;
import org.springframework.session.data.gemfire.config.annotation.web.http.GemFireHttpSessionConfiguration;
import org.springframework.session.events.AbstractSessionEvent;
import org.springframework.session.events.SessionCreatedEvent;
import org.springframework.session.events.SessionDeletedEvent;
import org.springframework.session.events.SessionDestroyedEvent;
import org.springframework.session.events.SessionExpiredEvent;

import com.gemstone.gemfire.cache.AttributesMutator;
import com.gemstone.gemfire.cache.EntryEvent;
import com.gemstone.gemfire.cache.Region;

/**
 * The AbstractGemFireOperationsSessionRepositoryTest class is a test suite of test cases testing the contract
 * and functionality of the AbstractGemFireOperationsSessionRepository class.
 *
 * @author John Blum
 * @see org.junit.Test
 * @see org.junit.runner.RunWith
 * @see org.mockito.Mock
 * @see org.mockito.Mockito
 * @see org.mockito.runners.MockitoJUnitRunner
 * @see org.springframework.session.data.gemfire.AbstractGemFireOperationsSessionRepository
 * @since 1.0.0
 */
@RunWith(MockitoJUnitRunner.class)
public class AbstractGemFireOperationsSessionRepositoryTest {

	@Rule
	public ExpectedException expectedException = ExpectedException.none();

	@Mock
	private GemfireOperations mockGemfireOperations;

	@Mock
	private Log mockLog;

	private AbstractGemFireOperationsSessionRepository sessionRepository;

	@Before
	public void setup() {
		sessionRepository = new TestGemFireOperationsSessionRepository(mockGemfireOperations) {
			@Override Log newLogger() {
				return mockLog;
			}
		};
	}

	@Test
	public void constructGemFireOperationsSessionRepositoryWithNullTemplate() {
		expectedException.expect(IllegalArgumentException.class);
		expectedException.expectCause(is(nullValue(Throwable.class)));
		expectedException.expectMessage("GemfireOperations must not be null");

		new TestGemFireOperationsSessionRepository(null);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void gemfireOperationsSessionRepositoryIsProperlyConstructedAndInitialized() throws Exception {
		ApplicationEventPublisher mockApplicationEventPublisher = mock(ApplicationEventPublisher.class);
		AttributesMutator mockAttributesMutator = mock(AttributesMutator.class);
		Region mockRegion = mock(Region.class);

		when(mockRegion.getFullPath()).thenReturn("/Example");
		when(mockRegion.getAttributesMutator()).thenReturn(mockAttributesMutator);

		GemfireTemplate template = new GemfireTemplate(mockRegion);

		AbstractGemFireOperationsSessionRepository sessionRepository =
			new TestGemFireOperationsSessionRepository(template);

		ApplicationEventPublisher applicationEventPublisher = sessionRepository.getApplicationEventPublisher();

		assertThat(applicationEventPublisher, is(notNullValue()));
		assertThat(sessionRepository.getFullyQualifiedRegionName(), is(nullValue()));
		assertThat(sessionRepository.getMaxInactiveIntervalInSeconds(), is(equalTo(
			GemFireHttpSessionConfiguration.DEFAULT_MAX_INACTIVE_INTERVAL_IN_SECONDS)));
		assertThat(sessionRepository.getTemplate(), is(sameInstance((GemfireOperations) template)));

		sessionRepository.setApplicationEventPublisher(mockApplicationEventPublisher);
		sessionRepository.setMaxInactiveIntervalInSeconds(300);
		sessionRepository.afterPropertiesSet();

		assertThat(sessionRepository.getApplicationEventPublisher(), is(sameInstance(mockApplicationEventPublisher)));
		assertThat(sessionRepository.getFullyQualifiedRegionName(), is(equalTo("/Example")));
		assertThat(sessionRepository.getMaxInactiveIntervalInSeconds(), is(equalTo(300)));
		assertThat(sessionRepository.getTemplate(), is(sameInstance((GemfireOperations) template)));

		verify(mockRegion, times(1)).getAttributesMutator();
		verify(mockRegion, times(1)).getFullPath();
		verify(mockAttributesMutator, times(1)).addCacheListener(same(sessionRepository));
	}

	@Test
	public void maxInactiveIntervalInSecondsAllowsNegativeValuesAndExtremelyLargeValues() {
		assertThat(sessionRepository.getMaxInactiveIntervalInSeconds(), is(equalTo(
			GemFireHttpSessionConfiguration.DEFAULT_MAX_INACTIVE_INTERVAL_IN_SECONDS)));

		sessionRepository.setMaxInactiveIntervalInSeconds(-1);

		assertThat(sessionRepository.getMaxInactiveIntervalInSeconds(), is(equalTo(-1)));

		sessionRepository.setMaxInactiveIntervalInSeconds(Integer.MIN_VALUE);

		assertThat(sessionRepository.getMaxInactiveIntervalInSeconds(), is(equalTo(Integer.MIN_VALUE)));

		sessionRepository.setMaxInactiveIntervalInSeconds(3600);

		assertThat(sessionRepository.getMaxInactiveIntervalInSeconds(), is(equalTo(3600)));

		sessionRepository.setMaxInactiveIntervalInSeconds(Integer.MAX_VALUE);

		assertThat(sessionRepository.getMaxInactiveIntervalInSeconds(), is(equalTo(Integer.MAX_VALUE)));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void afterCreateWithSessionPublishesSessionCreatedEvent() {
		final String sessionId = "abc123";
		final ExpiringSession mockSession = mock(ExpiringSession.class);

		when(mockSession.getId()).thenReturn(sessionId);

		ApplicationEventPublisher mockApplicationEventPublisher = mock(ApplicationEventPublisher.class);

		doAnswer(new Answer<Void>() {
			@Override public Void answer(final InvocationOnMock invocation) throws Throwable {
				ApplicationEvent applicationEvent = invocation.getArgumentAt(0, ApplicationEvent.class);

				assertThat(applicationEvent, is(instanceOf(SessionCreatedEvent.class)));

				AbstractSessionEvent sessionEvent = (AbstractSessionEvent) applicationEvent;

				assertThat(sessionEvent.getSource(), is(equalTo((Object) sessionRepository)));
				assertThat((ExpiringSession) sessionEvent.getSession(), is(equalTo(mockSession)));
				assertThat(sessionEvent.getSessionId(), is(equalTo(sessionId)));

				return null;
			}
		}).when(mockApplicationEventPublisher).publishEvent(isA(ApplicationEvent.class));

		EntryEvent<Object, ExpiringSession> mockEntryEvent = mock(EntryEvent.class);

		when(mockEntryEvent.getKey()).thenReturn(sessionId);
		when(mockEntryEvent.getNewValue()).thenReturn(mockSession);
		when(mockEntryEvent.getOldValue()).thenReturn(null);

		sessionRepository.setApplicationEventPublisher(mockApplicationEventPublisher);
		sessionRepository.afterCreate(mockEntryEvent);

		assertThat(sessionRepository.getApplicationEventPublisher(), is(sameInstance(mockApplicationEventPublisher)));

		verify(mockEntryEvent, times(1)).getKey();
		verify(mockEntryEvent, times(1)).getNewValue();
		verify(mockEntryEvent, never()).getOldValue();
		verify(mockSession, times(1)).getId();
		verify(mockApplicationEventPublisher, times(1)).publishEvent(isA(SessionCreatedEvent.class));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void afterCreateWithSessionIdPublishesSessionCreatedEvent() {
		final String sessionId = "abc123";

		ApplicationEventPublisher mockApplicationEventPublisher = mock(ApplicationEventPublisher.class);

		doAnswer(new Answer<Void>() {
			@Override public Void answer(final InvocationOnMock invocation) throws Throwable {
				ApplicationEvent applicationEvent = invocation.getArgumentAt(0, ApplicationEvent.class);

				assertThat(applicationEvent, is(instanceOf(SessionCreatedEvent.class)));

				AbstractSessionEvent sessionEvent = (AbstractSessionEvent) applicationEvent;

				assertThat(sessionEvent.getSource(), is(equalTo((Object) sessionRepository)));
				assertThat(sessionEvent.getSession(), is(nullValue()));
				assertThat(sessionEvent.getSessionId(), is(equalTo(sessionId)));

				return null;
			}
		}).when(mockApplicationEventPublisher).publishEvent(isA(ApplicationEvent.class));

		EntryEvent<Object, ExpiringSession> mockEntryEvent = mock(EntryEvent.class);

		when(mockEntryEvent.getKey()).thenReturn(sessionId);
		when(mockEntryEvent.getNewValue()).thenReturn(null);
		when(mockEntryEvent.getOldValue()).thenReturn(null);

		sessionRepository.setApplicationEventPublisher(mockApplicationEventPublisher);
		sessionRepository.afterCreate(mockEntryEvent);

		assertThat(sessionRepository.getApplicationEventPublisher(), is(sameInstance(mockApplicationEventPublisher)));

		verify(mockEntryEvent, times(1)).getKey();
		verify(mockEntryEvent, times(1)).getNewValue();
		verify(mockEntryEvent, never()).getOldValue();
		verify(mockApplicationEventPublisher, times(1)).publishEvent(isA(SessionCreatedEvent.class));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void afterDestroyWithSessionPublishesSessionDestroyedEvent() {
		final String sessionId = "abc123";
		final ExpiringSession mockSession = mock(ExpiringSession.class);

		when(mockSession.getId()).thenReturn(sessionId);

		ApplicationEventPublisher mockApplicationEventPublisher = mock(ApplicationEventPublisher.class);

		doAnswer(new Answer<Void>() {
			@Override public Void answer(final InvocationOnMock invocation) throws Throwable {
				ApplicationEvent applicationEvent = invocation.getArgumentAt(0, ApplicationEvent.class);

				assertThat(applicationEvent, is(instanceOf(SessionDestroyedEvent.class)));

				AbstractSessionEvent sessionEvent = (AbstractSessionEvent) applicationEvent;

				assertThat(sessionEvent.getSource(), is(equalTo((Object) sessionRepository)));
				assertThat((ExpiringSession) sessionEvent.getSession(), is(equalTo(mockSession)));
				assertThat(sessionEvent.getSessionId(), is(equalTo(sessionId)));

				return null;
			}
		}).when(mockApplicationEventPublisher).publishEvent(isA(ApplicationEvent.class));

		EntryEvent<Object, ExpiringSession> mockEntryEvent = mock(EntryEvent.class);

		when(mockEntryEvent.getKey()).thenReturn(sessionId);
		when(mockEntryEvent.getNewValue()).thenReturn(null);
		when(mockEntryEvent.getOldValue()).thenReturn(mockSession);

		sessionRepository.setApplicationEventPublisher(mockApplicationEventPublisher);
		sessionRepository.afterDestroy(mockEntryEvent);

		assertThat(sessionRepository.getApplicationEventPublisher(), is(sameInstance(mockApplicationEventPublisher)));

		verify(mockEntryEvent, times(1)).getKey();
		verify(mockEntryEvent, never()).getNewValue();
		verify(mockEntryEvent, times(1)).getOldValue();
		verify(mockSession, times(1)).getId();
		verify(mockApplicationEventPublisher, times(1)).publishEvent(isA(SessionDestroyedEvent.class));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void afterDestroyWithSessionIdPublishesSessionDestroyedEvent() {
		final String sessionId = "abc123";

		ApplicationEventPublisher mockApplicationEventPublisher = mock(ApplicationEventPublisher.class);

		doAnswer(new Answer<Void>() {
			@Override public Void answer(final InvocationOnMock invocation) throws Throwable {
				ApplicationEvent applicationEvent = invocation.getArgumentAt(0, ApplicationEvent.class);

				assertThat(applicationEvent, is(instanceOf(SessionDestroyedEvent.class)));

				AbstractSessionEvent sessionEvent = (AbstractSessionEvent) applicationEvent;

				assertThat(sessionEvent.getSource(), is(equalTo((Object) sessionRepository)));
				assertThat(sessionEvent.getSession(), is(nullValue()));
				assertThat(sessionEvent.getSessionId(), is(equalTo(sessionId)));

				return null;
			}
		}).when(mockApplicationEventPublisher).publishEvent(isA(ApplicationEvent.class));

		EntryEvent<Object, ExpiringSession> mockEntryEvent = mock(EntryEvent.class);

		when(mockEntryEvent.getKey()).thenReturn(sessionId);
		when(mockEntryEvent.getNewValue()).thenReturn(null);
		when(mockEntryEvent.getOldValue()).thenReturn(null);

		sessionRepository.setApplicationEventPublisher(mockApplicationEventPublisher);
		sessionRepository.afterDestroy(mockEntryEvent);

		assertThat(sessionRepository.getApplicationEventPublisher(), is(sameInstance(mockApplicationEventPublisher)));

		verify(mockEntryEvent, times(1)).getKey();
		verify(mockEntryEvent, never()).getNewValue();
		verify(mockEntryEvent, times(1)).getOldValue();
		verify(mockApplicationEventPublisher, times(1)).publishEvent(isA(SessionDestroyedEvent.class));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void afterInvalidateWithSessionPublishesSessionExpiredEvent() {
		final String sessionId = "abc123";
		final ExpiringSession mockSession = mock(ExpiringSession.class);

		when(mockSession.getId()).thenReturn(sessionId);

		ApplicationEventPublisher mockApplicationEventPublisher = mock(ApplicationEventPublisher.class);

		doAnswer(new Answer<Void>() {
			@Override public Void answer(final InvocationOnMock invocation) throws Throwable {
				ApplicationEvent applicationEvent = invocation.getArgumentAt(0, ApplicationEvent.class);

				assertThat(applicationEvent, is(instanceOf(SessionExpiredEvent.class)));

				AbstractSessionEvent sessionEvent = (AbstractSessionEvent) applicationEvent;

				assertThat(sessionEvent.getSource(), is(equalTo((Object) sessionRepository)));
				assertThat((ExpiringSession) sessionEvent.getSession(), is(equalTo(mockSession)));
				assertThat(sessionEvent.getSessionId(), is(equalTo(sessionId)));

				return null;
			}
		}).when(mockApplicationEventPublisher).publishEvent(isA(ApplicationEvent.class));

		EntryEvent<Object, ExpiringSession> mockEntryEvent = mock(EntryEvent.class);

		when(mockEntryEvent.getKey()).thenReturn(sessionId);
		when(mockEntryEvent.getNewValue()).thenReturn(null);
		when(mockEntryEvent.getOldValue()).thenReturn(mockSession);

		sessionRepository.setApplicationEventPublisher(mockApplicationEventPublisher);
		sessionRepository.afterInvalidate(mockEntryEvent);

		assertThat(sessionRepository.getApplicationEventPublisher(), is(sameInstance(mockApplicationEventPublisher)));

		verify(mockEntryEvent, times(1)).getKey();
		verify(mockEntryEvent, never()).getNewValue();
		verify(mockEntryEvent, times(1)).getOldValue();
		verify(mockSession, times(1)).getId();
		verify(mockApplicationEventPublisher, times(1)).publishEvent(isA(SessionExpiredEvent.class));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void afterInvalidateWithSessionIdPublishesSessionExpiredEvent() {
		final String sessionId = "abc123";

		ApplicationEventPublisher mockApplicationEventPublisher = mock(ApplicationEventPublisher.class);

		doAnswer(new Answer<Void>() {
			@Override public Void answer(final InvocationOnMock invocation) throws Throwable {
				ApplicationEvent applicationEvent = invocation.getArgumentAt(0, ApplicationEvent.class);

				assertThat(applicationEvent, is(instanceOf(SessionExpiredEvent.class)));

				AbstractSessionEvent sessionEvent = (AbstractSessionEvent) applicationEvent;

				assertThat(sessionEvent.getSource(), is(equalTo((Object) sessionRepository)));
				assertThat(sessionEvent.getSession(), is(nullValue()));
				assertThat(sessionEvent.getSessionId(), is(equalTo(sessionId)));

				return null;
			}
		}).when(mockApplicationEventPublisher).publishEvent(isA(ApplicationEvent.class));

		EntryEvent<Object, ExpiringSession> mockEntryEvent = mock(EntryEvent.class);

		when(mockEntryEvent.getKey()).thenReturn(sessionId);
		when(mockEntryEvent.getNewValue()).thenReturn(null);
		when(mockEntryEvent.getOldValue()).thenReturn(null);

		sessionRepository.setApplicationEventPublisher(mockApplicationEventPublisher);
		sessionRepository.afterInvalidate(mockEntryEvent);

		assertThat(sessionRepository.getApplicationEventPublisher(), is(sameInstance(mockApplicationEventPublisher)));

		verify(mockEntryEvent, times(1)).getKey();
		verify(mockEntryEvent, never()).getNewValue();
		verify(mockEntryEvent, times(1)).getOldValue();
		verify(mockApplicationEventPublisher, times(1)).publishEvent(isA(SessionExpiredEvent.class));
	}

	@Test
	public void handleDeletedWithSessionPublishesSessionDeletedEvent() {
		final String sessionId = "abc123";
		final ExpiringSession mockSession = mock(ExpiringSession.class);

		when(mockSession.getId()).thenReturn(sessionId);

		ApplicationEventPublisher mockApplicationEventPublisher = mock(ApplicationEventPublisher.class);

		doAnswer(new Answer<Void>() {
			@Override public Void answer(final InvocationOnMock invocation) throws Throwable {
				ApplicationEvent applicationEvent = invocation.getArgumentAt(0, ApplicationEvent.class);

				assertThat(applicationEvent, is(instanceOf(SessionDeletedEvent.class)));

				AbstractSessionEvent sessionEvent = (AbstractSessionEvent) applicationEvent;

				assertThat(sessionEvent.getSource(), is(equalTo((Object) sessionRepository)));
				assertThat((ExpiringSession) sessionEvent.getSession(), is(equalTo(mockSession)));
				assertThat(sessionEvent.getSessionId(), is(equalTo(sessionId)));

				return null;
			}
		}).when(mockApplicationEventPublisher).publishEvent(isA(ApplicationEvent.class));

		sessionRepository.setApplicationEventPublisher(mockApplicationEventPublisher);
		sessionRepository.handleDeleted(sessionId, mockSession);

		assertThat(sessionRepository.getApplicationEventPublisher(), is(sameInstance(mockApplicationEventPublisher)));

		verify(mockSession, times(1)).getId();
		verify(mockApplicationEventPublisher, times(1)).publishEvent(isA(SessionDeletedEvent.class));
	}

	@Test
	public void handleDeletedWithSessionIdPublishesSessionDeletedEvent() {
		final String sessionId = "abc123";

		ApplicationEventPublisher mockApplicationEventPublisher = mock(ApplicationEventPublisher.class);

		doAnswer(new Answer<Void>() {
			@Override public Void answer(final InvocationOnMock invocation) throws Throwable {
				ApplicationEvent applicationEvent = invocation.getArgumentAt(0, ApplicationEvent.class);

				assertThat(applicationEvent, is(instanceOf(SessionDeletedEvent.class)));

				AbstractSessionEvent sessionEvent = (AbstractSessionEvent) applicationEvent;

				assertThat(sessionEvent.getSource(), is(equalTo((Object) sessionRepository)));
				assertThat(sessionEvent.getSession(), is(nullValue()));
				assertThat(sessionEvent.getSessionId(), is(equalTo(sessionId)));

				return null;
			}
		}).when(mockApplicationEventPublisher).publishEvent(isA(ApplicationEvent.class));

		sessionRepository.setApplicationEventPublisher(mockApplicationEventPublisher);
		sessionRepository.handleDeleted(sessionId, null);

		assertThat(sessionRepository.getApplicationEventPublisher(), is(sameInstance(mockApplicationEventPublisher)));

		verify(mockApplicationEventPublisher, times(1)).publishEvent(isA(SessionDeletedEvent.class));
	}

	@Test
	public void publishEventHandlesThrowable() {
		ApplicationEvent mockApplicationEvent = mock(ApplicationEvent.class);

		ApplicationEventPublisher mockApplicationEventPublisher = mock(ApplicationEventPublisher.class);

		doThrow(new IllegalStateException("test")).when(mockApplicationEventPublisher)
			.publishEvent(any(ApplicationEvent.class));

		sessionRepository.setApplicationEventPublisher(mockApplicationEventPublisher);
		sessionRepository.publishEvent(mockApplicationEvent);

		assertThat(sessionRepository.getApplicationEventPublisher(), is(sameInstance(mockApplicationEventPublisher)));

		verify(mockApplicationEventPublisher, times(1)).publishEvent(eq(mockApplicationEvent));
		verify(mockLog, times(1)).error(eq(String.format("error occurred publishing event (%1$s)", mockApplicationEvent)),
			isA(IllegalStateException.class));
	}

	protected static class TestGemFireOperationsSessionRepository extends AbstractGemFireOperationsSessionRepository {

		protected TestGemFireOperationsSessionRepository(GemfireOperations gemfireOperations) {
			super(gemfireOperations);
		}

		@Override
		public Map<String, ExpiringSession> findByPrincipalName(String principalName) {
			throw new UnsupportedOperationException("not implemented");
		}

		@Override
		public ExpiringSession createSession() {
			throw new UnsupportedOperationException("not implemented");
		}

		@Override
		public ExpiringSession getSession(String id) {
			throw new UnsupportedOperationException("not implemented");
		}

		@Override
		public void save(ExpiringSession session) {
			throw new UnsupportedOperationException("not implemented");
		}

		@Override
		public void delete(String id) {
			throw new UnsupportedOperationException("not implemented");
		}
	}

}
