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

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.session.ExpiringSession;
import org.springframework.session.data.gemfire.support.GemFireUtils;
import org.springframework.session.events.AbstractSessionEvent;

import com.gemstone.gemfire.cache.Cache;
import com.gemstone.gemfire.cache.CacheClosedException;
import com.gemstone.gemfire.cache.DataPolicy;
import com.gemstone.gemfire.cache.ExpirationAction;
import com.gemstone.gemfire.cache.ExpirationAttributes;
import com.gemstone.gemfire.cache.GemFireCache;
import com.gemstone.gemfire.cache.Region;
import com.gemstone.gemfire.cache.client.ClientCache;
import com.gemstone.gemfire.cache.client.ClientCacheFactory;

/**
 * AbstractGemFireIntegrationTests is an abstract base class encapsulating common operations for writing
 * Spring Session GemFire integration tests.
 *
 * @author John Blum
 * @see org.springframework.session.ExpiringSession
 * @see org.springframework.session.events.AbstractSessionEvent
 * @see com.gemstone.gemfire.cache.Cache
 * @see com.gemstone.gemfire.cache.DataPolicy
 * @see com.gemstone.gemfire.cache.GemFireCache
 * @see com.gemstone.gemfire.cache.ExpirationAttributes
 * @see com.gemstone.gemfire.cache.Region
 * @see com.gemstone.gemfire.cache.client.ClientCache
 * @since 1.1.0
 */
@SuppressWarnings("unused")
public class AbstractGemFireIntegrationTests {

	protected static final boolean DEFAULT_ENABLE_QUERY_DEBUGGING = false;

	protected static final long DEFAULT_WAIT_DURATION = TimeUnit.SECONDS.toMillis(20);
	protected static final long DEFAULT_WAIT_INTERVAL = 500l;

	protected static final File WORKING_DIRECTORY = new File(System.getProperty("user.dir"));

	protected static final String DEFAULT_PROCESS_CONTROL_FILENAME = "process.ctl";

	@Autowired
	protected Cache gemfireCache;

	@Autowired
	protected GemFireOperationsSessionRepository gemfireSessionRepository;

	@Before
	public void setup() {
		System.setProperty("gemfire.Query.VERBOSE", String.valueOf(enableQueryDebugging()));
	}

	/* (non-Javadoc) */
	protected static File createDirectory(String pathname) {
		File directory = new File(WORKING_DIRECTORY, pathname);

		assertThat(String.format("Failed to create directory (%1$s).", directory),
			directory.isDirectory() || directory.mkdirs(), is(true));

		directory.deleteOnExit();

		return directory;
	}

	/* (non-Javadoc) */
	protected static List<String> createJavaProcessCommandLine(Class type, String... args) {
		List<String> commandLine = new ArrayList<String>();

		String javaHome = System.getProperty("java.home");
		String javaExe = new File(new File(javaHome, "bin"), "java").getAbsolutePath();

		commandLine.add(javaExe);
		commandLine.add("-server");
		commandLine.add("-ea");
		commandLine.add("-classpath");
		commandLine.add(System.getProperty("java.class.path"));
		commandLine.add(type.getName());

		if (args != null) {
			commandLine.addAll(Arrays.asList(args));
		}

		return commandLine;
	}

	/* (non-Javadoc) */
	protected static Process run(Class type, File directory) throws IOException {
		return new ProcessBuilder()
			.command(createJavaProcessCommandLine(type))
			.directory(directory)
			.start();
	}

	// NOTE this method would not be necessary except Spring Sessions' build does not fork the test JVM
	// for every test class.
	/* (non-Javadoc) */
	protected static boolean waitForClientCacheToClose() {
		return waitForClientCacheToClose(DEFAULT_WAIT_DURATION);
	}

	/* (non-Javadoc) */
	protected static boolean waitForClientCacheToClose(long duration) {
		try {
			final ClientCache clientCache = ClientCacheFactory.getAnyInstance();

			clientCache.close();

			waitOnCondition(new Condition() {
				@Override public boolean evaluate() {
					return clientCache.isClosed();
				}
			}, duration);

			return clientCache.isClosed();
		}
		catch (CacheClosedException ignore) {
			return true;
		}

	}

	/* (non-Javadoc) */
	protected static boolean waitForProcessToStart(Process process, File directory) {
		return waitForProcessToStart(process, directory, DEFAULT_WAIT_DURATION);
	}

	/* (non-Javadoc) */
	@SuppressWarnings("all")
	protected static boolean waitForProcessToStart(Process process, File directory, long duration) {
		final File processControl = new File(directory, DEFAULT_PROCESS_CONTROL_FILENAME);

		waitOnCondition(new Condition() {
			@Override public boolean evaluate() {
				return processControl.isFile();
			}
		}, duration);

		return process.isAlive();
	}

	/* (non-Javadoc) */
	protected static int waitForProcessToStop(Process process, File directory) {
		return waitForProcessToStop(process, directory, DEFAULT_WAIT_DURATION);
	}

	/* (non-Javadoc) */
	protected static int waitForProcessToStop(Process process, File directory, long duration) {
		final long timeout = (System.currentTimeMillis() + duration);

		try {
			while (process.isAlive() && System.currentTimeMillis() < timeout) {
				if (process.waitFor(DEFAULT_WAIT_INTERVAL, TimeUnit.MILLISECONDS)) {
					return process.exitValue();
				}
			}
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}

		return (process.isAlive() ? -1 : process.exitValue());
	}

	/* (non-Javadoc) */
	protected static boolean waitOnCondition(Condition condition) {
		return waitOnCondition(condition, DEFAULT_WAIT_DURATION);
	}

