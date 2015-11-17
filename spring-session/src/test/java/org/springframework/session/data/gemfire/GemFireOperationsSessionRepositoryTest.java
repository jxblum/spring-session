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
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isA;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.session.data.gemfire.GemFireOperationsSessionRepository.GemFireSession;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.runners.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.gemfire.GemfireAccessor;
import org.springframework.data.gemfire.GemfireOperations;
import org.springframework.session.ExpiringSession;
import org.springframework.session.Session;
import org.springframework.session.events.AbstractSessionEvent;
import org.springframework.session.events.SessionDeletedEvent;

import com.gemstone.gemfire.cache.AttributesMutator;
import com.gemstone.gemfire.cache.Region;
import com.gemstone.gemfire.cache.query.SelectResults;
import com.gemstone.gemfire.pdx.PdxReader;
import com.gemstone.gemfire.pdx.PdxWriter;

/**
 * The GemFireOperationsSessionRepositoryTest class is a test suite of test cases testing the contract and functionality
 * of the GemFireOperationsSessionRepository class.
 *
 * @author John Blum
 * @see org.junit.Test
 * @see org.junit.runner.RunWith
 * @see org.mockito.Mock
 * @see org.mockito.Mockito
 * @see org.mockito.runners.MockitoJUnitRunner
 * @see org.springframework.session.data.gemfire.GemFireOperationsSessionRepository
 * @since 1.0.0
 */
@RunWith(MockitoJUnitRunner.class)
public class GemFireOperationsSessionRepositoryTest {

	protected static final int MAX_INACTIVE_INTERVAL_IN_SECONDS = 600;

	@Mock
	private ApplicationEventPublisher mockApplicationEventPublisher;

	@Mock
	private AttributesMutator<Object, ExpiringSession> mockAttributesMutator;

	@Mock
	private Region<Object, ExpiringSession> mockRegion;

	@Mock
	private GemfireOperationsAccessor mockTemplate;

	private GemFireOperationsSessionRepository sessionRepository;

	@Before
	public void setup() throws Exception {
		when(mockRegion.getAttributesMutator()).thenReturn(mockAttributesMutator);
		when(mockRegion.getFullPath()).thenReturn("/Example");
		when(mockTemplate.<Object, ExpiringSession>getRegion()).thenReturn(mockRegion);

		sessionRepository = new GemFireOperationsSessionRepository(mockTemplate);
		sessionRepository.setApplicationEventPublisher(mockApplicationEventPublisher);
		sessionRepository.setMaxInactiveIntervalInSeconds(MAX_INACTIVE_INTERVAL_IN_SECONDS);
		sessionRepository.afterPropertiesSet();

		assertThat(sessionRepository.getApplicationEventPublisher(), is(sameInstance(mockApplicationEventPublisher)));
		assertThat(sessionRepository.getFullyQualifiedRegionName(), is(equalTo("/Example")));
		assertThat(sessionRepository.getMaxInactiveIntervalInSeconds(), is(equalTo(MAX_INACTIVE_INTERVAL_IN_SECONDS)));
	}

	@After
	public void tearDown() {
		verify(mockAttributesMutator, times(1)).addCacheListener(same(sessionRepository));
		verify(mockRegion, times(1)).getFullPath();
		verify(mockTemplate, times(1)).getRegion();
	}

	protected <E> Set<E> asSet(E... elements) {
		Set<E> set = new HashSet<E>(elements.length);
		Collections.addAll(set, elements);
		return set;
	}

