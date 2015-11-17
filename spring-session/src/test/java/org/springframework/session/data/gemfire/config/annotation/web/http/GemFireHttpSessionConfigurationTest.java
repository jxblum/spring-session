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
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.data.gemfire.GemfireOperations;
import org.springframework.data.gemfire.GemfireTemplate;
import org.springframework.data.gemfire.client.Interest;
import org.springframework.session.data.gemfire.GemFireOperationsSessionRepository;

import com.gemstone.gemfire.cache.Cache;
import com.gemstone.gemfire.cache.InterestResultPolicy;
import com.gemstone.gemfire.cache.Region;
import com.gemstone.gemfire.cache.RegionShortcut;
import com.gemstone.gemfire.cache.client.ClientCache;
import com.gemstone.gemfire.cache.client.ClientRegionShortcut;

/**
 * The GemFireHttpSessionConfigurationTest class is a test suite of test cases testing the contract and functionality
 * of the {@link GemFireHttpSessionConfiguration} class.
 *
 * @author John Blum
 * @see org.junit.Test
 * @see org.mockito.Mockito
 * @see org.springframework.session.data.gemfire.config.annotation.web.http.GemFireHttpSessionConfiguration
 * @see com.gemstone.gemfire.cache.Cache
 * @see com.gemstone.gemfire.cache.Region
 * @see com.gemstone.gemfire.cache.client.ClientCache
 * @since 1.0.0
 */
public class GemFireHttpSessionConfigurationTest {

	private GemFireHttpSessionConfiguration gemfireConfiguration;

	@Before
	public void setup() {
		gemfireConfiguration = new GemFireHttpSessionConfiguration();
	}

	@Test
	public void setAndGetBeanClassLoader() {
		assertThat(gemfireConfiguration.getBeanClassLoader(), is(nullValue()));

		gemfireConfiguration.setBeanClassLoader(Thread.currentThread().getContextClassLoader());

		assertThat(gemfireConfiguration.getBeanClassLoader(), is(equalTo(
			Thread.currentThread().getContextClassLoader())));

		gemfireConfiguration.setBeanClassLoader(null);

		assertThat(gemfireConfiguration.getBeanClassLoader(), is(nullValue()));
	}

	@Test
	public void setAndGetClientRegionShortcut() {
		assertThat(gemfireConfiguration.getClientRegionShortcut(), is(equalTo(
			GemFireHttpSessionConfiguration.DEFAULT_CLIENT_REGION_SHORTCUT)));

		gemfireConfiguration.setClientRegionShortcut(ClientRegionShortcut.CACHING_PROXY);

		assertThat(gemfireConfiguration.getClientRegionShortcut(), is(equalTo(ClientRegionShortcut.CACHING_PROXY)));

		gemfireConfiguration.setClientRegionShortcut(null);

		assertThat(gemfireConfiguration.getClientRegionShortcut(), is(equalTo(
			GemFireHttpSessionConfiguration.DEFAULT_CLIENT_REGION_SHORTCUT)));
	}

	@Test
	public void setAndGetMaxInactiveIntervalInSeconds() {
		assertThat(gemfireConfiguration.getMaxInactiveIntervalInSeconds(), is(equalTo(
			GemFireHttpSessionConfiguration.DEFAULT_MAX_INACTIVE_INTERVAL_IN_SECONDS)));

		gemfireConfiguration.setMaxInactiveIntervalInSeconds(300);

		assertThat(gemfireConfiguration.getMaxInactiveIntervalInSeconds(), is(equalTo(300)));

		gemfireConfiguration.setMaxInactiveIntervalInSeconds(-1);

		assertThat(gemfireConfiguration.getMaxInactiveIntervalInSeconds(), is(equalTo(0)));
	}

	@Test
	public void setAndGetServerRegionShortcut() {
		assertThat(gemfireConfiguration.getServerRegionShortcut(), is(equalTo(
			GemFireHttpSessionConfiguration.DEFAULT_SERVER_REGION_SHORTCUT)));

		gemfireConfiguration.setServerRegionShortcut(RegionShortcut.REPLICATE_PERSISTENT);

		assertThat(gemfireConfiguration.getServerRegionShortcut(), is(equalTo(RegionShortcut.REPLICATE_PERSISTENT)));

		gemfireConfiguration.setServerRegionShortcut(null);

		assertThat(gemfireConfiguration.getServerRegionShortcut(), is(equalTo(
			GemFireHttpSessionConfiguration.DEFAULT_SERVER_REGION_SHORTCUT)));
	}

