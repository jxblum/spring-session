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

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * AbstractCacheCondition is a Spring Condition type base class defining eligibility criteria for conditionally
 * registering {@link org.springframework.context.annotation.Conditional} annotated bean definitions only
 * in the presence of a GemFire cache of a specified type.
 *
 * @author John Blum
 * @see org.springframework.beans.factory.config.BeanDefinition
 * @see org.springframework.beans.factory.config.ConfigurableListableBeanFactory
 * @see org.springframework.context.annotation.Condition
 * @since 1.1.0
 */
public abstract class AbstractCacheCondition implements Condition {

	/**
	 * Determines whether state of the system satisfies the eligibility criteria of this Condition.
	 *
	 * @param context the context in which to evaluate the Condition's eligibility criteria.
	 * @param metadata metadata of the {@link org.springframework.core.type.AnnotationMetadata class}
	 * or {@link org.springframework.core.type.MethodMetadata method} being checked.
	 * @return {@code true} if the condition matches and the component can be registered
	 * or {@code false} to veto registration.
	 * @see org.springframework.context.annotation.ConditionContext
	 * @see org.springframework.core.type.AnnotatedTypeMetadata
	 */
	@Override
	public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
		return hasExpectedGemFireCacheBeanDefinition(context.getBeanFactory());
	}

	/**
	 * Determines whether the Spring {@link ConfigurableListableBeanFactory} has a bean definition with the desired
	 * GemFire cache type.
	 *
	 * @param beanFactory the {@link ConfigurableListableBeanFactory} used to search for the GemFire cache instance.
	 * @return a boolean value indicating whether the Spring container has a bean definition of the desired
	 * GemFire cache type.
	 * @see org.springframework.beans.factory.config.ConfigurableListableBeanFactory
	 */
	private boolean hasExpectedGemFireCacheBeanDefinition(ConfigurableListableBeanFactory beanFactory) {
		for (String beanName : beanFactory.getBeanDefinitionNames()) {
			BeanDefinition beanDefinition = beanFactory.getBeanDefinition(beanName);

			if (getGemFireCacheTypeName().equals(beanDefinition.getBeanClassName())) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Gets the fully-qualified class name of the desired GemFire cache type.
	 *
	 * @return a String fully-qualified class name of the desired GemFire cache type.
	 * @see #getGemFireCacheType()
	 */
	private String getGemFireCacheTypeName() {
		return getGemFireCacheType().getName();
	}

	/**
	 * Overridden by subclasses to specify the desired GemFire cache type.
	 *
	 * @return a Class object specifying the desired GemFire cache type.
	 * @see java.lang.Class
	 */
	protected abstract Class<?> getGemFireCacheType();

}