	@Test
	@SuppressWarnings("unchecked")
	public void findByPrincipalNameFindsMatchingSessions() throws Exception {
		ExpiringSession mockSessionOne = mock(ExpiringSession.class, "MockSessionOne");
		ExpiringSession mockSessionTwo = mock(ExpiringSession.class, "MockSessionTwo");
		ExpiringSession mockSessionThree = mock(ExpiringSession.class, "MockSessionThree");

		when(mockSessionOne.getId()).thenReturn("1");
		when(mockSessionTwo.getId()).thenReturn("2");
		when(mockSessionThree.getId()).thenReturn("3");

		SelectResults mockSelectResults = mock(SelectResults.class);

		when(mockSelectResults.asList()).thenReturn(Arrays.asList(mockSessionOne, mockSessionTwo, mockSessionThree));

		String principalName = "jblum";

		String expectedOql = String.format(GemFireOperationsSessionRepository.FIND_SESSIONS_BY_PRINCIPAL_NAME_QUERY,
			sessionRepository.getFullyQualifiedRegionName());

		when(mockTemplate.find(eq(expectedOql), eq(principalName))).thenReturn(mockSelectResults);

		Map<String, ExpiringSession> sessions = sessionRepository.findByPrincipalName(principalName);

		assertThat(sessions, is(notNullValue()));
		assertThat(sessions.size(), is(equalTo(3)));
		assertThat(sessions.get("1"), is(equalTo(mockSessionOne)));
		assertThat(sessions.get("2"), is(equalTo(mockSessionTwo)));
		assertThat(sessions.get("3"), is(equalTo(mockSessionThree)));

		verify(mockTemplate, times(1)).find(eq(expectedOql), eq(principalName));
		verify(mockSelectResults, times(1)).asList();
		verify(mockSessionOne, times(1)).getId();
		verify(mockSessionTwo, times(1)).getId();
		verify(mockSessionThree, times(1)).getId();
	}

	@Test
	@SuppressWarnings("unchecked")
	public void findByPrincipalNameReturnsNoMatchingSessions() {
		SelectResults mockSelectResults = mock(SelectResults.class);

		when(mockSelectResults.asList()).thenReturn(Collections.emptyList());

		String principalName = "jblum";

		String expectedOql = String.format(GemFireOperationsSessionRepository.FIND_SESSIONS_BY_PRINCIPAL_NAME_QUERY,
			sessionRepository.getFullyQualifiedRegionName());

		when(mockTemplate.find(eq(expectedOql), eq(principalName))).thenReturn(mockSelectResults);

		Map<String, ExpiringSession> sessions = sessionRepository.findByPrincipalName(principalName);

		assertThat(sessions, is(notNullValue()));
		assertThat(sessions.isEmpty(), is(true));

		verify(mockTemplate, times(1)).find(eq(expectedOql), eq(principalName));
		verify(mockSelectResults, times(1)).asList();
	}

	@Test
	public void createProperlyInitializedSession() {
		final long beforeCreationTime = System.currentTimeMillis();

		ExpiringSession session = sessionRepository.createSession();

		assertThat(session, is(instanceOf(GemFireSession.class)));
		assertThat(session.getId(), is(notNullValue()));
		assertThat(session.getAttributeNames().isEmpty(), is(true));
		assertThat(session.getCreationTime(), is(greaterThanOrEqualTo(beforeCreationTime)));
		assertThat(session.getMaxInactiveIntervalInSeconds(), is(equalTo(MAX_INACTIVE_INTERVAL_IN_SECONDS)));
		assertThat(session.getLastAccessedTime(), is(equalTo(0l)));
	}

	@Test
	public void getSessionDeletesMatchingExpiredSessionById() {
		final String expectedSessionId = "1";

		final ExpiringSession mockSession = mock(ExpiringSession.class);

		when(mockSession.isExpired()).thenReturn(true);
		when(mockSession.getId()).thenReturn(expectedSessionId);
		when(mockTemplate.get(eq(expectedSessionId))).thenReturn(mockSession);
		when(mockTemplate.remove(eq(expectedSessionId))).thenReturn(mockSession);

		doAnswer(new Answer<Void>() {
			@Override public Void answer(final InvocationOnMock invocation) throws Throwable {
				ApplicationEvent applicationEvent = invocation.getArgumentAt(0, ApplicationEvent.class);

				assertThat(applicationEvent, is(instanceOf(SessionDeletedEvent.class)));

				AbstractSessionEvent sessionEvent = (AbstractSessionEvent) applicationEvent;

				assertThat(sessionEvent.getSource(), is(sameInstance((Object) sessionRepository)));
				assertThat((ExpiringSession) sessionEvent.getSession(), is(same(mockSession)));
				assertThat(sessionEvent.getSessionId(), is(equalTo(expectedSessionId)));

				return null;
			}
		}).when(mockApplicationEventPublisher).publishEvent(any(ApplicationEvent.class));

		assertThat(sessionRepository.getSession(expectedSessionId), is(nullValue()));

		verify(mockTemplate, times(1)).get(eq(expectedSessionId));
		verify(mockTemplate, times(1)).remove(eq(expectedSessionId));
		verify(mockSession, times(1)).isExpired();
		verify(mockSession, times(2)).getId();
		verify(mockApplicationEventPublisher, times(1)).publishEvent(isA(SessionDeletedEvent.class));
	}