	/* (non-Javadoc) */
	@SuppressWarnings("all")
	protected static boolean waitOnCondition(Condition condition, long duration) {
		final long timeout = (System.currentTimeMillis() + duration);

		try {
			while (!condition.evaluate() && System.currentTimeMillis() < timeout) {
				synchronized (condition) {
					TimeUnit.MILLISECONDS.timedWait(condition, DEFAULT_WAIT_INTERVAL);
				}
			}
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}

		return condition.evaluate();
	}

	/* (non-Javadoc) */
	protected static File writeProcessControlFile(File path) throws IOException {
		assertThat(path != null && path.isDirectory(), is(true));

		File processControl = new File(path, DEFAULT_PROCESS_CONTROL_FILENAME);

		assertThat(processControl.createNewFile(), is(true));

		processControl.deleteOnExit();

		return processControl;
	}

	/* (non-Javadoc) */
	protected void assertRegion(Region<?, ?> actualRegion, String expectedName, DataPolicy expectedDataPolicy) {
		assertThat(actualRegion, is(notNullValue()));
		assertThat(actualRegion.getName(), is(equalTo(expectedName)));
		assertThat(actualRegion.getFullPath(), is(equalTo(GemFireUtils.toRegionPath(expectedName))));
		assertThat(actualRegion.getAttributes(), is(notNullValue()));
		assertThat(actualRegion.getAttributes().getDataPolicy(), is(equalTo(expectedDataPolicy)));
	}

	/* (non-Javadoc) */
	protected void assertEntryIdleTimeout(Region<?, ?> region, ExpirationAction expectedAction, int expectedTimeout) {
		assertEntryIdleTimeout(region.getAttributes().getEntryIdleTimeout(), expectedAction, expectedTimeout);
	}

	/* (non-Javadoc) */
	protected void assertEntryIdleTimeout(ExpirationAttributes actualExpirationAttributes,
			ExpirationAction expectedAction, int expectedTimeout) {
		assertThat(actualExpirationAttributes, is(notNullValue()));
		assertThat(actualExpirationAttributes.getAction(), is(equalTo(expectedAction)));
		assertThat(actualExpirationAttributes.getTimeout(), is(equalTo(expectedTimeout)));
	}

	/* (non-Javadoc) */
	protected boolean enableQueryDebugging() {
		return DEFAULT_ENABLE_QUERY_DEBUGGING;
	}

	/* (non-Javadoc) */
	protected List<String> listRegions(GemFireCache gemfireCache) {
		Set<Region<?, ?>> regions = gemfireCache.rootRegions();

		List<String> regionList = new ArrayList<String>(regions.size());

		for (Region region : regions) {
			regionList.add(region.getFullPath());
		}

		return regionList;
	}

	/* (non-Javadoc) */
	@SuppressWarnings("unchecked")
	protected <T extends ExpiringSession> T createSession() {
		T expiringSession = (T) gemfireSessionRepository.createSession();
		assertThat(expiringSession, is(notNullValue()));
		return expiringSession;
	}

	/* (non-Javadoc) */
	@SuppressWarnings("unchecked")
	protected <T extends ExpiringSession> T createSession(String principalName) {
		GemFireOperationsSessionRepository.GemFireSession session = createSession();
		session.setPrincipalName(principalName);
		return (T) session;
	}

	/* (non-Javadoc) */
	protected <T extends ExpiringSession> T expire(T session) {
		session.setLastAccessedTime(0l);
		return session;
	}

	/* (non-Javadoc) */
	@SuppressWarnings("unchecked")
	protected <T extends ExpiringSession> T get(String sessionId) {
		return (T) gemfireSessionRepository.getSession(sessionId);
	}

	/* (non-Javadoc) */
	protected <T extends ExpiringSession> T save(T session) {
		gemfireSessionRepository.save(session);
		return session;
	}

	/* (non-Javadoc) */
	protected <T extends ExpiringSession> T touch(T session) {
		session.setLastAccessedTime(System.currentTimeMillis());
		return session;
	}

	/**
	 * The SessionEventListener class is a Spring {@link ApplicationListener} listening for Spring HTTP Session
	 * application events.
	 *
	 * @see org.springframework.context.ApplicationListener
	 * @see org.springframework.session.events.AbstractSessionEvent
	 */
	public static class SessionEventListener implements ApplicationListener<AbstractSessionEvent> {

		private AbstractSessionEvent sessionEvent;

		private final Object lock = new Object();

		/* (non-Javadoc) */
		@SuppressWarnings("unchecked")
		public synchronized <T extends AbstractSessionEvent> T getSessionEvent() {
			T sessionEvent = (T) this.sessionEvent;
			this.sessionEvent = null;
			return sessionEvent;
		}

		/* (non-Javadoc) */
		public synchronized void onApplicationEvent(AbstractSessionEvent event) {
			sessionEvent = event;
		}

		/* (non-Javadoc) */
		public <T extends AbstractSessionEvent> T waitForSessionEvent(long duration) {
			final long timeout = (System.currentTimeMillis() + duration);

			T sessionEvent = getSessionEvent();

			try {
				while (sessionEvent == null && System.currentTimeMillis() < timeout) {
					synchronized (lock) {
						TimeUnit.MILLISECONDS.timedWait(lock, DEFAULT_WAIT_INTERVAL);
					}

					sessionEvent = getSessionEvent();
				}
			}
			catch (InterruptedException ignore) {
				Thread.currentThread().interrupt();
			}

			return sessionEvent;
		}

	}

	/**
	 * The Condition interface defines a logical condition that must be satisfied before it is safe to proceed.
	 */
	protected interface Condition {
		boolean evaluate();
	}

}