	@Test
	public void setAndGetSpringSessionGemFireRegionName() {
		assertThat(gemfireConfiguration.getSpringSessionGemFireRegionName(), is(equalTo(
			GemFireHttpSessionConfiguration.DEFAULT_SPRING_SESSION_GEMFIRE_REGION_NAME)));

		gemfireConfiguration.setSpringSessionGemFireRegionName("test");

		assertThat(gemfireConfiguration.getSpringSessionGemFireRegionName(), is(equalTo("test")));

		gemfireConfiguration.setSpringSessionGemFireRegionName("  ");

		assertThat(gemfireConfiguration.getSpringSessionGemFireRegionName(), is(equalTo(
			GemFireHttpSessionConfiguration.DEFAULT_SPRING_SESSION_GEMFIRE_REGION_NAME)));

		gemfireConfiguration.setSpringSessionGemFireRegionName("");

		assertThat(gemfireConfiguration.getSpringSessionGemFireRegionName(), is(equalTo(
			GemFireHttpSessionConfiguration.DEFAULT_SPRING_SESSION_GEMFIRE_REGION_NAME)));

		gemfireConfiguration.setSpringSessionGemFireRegionName(null);

		assertThat(gemfireConfiguration.getSpringSessionGemFireRegionName(), is(equalTo(
			GemFireHttpSessionConfiguration.DEFAULT_SPRING_SESSION_GEMFIRE_REGION_NAME)));
	}

	@Test
	public void setImportMetadata() {
		AnnotationMetadata mockAnnotationMetadata = mock(AnnotationMetadata.class, "testSetImportMetadata");

		Map<String, Object> annotationAttributes = new HashMap<String, Object>(4);

		annotationAttributes.put("clientRegionShortcut", ClientRegionShortcut.CACHING_PROXY);
		annotationAttributes.put("maxInactiveIntervalInSeconds", 600);
		annotationAttributes.put("serverRegionShortcut", RegionShortcut.REPLICATE);
		annotationAttributes.put("regionName", "TEST");

		when(mockAnnotationMetadata.getAnnotationAttributes(eq(EnableGemFireHttpSession.class.getName())))
			.thenReturn(annotationAttributes);

		gemfireConfiguration.setImportMetadata(mockAnnotationMetadata);

		assertThat(gemfireConfiguration.getClientRegionShortcut(), is(equalTo(ClientRegionShortcut.CACHING_PROXY)));
		assertThat(gemfireConfiguration.getMaxInactiveIntervalInSeconds(), is(equalTo(600)));
		assertThat(gemfireConfiguration.getServerRegionShortcut(), is(equalTo(RegionShortcut.REPLICATE)));
		assertThat(gemfireConfiguration.getSpringSessionGemFireRegionName(), is(equalTo("TEST")));

		verify(mockAnnotationMetadata, times(1)).getAnnotationAttributes(eq(EnableGemFireHttpSession.class.getName()));
	}