	@Test
	public void getSessionFindsMatchingNonExpiredSessionById() {
		final String expectedId = "1";

		final long expectedCreationTime = System.currentTimeMillis();
		final long currentLastAccessedTime = (expectedCreationTime + TimeUnit.MINUTES.toMillis(5));

		ExpiringSession mockSession = mock(ExpiringSession.class);

		when(mockSession.isExpired()).thenReturn(false);
		when(mockSession.getId()).thenReturn(expectedId);
		when(mockSession.getCreationTime()).thenReturn(expectedCreationTime);
		when(mockSession.getLastAccessedTime()).thenReturn(currentLastAccessedTime);
		when(mockSession.getAttributeNames()).thenReturn(Collections.singleton("attrOne"));
		when(mockSession.getAttribute(eq("attrOne"))).thenReturn("test");
		when(mockTemplate.get(eq(expectedId))).thenReturn(mockSession);

		ExpiringSession actualSession = sessionRepository.getSession(expectedId);

		assertThat(actualSession, is(not(sameInstance(mockSession))));
		assertThat(actualSession.getId(), is(equalTo(expectedId)));
		assertThat(actualSession.getCreationTime(), is(equalTo(expectedCreationTime)));
		assertThat(actualSession.getLastAccessedTime(), is(not(equalTo(currentLastAccessedTime))));
		assertThat(actualSession.getLastAccessedTime(), is(greaterThanOrEqualTo(expectedCreationTime)));
		assertThat(actualSession.getAttributeNames(), is(equalTo(Collections.singleton("attrOne"))));
		assertThat(String.valueOf(actualSession.getAttribute("attrOne")), is(equalTo("test")));

		verify(mockTemplate, times(1)).get(eq(expectedId));
		verify(mockSession, times(1)).isExpired();
		verify(mockSession, times(1)).getId();
		verify(mockSession, times(1)).getCreationTime();
		verify(mockSession, times(1)).getLastAccessedTime();
		verify(mockSession, times(1)).getAttributeNames();
		verify(mockSession, times(1)).getAttribute(eq("attrOne"));
	}

	@Test
	public void getSessionReturnsNull() {
		when(mockTemplate.get(anyString())).thenReturn(null);
		assertThat(sessionRepository.getSession("1"), is(nullValue()));
	}

