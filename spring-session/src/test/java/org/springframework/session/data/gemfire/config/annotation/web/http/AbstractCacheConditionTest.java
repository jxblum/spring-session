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

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.session.data.gemfire.config.annotation.web.http.support.ClientServerCacheCondition;
import org.springframework.session.data.gemfire.config.annotation.web.http.support.PeerToPeerCacheCondition;

/**
 * The AbstractCacheConditionTest class is a test suite of test cases testing the contract and functionality
 * of the AbstractCacheCondition class and subclasses: ClientServerCacheCondition and PeerToPeerCacheCondition.
 *
 * @author John Blum
 * @see org.junit.Test
 * @see org.mockito.Mockito
 * @see org.springframework.session.data.gemfire.config.annotation.web.http.AbstractCacheCondition
 * @see org.springframework.session.data.gemfire.config.annotation.web.http.support.ClientServerCacheCondition
 * @see org.springframework.session.data.gemfire.config.annotation.web.http.support.PeerToPeerCacheCondition
 * @since 1.0.0
 */
public class AbstractCacheConditionTest {

	private AbstractCacheCondition clientServerCacheCondition = new ClientServerCacheCondition();

	private AbstractCacheCondition peerToPeerCacheCondition = new PeerToPeerCacheCondition();

	protected BeanDefinition mockBeanDefinition(Class<?> beanClass) {
		String beanClassName = beanClass.getName();
		BeanDefinition mockBeanDefinition = mock(BeanDefinition.class, beanClassName);
		when(mockBeanDefinition.getBeanClassName()).thenReturn(beanClassName);
		return mockBeanDefinition;
	}

	protected ConditionContext mockConditionContext(ConfigurableListableBeanFactory beanFactory) {
		ConditionContext mockConditionContext = mock(ConditionContext.class);
		when(mockConditionContext.getBeanFactory()).thenReturn(beanFactory);
		return mockConditionContext;
	}

	@Test
	public void matchesClientCacheButNotPeer() {
		String[] beanDefinitionNames = { "beanOne", "beanTwo" };

		ConfigurableListableBeanFactory mockBeanFactory = mock(ConfigurableListableBeanFactory.class);

		BeanDefinition mockBeanOne = mockBeanDefinition(Object.class);
		BeanDefinition mockBeanTwo = mockBeanDefinition(clientServerCacheCondition.getGemFireCacheType());

		when(mockBeanFactory.getBeanDefinitionNames()).thenReturn(beanDefinitionNames);
		when(mockBeanFactory.getBeanDefinition(eq(beanDefinitionNames[0]))).thenReturn(mockBeanOne);
		when(mockBeanFactory.getBeanDefinition(eq(beanDefinitionNames[1]))).thenReturn(mockBeanTwo);

		ConditionContext mockConditionContext = mockConditionContext(mockBeanFactory);

		assertThat(clientServerCacheCondition.matches(mockConditionContext, null), is(true));
		assertThat(peerToPeerCacheCondition.matches(mockConditionContext, null), is(false));

		verify(mockBeanFactory, times(2)).getBeanDefinitionNames();
		verify(mockBeanFactory, times(2)).getBeanDefinition(eq(beanDefinitionNames[0]));
		verify(mockBeanFactory, times(2)).getBeanDefinition(eq(beanDefinitionNames[1]));
		verify(mockBeanOne, times(2)).getBeanClassName();
		verify(mockBeanTwo, times(2)).getBeanClassName();
		verify(mockConditionContext, times(2)).getBeanFactory();
	}

	@Test
	public void matchesPeerCacheButNotClient() {
		String[] beanDefinitionNames = { "beanOne", "beanTwo" };

		ConfigurableListableBeanFactory mockBeanFactory = mock(ConfigurableListableBeanFactory.class);

		BeanDefinition mockBeanOne = mockBeanDefinition(Object.class);
		BeanDefinition mockBeanTwo = mockBeanDefinition(peerToPeerCacheCondition.getGemFireCacheType());

		when(mockBeanFactory.getBeanDefinitionNames()).thenReturn(beanDefinitionNames);
		when(mockBeanFactory.getBeanDefinition(eq(beanDefinitionNames[0]))).thenReturn(mockBeanOne);
		when(mockBeanFactory.getBeanDefinition(eq(beanDefinitionNames[1]))).thenReturn(mockBeanTwo);

		ConditionContext mockConditionContext = mockConditionContext(mockBeanFactory);

		assertThat(clientServerCacheCondition.matches(mockConditionContext, null), is(false));
		assertThat(peerToPeerCacheCondition.matches(mockConditionContext, null), is(true));

		verify(mockBeanFactory, times(2)).getBeanDefinitionNames();
		verify(mockBeanFactory, times(2)).getBeanDefinition(eq(beanDefinitionNames[0]));
		verify(mockBeanFactory, times(2)).getBeanDefinition(eq(beanDefinitionNames[1]));
		verify(mockBeanOne, times(2)).getBeanClassName();
		verify(mockBeanTwo, times(2)).getBeanClassName();
		verify(mockConditionContext, times(2)).getBeanFactory();
	}

}