	@Test
	public void createAndInitializeSpringSessionRepositoryBean() {
		GemfireOperations mockGemfireOperations = mock(GemfireOperations.class, 
			"testCreateAndInitializeSpringSessionRepositoryBean");

		gemfireConfiguration.setMaxInactiveIntervalInSeconds(120);

		GemFireOperationsSessionRepository sessionRepository = gemfireConfiguration.sessionRepository(
			mockGemfireOperations);

		assertThat(sessionRepository, is(notNullValue()));
		assertThat(sessionRepository.getTemplate(), is(sameInstance(mockGemfireOperations)));
		assertThat(sessionRepository.getMaxInactiveIntervalInSeconds(), is(equalTo(120)));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void createAndInitializeSpringSessionGemFireRegionTemplate() {
		Region mockRegion = mock(Region.class, "testCreateAndInitializeSpringSessionGemFireRegionTemplate");

		GemfireTemplate template = gemfireConfiguration.springSessionGemFireRegionTemplate(mockRegion);

		assertThat(template, is(notNullValue()));
		assertThat(template.getRegion(), is(sameInstance(mockRegion)));
	}

	@Test
	public void clientRegionShortcutsAreLocal() {
		assertThat(gemfireConfiguration.isLocal(ClientRegionShortcut.LOCAL), is(true));
		assertThat(gemfireConfiguration.isLocal(ClientRegionShortcut.LOCAL_HEAP_LRU), is(true));
		assertThat(gemfireConfiguration.isLocal(ClientRegionShortcut.LOCAL_OVERFLOW), is(true));
		assertThat(gemfireConfiguration.isLocal(ClientRegionShortcut.LOCAL_PERSISTENT), is(true));
		assertThat(gemfireConfiguration.isLocal(ClientRegionShortcut.LOCAL_PERSISTENT_OVERFLOW), is(true));
	}

	@Test
	public void clientRegionShortcutsAreNotLocal() {
		assertThat(gemfireConfiguration.isLocal(ClientRegionShortcut.CACHING_PROXY), is(false));
		assertThat(gemfireConfiguration.isLocal(ClientRegionShortcut.CACHING_PROXY_HEAP_LRU), is(false));
		assertThat(gemfireConfiguration.isLocal(ClientRegionShortcut.CACHING_PROXY_OVERFLOW), is(false));
		assertThat(gemfireConfiguration.isLocal(ClientRegionShortcut.PROXY), is(false));
	}

	@Test
	public void emptyInterestsRegistration() {
		Interest[] interests = gemfireConfiguration.registerInterests(false);

		assertThat(interests, is(notNullValue()));
		assertThat(interests.length, is(equalTo(0)));
	}

	@Test
	public void allKeysInterestRegistration() {
		Interest[] interests = gemfireConfiguration.registerInterests(true);

		assertThat(interests, is(notNullValue()));
		assertThat(interests.length, is(equalTo(1)));
		assertThat(interests[0].isDurable(), is(false));
		assertThat(interests[0].getKey().toString(), is(equalTo("ALL_KEYS")));
		assertThat(interests[0].getPolicy(), is(equalTo(InterestResultPolicy.KEYS)));
		assertThat(interests[0].isReceiveValues(), is(true));
	}

	@Test
	public void clientCacheIsClient() {
		assertThat(gemfireConfiguration.isClient(mock(ClientCache.class)), is(true));
	}

	@Test
	public void peerCacheIsNotClient() {
		assertThat(gemfireConfiguration.isClient(mock(Cache.class)), is(false));
	}

	@Test
	public void expirationIsAllowed() {
		Cache mockCache = mock(Cache.class, "testExpirationIsAllowed.MockCache");
		ClientCache mockClientCache = mock(ClientCache.class, "testExpirationIsAllowed.MockClientCache");

		gemfireConfiguration.setClientRegionShortcut(ClientRegionShortcut.PROXY);
		gemfireConfiguration.setServerRegionShortcut(RegionShortcut.REPLICATE);

		assertThat(gemfireConfiguration.isExpirationAllowed(mockCache), is(true));

		gemfireConfiguration.setServerRegionShortcut(RegionShortcut.PARTITION_REDUNDANT_PERSISTENT_OVERFLOW);

		assertThat(gemfireConfiguration.isExpirationAllowed(mockCache), is(true));

		gemfireConfiguration.setClientRegionShortcut(ClientRegionShortcut.CACHING_PROXY);
		gemfireConfiguration.setServerRegionShortcut(RegionShortcut.PARTITION_PROXY);

		assertThat(gemfireConfiguration.isExpirationAllowed(mockClientCache), is(true));

		gemfireConfiguration.setClientRegionShortcut(ClientRegionShortcut.LOCAL_PERSISTENT_OVERFLOW);
		gemfireConfiguration.setServerRegionShortcut(RegionShortcut.REPLICATE_PROXY);

		assertThat(gemfireConfiguration.isExpirationAllowed(mockClientCache), is(true));
	}

	@Test
	public void expirationIsNotAllowed() {
		Cache mockCache = mock(Cache.class, "testExpirationIsAllowed.MockCache");
		ClientCache mockClientCache = mock(ClientCache.class, "testExpirationIsAllowed.MockClientCache");

		gemfireConfiguration.setClientRegionShortcut(ClientRegionShortcut.PROXY);
		gemfireConfiguration.setServerRegionShortcut(RegionShortcut.PARTITION);

		assertThat(gemfireConfiguration.isExpirationAllowed(mockClientCache), is(false));

		gemfireConfiguration.setClientRegionShortcut(ClientRegionShortcut.LOCAL);
		gemfireConfiguration.setServerRegionShortcut(RegionShortcut.PARTITION_PROXY);

		assertThat(gemfireConfiguration.isExpirationAllowed(mockCache), is(false));
	}

	@Test
	public void clientRegionShortcutIsProxy() {
		assertThat(gemfireConfiguration.isProxy(ClientRegionShortcut.PROXY), is(true));
	}

	@Test
	public void clientRegionShortcutIsNotProxy() {
		assertThat(gemfireConfiguration.isProxy(ClientRegionShortcut.CACHING_PROXY), is(false));
		assertThat(gemfireConfiguration.isProxy(ClientRegionShortcut.CACHING_PROXY_HEAP_LRU), is(false));
		assertThat(gemfireConfiguration.isProxy(ClientRegionShortcut.CACHING_PROXY_OVERFLOW), is(false));
		assertThat(gemfireConfiguration.isProxy(ClientRegionShortcut.LOCAL), is(false));
		assertThat(gemfireConfiguration.isProxy(ClientRegionShortcut.LOCAL_HEAP_LRU), is(false));
		assertThat(gemfireConfiguration.isProxy(ClientRegionShortcut.LOCAL_OVERFLOW), is(false));
		assertThat(gemfireConfiguration.isProxy(ClientRegionShortcut.LOCAL_PERSISTENT), is(false));
		assertThat(gemfireConfiguration.isProxy(ClientRegionShortcut.LOCAL_PERSISTENT_OVERFLOW), is(false));
	}

	@Test
	public void regionShortcutIsProxy() {
		assertThat(gemfireConfiguration.isProxy(RegionShortcut.PARTITION_PROXY), is(true));
		assertThat(gemfireConfiguration.isProxy(RegionShortcut.PARTITION_PROXY_REDUNDANT), is(true));
		assertThat(gemfireConfiguration.isProxy(RegionShortcut.REPLICATE_PROXY), is(true));
	}

	@Test
	public void regionShortcutIsNotProxy() {
		assertThat(gemfireConfiguration.isProxy(RegionShortcut.LOCAL), is(false));
		assertThat(gemfireConfiguration.isProxy(RegionShortcut.LOCAL_HEAP_LRU), is(false));
		assertThat(gemfireConfiguration.isProxy(RegionShortcut.LOCAL_OVERFLOW), is(false));
		assertThat(gemfireConfiguration.isProxy(RegionShortcut.LOCAL_PERSISTENT), is(false));
		assertThat(gemfireConfiguration.isProxy(RegionShortcut.LOCAL_PERSISTENT_OVERFLOW), is(false));
		assertThat(gemfireConfiguration.isProxy(RegionShortcut.REPLICATE), is(false));
		assertThat(gemfireConfiguration.isProxy(RegionShortcut.REPLICATE_HEAP_LRU), is(false));
		assertThat(gemfireConfiguration.isProxy(RegionShortcut.REPLICATE_OVERFLOW), is(false));
		assertThat(gemfireConfiguration.isProxy(RegionShortcut.REPLICATE_PERSISTENT), is(false));
		assertThat(gemfireConfiguration.isProxy(RegionShortcut.REPLICATE_PERSISTENT_OVERFLOW), is(false));
		assertThat(gemfireConfiguration.isProxy(RegionShortcut.PARTITION), is(false));
		assertThat(gemfireConfiguration.isProxy(RegionShortcut.PARTITION_HEAP_LRU), is(false));
		assertThat(gemfireConfiguration.isProxy(RegionShortcut.PARTITION_OVERFLOW), is(false));
		assertThat(gemfireConfiguration.isProxy(RegionShortcut.PARTITION_PERSISTENT), is(false));
		assertThat(gemfireConfiguration.isProxy(RegionShortcut.PARTITION_PERSISTENT_OVERFLOW), is(false));
		assertThat(gemfireConfiguration.isProxy(RegionShortcut.PARTITION_REDUNDANT), is(false));
		assertThat(gemfireConfiguration.isProxy(RegionShortcut.PARTITION_REDUNDANT_HEAP_LRU), is(false));
		assertThat(gemfireConfiguration.isProxy(RegionShortcut.PARTITION_REDUNDANT_OVERFLOW), is(false));
		assertThat(gemfireConfiguration.isProxy(RegionShortcut.PARTITION_REDUNDANT_PERSISTENT), is(false));
		assertThat(gemfireConfiguration.isProxy(RegionShortcut.PARTITION_REDUNDANT_PERSISTENT_OVERFLOW), is(false));
	}

}