	@Test
	public void saveStoresSession() {
		final String expectedSessionId = "1";

		final long expectedCreationTime = System.currentTimeMillis();
		final long expectedLastAccessTime = (expectedCreationTime + TimeUnit.MINUTES.toMillis(5));

		ExpiringSession mockSession = mock(ExpiringSession.class);

		when(mockSession.getId()).thenReturn(expectedSessionId);
		when(mockSession.getCreationTime()).thenReturn(expectedCreationTime);
		when(mockSession.getLastAccessedTime()).thenReturn(expectedLastAccessTime);
		when(mockSession.getMaxInactiveIntervalInSeconds()).thenReturn(MAX_INACTIVE_INTERVAL_IN_SECONDS);
		when(mockSession.getAttributeNames()).thenReturn(Collections.<String>emptySet());

		when(mockTemplate.put(eq(expectedSessionId), isA(GemFireSession.class)))
			.thenAnswer(new Answer<ExpiringSession>() {
				@Override public ExpiringSession answer(final InvocationOnMock invocation) throws Throwable {
					ExpiringSession session = invocation.getArgumentAt(1, ExpiringSession.class);

					assertThat(session, is(notNullValue()));
					assertThat(session.getId(), is(equalTo(expectedSessionId)));
					assertThat(session.getCreationTime(), is(equalTo(expectedCreationTime)));
					assertThat(session.getLastAccessedTime(), is(equalTo(expectedLastAccessTime)));
					assertThat(session.getMaxInactiveIntervalInSeconds(),
						is(equalTo(MAX_INACTIVE_INTERVAL_IN_SECONDS)));
					assertThat(session.getAttributeNames().isEmpty(), is(true));

					return null;
				}
			});

		sessionRepository.save(mockSession);

		verify(mockSession, times(2)).getId();
		verify(mockSession, times(1)).getCreationTime();
		verify(mockSession, times(1)).getLastAccessedTime();
		verify(mockSession, times(1)).getMaxInactiveIntervalInSeconds();
		verify(mockSession, times(1)).getAttributeNames();
		verify(mockTemplate, times(1)).put(eq(expectedSessionId), isA(GemFireSession.class));
	}

	@Test
	public void deleteRemovesExistingSessionAndHandlesDelete() {
		final String expectedSessionId = "1";

		final ExpiringSession mockSession = mock(ExpiringSession.class);

		when(mockSession.getId()).thenReturn(expectedSessionId);
		when(mockTemplate.remove(eq(expectedSessionId))).thenReturn(mockSession);

		doAnswer(new Answer<Void>() {
			@Override public Void answer(final InvocationOnMock invocation) throws Throwable {
				ApplicationEvent applicationEvent = invocation.getArgumentAt(0, ApplicationEvent.class);

				assertThat(applicationEvent, is(instanceOf(SessionDeletedEvent.class)));

				AbstractSessionEvent sessionEvent = (AbstractSessionEvent) applicationEvent;

				assertThat(sessionEvent.getSource(), is(sameInstance((Object) sessionRepository)));
				assertThat((ExpiringSession) sessionEvent.getSession(), is(sameInstance(mockSession)));
				assertThat(sessionEvent.getSessionId(), is(equalTo(expectedSessionId)));

				return null;
			}
		}).when(mockApplicationEventPublisher).publishEvent(isA(SessionDeletedEvent.class));

		sessionRepository.delete(expectedSessionId);

		verify(mockSession, times(1)).getId();
		verify(mockTemplate, times(1)).remove(eq(expectedSessionId));
		verify(mockApplicationEventPublisher, times(1)).publishEvent(isA(SessionDeletedEvent.class));
	}

	@Test
	public void deleteRemovesNonExistingSessionAndHandlesDelete() {
		final String expectedSessionId = "1";

		when(mockTemplate.remove(anyString())).thenReturn(null);

		doAnswer(new Answer<Void>() {
			@Override public Void answer(final InvocationOnMock invocation) throws Throwable {
				ApplicationEvent applicationEvent = invocation.getArgumentAt(0, ApplicationEvent.class);

				assertThat(applicationEvent, is(instanceOf(SessionDeletedEvent.class)));

				AbstractSessionEvent sessionEvent = (AbstractSessionEvent) applicationEvent;

				assertThat(sessionEvent.getSource(), is(sameInstance((Object) sessionRepository)));
				assertThat(sessionEvent.getSession(), is(nullValue()));
				assertThat(sessionEvent.getSessionId(), is(equalTo(expectedSessionId)));

				return null;
			}
		}).when(mockApplicationEventPublisher).publishEvent(isA(SessionDeletedEvent.class));

		sessionRepository.delete(expectedSessionId);

		verify(mockTemplate, times(1)).remove(eq(expectedSessionId));
		verify(mockApplicationEventPublisher, times(1)).publishEvent(isA(SessionDeletedEvent.class));
	}

