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

package org.springframework.session.data.gemfire.config.annotation.web.http.support;

import org.springframework.data.gemfire.client.ClientCacheFactoryBean;
import org.springframework.session.data.gemfire.config.annotation.web.http.AbstractCacheCondition;

/**
 * The ClientServerCacheCondition class is a Spring Condition type used with @Conditional annotated bean definitions
 * to indicate that the annotated bean will only be created if there exists a GemFire cache instance of type
 * {@link com.gemstone.gemfire.cache.client.ClientCache} in the Spring context.
 *
 * @author John Blum
 * @see org.springframework.data.gemfire.client.ClientCacheFactoryBean
 * @see org.springframework.session.data.gemfire.config.annotation.web.http.AbstractCacheCondition
 * @since 1.1.0
 */
public class ClientServerCacheCondition extends AbstractCacheCondition {

	/**
	 * Returns a type representative of a GemFire client cache.
	 *
	 * @return GemFire client cache type representation.
	 */
	@Override
	protected Class<?> getGemFireCacheType() {
		return ClientCacheFactoryBean.class;
	}

}
