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
 * @see org.springframework.context.ApplicationListener
 * @see org.springframework.session.ExpiringSession
 * @see org.springframework.session.events.AbstractSessionEvent
 * @see com.gemstone.gemfire.cache.Cache
 * @see com.gemstone.gemfire.cache.DataPolicy
 * @see com.gemstone.gemfire.cache.ExpirationAttributes
 * @see com.gemstone.gemfire.cache.GemFireCache
 * @see com.gemstone.gemfire.cache.Region
 * @since 1.1.0
 */
@SuppressWarnings("unused")
public class AbstractGemFireIntegrationTests {

	protected static final boolean DEFAULT_ENABLE_QUERY_DEBUGGING = false;

	protected static final long DEFAULT_INTERVAL = 500l;
	protected static final long DEFAULT_PROCESS_WAIT_DURATION = TimeUnit.SECONDS.toMillis(20);

	protected static final File WORKING_DIRECTORY = new File(System.getProperty("user.dir"));

	protected static final String DEFAULT_PROCESS_CONTROL_FILENAME = "process.cntrl";

	@Autowired
	protected Cache gemfireCache;

	@Autowired
	protected GemFireOperationsSessionRepository gemfireSessionRepository;

	@Before
	public void setup() {
		System.setProperty("gemfire.Query.VERBOSE", String.valueOf(enableQueryDebugging()));
	}

	protected static File createDirectory(String pathname) {
		File directory = new File(WORKING_DIRECTORY, pathname);

		assertThat(String.format("Failed to create directory (%1$s).", directory),
			directory.isDirectory() || directory.mkdirs(), is(true));

		directory.deleteOnExit();

		return directory;
	}

	protected static List<String> createJavaProcessCommandLineFrom(Class type, String... args) {
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

	protected static Process run(Class type, File directory) throws IOException {
		return new ProcessBuilder()
			.command(createJavaProcessCommandLineFrom(type))
			.directory(directory)
			.start();
	}

	protected static void waitForProcessToStart(File directory) {
		waitForProcessToStart(directory, DEFAULT_PROCESS_WAIT_DURATION);
	}

	@SuppressWarnings("all")
	protected static void waitForProcessToStart(File directory, long duration) {
		File processControlFile = new File(directory, DEFAULT_PROCESS_CONTROL_FILENAME);

		final long timeout = (System.currentTimeMillis() + duration);

		while (!processControlFile.isFile() && System.currentTimeMillis() < timeout) {
			try {
				synchronized (processControlFile) {
					TimeUnit.MILLISECONDS.timedWait(processControlFile, DEFAULT_INTERVAL);
				}
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			}
		}
	}

	protected static File writeProcessControlFile(File path) throws IOException {
		assertThat(path != null && path.isDirectory(), is(true));

		File processControl = new File(path, DEFAULT_PROCESS_CONTROL_FILENAME);

		assertThat(processControl.createNewFile(), is(true));

		processControl.deleteOnExit();

		return processControl;
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

	protected boolean enableQueryDebugging() {
		return DEFAULT_ENABLE_QUERY_DEBUGGING;
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

	protected <T extends ExpiringSession> T expire(T session) {
		session.setLastAccessedTime(0l);
		return session;
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

	public static class SessionEventListener implements ApplicationListener<AbstractSessionEvent> {

		private AbstractSessionEvent sessionEvent;

		private final Object lock = new Object();

		@SuppressWarnings("unchecked")
		public synchronized <T extends AbstractSessionEvent> T getSessionEvent() {
			T sessionEvent = (T) this.sessionEvent;
			this.sessionEvent = null;
			return sessionEvent;
		}

		public synchronized void onApplicationEvent(AbstractSessionEvent event) {
			sessionEvent = event;
		}

		public <T extends AbstractSessionEvent> T waitForSessionEvent(long duration) {
			final long timeout = (System.currentTimeMillis() + duration);

			T sessionEvent = getSessionEvent();

			while (sessionEvent == null && System.currentTimeMillis() < timeout) {
				try {
					synchronized (lock) {
						TimeUnit.MILLISECONDS.timedWait(lock, DEFAULT_INTERVAL);
					}
				}
				catch (InterruptedException ignore) {
				}
				finally {
					sessionEvent = getSessionEvent();
				}
			}

			return sessionEvent;
		}

	}
}