	@Test
	public void constructGemFireSessionWithId() {
		GemFireSession session = new GemFireSession("1");

		assertThat(session.getId(), is(equalTo("1")));
		assertThat(session.getCreationTime(), is(lessThanOrEqualTo(System.currentTimeMillis())));
		assertThat(session.getLastAccessedTime(), is(equalTo(0l)));
		assertThat(session.getMaxInactiveIntervalInSeconds(), is(equalTo(0)));
		assertThat(session.getAttributeNames().isEmpty(), is(true));
	}

	@Test
	public void constructGemFireSessionWithExistingSession() {
		ExpiringSession mockSession = mock(ExpiringSession.class);

		final long expectedCreationTime = System.currentTimeMillis();
		final long expectedLastAccessTime = (expectedCreationTime + TimeUnit.SECONDS.toMillis(30));

		Set<String> expectedAttributedNames = asSet("attrOne", "attrTwo");

		when(mockSession.getId()).thenReturn("2");
		when(mockSession.getCreationTime()).thenReturn(expectedCreationTime);
		when(mockSession.getLastAccessedTime()).thenReturn(expectedLastAccessTime);
		when(mockSession.getMaxInactiveIntervalInSeconds()).thenReturn(MAX_INACTIVE_INTERVAL_IN_SECONDS);
		when(mockSession.getAttributeNames()).thenReturn(expectedAttributedNames);
		when(mockSession.getAttribute(eq("attrOne"))).thenReturn("testOne");
		when(mockSession.getAttribute(eq("attrTwo"))).thenReturn("testTwo");

		GemFireSession gemFireSession = new GemFireSession(mockSession);

		assertThat(gemFireSession.getId(), is(equalTo("2")));
		assertThat(gemFireSession.getCreationTime(), is(equalTo(expectedCreationTime)));
		assertThat(gemFireSession.getLastAccessedTime(), is(equalTo(expectedLastAccessTime)));
		assertThat(gemFireSession.getMaxInactiveIntervalInSeconds(), is(equalTo(MAX_INACTIVE_INTERVAL_IN_SECONDS)));
		assertThat(gemFireSession.getAttributeNames(), is(equalTo(expectedAttributedNames)));
		assertThat(String.valueOf(gemFireSession.getAttribute("attrOne")), is(equalTo("testOne")));
		assertThat(String.valueOf(gemFireSession.getAttribute("attrTwo")), is(equalTo("testTwo")));

		verify(mockSession, times(1)).getId();
		verify(mockSession, times(1)).getCreationTime();
		verify(mockSession, times(1)).getLastAccessedTime();
		verify(mockSession, times(1)).getMaxInactiveIntervalInSeconds();
		verify(mockSession, times(1)).getAttributeNames();
		verify(mockSession, times(1)).getAttribute(eq("attrOne"));
		verify(mockSession, times(1)).getAttribute(eq("attrTwo"));
	}

	@Test
	public void createNewGemFireSession() {
		GemFireSession session = GemFireSession.create(120);

		assertThat(session, is(notNullValue()));
		assertThat(session.getId(), is(notNullValue()));
		assertThat(session.getCreationTime(), is(lessThanOrEqualTo(System.currentTimeMillis())));
		assertThat(session.getLastAccessedTime(), is(equalTo(0l)));
		assertThat(session.getMaxInactiveIntervalInSeconds(), is(equalTo(120)));
		assertThat(session.getAttributeNames().isEmpty(), is(true));
	}

	@Test
	public void fromExistingSession() {
		ExpiringSession mockSession = mock(ExpiringSession.class);

		final long expectedCreationTime = System.currentTimeMillis();
		final long expectedLastAccessTime = (expectedCreationTime + TimeUnit.SECONDS.toMillis(30));

		when(mockSession.getId()).thenReturn("4");
		when(mockSession.getCreationTime()).thenReturn(expectedCreationTime);
		when(mockSession.getLastAccessedTime()).thenReturn(expectedLastAccessTime);
		when(mockSession.getMaxInactiveIntervalInSeconds()).thenReturn(MAX_INACTIVE_INTERVAL_IN_SECONDS);
		when(mockSession.getAttributeNames()).thenReturn(Collections.<String>emptySet());

		GemFireSession gemFireSession = GemFireSession.from(mockSession);

		assertThat(gemFireSession.getId(), is(equalTo("4")));
		assertThat(gemFireSession.getCreationTime(), is(equalTo(expectedCreationTime)));
		assertThat(gemFireSession.getLastAccessedTime(), is(not(equalTo(expectedLastAccessTime))));
		assertThat(gemFireSession.getLastAccessedTime(), is(lessThanOrEqualTo(System.currentTimeMillis())));
		assertThat(gemFireSession.getMaxInactiveIntervalInSeconds(), is(equalTo(MAX_INACTIVE_INTERVAL_IN_SECONDS)));
		assertThat(gemFireSession.getAttributeNames().isEmpty(), is(true));

		verify(mockSession, times(1)).getId();
		verify(mockSession, times(1)).getCreationTime();
		verify(mockSession, times(1)).getLastAccessedTime();
		verify(mockSession, times(1)).getMaxInactiveIntervalInSeconds();
		verify(mockSession, times(1)).getAttributeNames();
		verify(mockSession, never()).getAttribute(anyString());
	}

	@Test
	public void setGetAndRemoveAttribute() {
		GemFireSession session = GemFireSession.create(60);

		assertThat(session, is(notNullValue()));
		assertThat(session.getMaxInactiveIntervalInSeconds(), is(equalTo(60)));
		assertThat(session.getAttributeNames().isEmpty(), is(true));

		session.setAttribute("attrOne", "testOne");

		assertThat(session.getAttributeNames(), is(equalTo(asSet("attrOne"))));
		assertThat(String.valueOf(session.getAttribute("attrOne")), is(equalTo("testOne")));
		assertThat(session.getAttribute("attrTwo"), is(nullValue()));

		session.setAttribute("attrTwo", "testTwo");

		assertThat(session.getAttributeNames(), is(equalTo(asSet("attrOne", "attrTwo"))));
		assertThat(String.valueOf(session.getAttribute("attrOne")), is(equalTo("testOne")));
		assertThat(String.valueOf(session.getAttribute("attrTwo")), is(equalTo("testTwo")));

		session.setAttribute("attrTwo", null);

		assertThat(session.getAttributeNames(), is(equalTo(asSet("attrOne"))));
		assertThat(String.valueOf(session.getAttribute("attrOne")), is(equalTo("testOne")));
		assertThat(session.getAttribute("attrTwo"), is(nullValue()));

		session.removeAttribute("attrOne");

		assertThat(session.getAttributeNames().isEmpty(), is(true));
		assertThat(session.getAttribute("attrOne"), is(nullValue()));
		assertThat(session.getAttribute("attrTwo"), is(nullValue()));
	}

	@Test
	public void setAndGetPrincipalName() {
		GemFireSession session = GemFireSession.create(0);

		assertThat(session, is(notNullValue()));
		assertThat(session.getPrincipalName(), is(nullValue()));

		session.setPrincipalName("jblum");

		assertThat(session.getPrincipalName(), is(equalTo("jblum")));
		assertThat(session.getAttributeNames(), is(equalTo(asSet(Session.PRINCIPAL_NAME_ATTRIBUTE_NAME))));
		assertThat(String.valueOf(session.getAttribute(Session.PRINCIPAL_NAME_ATTRIBUTE_NAME)), is(equalTo("jblum")));

		session.setAttribute(Session.PRINCIPAL_NAME_ATTRIBUTE_NAME, "rwinch");

		assertThat(session.getPrincipalName(), is(equalTo("rwinch")));
		assertThat(String.valueOf(session.getAttribute(Session.PRINCIPAL_NAME_ATTRIBUTE_NAME)), is(equalTo("rwinch")));

		session.removeAttribute(Session.PRINCIPAL_NAME_ATTRIBUTE_NAME);

		assertThat(session.getPrincipalName(), is(nullValue()));
	}

	@Test
	public void isExpiredIsFalseWhenMaxInactiveIntervalIsNegative() {
		GemFireSession session = GemFireSession.create(-1);

		assertThat(session, is(notNullValue()));
		assertThat(session.getLastAccessedTime(), is(equalTo(0l)));
		assertThat(session.getMaxInactiveIntervalInSeconds(), is(equalTo(-1)));

		assertThat(session.isExpired(), is(false));
	}

	@Test
	// NOTE the following test case has a race condition, but is highly unlikely
	public void isExpiredIsFalseWhenSessionIsActive() {
		final int maxInactiveIntervalInSeconds = (int) TimeUnit.HOURS.toSeconds(2);

		GemFireSession session = GemFireSession.create(maxInactiveIntervalInSeconds);

		assertThat(session, is(notNullValue()));
		assertThat(session.getMaxInactiveIntervalInSeconds(), is(equalTo(maxInactiveIntervalInSeconds)));

		session.setLastAccessedTime(System.currentTimeMillis());

		assertThat(session.isExpired(), is(false));
	}

	@Test
	// NOTE the following test case has a race condition, but is highly unlikely
	public void isExpiredIsTrueWhenSessionIsInactive() {
		GemFireSession session = GemFireSession.create(60);

		assertThat(session, is(notNullValue()));
		assertThat(session.getMaxInactiveIntervalInSeconds(), is(equalTo(60)));

		final long lastAccessedTwoHoursAgo = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(2);

		session.setLastAccessedTime(lastAccessedTwoHoursAgo);

		assertThat(session.isExpired(), is(true));
		assertThat(session.getLastAccessedTime(), is(equalTo(lastAccessedTwoHoursAgo)));
	}

	@Test
	public void toData() {
		GemFireSession session = new GemFireSession("1");

		session.setLastAccessedTime(123l);
		session.setMaxInactiveIntervalInSeconds(MAX_INACTIVE_INTERVAL_IN_SECONDS);
		session.setPrincipalName("jblum");
		session.setAttribute("attrOne", "testOne");

		PdxWriter mockPdxWriter = mock(PdxWriter.class);

		session.toData(mockPdxWriter);

		verify(mockPdxWriter, times(1)).writeString(eq("id"), eq("1"));
		verify(mockPdxWriter, times(1)).writeLong(eq("creationTime"), eq(session.getCreationTime()));
		verify(mockPdxWriter, times(1)).writeLong(eq("lastAccessedTime"), eq(session.getLastAccessedTime()));
		verify(mockPdxWriter, times(1)).writeInt(eq("maxInactiveIntervalInSeconds"),
			eq(session.getMaxInactiveIntervalInSeconds()));
		verify(mockPdxWriter, times(1)).writeString(eq("principalName"), eq(session.getPrincipalName()));
		verify(mockPdxWriter, times(1)).writeObject(eq("attributeNames"), eq(session.getAttributeNames()));
		verify(mockPdxWriter, times(1)).writeObject(eq("attrOne"), eq(session.getAttribute("attrOne")));
		verify(mockPdxWriter, times(1)).writeObject(eq(Session.PRINCIPAL_NAME_ATTRIBUTE_NAME),
			eq(session.getPrincipalName()));
	}

	@Test
	public void fromData() {
		final long expectedCreationTime = (System.currentTimeMillis() - TimeUnit.HOURS.toMillis(2));
		final long expectedLastAccessedTime = (expectedCreationTime + TimeUnit.MINUTES.toMillis(30));

		final int expectedMaxInactiveIntervalInSeconds = (int) TimeUnit.HOURS.toSeconds(6);

		Set<String> expectedAttributeNames = asSet("attrOne", "attrTwo");

		PdxReader mockPdxReader = mock(PdxReader.class);

		when(mockPdxReader.readString(eq("id"))).thenReturn("2");
		when(mockPdxReader.readLong(eq("creationTime"))).thenReturn(expectedCreationTime);
		when(mockPdxReader.readLong(eq("lastAccessedTime"))).thenReturn(expectedLastAccessedTime);
		when(mockPdxReader.readInt(eq("maxInactiveIntervalInSeconds"))).thenReturn(expectedMaxInactiveIntervalInSeconds);
		when(mockPdxReader.readString("principalName")).thenReturn("jblum");
		when(mockPdxReader.readObject("attributeNames")).thenReturn(expectedAttributeNames);
		when(mockPdxReader.readObject("attrOne")).thenReturn("testOne");
		when(mockPdxReader.readObject("attrTwo")).thenReturn("testTwo");

		GemFireSession session = new GemFireSession("1");

		session.fromData(mockPdxReader);

		assertThat(session.getId(), is(equalTo("2")));
		assertThat(session.getCreationTime(), is(equalTo(expectedCreationTime)));
		assertThat(session.getLastAccessedTime(), is(equalTo(expectedLastAccessedTime)));
		assertThat(session.getMaxInactiveIntervalInSeconds(), is(equalTo(expectedMaxInactiveIntervalInSeconds)));
		assertThat(session.getPrincipalName(), is(equalTo("jblum")));
		assertThat(session.getAttributeNames().size(), is(equalTo(expectedAttributeNames.size() + 1)));
		assertThat(session.getAttributeNames().containsAll(expectedAttributeNames), is(true));
		assertThat(String.valueOf(session.getAttribute("attrOne")), is(equalTo("testOne")));
		assertThat(String.valueOf(session.getAttribute("attrTwo")), is(equalTo("testTwo")));
		assertThat(String.valueOf(session.getAttribute(Session.PRINCIPAL_NAME_ATTRIBUTE_NAME)), is(equalTo("jblum")));

		verify(mockPdxReader, times(1)).readString(eq("id"));
		verify(mockPdxReader, times(1)).readLong(eq("creationTime"));
		verify(mockPdxReader, times(1)).readLong(eq("lastAccessedTime"));
		verify(mockPdxReader, times(1)).readInt(eq("maxInactiveIntervalInSeconds"));
		verify(mockPdxReader, times(1)).readString(eq("principalName"));
		verify(mockPdxReader, times(1)).readObject(eq("attributeNames"));

		for (String expectedAttributeName : expectedAttributeNames) {
			verify(mockPdxReader, times(1)).readObject(eq(expectedAttributeName));
		}
	}

	@Test
	public void sessionEqualsDifferentSessionBasedOnId() {
		GemFireSession sessionOne = new GemFireSession("1");

		sessionOne.setLastAccessedTime(123l);
		sessionOne.setMaxInactiveIntervalInSeconds(120);
		sessionOne.setPrincipalName("jblum");

		GemFireSession sessionTwo = new GemFireSession("1");

		sessionTwo.setLastAccessedTime(456l);
		sessionTwo.setMaxInactiveIntervalInSeconds(300);
		sessionTwo.setPrincipalName("rwinch");

		assertThat(sessionOne.getId().equals(sessionTwo.getId()), is(true));
		assertThat(sessionOne.getLastAccessedTime() == sessionTwo.getLastAccessedTime(), is(false));
		assertThat(sessionOne.getMaxInactiveIntervalInSeconds() == sessionTwo.getMaxInactiveIntervalInSeconds(), is(false));
		assertThat(sessionOne.getPrincipalName().equals(sessionTwo.getPrincipalName()), is(false));
		assertThat(sessionOne.equals(sessionTwo), is(true));
	}

	@Test
	public void sessionHashCodeIsNotEqualToStringHashCode() {
		GemFireSession session = new GemFireSession("1");

		assertThat(session.getId(), is(equalTo("1")));
		assertThat(session.hashCode(), is(not(equalTo("1".hashCode()))));
	}

	protected abstract class GemfireOperationsAccessor extends GemfireAccessor implements GemfireOperations {
	}

}
